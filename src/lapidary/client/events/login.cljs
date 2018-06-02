(ns lapidary.client.events.login
  (:require
   [ajax.core :as ajax]
   [lapidary.utils :as utils]
   [lapidary.client.db :as db]
   [re-frame.core :as rf]
   [lapidary.client.events.searches :as searches]
   [lapidary.client.router :as router]
   [lapidary.client.api :as api]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)]))

(defn login [{:keys [db]} [_ username password]]
  (debugf "Attempting login: %s" username)
  (when-not (get-in db [:login :loading?])
    (api/login {:username username :password password}
               #(rf/dispatch [:login-ok username password %])
               #(rf/dispatch [:login-error %]))
    {:db (update db :login merge
                 {:username nil
                  :password nil
                  :loading? true})}))

(defn login-ok [{:keys [db]} [_ username password jwt]]
  (debugf "login ok: %s" username)
  {:dispatch [:refresh]
   :db       (update db :login merge
                     {:username username
                      :password password
                      :jwt      jwt
                      :loading? false
                      :error    nil})})

(defn login-error [{:keys [db]} [_ error]]
  (errorf "login: %s" error)
  {:dispatch [:http-error :login error]
   :db       (update db :login merge {:jwt      nil
                                      :loading? false
                                      :error    error})})

(defn jwt-expired [{:keys [db]} _]
  (when-not (get-in db [:login :loading?])
    (if (db/login-ok? db)
      {:dispatch [:login (get-in db [:login :username]) (get-in db [:login :password])]
       :db       (update db :login merge {:jwt nil})}
      {:db (assoc-in db [:login :jwt] nil)})))

(rf/reg-event-fx :jwt-expired jwt-expired)
(rf/reg-event-fx :login login)
(rf/reg-event-fx :login-ok login-ok)
(rf/reg-event-fx :login-error login-error)
