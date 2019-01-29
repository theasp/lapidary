(ns lapidary.client.ui.activity-indicator
  (:require
   [lapidary.utils :as utils]
   [reagent.core :as reagent :refer [atom]]
   [re-frame.core :as rf]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)]))

(defn hbar []
  (let [connections @(rf/subscribe [:active-connections :http])]
    (when-not (empty? connections)
      (debugf "Connections: %s" connections))
    [:div
     (if (empty? connections)
       {:class "activity-hbar is-active"}
       {:class "activity-hbar is-active"})]))


(defn vbar []
  (let [connections @(rf/subscribe [:active-connections :http])]
    (when-not (empty? connections)
      (debugf "Connections: %s" connections))
    [:div
     (if (empty? connections)
       {:class "activity-vbar is-active"}
       {:class "activity-vbar is-active"})]))


(defn spinner []
  (let [connections @(rf/subscribe [:active-connections :http])]
    (when-not (empty? connections)
      (debugf "Connections: %s" connections)
      [:a {:title (str "Connections: " connections)
           :class "button is-link is-loading"}])))
