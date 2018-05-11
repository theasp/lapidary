(ns lapidary.server.pg-schema
  (:require
   [cljs.nodejs :as nodejs]
   [clojure.string :as str]
   [lapidary.server.pg :as pg]
   [lapidary.utils :as utils :refer [exception?]]
   [lapidary.server.config :refer [env]]
   [mount.core :refer [defstate]]
   ["postgrator" :as Postgrator]
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

(defn start-pg-schema []
  (infof "Running")
  (let [db (:db @env)]
    (-> #js {:migrationDirectory "resources/postgrator"
             :driver             "pg"
             :schemaTable        "postgrator.schemaversion"
             :host               (:hostname db)
             :port               (:port db)
             :database           (:database db)
             :username           (:username db)
             :password           (:password db)}
        (Postgrator.)
        (.migrate)
        (.then log-migrations)
        (.catch (fn [error]
                  (log-migrations (.-appliedMigrations error))
                  (errorf "DB migration failure: %s" error)
                  (nodejs/process.exit 1))))))

(defstate pg-schema
  :start (start-pg-schema))
