(ns lapidary.server.stats
  (:require
   [mount.core :refer [defstate]]
   [taoensso.timbre :as timbre
    :refer-macros [tracef debugf infof warnf errorf]]))

(defn record-stat [name start end]
  (debugf "%s %sms" name (- end start)))

(defn wrap-stats [handler name]
  (fn [req res raise]
    (let [start (js/Date.now)
          res   (fn [result]
                  (record-stat name start (js/Date.now))
                  (res result))]
      (handler req res raise))))
