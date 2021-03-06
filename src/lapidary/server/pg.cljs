(ns lapidary.server.pg
  (:require
   [clojure.string :as string]
   [lapidary.utils :as utils :refer [obj->map]]
   [goog.object :as gobj]
   [cljs.nodejs :as nodejs]
   [cljs.core.async
    :refer [<! chan put! close! onto-chan to-chan]]
   ["pg" :as pg]
   ["pg-native" :as pg-native]
   [taoensso.timbre :as timbre
    :refer-macros [tracef debugf infof warnf errorf]])
  (:require-macros
   [cljs.core.async.macros :as m :refer [go]]))

#_(def pg
    "The Node.js PostgreSQL client."
    (let [package (nodejs/require "pg")]
      (or (.-native package) package)))


#_(debugf "PG: %s" (pg.native.Pool. #js {}))

(defn native? [v]
  (or (nil? v)
      (string? v)
      (number? v)
      (boolean? v)
      (keyword? v)
      (vector? v)
      (map? v)
      (list? v)
      (fn? v)
      (inst? v)))


(defn result-chan [f & args]
  (let [channel  (chan 1)
        callback (fn [err rs]
                   (put! channel (or err rs))
                   (close! channel))]
    (apply f (concat args [callback]))
    channel))

(defn open-db
  "Creates a db connection"
  [{:keys [hostname port username password database
           pool-size ssl validation-query pipeline]
    :as   config}]
  (doseq [param [:hostname :username :password :database]]
    (when (nil? (param config))
      (errorf (str param " is required"))
      (->> (str param " is required")
           (new js/Error)
           (throw))))

  (pg/native.Client. #js {"host"     hostname
                          "port"     (or port 5432)
                          "user"     username
                          "database" database
                          "password" password
                          "ssl"      (boolean ssl)}))

(defn open-pool
  "Creates a db connection pool"
  [{:keys [hostname port username password database
           pool-size ssl validation-query pipeline idle-timeout]
    :as   config}]
  (doseq [param [:hostname :username :password :database]]
    (when (nil? (param config))
      (errorf (str param " is required"))
      (throw (new js/Error (str param " is required")))))

  (pg/native.Pool. #js {"host"              hostname
                        "port"              (or port 5432)
                        "user"              username
                        "database"          database
                        "password"          password
                        "ssl"               (boolean ssl)
                        "max"               (or pool-size 20)
                        "idleTimeoutMillis" (or idle-timeout 30000)}))

(defn close-db!
  "Closes a db connection pool"
  [db]
  (.close db))

(defn connect!
  ([db]
   (result-chan connect! db))
  ([db f]
   (.connect db
             (fn [err client done]
               (f nil [client done err])))))

(defn result [result]
  {:fields    (.-fields result)
   :rows      (.-rows result)
   :row-count (.-rowCount result)
   :command   (.-command result)})

(defn execute!
  "Executes an sql statement with parameters and returns result rows and update count."
  ([db sql]
   (result-chan execute! db sql))
  ([db [sql & params] f]
   (tracef "SQL: %s %s" sql (vec params))
   (try
     (.query db sql (-> params vec clj->js)
             (fn [err rs]
               (if err
                 (f err nil)
                 (f nil (result rs)))))
     (catch js/Object err
       (f err nil)))))

(def begin-transaction ["BEGIN TRANSACTION"])
(def rollback-transaction ["ROLLBACK TRANSACTION"])
(def commit-transaction ["COMMIT TRANSACTION"])

(defn error? [e]
  (instance? js/Error e))

(defn transaction!
  "Executes sql statements with parameters and return results."
  ([db all-sql]
   (result-chan transaction! db all-sql))
  ([db [all-sql & params] f]
   (go
     (let [start-time (js/Date.now)
           result     (<! (execute! db begin-transaction))]
       (if (error? result)
         (f result nil)
         (let [results (loop [results []
                              all-sql all-sql]
                         (if-let [sql (first all-sql)]
                           (let [result (<! (execute! db sql))]
                             (if (error? result)
                               result
                               (recur (conj results result)
                                      (rest all-sql))))
                           results))]
           (if (error? results)
             (do
               (<! (execute! db rollback-transaction))
               (f results nil))
             (let [result (<! (execute! db commit-transaction))]
               (if (error? result)
                 (f result nil)
                 (f nil {:result results
                         :time   (- (js/Date.now) start-time)}))))))))))
