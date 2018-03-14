(ns lapidary.client.events
  (:require
   [ajax.core :as ajax]
   [lapidary.utils :as utils]
   [lapidary.client.db :as db]
   [lapidary.client.events.tables :as tables]
   [lapidary.client.events.query :as query]
   [lapidary.client.events.field :as field]
   [lapidary.client.events.searches :as searches]
   [lapidary.client.events.login :as login]
   [re-frame.core :as rf]
   [lapidary.client.api :as api]
   [cljs.core.async :refer [<! chan put! close! promise-chan] :as async]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]))

(defonce timestep
  (go-loop []
    (rf/dispatch [:refresh])
    (<! (async/timeout 1000))
    (recur)))

(defn initialize [{:keys [db]} _]
  {:dispatch [:refresh]
   :db       (merge db/default-db db)})

(defn refresh [cofx _]
  (let [db (:db cofx)]
    (when (db/login-ok? db)
      {:dispatch-n
       (condp = (get-in db [:view :name])
         :lapidary/list-tables
         [[:tables-refresh]]

         :lapidary/query-table
         (let [table (get-in db [:view :params :table])
               field (get-in db [:query table :show-field :name])]
           [[:query-refresh table]
            [:searches-refresh table]
            (when field
              [:field-refresh table field])]))})))

(defn view-update  [db [_ new-view]]
  #_(debugf "view-update: %s" new-view)
  (condp = (:name new-view)
    :lapidary/list-tables
    (rf/dispatch [:tables-init])

    :lapidary/query-table
    (let [table (get-in new-view [:params :table])]
      (rf/dispatch [:query-init table])
      (when-let [field (get-in new-view [:query :show-field :name])]
        (rf/dispatch [:field-init table field]))))
  (assoc db :view new-view))

(rf/reg-event-fx :initialize initialize)
(rf/reg-event-db :view-update view-update)
(rf/reg-event-fx :refresh refresh)
