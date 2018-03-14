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
  (rf/dispatch [:login-ok result]))

(defn dispatch-login-error [result]
  (rf/dispatch [:login-error result]))

(defn login [db [_ username password]]
  (debugf "login: %s %s" username password)
  (if (get-in db [:login :loading?])
    db
    (do
      (api/login {:username username :password password}
                 dispatch-login-ok
                 dispatch-login-error)
      (assoc-in db [:login :loading?] true))))

(defn login-ok [cofx [_ jwt]]
  {:dispatch [:refresh]
   :db       (-> (:db cofx)
                 (update :login merge
                         {:jwt      jwt
                          :loading? false
                          :error    nil}))})

(defn login-error [db [_ error]]
  (update db :login merge
          {:jwt      nil
           :loading? false
           :error    error}))

(rf/reg-event-db :login login)
(rf/reg-event-fx :login-ok login-ok)
(rf/reg-event-db :login-error login-error)
