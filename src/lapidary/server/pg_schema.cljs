(ns lapidary.server.pg-schema
  (:require
   [com.stuartsierra.component :as component]
   [lapidary.server.pg :as pg]
   [lapidary.utils :as utils :refer [exception?]]
   [cljs.core.async :refer [<! chan put! close!]]
   [taoensso.timbre :as timbre
    :refer-macros [tracef debugf infof warnf errorf]])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]))



(defrecord PgSchema [config]
  component/Lifecycle
  (start [this]
    (infof "Running")
    (let [db (:db config)]
      (def config {:migration-dir "resources/postgrator"
                   :schema-table  "postgrator"
                   :driver        "pg"
                   :database      (:db db)
                   :username      (:user db)
                   :password      (:password db)
                   :host          (:host db)
                   :port          (:port db)})

      ;; migrate to the latest version
      #_(migrations/migrate config)

      ;;(reset-db db)
      (assoc this :db db)))

  (stop [this]))

(defn new-pg-schema []
  (-> (map->PgSchema {})
      (component/using [:config])))
