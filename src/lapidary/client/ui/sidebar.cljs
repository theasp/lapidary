(ns lapidary.client.ui.sidebar
  (:require
   [lapidary.utils :as utils]
   [lapidary.search-query :as query]
   [lapidary.client.ui.misc :as ui-misc]
   [re-frame.core :as rf]
   [clojure.string :as str]
   [reagent.core :as reagent :refer [atom]]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)]))

(defn column-pos [columns name]
  (->> columns
       (map-indexed vector)
       (filter #(= name (second %)))
       (first)
       (first)))


(defn stream-field-selected-controls [table name type freq]
  (let [columns   @(rf/subscribe [:query-columns table])
        pos       (column-pos columns name)
        is-first? (= pos 0)
        is-last?  (= pos (dec (count columns)))
        remove-fn #(rf/dispatch [:query-column-remove table name])
        left-fn   (-> #(rf/dispatch [:query-column-left table name])
                      (utils/stop-propogation))
        right-fn  (-> #(rf/dispatch [:query-column-right table name])
                      (utils/stop-propogation))
        info-fn   (-> #(rf/dispatch [:query-show-field table name])
                      (utils/stop-propogation))]
    [:span.buttons.has-addons.is-pulled-right
     [:button.button.is-small
      {:title    "Move Column Left"
       :disabled is-first?
       :on-click left-fn}
      [:span.icon [:i.fas.fa-arrow-left]]]
     [:button.button.is-small
      {:title    "Move Column Right"
       :disabled is-last?
       :on-click right-fn}
      [:span.icon [:i.fas.fa-arrow-right]]]
     [:button.button.is-small
      {:title    "Remove Column"
       :on-click remove-fn}
      [:span.icon [:i.fas.fa-minus]]]
     [:button.button.is-small
      {:title    "Show Values"
       :on-click info-fn}
      [:span.icon [:i.fas.fa-info]]]]))

(defn stream-field-available-controls [table name type freq]
  (let [add-column #(rf/dispatch [:query-column-add table name])
        field-info (when-not (= :timestamp type)
                     (-> #(rf/dispatch [:query-show-field table name])
                         (utils/stop-propogation)))]
    [:span.buttons.has-addons.is-pulled-right
     [:button.button.is-small
      {:title    "Add Column"
       :on-click add-column}
      [:span.icon [:i.fas.fa-plus]]]
     [:button.button.is-small
      {:title    "Show Values"
       :disabled (nil? field-info)
       :on-click field-info}
      [:span.icon [:i.fas.fa-info]]]]))

(defn stream-field [table type name selected? used? freq]
  (let [formats  (get ui-misc/type-formats type)
        on-click (when-not selected?
                   #(rf/dispatch [:query-expand-field table name]))]
    [:li.is-clearfix
     [:a
      {:class    (str "is-clearfix" (if selected? " is-active"))
       :on-click on-click}
      [:span.icon (get ui-misc/field-type-names type "?")]
      " "
      (ui-misc/format-path name)
      (when selected?
        [:div {:style {:display :flex}}
         [:div {:style {:flex          1
                        :margin-top    :auto
                        :margin-bottom :auto}}
          [ui-misc/battery-icon freq]
          #_[ui-misc/popularity freq]]
         (if used?
           [stream-field-selected-controls table name type freq]
           [stream-field-available-controls table name type freq])])]]))

(defn stream-field-values-list [table name]
  (let [values (->> @(rf/subscribe [:field-values table name])
                    (:values)
                    (sort-by :count)
                    (reverse)
                    (take 5))]
    [:li
     [:ul.side-panel
      (for [value values]
        ^{:key value}
        [:li
         [ui-misc/battery-icon (:percentage value)]
         [:span (ui-misc/format-value-label (:value value))]])]]))

(defn saved-query [table name]
  [:li
   [:a {:on-click #(rf/dispatch [:table-query-search table name])}
    [:span.icon [:i.fas.fa-bookmark]]
    name]])

(defn saved-add-submit [table state]
  (rf/dispatch [:searches-save table (:name @state)])
  (reset! state nil))

(defn saved-add-on-keyup [add-fn cancel-fn e]
  (case (.-keyCode e)
    13 (add-fn e)
    27 (cancel-fn e)
    nil))

(defn saved-add-ask-name-blur [state]
  (if (str/blank? (:name state))
    nil
    state))

(defn saved-add-ask-name [table state saved]
  (let [name      (:name @state)
        name-ok?  (and (not (str/blank? name))
                       (not (some? (get saved name))))
        cancel-fn #(reset! state nil)
        add-fn    (-> #(saved-add-submit table state)
                      (utils/stop-propogation))]
    [:div.field.has-addons
     [:div.control
      [ui-misc/trigger {:component-did-mount #(some-> % reagent/dom-node .focus)}
       [:input.input {:on-change   #(swap! state assoc :name (-> % .-target .-value))
                      :on-blur     #(swap! state saved-add-ask-name-blur)
                      :on-keyUp    #(saved-add-on-keyup add-fn cancel-fn %)
                      :type        :text
                      :placeholder "Name..."
                      :value       (:name @state)}]]]
     [:div.control
      [:button.button.is-primary {:disabled (not name-ok?)
                                  :on-click #(add-fn %)}
       [:span.icon [:i.fas.fa-plus]]]]]))

(defn saved-add-button [table state]
  [:a {:title    "Add Saved Search"
       :on-click #(swap! state assoc :stage :ask-name)}
   [:span.icon [:i.fas.fa-plus]]
   [:span "Add..."]])

(defn saved-add [table saved]
  (let [state (atom nil)]
    (fn []
      [:li
       (case (get @state :stage :start)
         :start    [saved-add-button table state]
         :ask-name [saved-add-ask-name table state saved])])))

(defn saved-searches [table]
  (let [searches @(rf/subscribe [:table-searches table])]
    [:ul.menu-list
     (for [[name query] searches]
       ^{:key name} [saved-query table name query])
     [saved-add table searches]]))


(defn used-fields [table]
  (let [columns      @(rf/subscribe [:query-columns table])
        expand-field @(rf/subscribe [:query-expand-field table])
        total        @(rf/subscribe [:query-result-count table])
        fields       @(rf/subscribe [:query-fields-used table])]
    [:ul.menu-list
     (->> (for [name columns]
            (let [{:keys [freq type]} (get fields name {:freq 0 :type nil})
                  selected?           (= name expand-field)]
              [^{:key name}
               [stream-field table type name selected? true (/ freq total)]

               (when selected?
                 ^{:key {:type :stream-field-values :name name}}
                 [stream-field-values-list table name])]))
          (apply concat))]))

(defn available-fields [table]
  (let [expand-field @(rf/subscribe [:query-expand-field table])
        total        @(rf/subscribe [:query-result-count table])
        fields       @(rf/subscribe [:query-fields-available table])]
    [:ul.menu-list
     (->> (for [[name freq type] fields]
            (let [selected? (= name expand-field)]
              [^{:key {:type :stream-field :name name}}
               [stream-field table type name selected? false (/ freq total)]

               (when selected?
                 ^{:key {:type :stream-field-values :name name}}
                 [stream-field-values-list table name])]))
          (apply concat))]))


(defn sidebar-label [show? label]
  [:p.menu-label
   [:a.is-unselectable {:on-click #(swap! show? not)}
    [:span.icon.is-small]
    [:span label]
    [:span.icon.is-small
     (if @show?
       [:i.fas.fa-caret-down]
       [:i.fas.fa-caret-right])]]])

(defn stream-fields [table]
  (let [saved-searches?   (atom true)
        used-fields?      (atom true)
        available-fields? (atom true)]
    (fn [table]
      [:div.column.side-panel.is-narrow.section.notification.is-dark.is-plain
       [:aside.menu.side-panel
        [sidebar-label saved-searches? "Saved Searches"]
        (when @saved-searches?
          [saved-searches table])

        [sidebar-label used-fields? "Used Fields"]
        (when @used-fields?
          [used-fields table])

        [sidebar-label available-fields? "Available Fields"]
        (when @available-fields?
          [available-fields table])]])))
