(ns lapidary.client.ui.query
  (:require
   ["clipboard-copy" :as copy]
   [lapidary.utils :as utils]
   [lapidary.client.state :as state]
   [lapidary.sugar :as sugar]
   [lapidary.search-query :as query]
   [lapidary.client.format-value :as format-value]
   [lapidary.client.ui.misc :as ui-misc]
   [lapidary.client.ui.navbar :as navbar]
   [lapidary.client.ui.pagination :as pagination]
   [lapidary.client.ui.field :as field]
   [lapidary.client.ui.table-settings :as table-settings]
   [lapidary.client.ui.sidebar :as sidebar]
   [lapidary.client.ui.confirm-dialog :as confirm-dialog]
   [lapidary.client.ui.activity-indicator :as activity-indicator]
   [re-frame.core :as rf]
   [lapidary.client.router :as router]
   [clojure.string :as str]
   [reagent.core :as reagent :refer [atom]]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)]))

(defn detail-tag [table field value selected? set-selected column?]
  (let [type      (utils/detect-type value)
        value     (clj->js value)
        value-str (if (object? value)
                    (js/JSON.stringify value)
                    (str value))]
    [:div.control
     [:div.tags.has-addons.buttons
      [:a {:class    (str "tag " (if column? "is-primary" "is-link"))
           :on-click #(rf/dispatch [:query-show-field table field])}
       [:tt (ui-misc/format-path field)]]
      [:a.tag.is-dark
       {:on-click #(set-selected (if selected? nil field))}
       [:tt {:title value-str}
        (-> (format-value/format value :auto nil)
            (utils/shorten 120))]]
      (when selected?
        [:span.tag.is-light
         [:div.buttons.has-addon
          [:a.button.is-link.is-small
           {:title    "Copy Value"
            :on-click #(copy value-str)}
           [:span.icon (:value-copy-sm ui-misc/icons)]]
          [:a.button.is-success.is-small
           {:title    "Require Value"
            :on-click #(rf/dispatch [:query-filter-add table :require field value])}
           [:span.icon (:value-require-sm ui-misc/icons)]]
          [:a.button.is-danger.is-small
           {:title    "Exclude Value"
            :on-click #(rf/dispatch [:query-filter-add table :exclude field value])}
           [:span.icon (:value-exclude-sm ui-misc/icons)]]]])]]))

(defn detail-tags [table log columns]
  (let [selected-field (atom nil)
        set-selected   #(reset! selected-field %)]
    (fn [table log columns]
      (let [selected-field @selected-field]
        [:div.field.is-grouped.is-grouped-multiline
         (for [field (->> (utils/kvpaths-all log)
                          (sort)
                          (remove #(= % [:record])))]
           ^{:key field}
           [detail-tag table field (get-in log field) (= field selected-field) set-selected (contains? columns field)])]))))

(defn detail-table [table log columns]
  [:table.table.is-wide
   [:thead
    [:tr
     [:th]
     [:th "Field"]
     [:th "Value"]]]
   [:tbody
    (for [field (->> (utils/kvpaths-all log)
                     (sort)
                     (remove #(= % [:record])))]
      (let [value     (get-in log field)
            type      (utils/detect-type value)
            value     (clj->js value)
            value-str (if (object? value)
                        (js/JSON.stringify value)
                        (str value))]
        ^{:key field}
        [:tr
         [:td.is-size-7-mobile
          [:div.field.has-addons
           [:div.control
            [:button.button.is-link.is-small
             {:title    "Copy Value"
              :on-click #(copy value-str)}
             [:span.icon (:value-copy-sm ui-misc/icons)]]]
           [:div.control
            [:button.button.is-success.is-small
             {:title    "Require Value"
              :on-click #(rf/dispatch [:query-filter-add table :require field value])}
             [:span.icon (:value-require-sm ui-misc/icons)]]]
           [:div.control
            [:button.button.is-danger.is-small
             {:title    "Exclude Value"
              :on-click #(rf/dispatch [:query-filter-add table :exclude field value])}
             [:span.icon (:value-exclude-sm ui-misc/icons)]]]]]
         [:td.is-size-7-mobile
          [:tt (ui-misc/format-path field)]]
         [:td.is-size-7-mobile.ellipsis
          [:tt {:title value-str}
           (-> (format-value/format value :auto nil)
               (utils/shorten 120))]]]))]])

(defn detail [table log columns]
  (let [as-table? (atom false)]
    (fn [table log columns]
      (let [columns (set columns)]
        [:tr.is-light
         [:td.is-size-7-mobile {:col-span (-> columns count)}
          [:button {:class    [:button :is-pulled-right	(when @as-table? :is-primary)]
                    :on-click #(swap! as-table? not)}
           [:span.icon.is-small (ui-misc/icons :table)]]
          (if @as-table?
            [detail-table table log columns]
            [detail-tags table log columns])]]))))


(defn log-table-entry [table log columns selected? column-options]
  (let [checked? (atom false)
        check-fn (fn [e]
                   (swap! checked? not)
                   (.stopPropagation e))]
    (fn [table log columns selected? column-options]
      [:tr {:on-click #(rf/dispatch [:query-expand-log table (if selected? nil (get log ui-misc/id-column))])
            :class    (when selected? "is-selected is-link is-unselectable")}
       #_[:td
          [:button
           {:class    (str "button is-small" (when @checked? " is-primary"))
            :on-click check-fn}
           [:span.icon
            (if @checked?
              (:checkbox-checked ui-misc/icons)
              (:checkbox-unchecked ui-misc/icons))]]]
       (for [column columns]
         (let [value   (get-in log column)
               options (get column-options column)
               type    (-> (get options :type :auto)
                           (utils/update-type value))
               fmt     (get options :format "")]
           ^{:key column}
           [:td.is-size-7-mobile {:title (str value)}
            #_(js/console.log value)
            (format-value/format value type fmt)]))])))

(defn log-table-header-column [table column options sort? reverse? last?]
  (let [width (-> options (get :width 12) (str "em"))]
    [:th {:class    :is-size-7-mobile
          :on-click #(rf/dispatch (if sort?
                                    [:query-sort-reverse table (not reverse?)]
                                    [:query-sort-column table column]))
          :style    (when-not last? {:width width})}
     [:span (ui-misc/format-path column)]
     (when sort?
       [:span.icon
        (if reverse?
          (:order-ascend ui-misc/icons)
          (:order-descend ui-misc/icons))])]))

(defn log-table-header [table columns]
  (let [sort-column    @(rf/subscribe [:query-sort-column table])
        reverse?       @(rf/subscribe [:query-reverse? table])
        column-options @(rf/subscribe [:query-column-options-all table])
        column-count   (count columns)]
    [:thead
     [:tr
      #_[:th {:style {:min-width "44px"
                      :width     "44px"
                      :max-width "44px"}}]
      (for [column columns]
        (let [sort?   (= sort-column column)
              last?   (= column (last columns))
              options (merge {:width 12} (get column-options column))]
          ^{:key column}
          [log-table-header-column table column options sort? reverse? last?]))]]))

(defn log-table-body [table columns]
  (let [expand-log     @(rf/subscribe [:query-expand-log table])
        column-options @(rf/subscribe [:query-column-options-all table])]
    [:tbody
     (for [log @(rf/subscribe [:query-logs table])
           f   [:entry :record]]
       (let [id        (:id log)
             selected? (= id expand-log)]
         (with-meta
           (case f
             :entry  [log-table-entry table log columns selected? column-options]
             :record (when (= expand-log (get log ui-misc/id-column))
                       [detail table log columns]))
           {:key {:f f :id id}})))]))

(defn log-table [table]
  (let [columns @(rf/subscribe [:query-columns table])]
    [:table.table.is-hoverable.is-wide.is-fixed
     [log-table-header table columns]
     [log-table-body table columns]]))

(defn filter-tag [table type field value]
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

(defn filter-tags [table]
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
               [filter-tag table type field value])))))]))

(defn submit [table state]
  (let [{:keys [query-str start-str end-str query-parsed]} @state]
    (let [parsed     (query/query-parse query-str)
          parsed-ok? (not (map? parsed))
          start-ok?  (-> start-str sugar/parse-time sugar/parse-valid?)
          end-ok?    (-> end-str sugar/parse-time sugar/parse-valid?)
          form-ok?   (and start-ok? end-ok?)]
      (if (and form-ok? parsed-ok?)
        (rf/dispatch [:query-submit table query-str start-str end-str])))))

(defn submit-on-enter [table state e]
  (let [{:keys [query-str start-str end-str query-parsed]} @state]
    (when (case (.-keyCode e)
            13 (submit table state)
            false)
      (.preventDefault e))))

(def start-time-values
  ["15 minutes ago"
   "30 minutes ago"
   "1 hour ago"
   "4 hours ago"
   "1 day ago"
   "7 days ago"
   "1 month ago"
   "3 months ago"
   "1 year ago"])

(def end-time-values
  ["now"
   "30 minutes ago"
   "1 hour ago"
   "4 hours ago"
   "1 day ago"
   "7 days ago"
   "1 month ago"
   "3 months ago"
   "1 year ago"])

(defn input [placeholder value valid? change-fn submit-fn]
  [:input {:class       [:input (when-not valid? :is-danger)]
           :type        :text
           :placeholder placeholder
           :value       value
           :on-change   #(-> % .-target .-value change-fn)
           :on-key-up   (fn [e]
                          (when (= 13 (.-keyCode e))
                            (submit-fn)
                            (.preventDefault e)))}])


(defn input-dropdown [expand? placeholder value valid? active? values change-fn submit-fn dropdown-fn]
  (if (empty? values)
    [:p
     {:class [:control :is-fullwidth (when expand? :is-expanded)]}
     [input placeholder value valid? change-fn submit-fn]]
    [:div
     {:class [:dropdown :is-block :is-right (when active? :is-active)]}
     [:div.dropdown-trigger
      [:div.field.has-addons
       [:p
        {:class [:control :is-fullwidth (when expand? :is-expanded)]}
        [input placeholder value valid? change-fn submit-fn]]
       [:p.control.is-fullwidth
        [:button
         {:on-click #(-> active? not dropdown-fn)
          :class    [:button :has-icon (when active? :is-primary)]}
         [:span.icon (:dropdown ui-misc/icons)]]]]]
     [:div.dropdown-menu
      [:div.dropdown-content
       (keep-indexed
        (fn [index value]
          ^{:key index}
          [:a.dropdown-item
           {:on-click (fn []
                        (dropdown-fn false)
                        (change-fn value)
                        (submit-fn))}
           value])
        values)]]]))

(defn time-valid? [time]
  (-> time sugar/parse-time sugar/parse-valid?))

(defn query-form [table]
  #_(debugf "Query form: %s %s" params query)
  (let [state       (atom @(rf/subscribe [:query-form-values table]))
        parse-query (-> #(swap! state assoc :query-parsed (query/query-parse %))
                        (utils/debounce 250))]
    #_(debugf "FORM A: %s" @state)
    (fn [table]
      (let [{:keys [query-str start-str end-str query-parsed dropdown debug?]} @state

            query-ok?       (not (map? query-parsed))
            start-ok?       (time-valid? start-str)
            end-ok?         (time-valid? end-str)
            form-ok?        (and query-ok? start-ok? end-ok?)
            result-count    @(rf/subscribe [:query-result-count table])
            query-dropdown? (= :query dropdown)
            start-dropdown? (= :start dropdown)
            end-dropdown?   (= :end dropdown)
            history         @(rf/subscribe [:query-history table])
            query-history   (->> history
                                 (map :query-str)
                                 (remove nil?)
                                 (remove str/blank?)
                                 (map str)
                                 (remove #(= % query-str))
                                 (dedupe))]
        (parse-query query-str)
        #_(debugf "History: %s" (count query-history))

        [:div
         [:div.field.is-grouped.is-block-mobile
          [:div.control.is-expanded.is-block-mobile
           [:label.label "Query" (when (> result-count 0) (str " (" result-count ")"))]
           [input-dropdown true "Query..." query-str query-ok? query-dropdown? query-history
            #(swap! state assoc :query-str %)
            #(submit table state)
            #(swap! state assoc :dropdown (when % :query))]]

          [:div.control.is-block-mobile
           [:label.label "Start Time"]
           [input-dropdown false "Start time..." start-str start-ok? start-dropdown? start-time-values
            #(swap! state assoc :start-str %)
            #(submit table state)
            #(swap! state assoc :dropdown (when % :start))]]

          [:div.control.is-block-mobile
           [:label.label "End Time"]
           [input-dropdown false "End time..." end-str end-ok? end-dropdown? end-time-values
            #(swap! state assoc :end-str %)
            #(submit table state)
            #(swap! state assoc :dropdown (when % :end))]]
          [:div.control.is-block-mobile
           [:label.label {:dangerouslySetInnerHTML {:__html "&nbsp;"}}]
           [:button {:class    [:button (when debug? :is-primary)]
                     :on-click #(swap! state update :debug? not)}
            [:span.icon (ui-misc/icons :information)]]]]
         (when debug?
           (let [where  (try (query/query->where query-str)
                             (catch js/Object e e))
                 parsed (try (query/search-query table {:query-str query-str
                                                        :start-str start-str
                                                        :end-str   end-str})
                             (catch js/Object e [e]))]
             [:div
              [:div.field
               [:div.control.is-expanded
                [:label.label "Parsed Query"]
                [:tt (str query-parsed)]]]
              [:div.field
               [:div.control.is-expanded
                [:label.label "SQL Where"]
                [:tt (str where)]]]
              [:div.field
               [:div.control.is-expanded
                [:label.label "SQL Query"]
                [:tt (str (first parsed))]]]

              [:div.field
               [:div.control.is-expanded
                [:label.label "SQL Placeholders"]
                [:tt (str (rest parsed))]]]]))]))))

(defn fields-toggle-button [table]
  (let [visible? @(rf/subscribe [:query-fields-visible? table])]
    [:a {:title    (str (if visible? "Hide" "Show") " Fields")
         :on-click #(rf/dispatch [:query-fields-visible table (not visible?)])
         :class    (str "button has-tooltip" (if visible? " is-primary")) }
     [:span.icon
      (if visible?
        (:fields-hidden ui-misc/icons)
        (:fields-visible ui-misc/icons))]]))

(defn view-query [view]
  (let [table                 (get-in view [:params :table])
        page                  @(rf/subscribe [:query-page table])
        fields-visible?       @(rf/subscribe [:query-fields-visible? table])
        settings-visible?     @(rf/subscribe [:query-settings-visible? table])
        confirm-search-delete @(rf/subscribe [:query-confirm-search-delete table])
        confirm-table-delete  @(rf/subscribe [:query-confirm-table-delete table])
        show-field            @(rf/subscribe [:query-show-field table])
        pages                 @(rf/subscribe [:query-pages table])]
    [:div
     [navbar/navbar {:brand [{:key :fields :item [fields-toggle-button table]}
                             {:key :title :item [:span.title.has-text-white table]}]}
      [:div.navbar-end
       [:div.navbar-item
        [:div.field.is-grouped
         [:p.control
          [activity-indicator/spinner]]
         [:p.control
          [:a.button.is-white
           {:on-click #(rf/dispatch [:query-settings-visible table true])}
           [:span.icon (:settings ui-misc/icons)]
           [:span "Settings"]]]
         [:p.control
          [:a.button.is-white
           {:on-click #(rf/dispatch [:tables-navigate])}
           [:span.icon (:tables ui-misc/icons)]
           [:span "Tables"]]]]]]]
     [:div.columns.is-mobile.is-flex.is-fullsize
      (cond
        (some? confirm-table-delete)
        [confirm-dialog/dialog
         {:on-ok       #(rf/dispatch [:query-confirm-table-delete-ok table])
          :on-cancel   #(rf/dispatch [:query-confirm-table-delete-cancel table])
          :confirm-str table}]

        (some? confirm-search-delete)
        [confirm-dialog/dialog
         {:on-ok       #(rf/dispatch [:query-confirm-search-delete-ok table confirm-search-delete])
          :on-cancel   #(rf/dispatch [:query-confirm-search-delete-cancel table])
          :confirm-str confirm-search-delete}]

        (some? show-field)
        [field/field-dialog table]

        settings-visible?
        [table-settings/dialog table])

      (when fields-visible?
        [sidebar/fields table])
      [:div.column.section.is-flex {:style {:flex-direction :column}}
       [query-form table]
       [filter-tags table]
       [log-table table]
       [:div {:style {:margin-top :auto}}
        [pagination/pagination page #(rf/dispatch [:query-page table %]) pages]]]]]))
