(ns lapidary.client.db
  (:require
   [clojure.spec.alpha :as s]
   [lapidary.utils :as utils]
   [clojure.string :as str]
   [lapidary.client.format-value :as fv]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)]))

(def table-expiry (* 60 1000))
(def tables-expiry (* 30 1000))

(def default-db
  {:tables     nil
   :view       nil
   :query      nil
   :field      nil
   :connected? false})

(def default-query
  {:query-str      ""
   :start-str      "2 hours ago"
   :end-str        "now"
   :columns        [[:time] [:record :hostname] [:record :message]]
   :column-options {[:time] {:width 16
                             :type  :timestamp}}
   :sort-column    [:time]
   :show-field     nil
   :filters        {:require {}
                    :exclude {}}
   :expand-log     nil
   :page           0
   :page-size      250
   :reverse?       true})

(def search-keys
  #{:query-str :end-str :start-str :reverse? :filters :sort-column})

(def save-search-keys
  (-> #{:columns :column-options}
      (concat search-keys)))

(def same-query-keys
  (-> #{:page :page-size}
      (concat search-keys)))

(def query-params
  (-> #{:columns :expand-log :show-field :column-options}
      (concat same-query-keys)))

(defn query-equal? [a b]
  #_(debugf "QUERY EQUAL: %s %s"
            (select-keys a same-query-keys)
            (select-keys b same-query-keys))
  (= (select-keys a same-query-keys)
     (select-keys b same-query-keys)))

(s/def :type/path
  (s/coll-of keyword?))

(s/def :type/paths
  (s/coll-of :path))

(s/def :type/value
  #(s/or :string string? :number number? :boolean boolean? :inst inst?))

(s/def :type/values
  (s/coll-of :type/value))

(s/def :column/format string?)
(s/def :column/width int?)
(s/def :column/type (s/nilable fv/formats))

(s/def :column/options
  (s/keys :opt-un [:column/type
                   :column/format
                   :column/width]))

(s/def :search/table string?)
(s/def :search/query-str string?)
(s/def :search/start-str string?)
(s/def :search/end-str string?)
(s/def :search/page int?)
(s/def :search/page-size int?)
(s/def :search/reverse? (s/nilable boolean?))

(s/def :search/columns
  (s/coll-of :type/path))

(s/def :search/filter-values
  (s/coll-of :type/value))

(s/def :search/exclude
  (s/nilable (s/map-of :type/path :type/values)))

(s/def :search/require
  (s/nilable (s/map-of :type/path :type/values)))

(s/def :search/filters
  (s/nilable (s/keys :opt-un [:search/exclude
                              :search/require])))

(s/def :search/sort-column (s/nilable :type/path))

(s/def :search/column-options
  (s/map-of :type/path :column/options))

(s/def :table/search
  (s/keys :req-un [:search/query-str
                   :search/start-str
                   :search/end-str
                   :search/columns
                   :search/sort-column]
          :opt-un [:search/column-options
                   :search/filters
                   :search/reverse?]))

(defn conform-search [search]
  (s/conform :table/search search))

(defn explain-search [search]
  (s/explain-str :table/search search))

(defn query-defaults [query]
  (merge default-query query))

(defn table-name-ok? [name]
  (and (not (str/blank? name))
       (not (str/starts-with? name "_"))
       (not (str/ends-with? name "_"))))

(defn login-ok? [db]
  (let [login (:login db)
        jwt   (:jwt login)]
    (and (some? jwt))))

(defn put-table [db table]
  (assoc-in db [:tables (:table_name table)] table))

(defn table-expired? [db table]
  (utils/expired? (get-in db [:tables table :time]) tables-expiry))

(defn oldest-time [c]
  (apply min (map :time c)))

(defn tables-expired? [db]
  (let [min-time (-> db :tables vals oldest-time)]
    (utils/expired? min-time tables-expiry)))

(defn table-loading? [db table]
  (get-in db [:tables table :loading?] false))

(defn tables-loading? [db]
  (get db :tables-loading? false))

(defn table-refresh? [db table]
  (and (not (table-loading? db table))
       (table-expired? db table)))

(defn tables-refresh? [db]
  (and (not (tables-loading? db))
       (tables-expired? db)))

(defn table-load? [db table]
  (and (login-ok? db)
       (not (table-loading? db table))))

(defn tables-load? [db]
  (and (login-ok? db)
       (not (tables-loading? db))))

(defn active-connections [db protocol]
  (->> (get-in db [:connections protocol])
       (remove #(-> (second %) (= 0)))))
