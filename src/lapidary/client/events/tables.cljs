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
   [cljs.core.async :refer [<! chan put! close! promise-chan] :as async]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]))

(defn tables-refresh [db _]
  (when (and (not (:tables-loading? db))
             (utils/expired? (:tables-time db) (js/Date.now) (* 30 1000)))
    #_(debugf "Refreshing tables")
    (rf/dispatch [:tables-load]))
  db)

(defn tables-load [db _]
  (if (and (not (:tables-loading? db))
           (db/login-ok? db))
    (do
      (api/get-tables! (get-in db [:login :jwt])
                       #(rf/dispatch [:tables-load-ok (-> % :result :rows walk/keywordize-keys)])
                       #(rf/dispatch [:tables-load-error %]))
      (assoc db :tables-loading? true))
    db))

(defn result->searches [searches]
  (reduce searches/add-search {} searches))

(defn result->table [table]
  (update table :searches result->searches))

(defn tables-load-ok [db [_ tables]]
  #_(debugf "tables-load-ok: %s" tables)
  (merge db {:tables          (map result->table tables)
             :tables-time     (js/Date.now)
             :tables-loading? false
             :tables-error    nil}))

(defn tables-load-error [db [_ error]]
  #_(debugf "tables-load-error: %s" error)
  (merge db {:tables          nil
             :tables-time     (js/Date.now)
             :tables-loading? false
             :tables-error    error}))

(defn tables-init [cofx _]
  {:dispatch [:tables-refresh]})

(defn tables-query [cofx [_ table options]]
  (router/navigate! :lapidary/query-table {:table table} options)
  nil)

(defn tables-create [db [_ name]]
  (when (and (db/table-name-ok? name))
    (do
      (api/create-log-table name (get-in db [:login :jwt])
                            #(rf/dispatch [:tables-create-ok name %])
                            #(rf/dispatch [:tables-create-error name %]))
      {:db (assoc db :tables-creating? true)})))

(defn tables-create-ok [db [_ name result]]
  (merge db {:tables-creating?    false
             :tables-create-error nil}))

(defn tables-create-error [db [_ name result]]
  (merge db {:tables-creating?    false
             :tables-create-error result}))

(rf/reg-event-fx :tables-init tables-init)
(rf/reg-event-db :tables-load tables-load)
(rf/reg-event-db :tables-load-ok tables-load-ok)
(rf/reg-event-db :tables-load-error tables-load-error)
(rf/reg-event-db :tables-refresh tables-refresh)
(rf/reg-event-fx :tables-query tables-query)
(rf/reg-event-db :tables-create tables-create)
(rf/reg-event-db :tables-create-ok tables-create-ok)
(rf/reg-event-db :tables-create-error tables-create-error)
