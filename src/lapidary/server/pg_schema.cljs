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

(defrecord PgSchema [config]
  component/Lifecycle
  (start [this]
    (infof "Running")
    (let [db         (:db config)
          migration  (new postgrator #js
                          {:migrationDirectory "resources/postgrator"
                           :driver             "pg"
                           :host               (:host db)
                           :port               (:port db)
                           :database           (:db db)
                           :username           (:user db)
                           :password           (:password db)})
          on-success log-migrations
          on-error   (fn [error]
                       (log-migrations (.-appliedMigrations error))
                       (errorf "DB migration failure: %s" error)
                       (nodejs/process.exit 1))]

      (-> migration
          (.migrate)
          (.then on-success)
          (.catch on-error))

      (assoc this :db db)))

  (stop [this]))

(defn new-pg-schema []
  (-> (map->PgSchema {})
      (component/using [:config])))
