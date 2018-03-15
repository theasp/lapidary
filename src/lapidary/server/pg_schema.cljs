(ns lapidary.server.pg-schema
  (:require
   [cljs.nodejs :as nodejs]
   [clojure.string :as str]
   [com.stuartsierra.component :as component]
   [lapidary.server.pg :as pg]
   [lapidary.utils :as utils :refer [exception?]]
   ["postgrator" :as postgrator]
   [cljs.core.async :refer [<! chan put! close!]]
   [taoensso.timbre :as timbre
    :refer-macros [tracef debugf infof warnf errorf]])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]))

(defn log-migrations [migrations]
  (let [migrations (map #(js->clj % :keywordize-keys true) migrations)]
    (infof "Migrations applied: %s" (count migrations))
    (doseq [migration migrations]
      (infof "%s" (:filename migration)))))

(defn make-config [{:keys [user password host port db pool-size]}]
  {:hostname  host
   :port      port
   :database  db
   :username  user
   :password  password
   :pool-size pool-size})

(defrecord PgSchema [config database]
  component/Lifecycle
  (start [this]
    (infof "Running")
    (let [db          (:db config)
          conn-config (make-config db)
          pg          (pg/open-db conn-config)]
      (debugf "Conn: %s" conn-config)
      (go (debugf "Creating schema")
          (when-let [conn (<! (pg/connect! pg))]
            (<! (pg/execute! pg ["CREATE SCHEMA IF NOT EXISTS postgrator;"]))
            (.end pg))

          (-> #js {:migrationDirectory "resources/postgrator"
                   :driver             "pg"
                   :schemaTable        "postgrator.schemaversion"
                   :host               (:host db)
                   :port               (:port db)
                   :database           (:db db)
                   :username           (:user db)
                   :password           (:password db)}
              (postgrator.)
              (.migrate)
              (.then log-migrations)
              (.catch (fn [error]
                        (log-migrations (.-appliedMigrations error))
                        (errorf "DB migration failure: %s" error)
                        (nodejs/process.exit 1)))))

      (assoc this :db db)))

  (stop [this]))

(defn new-pg-schema []
  (-> (map->PgSchema {})
      (component/using [:config])))
