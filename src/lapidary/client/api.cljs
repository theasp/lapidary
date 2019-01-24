(ns lapidary.client.api
  (:require
   [clojure.string :as str]
   [lapidary.search-query :as search]
   [lapidary.sql-query :as sql-query]
   [sqlingvo.core :as sql]
   [sqlingvo.util :as sql-util]
   [ajax.core :as ajax]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)]))

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

(defn login [body]
  {:uri             (str base-url "/api/login")
   :method          :post
   :params          body
   :format          (ajax/transit-request-format)
   :response-format (ajax/transit-response-format)})

(defn sql-execute [sql]
  {:uri             (str base-url "/api/query")
   :method          :post
   :params          {:execute sql}
   :format          (ajax/transit-request-format)
   :response-format (ajax/transit-response-format)})

(defn sql-transaction [all-sql]
  {:uri             (str base-url "/api/query")
   :method          :post
   :params          {:transaction all-sql}
   :format          (ajax/transit-request-format)
   :response-format (ajax/transit-response-format)})

(defn create-log-table [table]
  (-> (sql-query/create-log-table table)
      (sql-transaction)))

(defn drop-log-table [table]
  (-> (sql-query/drop-log-table table)
      (sql-execute)))

(defn save-search [table name options]
  (-> (sql-query/save-search table name options)
      (sql-execute)))

(defn get-searches [table]
  (-> (sql-query/get-searches table)
      (sql-execute)))

(defn delete-table-search [table search]
  (-> (sql-query/delete-table-search table search)
      (sql-execute)))

(defn delete-table [table]
  (-> (sql-query/delete-table table)
      (sql-transaction)))

(defn get-table-options [table]
  (-> (sql-query/get-table-options table)
      (sql-execute)))

(defn save-table-options [table options]
  (-> (sql-query/save-table-options table options)
      (sql-execute)))

(defn get-table [table]
  (-> (sql-query/get-table table)
      (sql-execute)))

(defn get-tables []
  (-> (sql-query/get-tables)
      (sql-execute)))

(defn query-field-values [table field offset limit where]
  #_(debugf "query-field-values: %s" [table field offset limit where])
  (-> (sql-query/query-field-values table field offset limit where)
      (sql-execute)))

(defn search-query [table query-options]
  (-> (search/search-query table query-options)
      (sql-execute)))
