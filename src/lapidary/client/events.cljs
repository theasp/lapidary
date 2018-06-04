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
   [lapidary.client.events.error :as error]
   [mount.core :refer [defstate]]
   [re-frame.core :as rf]
   [lapidary.client.api :as api]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)]))

(defn initialize [{:keys [db]} _]
  {:dispatch [:refresh]
   :db       (merge db/default-db db)})

(defn refresh [cofx _]
  (let [db (:db cofx)]
    (when (db/login-ok? db)
      (condp = (get-in db [:view :name])
        :lapidary/list-tables
        {:dispatch [:tables-refresh]}

        :lapidary/query-table
        (let [table (get-in db [:view :params :table])
              field (get-in db [:query table :show-field :name])]
          {:dispatch-n [[:query-refresh table]
                        [:searches-refresh table]
                        #_[:table-refresh table]
                        (when field
                          [:field-refresh table field])]})))))

(defn view-update  [db [_ new-view]]
  #_(debugf "view-update: %s" new-view)
  (merge (condp = (:name new-view)
           :lapidary/list-tables
           {:dispatch [:tables-init]}

           :lapidary/query-table
           (let [table (get-in new-view [:params :table])
                 field (get-in new-view [:query :show-field :name])]
             {:dispatch-n [[:query-init table]
                           (when field
                             [:field-init table field])]}))
         {:db (assoc db :view new-view)}))

(rf/reg-event-fx :initialize initialize)
(rf/reg-event-fx :view-update view-update)
(rf/reg-event-fx :refresh refresh)

(defn refresh-timer []
  (let [timer (js/setInterval #(rf/dispatch [:refresh]) 1000)]
    #(js/clearInterval timer)))

(defstate timestep
  :start (refresh-timer)
  :stop (timestep))
