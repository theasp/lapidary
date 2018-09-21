(ns lapidary.client.query
  (:require
   [sqlingvo.core :as sql]
   [sqlingvo.util :as sql-util]
   [lapidary.client.state :as state]
   [lapidary.client.sugar :as sugar]
   [lapidary.client.api :as api]
   [instaparse.core :as insta :refer-macros [defparser]]
   [lapidary.utils :as utils]
   [taoensso.timbre :as timbre :refer-macros (tracef debugf infof warnf errorf)]))

(def db (sql/db :postgresql
                {:sql-name        sql-util/sql-name-underscore
                 :sql-placeholder sql-util/sql-placeholder-count}))

(defparser p
  "
  query  = <whitespace>* ( clause )* <whitespace>*
  clause = <whitespace>* ( not? <whitespace>* <'('> grouping clause* <')'> | not? <whitespace>* term ) <whitespace>*
  term = ( meta-keyword / record-keyword ) <':'> comparison? value <whitespace>*

  grouping = and | or
  meta-keyword = <'@'> WORD
  record-keyword = WORD
  whitespace = #'\\s+'

  not = < '!' / 'not' whitespace >
  and = < 'and' | '&&' >
  or = < 'or' | '||' >

  lt =   < '<'  / 'lt.' >
  le =   < '<=' / 'le.' >
  gt =   < '>'  / 'gt.' >
  ge =   < '>=' / 'ge.' >
  eq =   < '==' / '='  / 'eq.' >
  fts =  < '~' / '@@' / 'fts.' >
  auto = < 'auto.' / '' >

  comparison = le / ge / lt / gt / eq / fts / auto
  value = string / float / integer / null / word

  null = <'null' | 'nil' | 'undefined'>

  string = <'\"'> ( ESCAPED-CHAR | NORMAL-CHAR )+ <'\"'>

  <NORMAL-CHAR> = #'[^\"]'
  <ESCAPED-CHAR> = #'\\\\.'

  word = #'[^\":() ]+'
  <WORD> = #'[^\":() ]+'

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

(defn op-auto [field value]
  `(or (like ~(api/path-text field)
             ~(str "%" value "%"))
       (~(keyword "@@")
        (to_tsvector "english" ~(api/path-value field))
        (plainto_tsquery "english" ~(str value)))))

(defn op [f]
  (fn [field value]
    (if (api/path-shallow? field)
      `(~f ~(first field) ~value)
      `(~f ~(api/path-value field) (cast ~(utils/clj->json value) :jsonb)))))

(def op-eq (op '=))
(def op-le (op '<=))
(def op-lt (op '<))
(def op-gt (op '>))
(def op-ge (op '>=))

(def operations
  {:auto op-auto
   :fts  op-fts
   :like op-like
   :eq   op-eq
   :le   op-le
   :lt   op-lt
   :gt   op-gt
   :ge   op-ge})

(defn parse-term [field & options]
  (let [options (reduce #(assoc %1 (first %2) (second %2)) {} options)
        value   (get options :value)]
    (if-let [operation (->  (:comparison options)
                            (first)
                            (or :auto)
                            (operations))]
      (operation field value)
      (warnf "Unknown operation: %s" (get options :comparison)))))

(def parse-tx
  {:string         str
   :meta-keyword   (fn [k] [(keyword k)])
   :record-keyword (fn [k] [:record (keyword k)])
   :word           identity
   :term           parse-term
   :integer        js/parseInt
   :float          js/parseFloat
   :null           (fn [_] nil)
   :grouping       (fn [grouping]
                     (condp = (first grouping)
                       :and 'and
                       :or  'or))
   :clause         (fn [& q]
                     (if (= [:not] (first q))
                       `(not ~q)
                       q))
   :query          (fn [& q]
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

(defn execute-query [table query-options]
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
        (sql/sql)
        (api/sql-execute))))
