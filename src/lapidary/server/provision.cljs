(ns lapidary.server.provision
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [lapidary.utils :as utils]
   [lapidary.sql-query :as sql-query]
   [lapidary.server.pg :as pg]
   [lapidary.server.pg-pool :refer [pg-pool]]
   [cljs.core.async
    :refer [<! chan put! close! onto-chan to-chan]
    :as async]
   [lapidary.server.config :as config :refer [env]]
   [mount.core :refer [defstate]]
   [taoensso.timbre :as timbre
    :refer-macros [tracef debugf infof warnf errorf]]))

(defn provision-table [db table]
  (infof "Provisioning table %s" table)
  (go
    (let [sql    (sql-query/create-log-table table)
          result (<! (pg/transaction! db [sql]))]
      (if (utils/error? result)
        (errorf "Provisioned table %s: %s" table result)
        (infof "Provisioned table %s" table)))))

(defn start-provision []
  (infof "Starting provision")
  (let [db      (:pool @pg-pool)
        options (:provision @env)]
    (doseq [table (:tables options)]
      (provision-table db table))))

(defstate tables
  :start (start-provision))
