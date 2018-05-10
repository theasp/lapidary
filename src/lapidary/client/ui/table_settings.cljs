(ns lapidary.client.ui.table-settings
  (:require
   [lapidary.utils :as utils]
   [lapidary.client.state :as state]
   [lapidary.client.sugar :as sugar]
   [lapidary.client.query :as query]
   [lapidary.client.ui.misc :as ui-misc]
   [lapidary.client.ui.navbar :as navbar]
   [lapidary.client.ui.dialog :as dialog]
   [lapidary.client.ui.pagination :as pagination]
   [re-frame.core :as rf]
   [lapidary.client.router :as router]
   [clojure.string :as str]
   [reagent.core :as reagent :refer [atom]]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)]))

(defn stream-field-table-row [table field value percentage]
  [:tr
   [:td.is-narrow {:style {:vertical-align :middle}}
    [:div.field.has-addons
     [:p.control
      [:a.button.is-small.is-success
       {:title    "Require Value"
        :on-click #(rf/dispatch [:query-filter-add table :require field value])}
       [:span.icon [:i.fas.fa-check]]]]
     [:p.control
      [:a.button.is-small.is-danger
       {:title    "Exclude Value"
        :on-click #(rf/dispatch [:query-filter-add table :exclude field value])}
       [:span.icon [:i.fas.fa-times]]]]]]
   [:td.is-narrow {:style {:vertical-align :middle}}
    [ui-misc/popularity percentage]]
   [:td.is-narrow (goog.string/format "%0.1f%" (* 100 percentage))]
   [:td
    [:p
     (if (some? value)
       (if (= "" value)
         [:i "[empty]"]
         (str value))
       [:i "[null]"])]]])

(defn dialog-header [table]
  [:header.modal-card-head
   [:p.modal-card-title table]
   [:button.delete {:on-click #(rf/dispatch [:query-settings-visible table false])}]])

(defn date-format [table]
  [:div.field
   [:label.label "Date Format"]
   [:div.control
    [:input.input {:type :text}]]
   [:p.help [:a {:href "https://sugarjs.com/dates/#/Formatting"} "See Sugar date formatting tokens"]]])

(defn saved-search [table name query]
  (let [default? (= name "Default")]
    [:tr
     [:td
      [:button
       {:class (str "button is-small" (when default? " is-primary"))}
       [:span.icon
        (if default?
          [:i.fas.fa-star]
          [:i.far.fa-star])]]]
     [:td name]
     [:td
      [:button.button.is-small.is-danger
       [:span.icon
        [:i.fas.fa-trash]]]]]))

(defn searches-table [table]
  [:div.field
   [:label.label "Saved Searches"]
   [:div.control
    [:table.table
     [:thead
      [:tr
       [:th]
       [:th.is-expand "Name"]
       [:th]]]
     [:tbody
      (for [[name query] @(rf/subscribe [:searches table])]
        ^{:key name} [saved-search table name query])]]]])

(defn dialog-body [table]
  [:section.modal-card-body
   [date-format table]
   [searches-table table]])

(defn dialog [table]
  (debugf "Table settings dialog: %s" table)
  [dialog/dialog #(rf/dispatch [:query-settings-visible table false])
   [:dialog
    [:div.modal.is-active
     [:div.modal-background]
     [:div.modal-card
      [dialog-header table]
      [dialog-body table]
      [:footer.modal-card-foot {:style {:justify-content :flex-end}}
       [:button.button.is-pulled-right {:on-click #(rf/dispatch [:query-settings-visible table false])}
        "Ok"]]]]]])
