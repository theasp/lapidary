(ns lapidary.client.events.error
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

(defn http-error [{:keys [db]} [_ type error]]
  (case (:status error)
    403 {:dispatch [:login-expired]}
    401 {:dispatch [:login-expired]}
    {:dispatch [:api-error type]}))

(rf/reg-event-fx :http-error http-error)


(rf/reg-event-fx
 :api-error
 (fn api-error [& stuff]
   (debugf "api-error: %s" stuff)))
