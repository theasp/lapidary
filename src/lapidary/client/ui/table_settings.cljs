(ns lapidary.client.ui.table-settings
  (:require
   [lapidary.utils :as utils]
   [lapidary.client.state :as state]
   [lapidary.client.sugar :as sugar]
   [lapidary.client.query :as query]
   [lapidary.client.format-value :as format-value]
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

(defn column-table-row [table pos column column-count]
  (let [options     @(rf/subscribe [:query-column-options table column])
        column-type (get options :type :auto)
        width       (get options :width 12)]
    [:tr
     [:td
      [:div.buttons.has-addons
       [:button.button
        {:disabled (= pos 0)
         :on-click #(rf/dispatch [:query-column-left table column])}
        [:span.icon
         [:i.fas.fa-arrow-up]]]

       [:button.button
        {:disabled (= pos (- column-count 1))
         :on-click #(rf/dispatch [:query-column-right table column])}
        [:span.icon
         [:i.fas.fa-arrow-down]]]]]
     [:td (ui-misc/format-path column)]
     [:td
      [:div.field
       [:div.control
        [:div.select
         [:select
          {:value     (name column-type)
           :on-change #(rf/dispatch [:query-column-type table column (-> % .-target .-value keyword)])}
          (for [[type format] (sort-by first format-value/formats)]
            ^{:key type}
            [:option {:value (name type)} (:name format)])]]]]]
     [:td
      (when-not (= column-type :auto)
        [:div.field
         [:div.control
          [:input.input
           {:size      8
            :value     (get options :format "")
            :on-change #(rf/dispatch [:query-column-format table column (-> % .-target .-value)])}]]])]
     [:td
      [:div.field.has-addons
       [:div.control
        [:button.button
         {:on-click #(rf/dispatch [:query-column-width-dec table column])
          :disabled (<= width 1)}
         [:i.fas.fa-minus]]]
       [:div.control
        [:input.input
         {:value     width
          :on-change #(rf/dispatch [:query-column-width-set table column (-> % .-target .-value keyword)])
          :size      4}]]
       [:div.control
        [:button.button
         {:on-click #(rf/dispatch [:query-column-width-inc table column])}
         [:span.icon
          [:i.fas.fa-plus]]]]]]]))

(defn query-columns-table [table]
  (let [columns      @(rf/subscribe [:query-columns table])
        column-count (count columns)]
    [:table.table
     [:thead
      [:tr
       [:th]
       [:th "Column"]
       [:th "Type"]
       [:th "Format"]
       [:th "Width"]]]
     [:tbody
      (for [[pos column] (map-indexed vector columns)]
        ^{:key pos}
        [column-table-row table pos column column-count])]]))

(defn query-tab [table]
  [query-columns-table table])

(defn tabs [active-tab]
  [:div.tabs.is-toggle.is-centered
   [:ul
    [:li
     {:class (when (= :query @active-tab) "is-active")}
     [:a {:on-click #(reset! active-tab :query)}
      [:span.icon.is-small
       [:i.fas.fa-search]]
      [:span "Query"]]]
    [:li
     {:class (when (= :searches @active-tab) "is-active")}
     [:a {:on-click #(reset! active-tab :searches)}
      [:span.icon.is-small
       [:i.fas.fa-bookmark]]
      [:span "Searches"]]]
    [:li
     {:class (when (= :maintenance @active-tab) "is-active")}
     [:a {:on-click #(reset! active-tab :maintenance)}
      [:span.icon.is-small
       [:i.fas.fa-cog]]
      [:span "Maintenance"]]]]])

(defn dialog-body [_]
  (let [active-tab (atom :query)]
    (fn [table]
      (let [table-options @(rf/subscribe [:table-options table])]
        [:section.modal-card-body
         [tabs active-tab]
         (case @active-tab
           :query       [query-tab table]
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
