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
  (debugf "login: %s" username)
  (when-not (get-in db [:login :loading?])
    {:db         (update db :login merge
                         {:username nil
                          :password nil
                          :loading? true})
     :http-xhrio (-> (api/login {:username username :password password})
                     (merge {:on-success [:login-ok]
                             :on-failure [:login-error]}))}))

(defn login-ok [{:keys [db]} [_ result]]
  (let [{:keys [username password jwt]} result]
    (debugf "login-ok: %s" result)
    {:dispatch [:refresh]
     :db       (update db :login merge
                       {:username username
                        :password password
                        :jwt      jwt
                        :loading? false
                        :error    nil})}))

(defn login-error [{:keys [db]} [_ error]]
  (errorf "login-error: %s" error)
  {:dispatch [:http-error :login error]
   :db       (update db :login merge {:jwt      nil
                                      :loading? false
                                      :error    error})})

(defn login-expired [{:keys [db]} _]
  (when-not (get-in db [:login :loading?])
    (if (db/login-ok? db)
      {:dispatch [:login (get-in db [:login :username]) (get-in db [:login :password])]
       :db       (update db :login merge {:jwt nil})}
      {:db (assoc-in db [:login :jwt] nil)})))

(rf/reg-event-fx :login-expired login-expired)
(rf/reg-event-fx :login login)
(rf/reg-event-fx :login-ok login-ok)
(rf/reg-event-fx :login-error login-error)
