(ns lapidary.client.events.query
  (:require
   [ajax.core :as ajax]
   [clojure.walk :as walk]
   [clojure.string :as str]
   [lapidary.utils :as utils]
   [lapidary.client.state :as state]
   [lapidary.client.db :as db]
   [lapidary.client.api :as api]
   [lapidary.search-query :as query]
   [lapidary.client.router :as router]
   [lapidary.client.format-value :as format-value]
   [re-frame.core :as rf]
   [lapidary.client.api :as api]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)]))

(defn before-now [duration]
  #_(debugf "Before now: %s" duration)
  (-> (js/Date.now)
      (- duration)
      (js/Date.)))

(defn stream-set [sort-column]
  #_(debugf "stream-set")
  (let [values  (juxt #(get-in % sort-column) :id)
        sort-fn #(compare (values %1) (values %2))]
    (sorted-set-by sort-fn)))

(defn path-keywords
  ([c]
   (-> (fn [c k v] (assoc c (keyword k) v))
       (reduce-kv {} c)))
  ([c path]
   (-> (fn [c k v] (assoc c (keyword path (name k)) v))
       (reduce-kv {} c))))

(defn add-log [query log]
  (let [logs (-> (get query :logs (stream-set (:sort-column query)))
                 (conj log))]
    (-> query
        (assoc :logs logs)
        (update :highest max (:id log)))))

(defn trim-sorted-set [num s]
  #_(debugf "trim-sorted-set: %s" num)
  (loop [num num
         s   s]
    (if (<= (count s) num)
      s
      (recur num (disj s (first s))))))


(defn add-logs [query logs]
  #_(debugf "add-logs: %s" (count logs))
  (->> (reduce add-log query logs)
       (trim-sorted-set (:page-size query))))


(defn add-field [fields field]
  #_(debugf "add-field: %s" field)
  (assoc fields [:record (keyword (:field field))]
         {:type (keyword (:type field))
          :freq (utils/parse-int (:freq field))}))

(def schema-fields {[:id]     {:type :number}
                    [:time]   {:type :timestamp}
                    [:tag]    {:type :ltree}
                    [:record] {:type :jsonb}
                    [:*]      {:type :*}})

(defn add-schema-fields [fields]
  #_(debugf "add-schema-fields: %s" fields)
  (let [count-path [:record :*]
        freq       (get-in fields [count-path :freq])]
    (-> (reduce (fn [m [k v]] (assoc m k (assoc v :freq freq)))
                fields
                schema-fields)
        (dissoc count-path))))

(defn add-fields [query fields]
  #_(debugf "add-fields")
  (assoc query :fields (-> (reduce add-field nil fields)
                           (add-schema-fields))))

(def query-expiry (* 30 1000))

(defn query-refresh [{:keys [db]} [_ table]]
  #_(debugf "Query refresh: %s" table)
  (let [{:keys [time loading?]} (get-in db [:query table])]
    (when (and (not loading?)
               (utils/expired? time query-expiry))
      {:dispatch [:query-load table]})))

(defn same-query? [a b]
  (= (select-keys a db/same-query-keys)
     (select-keys b db/same-query-keys)))

(defn view->query [view]
  (-> (:query view)
      (select-keys db/query-params)
      (db/query-defaults)))

(defn query-init [{:keys [db]} [_ table]]
  (let [cur-query (get-in db [:query table])
        new-query (merge cur-query (view->query (:view db)))]
    (if (db/query-equal? cur-query new-query)
      {:db (update-in db [:query table] merge new-query)}
      (let [new-query (merge new-query
                             {:logs         nil
                              :fields       nil
                              :loading?     false
                              :highest      -1
                              :field-values nil})]
        {:db       (assoc-in db [:query table] new-query)
         :dispatch [:query-load table]}))))

(defn query-load [{:keys [db]} [_ table]]
  (when (and (not (get-in db [:query table :loading?]))
             (db/login-ok? db))
    (let [db      (update-in db [:query table :id] inc)
          options (-> (get-in db [:query table])
                      (update :sort-column #(when-not (empty? %) %)))
          id      (:id options)]
      (debugf "History: %s" (get-in db [:query table :history]))

      {:db         (-> db
                       (update-in [:connections :http :query-load] inc)
                       (assoc-in [:query table :loading?] true)
                       (update-in [:query table :history]
                                  #(-> %
                                       (conj (select-keys options [:query-str :start-str :end-str]))
                                       (dedupe))))
       :http-xhrio (-> (api/search-query table options)
                       (merge {:on-success [:query-load-ok table id]
                               :on-failure [:query-load-error table id]}))})))

(defn query-load-ok [db [_ table id result]]
  (if (= id (get-in db [:query table :id]))
    (let [{:keys [logs stats]} (-> result :result :rows first walk/keywordize-keys)]
      (debugf "query-load-ok: Table: %s  Logs: %s  Stats: %s  DB Time: %s" table (count logs) (count stats) (:time result))
      (-> db
          (update-in [:connections :http :query-load] dec)
          (update-in [:query table] add-logs logs)
          (update-in [:query table] add-fields stats)
          (update-in [:query table] merge {:time     (js/Date.now)
                                           :loading? false
                                           :error    nil})))

    (update-in db [:connection :http :query-load] dec)))

(defn query-load-error [{:keys [db]} [_ table query-id error]]
  (errorf "query-load-error: %s" table error)
  (if (= query-id (get-in db [:query table :id]))
    {:dispatch [:http-error :query-load error]
     :db       (-> db
                   (update-in [:connections :http :query-load] dec)
                   (update-in [:query table] merge
                              {:time     (js/Date.now)
                               :loading? false
                               :error    error}))}
    {:db (update-in db [:connection :http :query-load] dec)}))

(defn query-page-next [db [_ table]]
  (let [{:keys [page pages]} (get-in db [:query table])]
    (if (< page pages)
      (assoc-in db [:query :page] (inc page))
      db)))

(defn query-page-prev [db [_ table]]
  (let [{:keys [page pages]} (get-in db [:query table])]
    (if (> page 0)
      (assoc-in db [:query table :page] (dec page))
      db)))

(defn query-expand-log [db [_ table id]]
  (router/query-navigate! table
                          (-> (get-in db [:view :query])
                              (assoc :expand-log (if (= id (get-in db [:query :expand-log])) nil id))))
  db)

(defn query-fields-visible [db [_ table visible?]]
  (assoc-in db [:query table :fields-visible?] visible?))

(defn query-sort-column [db [_ table column]]
  (router/query-navigate! table
                   (-> (get-in db [:view :query])
                       (assoc :sort-column column)))
  db)

(defn query-sort-reverse [db [_ table reverse?]]
  (router/query-navigate! table
                   (-> (get-in db [:view :query])
                       (assoc :reverse? reverse?)))
  db)

(defn query-submit [db [_ table query-str start-str end-str]]
  (let [query-str (str/trim query-str)
        start-str (str/trim start-str)
        end-str   (str/trim end-str)]
    (router/query-navigate! table
                            (-> (get-in db [:view :query])
                                (merge {:query-str query-str
                                        :start-str start-str
                                        :end-str   end-str
                                        :page      0}))))
  (-> db
      (update-in [:query table :history] conj query-str)
      (update-in [:query table :history] dedupe)))

(defn query-expand-field [db [_ table field]]
  (assoc-in db [:query table :expand-field] field))

(defn query-column-add [db [_ table field]]
  (router/query-navigate! table
                          (-> (get-in db [:view :query])
                              (assoc :columns (-> (get-in db [:query table :columns])
                                                  (utils/append field)))))
  db)

(defn query-column-remove [db [_ table field]]
  #_(debugf "Removing column %s from %s" field table)
  (router/query-navigate! table
                          (-> (get-in db [:view :query])
                              (assoc :columns (-> (get-in db [:query table :columns])
                                                  (utils/remove= field)))))
  db)

(defn index-of [coll value]
  (->> (map-indexed vector coll)
       (filter #(= value (second %)))
       (first)
       (first)))

(defn vec-insert-at [coll pos value]
  (let [pos            (-> pos
                           (max 0)
                           (min (count coll)))
        _              (debugf "Split at: %s" pos)
        [before after] (split-at pos coll)]
    #_(debugf "Coll: %s Before: %s  After: %s" coll before after)
    (-> (vec before)
        (conj value)
        (concat after)
        (vec))))

(defn query-column-left [db [_ table field]]
  (let [columns (get-in db [:query table :columns])
        pos     (-> (index-of columns field)
                    (dec))
        columns (-> (remove #(= % field) columns)
                    (vec-insert-at pos field))]
    (router/query-navigate! table
                            (-> (get-in db [:view :query])
                                (assoc :columns columns))))
  db)

(defn query-column-right [db [_ table field]]
  (let [columns (get-in db [:query table :columns])
        pos     (-> (index-of columns field)
                    (inc))
        columns (-> (remove #(= % field) columns)
                    (vec-insert-at pos field))]
    (router/query-navigate! table
                            (-> (get-in db [:view :query])
                                (assoc :columns columns))))
  db)

(defn query-page [db [_ table page]]
  (router/query-navigate! table
                          (-> (get-in db [:view :query])
                              (assoc :page page)))
  db)


(defn set-conj [coll value]
  (-> (set coll)
      (conj value)))

(defn set-disj [coll value]
  (-> (set coll)
      (disj value)))

(defn query-filter-add [db [_ table filter-type field value]]
  (let [other-type (if (= :exclude filter-type) :require :exclude)]
    (router/query-navigate! table
                            (-> (get-in db [:view :query])
                                (update-in [:filters filter-type field] set-conj value)
                                (update-in [:filters other-type field] set-disj value))))
  db)

(defn query-filter-remove [db [_ table filter-type field value]]
  (router/query-navigate! table
                          (-> (get-in db [:view :query])
                              (update-in [:filters filter-type field] set-disj value)))
  db)

(defn query-show-field [db [_ table field]]
  (router/query-navigate! table
                          (-> (get-in db [:view :query])
                              (assoc :show-field {:name        field
                                                  :all-fields? false
                                                  :page        0
                                                  :page-size   20})))
  db)

(defn query-show-field-close [db [_ table]]
  (router/query-navigate! table
                          (-> (get-in db [:view :query])
                              (dissoc :show-field)))
  db)

;; (defn query-saved-load [db [_ table name]]
;;   (router/query-navigate! table
;;                           (merge (get-in db [:view :query])
;;                                  (get-in db [:query table :searches :saved name])))
;;   db)

(defn query-settings-visible [db [_ table visible?]]
  (assoc-in db [:query table :settings-visible?] visible?))

(rf/reg-event-fx
 :query-confirm-search-delete
 (fn query-confirm-search-delete [{:keys [db]} [_ table search]]
   (debugf "query-confirm-search-delete: %s %s" table search)
   {:db (assoc-in db [:query table :confirm-search-delete] search)}))

(rf/reg-event-fx
 :query-confirm-search-delete-ok
 (fn query-confirm-search-delete-ok [{:keys [db]} [_ table search]]
   (debugf "query-confirm-search-delete-ok: %s %s" table search)
   {:dispatch [:table-search-delete table search]
    :db       (update-in db [:query table] dissoc :confirm-search-delete)}))

(rf/reg-event-fx
 :query-confirm-search-delete-cancel
 (fn query-confirm-search-delete-cancel [{:keys [db]} [_ table search]]
   (debugf "query-confirm-search-delete-cancel: %s %s" table search)
   {:db (update-in db [:query table] dissoc :confirm-search-delete)}))


(rf/reg-event-fx
 :query-confirm-table-delete
 (fn query-confirm-table-delete [{:keys [db]} [_ table]]
   (debugf "query-confirm-table-delete: %s" table)
   {:db (assoc-in db [:query table :confirm-table-delete] true)}))

(rf/reg-event-fx
 :query-confirm-table-delete-ok
 (fn query-confirm-table-delete-ok [{:keys [db]} [_ table]]
   (debugf "query-confirm-table-delete-ok: %s" table)
   {:dispatch [:table-delete table]
    :db       (update-in db [:query table] dissoc :confirm-table-delete)}))

(rf/reg-event-fx
 :query-confirm-table-delete-cancel
 (fn query-confirm-table-delete-cancel [{:keys [db]} [_ table]]
   (debugf "query-confirm-table-delete-cancel: %s" table)
   {:db (update-in db [:query table] dissoc :confirm-table-delete)}))


(rf/reg-event-fx
 :query-column-width-dec
 (fn query-column-width-dec [{:keys [db]} [_ table column]]
   (router/query-navigate! table
                           (-> (get-in db [:view :query])
                               (update-in [:column-options column :width]
                                          #(-> (or % 12) (dec) (max 1)))))
   nil))

(rf/reg-event-fx
 :query-column-width-inc
 (fn query-column-width-inc [{:keys [db]} [_ table column]]
   (router/query-navigate! table
                           (-> (get-in db [:view :query])
                               (update-in [:column-options column :width]
                                          #(-> (or % 12) (inc)))))
   nil))

(rf/reg-event-fx
 :query-column-width-set
 (fn query-column-width-set [{:keys [db]} [_ table column width]]
   (router/query-navigate! table
                           (-> (get-in db [:view :query])
                               (assoc-in [:column-options column :width] width)))
   nil))

(rf/reg-event-fx
 :query-column-type
 (fn query-column-width-set [{:keys [db]} [_ table column type]]
   (router/query-navigate! table
                           (-> (get-in db [:view :query])
                               (assoc-in [:column-options column :type] type)
                               (assoc-in [:column-options column :format]
                                         (get-in format-value/formats [type :fmt]))))
   nil))

(rf/reg-event-fx
 :query-column-format
 (fn query-column-width-set [{:keys [db]} [_ table column format]]
   (router/query-navigate! table
                           (-> (get-in db [:view :query])
                               (assoc-in [:column-options column :format] format)))
   nil))

(rf/reg-event-db
 :query-detail-table
 (fn [db [_ table detail-table?]]
   (assoc-in db [:query table :detail-table?] detail-table?)))

(rf/reg-event-fx :query-init query-init)
(rf/reg-event-fx :query-refresh query-refresh)
(rf/reg-event-fx :query-load query-load)
(rf/reg-event-db :query-load-ok query-load-ok)
(rf/reg-event-fx :query-load-error query-load-error)
(rf/reg-event-db :query-fields-visible query-fields-visible)
(rf/reg-event-db :query-expand-log query-expand-log)
(rf/reg-event-db :query-sort-reverse query-sort-reverse)
(rf/reg-event-db :query-sort-column query-sort-column)
(rf/reg-event-db :query-submit query-submit)
(rf/reg-event-db :query-expand-field query-expand-field)
(rf/reg-event-db :query-column-add query-column-add)
(rf/reg-event-db :query-column-remove query-column-remove)
(rf/reg-event-db :query-column-left query-column-left)
(rf/reg-event-db :query-column-right query-column-right)
(rf/reg-event-db :query-page query-page)
(rf/reg-event-db :query-filter-add query-filter-add)
(rf/reg-event-db :query-filter-remove query-filter-remove)
(rf/reg-event-db :query-show-field query-show-field)
(rf/reg-event-db :query-show-field-close query-show-field-close)
;;(rf/reg-event-db :query-saved-load query-saved-load)
(rf/reg-event-db :query-settings-visible query-settings-visible)
