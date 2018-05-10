(ns lapidary.client
  (:require
   [lapidary.client.ui :as ui]
   [lapidary.client.router :as router]
   [lapidary.client.state :as state]
   [mount.core :as mount :refer [defstate]]
   [bide.core :as bide]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)]))

(timbre/set-level! :trace)
(enable-console-print!)

(defn start! []
  (infof "Stopping")
  (mount/start))

(defn stop! []
  (infof "Stopping")
  (mount/stop))

(defn restart! []
  (js/console.clear)
  (stop!)
  (start!))

(defn on-js-reload []
  #_(restart!))

(restart!)
