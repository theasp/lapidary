(ns lapidary.client.ui.tables
  (:require
   [lapidary.utils :as utils]
   [lapidary.client.ui.navbar :as navbar]
   [lapidary.client.state :as state]
   [lapidary.client.api :as api]
   [lapidary.client.db :as db]
   [clojure.string :as str]
   [reagent.core :as reagent :refer [atom]]
   [re-frame.core :as rf]
   [cljs.core.async :refer [<! chan put! close! promise-chan] :as async]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]))

(def column-class "column is-3-fullhd is-4-widescreen is-4-desktop is-6-tablet")

(defn list-table [view table]
  (let [table-name (:table_name table)
        searches   (:searches table)]
    [:div {:class column-class}
     [:div.hero.is-rounded.is-link.is-tall
      [:div.hero-body
       [:div.field
        [:div.control.has-text-centered
         [:button.button.is-primary.is-large {:on-click #(rf/dispatch [:tables-query table-name (get searches "Default")])}
          [:span.icon
           [:i.fas.fa-search]]
          [:span (:table_name table)]]]]
       [:div.field
        [:div.control.has-text-centered
         (utils/byte-size-str (:table_size table))
         " / "
         (utils/si-size-str (:table_rows table)) " rows"]]
       [:div.buttons
        (for [[name query] (sort-by :search_name searches)]
          ^{:key name}
          [:button.button {:on-click #(rf/dispatch [:tables-query table-name query])}
           [:span.icon [:i.fas.fa-bookmark]]
           [:span name]])]]]]))

(defn new-table-card [state]
  (let [name     (:name @state)
        name-ok? (db/table-name-ok? name)]
    [:div.hero.is-light.is-rounded
     [:div
      [:button.delete.is-pulled-right
       {:on-click #(reset! state nil)}]]
     [:div.hero-body
      [:div.field
       [:div.control
        [:input.input
         {:class       (str "input" (when-not name-ok? " is-danger"))
          :placeholder "Name..."
          :on-change   #(swap! state assoc :name (-> % .-target .-value str/lower-case))
          :value       name}]]]
      [:div.field.has-text-centered
       [:div.control.has-text-right
        [:a.button.is-primary
         {:disabled (not name-ok?)
          :on-click #(rf/dispatch [:create-table])}
         [:span "Create"]]]]]]))

(defn hidden-new-table [state]
  [:div.hero.is-light.is-rounded.is-tall
   [:div.hero-body
    [:div.field
     [:div.control.has-text-centered
      [:button.button.is-primary.is-large {:on-click #(swap! state assoc :card-mode :form)}
       [:span.icon
        [:i.fas.fa-plus]]
       [:span "New"]]]]
    [:div.field.has-text-centered
     [:div.control.has-text-centered
      "Create new table"]]]])

(defn new-table []
  (let [state (atom nil)]
    (fn []
      [:div {:class column-class}
       (case (get @state :card-mode :hidden)
         :hidden [hidden-new-table state]
         :form   [new-table-card state])])))

(defn list-tables [view]
  [:div
   [navbar/navbar state/app-state nil]
   [:section.section
    [:div.columns.is-multiline
     (for [table @(rf/subscribe [:tables])]
       ^{:key (:table_name table)}
       [list-table view table])
     [new-table]]]])
