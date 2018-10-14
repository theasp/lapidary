(ns lapidary.server
  (:require
   [cljs.nodejs :as nodejs]
   [cljs.core.async :refer [<! chan put! close! onto-chan to-chan]]
   [clojure.string :as str]
   [lapidary.server.web-handler]
   [lapidary.server.pg-pool]
   [lapidary.server.provision]
   [lapidary.server.api :as api]
   [lapidary.server.pg-schema :as pg-schema]
   [lapidary.server.config :refer [env]]
   [mount.core :as mount :refer [defstate]]
   [taoensso.timbre :as timbre :refer-macros [tracef debugf infof warnf errorf]]
   ["death" :as on-death])
  (:require-macros
   [cljs.core.async.macros :as m :refer [go]]))

(enable-console-print!)

(set! js/console.debug js/console.log)

(timbre/set-level! :debug)

(defn death-handler [signal err]
  (if (some? err)
    (do
      (errorf "Uncaught exception: %s" err)
      (tracef "Cause:")
      (.exit js/process 1))
    (do
      (infof "Recieved signal: %s" signal)
      (.exit js/process 0))))

(on-death death-handler)

(defn start! []
  (infof "Lapidary starting...")
  (mount/start)
  (infof "Lapidary running"))

(defn stop! []
  (infof "Lapidary stopping...")
  (mount/stop)
  (infof "Lapidary stopped"))
