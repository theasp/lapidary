(ns lapidary.client.api
  (:require
   [clojure.string :as str]
   [clojure.walk :as walk]
   [lapidary.utils :as utils]
   [lapidary.client.state :as state]
   [lapidary.client.sugar :as sugar]
   [sqlingvo.core :as sql]
   [sqlingvo.util :as sql-util]
   [ajax.core :as ajax]
   [cljs.core.async :refer [<! chan put! close! promise-chan] :as async]
   [clojure.walk :as walk]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]))

(def db (sql/db :postgresql {:sql-name        sql-util/sql-name-underscore
                             :sql-placeholder sql-util/sql-placeholder-count}))

(def format :transit)

(def base-url (str js/window.location.protocol "//" js/window.location.hostname
                   (when-not (str/blank? js/window.location.port)
                     (str ":" js/window.location.port))))

(defn path-shallow? [path]
  (<= (count path) 1))

(defn path-text [path]
  (if (path-shallow? path)
    (first path)
    (->> (conj (vec (butlast path))
               (name (last path)))
         (cons '->>))))

(defn path-value [path]
  (if (path-shallow? path)
    (first path)
    (->> (conj (vec (butlast path))
               (name (last path)))
         (cons '->))))

(defn jwt-time-left [jwt]
  (-> (:exp jwt)
      (* 1000)
      (js/Date.)
      (- (js/Date.now))))

(defn jwt-ok? [jwt]
  (and (some? jwt)
       (> (jwt-time-left jwt) 0)))

(defn login [body ok-fn error-fn]
  (ajax/POST (str base-url "/api/login")
             {:params          body
              :format          format
              :response-format format
              :handler         ok-fn
              :error-handler   error-fn}))


(defn sql-execute! [sql jwt ok-fn error-fn]
  ;; (debugf "QUERY: %s" (first sql))
  ;; (debugf "ARGS: %s" (rest sql))
  (ajax/POST (str base-url "/api/query")
             {:headers         {:Authorization (str "Bearer " (:token jwt))}
              :params          {:execute sql}
              :format          format
              :response-format format
              :handler         ok-fn
              :error-handler   error-fn
              :keywords?       true}))

(defn sql-transaction! [sql jwt ok-fn error-fn]
  ;; (debugf "TRANSACTION:")
  ;; (doseq [transaction sql]
  ;;   (debugf "TX QUERY: %s %s" (first sql) (rest sql)))
  (ajax/POST (str base-url "/api/query")
             {:headers         {:Authorization (str "Bearer " (:token jwt))}
              :params          {:transaction sql}
              :format          format
              :response-format format
              :handler         ok-fn
              :error-handler   error-fn
              :keywords?       true}))

(defn query-window [data-query order-by offset limit & [where]]
  (sql/with db
            [:_all data-query
             :_window (sql/select db [:*]
                                  (sql/from :_all)
                                  where
                                  (sql/order-by order-by)
                                  (sql/offset offset)
                                  (sql/limit limit))]
            (sql/select db [(sql/as (sql/select db [`(cast (count :*) :integer)] (sql/from :_all))
                                    :count)
                            (sql/as `(json_agg :_window)
                                    :body)]
                        (sql/from :_window))))

(defn create-log-table [table-name jwt ok-fn error-fn]
  ;; TODO: Make sure table-name does't suck
  (-> [(sql/sql (sql/create-table db (keyword (str "public." table-name))
                                  (sql/column :id :serial :not-null? true :primary-key? true)
                                  (sql/column :tag :text)
                                  (sql/column :time :timestamptz)
                                  (sql/column :record :jsonb)))
       [(str "CREATE INDEX " table-name "_tag_idx ON " table-name "(tag)")]
       [(str "CREATE INDEX " table-name "_time_idx ON " table-name "(time)")]
       [(str "CREATE INDEX " table-name "_record_idx ON " table-name " USING GIN (record jsonb_path_ops)")]]
      (sql-transaction! jwt ok-fn error-fn)))

(defn drop-log-table [table-name jwt ok-fn error-fn]
  (-> (sql/drop-table db (keyword "public" table-name))
      (sql/sql)
      (sql-execute! jwt ok-fn error-fn)))

(defn select-field-stats [table]
  (sql/select db [(sql/as :r.key :field)
                  (sql/as `(jsonb_typeof :r.value) :type)
                  (sql/as `(count :*) :freq)]
              (sql/from (sql/as (keyword table) :t)
                        (sql/as `(jsonb_each :t.record) :r))
              (sql/group-by :r.key :type)))

(defn select-match-count [table]
  (sql/select db [(sql/as "*" :field)
                  (sql/as "" :type)
                  (sql/as `(count :*) :freq)]
              (sql/from (keyword table))))

(defn upsert-search [table name options]
  #_(debugf "upsert-search: %s" [table name options])
  (let [options (clj->js options)]
    (sql/insert db :lapidary.search [:table-name :search-name :options]
                (sql/values [{:table-name  table
                              :search-name name
                              :options     options}])
                (sql/on-conflict [:table-name :search-name]
                                 (sql/do-update {:options options})))))

(defn save-search! [table name options jwt ok-fn error-fn]
  (-> (upsert-search table name options)
      (sql/sql)
      (sql-execute! jwt ok-fn error-fn)))

(defn select-searches [table]
  (sql/select db [(sql/as `(json-agg :_searches) :searches)]
              (sql/from (sql/as :lapidary.search :_searches))
              (when table (sql/where `(= :table_name ~table)))))

(defn get-searches! [table jwt ok-fn error-fn]
  (-> (select-searches table)
      (sql/sql)
      (sql-execute! jwt ok-fn error-fn)))

(defn select-log-tables []
  (sql/select db
              [(sql/as :pg_catalog.pg_class.relname :table-name)
               (sql/as `(pg_catalog.pg_table_size :pg_catalog.pg_class.oid) :table-size)
               (sql/as :pg_catalog.pg_class.reltuples :table-rows)
               (sql/as (select-searches :pg_catalog.pg_class.relname) :searches)]
              (sql/from :pg_catalog.pg_class)
              (sql/join :pg_catalog.pg_namespace.oid
                        :pg_catalog.pg_class.relnamespace)
              (sql/where `(and (= :pg_catalog.pg_class.relkind "r")
                               (= :pg_catalog.pg_namespace.nspname "public")))))

(defn get-tables! [jwt ok-fn error-fn]
  (-> (select-log-tables)
      (sql/sql)
      (sql-execute! jwt ok-fn error-fn)))

(defn select-matching [table field start end]
  (sql/select db
              [:*]
              (sql/from table)
              (sql/where `(and (>= :time ~start) (< :time ~end)))))

(defn select-field-values [table field where]
  #_(debugf "select-field-values: %s" [table field where])
  (sql/select db
              [(sql/as (path-value field) :value)
               (sql/as `(count :*) :count)
               (sql/as `(/ (count :*)
                           (cast ~(sql/select db
                                              [(sql/as `(count :*) :total)]
                                              (sql/from table)
                                              (sql/where where))
                                 :numeric))
                       :percentage)]
              (sql/from table)
              (sql/where where)
              (sql/group-by :value)))

(defn query-field-values [table field offset limit where jwt ok-fn error-fn]
  (debugf "query-field-values: %s" [table field offset limit where])
  (-> (select-field-values table field where)
      (query-window (sql/desc :count) offset limit)
      (sql/sql)
      (sql-execute! jwt ok-fn error-fn)))
