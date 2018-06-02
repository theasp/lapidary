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

(def table-expiry (* 60 1000))
(def tables-expiry (* 30 1000))

(defn table-refresh [{:keys [db]} [_ table]]
  (when (and (not (get-in db [:tables table :loading?]))
             (utils/expired? (get-in db [:tables table :time]) table-expiry))
    {:dispatch [:table-load table]}))

(defn table-load [{:keys [db]} [_ table]]
  (when (and (not (:tables-loading? db))
             (db/login-ok? db))
    {:db         (assoc db :tables-loading? true)
     :http-xhrio (-> (api/get-table table)
                     (merge {:on-success [:table-load-ok]
                             :on-failure [:table-load-error]}))}))

(defn tables-refresh [{:keys [db]} _]
  (when (and (not (:tables-loading? db))
             (utils/expired? (:tables-time db) tables-expiry))
    {:dispatch [:tables-load]}))

(defn tables-load [{:keys [db]} _]
  (when (and (not (:tables-loading? db))
             (db/login-ok? db))
    {:db         (assoc db :tables-loading? true)
     :http-xhrio (merge (api/get-tables)
                        {:on-success [:tables-load-ok]
                         :on-failure [:tables-load-error]})}))

(defn result->searches [searches]
  (reduce searches/add-search {} searches))

(defn result->table [table]
  (update table :searches result->searches))

(defn tables-load-ok [db [_ tables]]
  (let [tables (-> tables :result :rows walk/keywordize-keys)]
    (debugf "tables-load: %s tables" (count tables))
    (merge db {:tables          (map result->table tables)
               :tables-time     (js/Date.now)
               :tables-loading? false
               :tables-error    nil})))

(defn tables-load-error [{:keys [db]} [_ error]]
  (errorf "tables-load: %s" error)
  {:dispatch [:http-error :tables-load error]
   :db       (merge db {:tables-time     (js/Date.now)
                        :tables-loading? false
                        :tables-error    error})})

(defn tables-init [cofx _]
  {:dispatch [:tables-refresh]})

(defn tables-query [cofx [_ table options]]
  (router/navigate! :lapidary/query-table {:table table} options)
  nil)

(defn tables-create [{:keys [db]} [_ name]]
  (debugf "creating table: %s" name)
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

(defn tables-navigate [_ _]
  (router/navigate! {:name :lapidary/list-tables})
  nil)

(rf/reg-event-fx :tables-init tables-init)
(rf/reg-event-fx :tables-load tables-load)
(rf/reg-event-db :tables-load-ok tables-load-ok)
(rf/reg-event-fx :tables-load-error tables-load-error)
(rf/reg-event-fx :tables-refresh tables-refresh)
(rf/reg-event-fx :tables-query tables-query)
(rf/reg-event-fx :tables-create tables-create)
(rf/reg-event-db :tables-create-ok tables-create-ok)
(rf/reg-event-fx :tables-create-error tables-create-error)
(rf/reg-event-fx :tables-navigate tables-navigate)
