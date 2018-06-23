(ns lapidary.client.subs
  (:require
   [clojure.string :as str]
   [lapidary.client.db :as db]
   [lapidary.utils :as utils]
   [re-frame.core :as rf]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)]))

(rf/reg-sub
 :time
 (fn [db _]
   (:time db)))

(rf/reg-sub
 :tables
 (fn [db _]
   (->> (:tables db)
        (vals)
        (sort-by :table_name))))

(rf/reg-sub
 :view
 (fn [db _]
   (:view db)))

(rf/reg-sub
 :query-show-field
 (fn [db [_ table]]
   (get-in db [:query table :show-field])))

(rf/reg-sub
 :query-fields
 (fn [db [_ table]]
   (get-in db [:query table :fields])))

(rf/reg-sub
 :query-field
 (fn [db [_ table field]]
   (get-in db [:query table :fields field])))


(defn sort-fields [fields]
  (->> fields
       (map (fn [[k v]] [k (:freq v) (:type v)]))
       (sort-by (fn [[name freq type]] [(* -1 freq) name type]))))

(rf/reg-sub
 :query-fields-available
 (fn [db [_ table]]
   (-> (get-in db [:query table :fields])
       (utils/drop-keys (get-in db [:query table :columns]))
       (utils/drop-keys [[:*] [:record]])
       (sort-fields))))

(defn create-missing [coll keys]
  (reduce #(if (contains? %1 %2)
             %1
             (assoc %1 %2 {:freq 0
                           :type :missing}))
          coll
          keys))

(rf/reg-sub
 :query-fields-used
 (fn [db [_ table]]
   (let [columns (get-in db [:query table :columns])]
     (-> (get-in db [:query table :fields])
         (select-keys columns)))))

(rf/reg-sub
 :query-result-count
 (fn [db [_ table]]
   (get-in db [:query table :fields [:*] :freq])))

(defn make-sort-fn [column reverse?]
  (let [sort-key (if column (juxt #(get-in % column) :id) :id)
        compare  (if reverse? utils/rcompare compare)]
    (fn [c]
      (sort-by sort-key compare c))))

(rf/reg-sub
 :query-logs
 (fn [db [_ table]]
   (let [{:keys [sort-column columns logs reverse?]} (get-in db [:query table])

         sort-column (if (utils/contains-val? columns sort-column)
                       sort-column
                       [(first columns)])
         sort-fn     (make-sort-fn sort-column reverse?)]
     (sort-fn logs))))

(rf/reg-sub
 :query-expand-log
 (fn [db [_ table]]
   (get-in db [:query table :expand-log])))

(rf/reg-sub
 :query-columns
 (fn [db [_ table]]
   (get-in db [:query table :columns])))

(rf/reg-sub
 :query-column-options
 (fn [db [_ table column]]
   (get-in db [:query table :column-options column])))

(rf/reg-sub
 :query-sort-column
 (fn [db [_ table]]
   (or (get-in db [:query table :sort-column])
       (first (get-in db [:query table :columns])))))

(rf/reg-sub
 :query-reverse?
 (fn [db [_ table]]
   (get-in db [:query table :reverse?])))

(rf/reg-sub
 :query-show-field
 (fn [db [_ table]]
   (get-in db [:query table :show-field])))

(rf/reg-sub
 :field-values
 (fn [db [_ table field]]
   (get-in db [:query table :field-values field])))

(rf/reg-sub
 :query-expand-field
 (fn [db [_ table]]
   (get-in db [:query table :expand-field])))

(rf/reg-sub
 :query-form-values
 (fn [db [_ table]]
   (-> (get-in db [:query table])
       (select-keys [:query-str :start-str :end-str]))))

(rf/reg-sub
 :query-fields-visible?
 (fn [db [_ table]]
   (get-in db [:query table :fields-visible?] false)))

(rf/reg-sub
 :query-settings-visible?
 (fn [db [_ table]]
   (get-in db [:query table :settings-visible?] false)))

(rf/reg-sub
 :query-page
 (fn [db [_ table]]
   (get-in db [:query table :page])))

(rf/reg-sub
 :query-filters
 (fn [db [_ table]]
   (get-in db [:query table :filters])))

(rf/reg-sub
 :query-pages
 (fn [db [_ table]]
   (-> (get-in db [:query table :fields [:*] :freq] 0)
       (/ (get-in db [:query table :page-size]))
       (js/Math.floor))))

(rf/reg-sub
 :query-confirm-search-delete
 (fn [db [_ table]]
   (get-in db [:query table :confirm-search-delete])))

(rf/reg-sub
 :query-confirm-table-delete
 (fn [db [_ table]]
   (get-in db [:query table :confirm-table-delete])))

(rf/reg-sub
 :table-searches
 (fn [db [_ table]]
   (->> (get-in db [:tables table :searches])
        (sort-by first))))

(rf/reg-sub
 :table-options
 (fn [db [_ table]]
   (->> (get-in db [:tables table :options])
        (sort-by first))))

(rf/reg-sub
 :login
 (fn [db _]
   (:login db)))

(rf/reg-sub
 :login-ok?
 (fn [db _]
   (db/login-ok? db)))

(rf/reg-sub
 :login-error
 (fn [db _]
   (get-in db [:login :error])))

(rf/reg-sub
 :table-options
 (fn [db [_ table]]
   (get-in db [:tables table :options])))
