(ns lapidary.search-query
  (:require
   [sqlingvo.core :as sql]
   [sqlingvo.util :as sql-util]
   [lapidary.sugar :as sugar]
   [lapidary.sql-query :as api]
   [instaparse.core :as insta :refer-macros [defparser]]
   [lapidary.utils :as utils]
   [taoensso.timbre :as timbre :refer-macros (tracef debugf infof warnf errorf)]))

(def db (sql/db :postgresql
                {:sql-name        sql-util/sql-name-underscore
                 :sql-placeholder sql-util/sql-placeholder-count}))

(defparser p
  "
  query  = <whitespace>* ( clause )* <whitespace>*
  clause = <whitespace>* ( not? <whitespace>* <'('> grouping clause* <')'> | not? term ) <whitespace>*
  term = ( meta-keyword / record-keyword ) <':'> (range / value-list / value) <whitespace>*

  range = range-start <whitespace>+ <'to'> <whitespace>+ range-end

  range-start = (range-start-include / range-start-exclude) <whitespace>* value
  range-start-include = <'['>
  range-start-exclude = <'{'>

  range-end = value <whitespace>* (range-end-include / range-end-exclude)
  range-end-include = <']'>
  range-end-exclude = <'}'>

  value-list = <'('> (<whitespace>* value <whitespace>*)+ <')'>

  grouping = and | or
  meta-keyword = <'@'> WORD
  record-keyword = WORD
  whitespace = #'\\s+'

  not = < '!' / ('not' whitespace) >
  and = < 'and' | '&&' >
  or = < 'or' | '||' >

  value = string / float / integer / null / word

  null = <'null' | 'nil' | 'undefined'>

  string = <'\"'> STRING <'\"'>
  <STRING> = #'([^\"]|\\\\.)*'

  word = #'[^\":()\\[\\]{} ]+'
  <WORD> = #'[^\":()\\[\\]{} ]+'

  integer = INTEGER
  float = FLOAT

  <EXPONENT> = #'[eE][+-]?[0-9]+'
  <FLOAT> = #'[+-]?[0-9]+\\.[0-9]+' EXPONENT?
  <INTEGER> = #'[+-]?[0-9]+' EXPONENT?"
  :string-ci true)


(defn op-fts [field value]
  `(~(keyword "@@")
    (to_tsvector "english" ~(if (api/path-shallow? field)
                              (first field)
                              (api/path-text field)))
    (plainto_tsquery "english" ~(str value))))

(defn op-like [field value]
  `(like (cast ~(if (api/path-shallow? field)
                  (first field)
                  (api/path-text field))
               :text)
         ~(str value)))

(defn field-op [field op value]
  (if (api/path-shallow? field)
    `(~op ~(first field) ~value)
    `(~op ~(api/path-value field) (cast ~(utils/clj->json value) :jsonb))))

(defn op-auto [field value]
  (if (number? value)
    (field-op field `= value)
    `(or (like ~(api/path-text field)
               ~(str "%" value "%"))
         (~(keyword "@@")
          (to_tsvector "english" ~(api/path-value field))
          (plainto_tsquery "english" ~(str value))))))

(defn match-range [field [start end]]
  (debugf "Range %s to %s" start end)
  (let [start  (when-not (= "*" (:value start))
                 (field-op field (:op start) (:value start)))
        end    (when-not (= "*" (:value end))
                 (field-op field (:op end) (:value end)))
        result (remove nil? [start end])]
    (if (> (count result) 1)
      (conj result `and)
      (first result))))

(defn match-value [field value]
  (op-auto field (first value)))

(defn match-value-list [field values]
  (let [result (map #(op-auto field (second %)) values)]
    (if (> (count result) 1)
      (-> (mapcat rest result)
          (conj `or))
      (first result))))

(defn parse-term [field args]
  (let [value-type (first args)
        options    (rest args)]
    #_(debugf "Options: %s" options)
    (case value-type
      :range      (match-range field options)
      :value      (match-value field options)
      :value-list (match-value-list field options))))

