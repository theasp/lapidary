(ns lapidary.client.db
  (:require
   [lapidary.utils :as utils]
   [clojure.string :as str]
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
  {:query-str   ""
   :start-str   "2 hours ago"
   :end-str     "now"
   :columns     [[:time] [:record :hostname] [:record :message]]
   :sort-column [:time]
   :show-field  nil
   :filters     {:require {}
                 :exclude {}}
   :expand-log  nil
   :page        0
   :page-size   25
   :reverse?    true})

(def search-keys
  #{:query-str :end-str :start-str :reverse? :filters :sort-column})

(def same-query-keys
  (-> #{:page :page-size}
      (concat search-keys)))

(defn query-equal? [a b]
  #_(debugf "QUERY EQUAL: %s %s"
            (select-keys a same-query-keys)
            (select-keys b same-query-keys))
  (= (select-keys a same-query-keys)
     (select-keys b same-query-keys)))

(def query-params
  (-> #{:columns :expand-log :show-field :column-options}
      (concat same-query-keys)))

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
