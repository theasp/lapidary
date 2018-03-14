(ns lapidary.client.ui.navbar
  (:require
   [lapidary.utils :as utils]
   [lapidary.client.router :as router]
   [reagent.core :as reagent :refer [atom]]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]))

(defn navbar [ctx options]
  (let [menu-visible?    (:navbar-menu-visbile? @ctx)
        view             (:view @ctx)
        list-tables-view (when (not= view :lapidary/list-tables)
                           #(router/navigate! {:name :lapidary/list-tables}))
        brand            (:brand options)]
    [:nav.navbar.is-fixed-top.is-link.has-shadow
     [:div.navbar-brand
      (for [item brand]
        ^{:key (:key item)} [:div.navbar-item (:item item)])
      [:button
       {:class    (str "button navbar-burger is-link is-shadowless" (when menu-visible? " is-active"))
        :on-click #(swap! ctx update :navbar-menu-visible? not)}
       [:span]
       [:span]
       [:span]]]
     [:div {:class (str "navbar-menu" (when menu-visible? " is-active"))}
      [:div.navbar-end
       [:div.navbar-item
        [:div.field.is-grouped
         [:p.control
          [:a.button.is-link
           [:span.icon [:i.fas.fa-cog]]
           [:span "Settings"]]]
         [:p.control
          [:a.button.is-primary
           {:disabled (= :lapidary/list-tables (:name view))
            :on-click list-tables-view}
           [:span.icon [:i.fas.fa-table]]
           [:span "Tables"]]]]]]]]))
