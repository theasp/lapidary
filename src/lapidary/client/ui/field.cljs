(ns lapidary.client.ui.field
  (:require
   [lapidary.utils :as utils]
   [lapidary.client.state :as state]
   [lapidary.sugar :as sugar]
   [lapidary.search-query :as query]
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

(defn field-table-row [table field value percentage]
  [:tr
   [:td.is-narrow {:style {:vertical-align :middle}}
    [:div.field.has-addons
     [:p.control
      [:a.button.is-small.has-text-success
       {:title    "Require Value"
        :on-click #(rf/dispatch [:query-filter-add table :require field value])}
       [:span.icon (:value-require-sm ui-misc/icons)]]]
     [:p.control
      [:a.button.is-small.has-text-danger
       {:title    "Exclude Value"
        :on-click #(rf/dispatch [:query-filter-add table :exclude field value])}
       [:span.icon (:value-exclude-sm ui-misc/icons)]]]]]
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

(defn field-header [table field all-values?]
  [:header.modal-card-head
   [:button {:title    (if all-values? "Filtered Values" "All Values")
             :class    (str "button" (when-not all-values? " is-primary"))
             :style    {:margin-right "10px"}
             :on-click #(rf/dispatch [:field-all-values table field (not all-values?)])}
    [:span.icon
     (if all-values?
       (:filter-disabled ui-misc/icons)
       (:filter-enabled ui-misc/icons))]]
   [:p.modal-card-title (ui-misc/format-path field)]
   [:button.delete {:on-click #(rf/dispatch [:query-show-field-close table])}]])

(defn field-body [table field values]
  [:section.modal-card-body
   [:table.table.is-fullwidth.is-narrow
    [:tbody
     (for [value values]
       (let [{:keys [value count percentage]} value]
         ^{:key (if (some? value) value 'utils/Null)}
         [field-table-row table field value percentage]))]]])

(defn field-dialog [table]
  (let  [show-field   @(rf/subscribe [:query-show-field table])
         field        (:name show-field)
         field-values @(rf/subscribe [:field-values table field])
         values       (:values field-values)
         all-values?  (:all-values? show-field)
         page-size    (or (:page-size show-field) 20)
         page         (or (:page show-field) 0)
         last-page    (-> (:count field-values)
                          (/ page-size)
                          (js/Math.ceil)
                          (dec))
         set-page     #(rf/dispatch [:field-page table field %])]
    [dialog/dialog #(rf/dispatch [:query-show-field-close table])
     [:dialog
      [:div.modal.is-active
       [:div.modal-background]
       [:div.modal-card
        [field-header table field all-values?]
        [field-body table field values]
        [:footer.modal-card-foot
         [:div  {:style {:width "100%"}}
          [pagination/pagination page set-page last-page]]]]]]]))
