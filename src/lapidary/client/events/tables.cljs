(ns lapidary.client.events.tables
  (:require
   [clojure.walk :as walk]
   [ajax.core :as ajax]
   [lapidary.utils :as utils]
   [lapidary.client.db :as db]
   [re-frame.core :as rf]
   [lapidary.client.events.searches :as searches]
   [lapidary.client.router :as router]
   [lapidary.client.api :as api]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)]))

(defn result->searches [searches]
  (reduce searches/add-search {} searches))

(defn result->table [result]
  (-> result
      (update :searches result->searches)
      (assoc :time (js/Date.now))))

(defn results->tables [results]
  (->> results
       (map result->table)
       (reduce #(assoc %1 (:table_name %2) %2) nil)))

(defn table-init [cofx [_ table-name]]
  {:dispatch [:table-load table-name]})

(defn table-refresh [{:keys [db]} [_ table-name]]
  (when (db/table-refresh? db table-name)
    (debugf "table-refresh: %s" table-name)
    {:dispatch [:table-load table-name]}))

(rf/reg-event-fx :table-init table-init)
(rf/reg-event-fx :table-refresh table-refresh)

(defn table-load [{:keys [db]} [_ table-name]]
  (when (db/table-load? db table-name)
    (debugf "table-load: %s" table-name)
    (let [query (api/get-table table-name)]
      {:db         (assoc-in db [:tables table-name :loading?] true)
       :http-xhrio (merge query
                          {:on-success [:table-load-ok table-name]
                           :on-failure [:table-load-error table-name]})})))

(defn table-load-ok [db [_ table-name result]]
  (debugf "table-load-ok: Table: %s  DB Time: %s" table-name (:time result))
  (let [table (-> result :result :rows walk/keywordize-keys first result->table)]
    (db/put-table db table)))

(defn table-load-error [{:keys [db]} [_ table-name error]]
  (errorf "tables-load-error: %s %s" table-name error)
  {:dispatch [:http-error :table-load error]
   :db       (update-in db [:tables table-name] merge
                        {:time     (js/Date.now)
                         :loading? false
                         :error    error})})

(rf/reg-event-fx :table-load table-load)
(rf/reg-event-db :table-load-ok table-load-ok)
(rf/reg-event-fx :table-load-error table-load-error)

(defn tables-init [cofx _]
  {:dispatch [:tables-load]})

(defn tables-refresh [{:keys [db]} _]
  (when (db/tables-refresh? db)
    {:dispatch [:tables-load]}))

(defn tables-load [{:keys [db]} _]
  (when (db/tables-load? db)
    (debugf "tables-load")
    {:db         (assoc db :tables-loading? true)
     :http-xhrio (merge (api/get-tables)
                        {:on-success [:tables-load-ok]
                         :on-failure [:tables-load-error]})}))

(defn tables-load-ok [db [_ tables]]
  (let [tables (-> tables :result :rows walk/keywordize-keys results->tables)]
    (debugf "tables-load-ok: %s tables" (count tables))
    (merge db {:tables          tables
               :tables-time     (js/Date.now)
               :tables-loading? false
               :tables-error    nil})))

(defn tables-load-error [{:keys [db]} [_ error]]
  (errorf "tables-load-error: %s" error)
  {:dispatch [:http-error :tables-load error]
   :db       (merge db {:tables-time     (js/Date.now)
                        :tables-loading? false
                        :tables-error    error})})




(defn table-set-default-search [{:keys [db]} [_ table-name search]]
  (debugf "table-set-default-search: %s %s" table-name search)
  (debugf "current: %s" (get-in db [:tables table-name]))
  (let [options (-> (get-in db [:tables table-name :options])
                    (assoc :default-search search))]
    {:dispatch [:table-set-options table-name options]}))

(rf/reg-event-fx :tables-init tables-init)
(rf/reg-event-fx :tables-load tables-load)
(rf/reg-event-db :tables-load-ok tables-load-ok)
(rf/reg-event-fx :tables-load-error tables-load-error)
(rf/reg-event-fx :tables-refresh tables-refresh)

(defn table-query-search [{:keys [db]} [_ table-name search-name]]
  (debugf "table-search-load: %s %s" table-name search-name)
  (let [options (get-in db [:tables table-name :options])
        default (:default-search options)
        query   (or (when search-name (get-in db [:tables table-name :searches search-name]))
                    (get-in db [:tables table-name :searches (or default "Default")]))]
    (debugf "table-search-load: options=%s" options)
    (debugf "table-search-load: default=%s" default)
    {:dispatch [:tables-query table-name query]}))


(defn tables-query [cofx [_ table options]]
  (router/navigate! :lapidary/query-table {:table table} options)
  nil)

(rf/reg-event-fx :table-query-search table-query-search)
(rf/reg-event-fx :tables-query tables-query)

(defn tables-navigate [_ _]
  (router/navigate! {:name :lapidary/list-tables})
  nil)

(rf/reg-event-fx :tables-navigate tables-navigate)

(defn tables-create [{:keys [db]} [_ name]]
  (debugf "tables-create: %s" name)
  (when (and (db/table-name-ok? name))
    {:db         (assoc db :tables-creating? true)
     :http-xhrio (-> (api/create-log-table name)
                     (merge {:on-success [:tables-create-ok name]
                             :on-failure [:tables-create-error name]}))}))

(defn tables-create-ok [db [_ name result]]
  (debugf "table created: %s" name)
  (-> db
      (update :tables conj {:table_name name})
      (merge {:tables-creating?    false
              :tables-create-error nil})))

(defn tables-create-error [{:keys [db]} [_ name error]]
  (errorf "tables-create-error: %s %s" name error)
  {:db       (merge db {:tables-creating?    false
                        :tables-create-error error})
   :dispatch [:http-error :tables-create error]})

(rf/reg-event-fx :tables-create tables-create)
(rf/reg-event-db :tables-create-ok tables-create-ok)
(rf/reg-event-fx :tables-create-error tables-create-error)


(defn table-set-options [{:keys [db]} [_ table-name options]]
  (debugf "table-set-options: %s" table-name options)
  {:db         (assoc-in db [:tables table-name :options] options)
   :http-xhrio (-> (api/save-table-options table-name options)
                   (merge {:on-success [:table-set-options-ok table-name]
                           :on-failure [:table-set-options-error table-name]}))})

(defn table-set-options-ok [{:keys [db]} [_ table-name result]]
  (debugf "table-set-options-ok: %s %s" table-name result))

(defn table-set-options-error [{:keys [db]} [_ table-name error]]
  (errorf "table-set-options-error: %s %s" table-name error)
  {:dispatch [:http-error :table-load error]
   :db       (update-in db [:tables table-name] merge
                        {:time     (js/Date.now)
                         :loading? false
                         :error    error})})

(rf/reg-event-fx :table-set-default-search table-set-default-search)
(rf/reg-event-fx :table-set-options table-set-options)
(rf/reg-event-fx :table-set-options-ok table-set-options-ok)
(rf/reg-event-fx :table-set-options-error table-set-options-error)

(defn table-search-delete [{:keys [db]} [_ table search]]
  (debugf "table-search-delete: %s %s" table search)
  {:db         (update-in db [:tables table :searches] dissoc search)
   :http-xhrio (merge (api/delete-table-search table search)
                      {:on-success [:table-search-delete-ok table search]
                       :on-failure [:table-search-delete-error table search]})})

(defn table-search-delete-ok [{:keys [db]} [_ table search]]
  (debugf "table-search-delete-ok: %s %s" table search))

(defn table-search-delete-error [{:keys [db]} [_ table search error]]
  (errorf "table-search-delete-error: %s %s %s" table search error)
  {:dispatch [:http-error :table-search-delete error]})

(rf/reg-event-fx :table-search-delete table-search-delete)
(rf/reg-event-fx :table-search-delete-ok table-search-delete-ok)
(rf/reg-event-fx :table-search-delete-error table-search-delete-error)
