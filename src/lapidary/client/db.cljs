(ns lapidary.client.db
  (:require
   [clojure.string :as str]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)]))

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
   :page-size   100
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
  (-> #{:columns :expand-log :show-field}
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
    #_(debugf "LOGIN: %s" (some? jwt))
    (and (some? jwt))))
