(ns lapidary.client.events.login
  (:require
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

(defn dispatch-login-ok [result]
  )

(defn dispatch-login-error [result]
  (rf/dispatch [:login-error result]))

(defn login [{:keys [db]} [_ username password]]
  (debugf "login: %s %s" username password)
  (when-not (get-in db [:login :loading?])
    (api/login {:username username :password password}
               #(rf/dispatch [:login-ok username password %])
               #(rf/dispatch [:login-error %]))
    {:db (update db :login merge
                 {:username nil
                  :password nil
                  :loading? true})}))

(defn login-ok [{:keys [db]} [_ username password jwt]]
  {:dispatch [:refresh]
   :db       (update db :login merge
                     {:username username
                      :password password
                      :jwt      jwt
                      :loading? false
                      :error    nil})})

(defn login-error [{:keys [db]} [_ error]]
  {:dispatch [:api-error :login error]
   :db       (update db :login merge
                     {:jwt      nil
                      :loading? false
                      :error    error})})

(rf/reg-event-fx :login login)
(rf/reg-event-fx :login-ok login-ok)
(rf/reg-event-fx :login-error login-error)
