(ns lapidary.client.ui.navbar
  (:require
   [lapidary.utils :as utils]
   [lapidary.client.router :as router]
   [reagent.core :as reagent :refer [atom]]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]))

(defn navbar [options menu]
  (let [menu-visible? (atom false)]
    (fn [options menu]
      [:nav.navbar.is-fixed-top.is-link.has-shadow
       [:div.navbar-brand
        (for [item (:brand options)]
          ^{:key (:key item)} [:div.navbar-item (:item item)])
        [:button
         {:class    (str "button navbar-burger is-link is-shadowless" (when @menu-visible? " is-active"))
          :on-click #(swap! menu-visible? not)}
         [:span]
         [:span]
         [:span]]]
       [:div {:class (str "navbar-menu" (when @menu-visible? " is-active"))}
        menu]])))
