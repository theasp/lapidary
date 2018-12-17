(ns lapidary.client.ui.tables
  (:require
   [lapidary.utils :as utils]
   [lapidary.client.ui.navbar :as navbar]
   [lapidary.client.ui.misc :as ui-misc]
   [lapidary.client.state :as state]
   [lapidary.client.api :as api]
   [lapidary.client.db :as db]
   [lapidary.client.ui.activity-indicator :as activity-indicator]
   [clojure.string :as str]
   [reagent.core :as reagent :refer [atom]]
   [re-frame.core :as rf]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)]))

(def column-class "column is-3-fullhd is-4-widescreen is-4-desktop is-6-tablet")

(defn list-table [view table]
  #_(debugf "list-table: %s" table)
  (let [table-name     (:table_name table)
        searches       (:searches table)
        options        (:options table)
        default-search (get options :default-search "Default")
        default-query  (get searches default-search "Default")]
    [:div {:class column-class}
     [:div.hero.is-rounded.is-link.is-tall
      [:div.hero-body
       [:div.field
        [:div.control.has-text-centered
         [:button.button.is-primary.is-large {:on-click #(rf/dispatch [:table-query-search table-name])}
          [:span.icon.is-medium (:search-lg ui-misc/icons)]
          [:span (:table_name table)]]]]
       [:div.field
        [:div.control.has-text-centered
         (utils/byte-size-str (:size table))
         " / "
         (utils/si-size-str (:rows table)) " rows"]]
       [:div.buttons
        (for [[name query] (sort-by :search_name searches)]
          ^{:key name}
          [:button.button {:on-click #(rf/dispatch [:table-query-search table-name name])}
           [:span.icon (:query-saved ui-misc/icons)]
           [:span name]])]]]]))

(defn new-table-card [state]
  (let [name     (:name @state)
        name-ok? (db/table-name-ok? name)
        submit   (if name-ok?
                   (fn [e]
                     (rf/dispatch [:tables-create name])
                     (reset! state nil)
                     (.preventDefault e))
                   (fn [e]
                     (.preventDefault e)))]
    [:form.hero.is-light.is-rounded
     {:on-submit submit}
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
          :auto-focus  true
          :value       name}]]]
      [:div.field.has-text-centered
       [:div.control.has-text-right
        [:button.button.is-primary
         {:disabled (not name-ok?)}
         [:span.icon (:table-create-lg ui-misc/icons)]
         [:span "Create"]]]]]]))

(defn hidden-new-table [state]
  [:div.hero.is-light.is-rounded.is-tall
   [:div.hero-body
    [:div.field
     [:div.control.has-text-centered
      [:button.button.is-primary.is-large {:on-click #(swap! state assoc :card-mode :form)}
       [:span.icon (:table-create-lg ui-misc/icons)]
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
   [navbar/navbar {:brand [{:key :title :item [:span.title.has-text-white "Tables"]}]}
    [:div.navbar-end
     [:div.navbar-item
      [:div.field.is-grouped
       [:p.control
        [activity-indicator/spinner]]]]]]
   [:section.section
    [:div.columns.is-multiline
     (for [table @(rf/subscribe [:tables])]
       ^{:key (:table_name table)}
       [list-table view table])
     [new-table]]]])