(def parse-tx
  {:meta-keyword        (fn [k] [(keyword k)])
   :record-keyword      (fn [k] [:record (keyword k)])
   :word                identity
   :term                parse-term
   :integer             js/parseInt
   :float               js/parseFloat
   :string              identity
   :null                (constantly nil)
   :and                 (constantly :and)
   :or                  (constantly :or)
   :range-start-include (constantly `>=)
   :range-start-exclude (constantly `>)
   :range-end-include   (constantly '<=)
   :range-end-exclude   (constantly `<)
   :range-start         (fn [op value]
                          {:op    op
                           :value (-> value second)})
   :range-end           (fn [value op]
                          {:op    op
                           :value (-> value second)})
   :grouping            (fn [type]
                          (condp = type
                            :and 'and
                            :or  'or))
   :not                 (constantly 'not)
   :clause              list
   :query               (fn [& q]
                          (when q
                            (if (> (count q) 1)
                              (conj q 'or)
                              (first q))))})

(defn parse-error [{:keys [index reason line column text] :as err}]
  (errorf "Error parsing query (%s,%s): %s %s" line column index text)
  (errorf "Reason: %s" reason)
  err)

(defn query-parse [query]
  (let [parsed (insta/parse p query)]
    (if (insta/failure? parsed)
      (select-keys parsed [:index :reason :line :column :text])
      parsed)))

(defn query->where [query]
  #_(debugf "Raw query: %s" query)
  (let [parsed (insta/parse p query)]
    #_(debugf "Parsed: %s" parsed)
    (if (insta/failure? parsed)
      (parse-error parsed)
      (insta/transform parse-tx parsed))))

(defn cast-json [data]
  `(cast ~(.stringify js/JSON (clj->js data)) :jsonb))

(defn data->json [data]
  [(cast-json data)])

(defn field-values->where [field values]
  #_(debugf "field-values->where: %s" field)
  #_(if (api/path-shallow? field)
      (debugf "shallow: %s" (first field))
      (debugf "deep: %s" (api/path-value field)))

  (if (api/path-shallow? field)
    `(in ~(first field)
         ~(sql/values (map vector values)))
    `(in ~(api/path-value field)
         ~(sql/values (map data->json values)))))


(defn empty-filter? [[key value]]
  (empty? value))

(defn require-filter->where [[field values]]
  (when-not (empty? values)
    (field-values->where field values)))

(defn exclude-filter->where [[field values]]
  (when-not (empty? values)
    `(not ~(field-values->where field values))))

(defn filter->where [[type filters]]
  (case type
    :require (map require-filter->where filters)
    :exclude (map exclude-filter->where filters)))

(defn filters->where [filters]
  (let [filters (->> filters
                     (mapcat filter->where filters)
                     (remove nil?))]
    (let [where (when-not (empty? filters)
                  (if (> (count filters) 1)
                    (conj filters 'and)
                    (first filters)))]
      where)))

(defn query-filters [highest start-time end-time filters query]
  #_(debugf "query-filters: %s" [highest start-time end-time filters query])
  (->> `(and (<= :time ~end-time)
             (>= :time ~start-time)
             ~(when highest `(> :id ~highest))
             ~filters
             ~query)
       (remove nil?)
       (apply list)))

(def query-debug-keys [:table :highest :page-size :page :query-str :start-str :end-str :sort-column :reverse? :filters])

(defn search-query [table query-options]
  #_(debugf "QUERY: %s %s" table (select-keys query-options query-debug-keys))
  (let [{:keys [highest page-size page query-str start-str end-str sort-column reverse? filters]}
        query-options

        sort-column   (or sort-column [:time])
        start-time    (sugar/parse-time start-str)
        end-time      (sugar/parse-time end-str)
        query         (query->where query-str)
        filters-where (filters->where filters)
        where         (query-filters nil start-time end-time filters-where query)
        order         (if reverse? sql/desc sql/asc)
        sort-column   (if (api/path-shallow? sort-column)
                        (first sort-column)
                        (api/path-value sort-column))

        logs-sql   (sql/select db
                               [:*]
                               (sql/from table)
                               (sql/where where))
        window-sql (sql/select db
                               [:*]
                               (sql/from :_logs)
                               (when sort-column (sql/order-by (order sort-column)))
                               (sql/offset (* page page-size))
                               (sql/limit page-size)
                               (when highest (sql/where `(> :_logs.id ~highest))))
        stats-sql  (sql/union (api/select-field-stats :_logs)
                              (api/select-match-count :_logs))]

    (-> (sql/with db [:_logs logs-sql
                      :_window window-sql
                      :_stats stats-sql]
                  (sql/select db
                              [(sql/as (sql/select db [`(json_agg :_window.*)] (sql/from :_window)) :logs)
                               (sql/as (sql/select db [`(json_agg :_stats.*)] (sql/from :_stats)) :stats)]))
        (sql/sql))))
