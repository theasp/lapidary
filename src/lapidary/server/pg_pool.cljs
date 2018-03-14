(ns lapidary.server.pg-pool
  (:require
   [com.stuartsierra.component :as component]
   [lapidary.server.pg :as pg]
   [taoensso.timbre :as timbre
    :refer-macros [tracef debugf infof warnf errorf]]))

(defn make-config [{:keys [user password host port db pool-size]}]
  {:hostname  host
   :port      port
   :database  db
   :username  user
   :password  password
   :pool-size pool-size})

(defrecord PgPool [config db]
  component/Lifecycle
  (start [this]
    (infof "Starting")
    ;; TODO: Add error handling
    (let [db (-> config :db make-config pg/open-pool)]
      (assoc this :db db)))

  (stop [this]
    (infof "Stopping")
    (when db
      (.end db))
    (dissoc this :db)))

(defn new-pg-pool []
  (-> (map->PgPool {})
      (component/using [:config])))
