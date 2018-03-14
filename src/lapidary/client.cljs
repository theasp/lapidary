(ns lapidary.client
  (:require
   [lapidary.client.ui :as ui]
   [lapidary.client.router :as router]
   [lapidary.client.state :as state]
   [reagent.core :as reagent :refer [atom]]
   [bide.core :as bide]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)]))

(timbre/set-level! :trace)
(enable-console-print!)

(defn start! []
  (router/start! state/app-state)
  (ui/start! state/app-state))

(defn stop! []
  (infof "Stopping"))

(defn restart! []
  (js/console.clear)
  (stop!)
  (start!))

(defn on-js-reload []
  #_(restart!))

(restart!)
