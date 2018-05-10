(ns lapidary.server.pg-pool
  (:require
   [lapidary.server.pg :as pg]
   [lapidary.server.config :refer [env]]
   [mount.core :refer [defstate]]
   [taoensso.timbre :as timbre
    :refer-macros [tracef debugf infof warnf errorf]]))

(defn start-pg-pool []
  (infof "Starting")
  ;; TODO: Add error handling
  (pg/open-pool (:db @env)))

(defn stop-pg-pool [pg-pool]
  (infof "Stopping")
  (when pg-pool
    (.end pg-pool)))

(defstate pg-pool
  :start (start-pg-pool)
  :stop (stop-pg-pool pg-pool))
