(ns lapidary.client.ui.query
  (:require
   ["clipboard-copy" :as copy]
   [lapidary.utils :as utils]
   [lapidary.client.state :as state]
   [lapidary.client.sugar :as sugar]
   [lapidary.client.query :as query]
   [lapidary.client.ui.misc :as ui-misc]
   [lapidary.client.ui.navbar :as navbar]
   [lapidary.client.ui.pagination :as pagination]
   [lapidary.client.ui.field :as field]
   [lapidary.client.ui.table-settings :as table-settings]
   [lapidary.client.ui.sidebar :as sidebar]
   [re-frame.core :as rf]
   [lapidary.client.router :as router]
   [clojure.string :as str]
   [reagent.core :as reagent :refer [atom]]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)]))

(defn stream-detail-value [table field value selected? set-selected column?]
  (let [type (state/detect-type value)]
    [:div.control
     [:div.tags.has-addons.buttons
      [:a {:class    (str "tag " (if column? "is-primary" "is-link"))
           :on-click #(rf/dispatch [:query-show-field table field])}
       [:tt (ui-misc/format-path field)]]
      [:a.tag.is-dark
       {:on-click #(set-selected (if selected? nil field))}
       [:tt {:title value}
        (-> value
            (ui-misc/format-value type :long)
            (utils/shorten 120))]]
      (when selected?
        [:span.tag.is-light
         [:div.buttons.has-addon
          [:a.button.is-link.is-small
           {:title    "Copy Value"
            :on-click #(copy (str value))}
           [:span.icon
            [:i.fas.fa-copy]]]
          [:a.button.is-success.is-small
           {:title    "Require Value"
            :on-click #(rf/dispatch [:query-filter-add table :require field value])}
           [:span.icon
            [:i.fas.fa-check]]]
          [:a.button.is-danger.is-small
           {:title    "Exclude Value"
            :on-click #(rf/dispatch [:query-filter-add table :exclude field value])}
           [:span.icon
            [:i.fas.fa-times]]]]])]]))

(defn stream-detail [table log columns]
  (let [selected-field (atom nil)
        set-selected   #(reset! selected-field %)]
    (fn [table log columns]
      (let [columns        (set columns)
            selected-field @selected-field]
        [:tr
         [:td {:col-span (-> columns count inc)}
          [:div.field.is-grouped.is-grouped-multiline
           (for [field (->> (utils/kvpaths-all log)
                            (sort)
                            (remove #(= % [:record])))]
             ^{:key field}
             [stream-detail-value table field (get-in log field) (= field selected-field) set-selected (contains? columns field)])]]]))))

(defn stream-entry [table log columns selected?]
  (let [checked? (atom false)
        check-fn (fn [e]
                   (swap! checked? not)
                   (.stopPropagation e))]
    (fn [table log columns selected?]
      [:tr {:on-click #(rf/dispatch [:query-expand-log table (if selected? nil (get log ui-misc/id-column))])
            :class    (when selected? "is-selected is-link is-unselectable")}
       [:td
        [:button
         {:class    (str "button is-small" (when @checked? " is-primary"))
          :on-click check-fn}
         [:span.icon
          (if @checked?
            [:i.fas.fa-check]
            " ")]]]
       (for [column columns]
         (let [value (get-in log column)
               type  (state/detect-type value)]
           ^{:key column}
           [:td.ellipsis
            {:title (str value)}
            (ui-misc/format-value value type :short)]))])))

(defn stream-table-header [table columns]
  (let [sort-column @(rf/subscribe [:query-sort-column table])
        reverse?    @(rf/subscribe [:query-reverse? table])]
    [:thead
     [:tr
      [:th]
      (for [column columns]
        ^{:key column}
        [:th {:on-click #(if (= column sort-column)
                           (rf/dispatch [:query-sort-reverse table (not reverse?)])
                           (rf/dispatch [:query-sort-column table column]))}
         [:span (ui-misc/format-path column)]
         (if (= column sort-column)
           [:span.icon
            (if reverse?
              [:i.fas.fa-chevron-down]
              [:i.fas.fa-chevron-up])])])]]))

(defn stream-table-body [table columns]
  (let [expand-log @(rf/subscribe [:query-expand-log table])]
    [:tbody
     (for [log @(rf/subscribe [:query-logs table])
           f   [:entry :record]]
       (let [id        (:id log)
             selected? (= id expand-log)]
         (with-meta
           (case f
             :entry  [stream-entry table log columns selected?]
             :record (when (= expand-log (get log ui-misc/id-column))
                       [stream-detail table log columns]))
           {:key {:f  f
                  :id id}})))]))

(defn stream-table [table]
  (let [columns @(rf/subscribe [:query-columns table])]
    [:table.table.is-hoverable.is-wide
     [stream-table-header table columns]
     [stream-table-body table columns]]))

(defn stream-filter-tag [table type field value]
  (let [cmp-text (case type :require " = " :exclude " != ")
        class    (str "tag " (case type
                               :require "is-success"
                               :exclude "is-danger"))]
    [:div.control
     [:div.tags.has-addons
      [:span.tag {:class class}
       (str (ui-misc/format-path field) cmp-text value)]
      [:a.tag.is-delete
       {:on-click #(rf/dispatch [:query-filter-remove table type field value])}]]]))

(defn stream-filter-tags [table]
  (let [filters @(rf/subscribe [:query-filters table])]
    [:div.field.is-grouped.is-grouped-multiline
     (for [type [:require :exclude]]
       (for [field (-> filters type keys sort)]
         (let [values (get-in filters [type field])]
           (for [value (sort values)]
             (when-not (or (nil? values)
                           (empty? values))
               ^{:key {:type  type
                       :field field
                       :value (if (some? value) value 'Null)}}
               [stream-filter-tag table type field value])))))]))

(defn query-form-submit [table state e]
  (let [{:keys [query-str start-str end-str query-parsed]} @state]
    (when
        (case (.-keyCode e)
          13
          (let [parsed     (query/query-parse query-str)
                parsed-ok? (not (map? parsed))
                start-ok?  (-> start-str sugar/parse-time sugar/parse-valid?)
                end-ok?    (-> end-str sugar/parse-time sugar/parse-valid?)
                form-ok?   (and start-ok? end-ok?)]
            (if (and form-ok? parsed-ok?)
              (rf/dispatch [:query-submit table query-str start-str end-str])))
          false)
      (.preventDefault e))))

(defn query-form [table]
  #_(debugf "Query form: %s %s" params query)
  (let [state       (atom @(rf/subscribe [:query-form-values table]))
        parse-query (-> #(swap! state assoc :query-parsed (query/query-parse %))
                        (utils/debounce 250))]
    #_(debugf "FORM A: %s" @state)
    (fn []
      (let [{:keys [query-str start-str end-str query-parsed]} @state

            parsed-ok? (not (map? query-parsed))
            start-ok?  (-> start-str sugar/parse-time sugar/parse-valid?)
            end-ok?    (-> end-str sugar/parse-time sugar/parse-valid?)
            form-ok?   (and start-ok? end-ok?)
            submit     #(query-form-submit table state %)

            result-count @(rf/subscribe [:query-result-count table])]
        (parse-query query-str)
        [:div.field.is-grouped
         [:div.control.is-expanded
          [:label.label "Query" (when (> result-count 0) (str " (" result-count ")"))]
          [:input {:class       (str "input" (when-not parsed-ok? " is-danger"))
                   :type        :text
                   :placeholder "Query..."
                   :value       query-str
                   :on-change   #(swap! state assoc :query-str (-> % .-target .-value))
                   :on-key-up   submit}]]

         [:div.control
          [:label.label "Start Time"]
          [:input {:class       (str "input" (when-not start-ok? " is-danger"))
                   :type        :text
                   :placeholder "Start Time..."
                   :value       start-str
                   :on-change   #(swap! state assoc :start-str (-> % .-target .-value))
                   :on-key-up   submit}]]

         [:div.control
          [:label.label "End Time"]
          [:input {:class       (str "input" (when-not end-ok? " is-danger"))
                   :type        :text
                   :placeholder "End Time..."
                   :value       end-str
                   :on-change   #(swap! state assoc :end-str (-> % .-target .-value))
                   :on-key-up   submit}]]]))))

(defn fields-toggle-button [table]
  (let [visible? @(rf/subscribe [:query-fields-visible? table])]
    [:a {:title    (str (if visible? "Hide" "Show") " Fields")
         :on-click #(rf/dispatch [:query-fields-visible table (not visible?)])
         :class    (str "button has-tooltip" (if visible? " is-primary")) }
     [:span.icon [:i.fas.fa-list]]]))

(defn view-query [view]
  (let [table             (get-in view [:params :table])
        page              @(rf/subscribe [:query-page table])
        fields-visible?   @(rf/subscribe [:query-fields-visible? table])
        settings-visible? @(rf/subscribe [:query-settings-visible? table])
        show-field        @(rf/subscribe [:query-show-field table])
        pages             @(rf/subscribe [:query-pages table])]
    (debugf "Settings: %s" settings-visible?)
    [:div
     [navbar/navbar {:brand [{:key :fields :item [fields-toggle-button table]}
                             {:key :title :item table}]}
      [:div.navbar-end
       [:div.navbar-item
        [:div.field.is-grouped
         [:p.control
          [:a.button.is-white
           {:on-click #(rf/dispatch [:query-settings-visible table true])}
           [:span.icon [:i.fas.fa-cog]]
           [:span "Settings"]]]
         [:p.control
          [:a.button.is-white
           {:on-click #(rf/dispatch [:tables-navigate])}
           [:span.icon [:i.fas.fa-table]]
           [:span "Tables"]]]]]]]
     [:div.columns.is-mobile.is-flex.is-fullsize
      (when (some? show-field)
        [field/stream-field-dialog table])
      (when fields-visible?
        [sidebar/stream-fields table])
      (when settings-visible?
        [table-settings/dialog table])
      [:div.column.section.is-flex {:style {:flex-direction :column}}
       [query-form table]
       [stream-filter-tags table]
       [stream-table table]
       [:div {:style {:margin-top :auto}}
        [pagination/pagination page #(rf/dispatch [:query-page table %]) pages]]]]]))
