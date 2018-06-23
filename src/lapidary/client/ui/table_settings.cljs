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

(defn saved-search [table name query default?]
  [:tr
   [:td
    [:button
     (if default?
       {:class "button is-small is-primary"}
       {:class    "button is-small"
        :on-click #(rf/dispatch [:table-set-default-search table name])})
     [:span.icon
      (if default?
        [:i.fas.fa-star]
        [:i.far.fa-star])]]]
   [:td name]
   [:td
    [:button.button.is-small.is-danger
     {:on-click #(rf/dispatch [:query-confirm-search-delete table name])}
     [:span.icon
      [:i.fas.fa-trash]]]]])


(defn searches-table [table]
  (let [options  @(rf/subscribe [:table-options table])
        searches @(rf/subscribe [:table-searches table])
        default  (get options :default-search "Default")]
    [:div.field
     [:div.control
      [:table.table
       [:thead
        [:tr
         [:th]
         [:th.is-expand "Name"]
         [:th]]]
       [:tbody
        (for [[name query] searches]
          ^{:key name}
          [saved-search table name query (= name default)])]]]]))

(defn delete-table [table]
  [:button.button.is-danger
   {:on-click #(rf/dispatch [:query-confirm-table-delete table])}
   "Delete Table"])

(defn maintenance-tab [table]
  [:div
   [:div.field
    [:div.control
     [date-format table]]]
   [:div.field
    [:label.label "DANGER"]
    [:div.control
     [delete-table table]]]])

(def column-types
  [[:auto "Auto"]
   [:string "String"]
   [:timestamp "Timestamp"]
   [:integer "Integer"]
   [:boolean "Boolean"]])

(defn column-table-row [table column]
  (let [options     @(rf/subscribe [:query-column-options table column])
        field       @(rf/subscribe [:query-field table column])
        column-type (or (:type options)
                        (:type field)
                        :auto)]
    [:tr
     [:td
      (debugf "Column: %s" column)
      [:div.buttons.has-addons
       [:button.button
        [:span.icon
         [:i.fas.fa-arrow-up]]]

       [:button.button
        [:span.icon
         [:i.fas.fa-arrow-down]]]]]
     [:td (ui-misc/format-path column)]
     [:td
      [:div.field
       [:div.control
        [:div.select
         [:select
          {:value     (name column-type)
           :on-change #(debugf "Select: %s" (-> % .-target .-value keyword))}
          (for [[type label] column-types]
            (let [selected? (= column-type type)]
              ^{:key type}
              [:option
               {:value (name type)}
               label]))]]]]]
     [:td
      [:input.input {:size 8}]]
     [:td
      [:input.input {:size 8}]]]))

(defn columns-tab [table]
  [:table.table
   [:thead
    [:tr
     [:th]
     [:th "Column"]
     [:th "Type"]
     [:th "Format"]
     [:th "Size"]]]
   [:tbody
    (for [column @(rf/subscribe [:query-columns table])]
      ^{:key column}
      [column-table-row table column])]])



(defn tabs [active-tab]
  [:div.tabs.is-toggle.is-centered
   [:ul
    [:li
     {:class (when (= :columns @active-tab) "is-active")}
     [:a {:on-click #(reset! active-tab :columns)}
      [:span.icon.is-small
       [:i.fas.fa-bookmark]]
      [:span "Columns"]]]
    [:li
     {:class (when (= :searches @active-tab) "is-active")}
     [:a {:on-click #(reset! active-tab :searches)}
      [:span.icon.is-small
       [:i.fas.fa-bookmark]]
      [:span "Saved Searches"]]]
    [:li
     {:class (when (= :maintenance @active-tab) "is-active")}
     [:a {:on-click #(reset! active-tab :maintenance)}
      [:span.icon.is-small
       [:i.fas.fa-cog]]
      [:span "Maintenance"]]]]])

(defn dialog-body [_]
  (let [active-tab (atom :columns)]
    (fn [table]
      (let [table-options @(rf/subscribe [:table-options table])]
        [:section.modal-card-body
         [tabs active-tab]
         (case @active-tab
           :columns     [columns-tab table]
           :searches    [searches-table table]
           :maintenance [maintenance-tab table])]))))

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
       [:button.button.is-pulled-right.is-primary
        {:on-click #(rf/dispatch [:query-settings-visible table false])}
        "Ok"]]]]]])
