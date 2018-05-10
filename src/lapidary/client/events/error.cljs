(ns lapidary.client.events.error
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

(defn http-error [{:keys [db]} [_ type error]]
  (case (:status error)
    403 {:dispatch [:jwt-expired]}
    401 {:dispatch [:jwt-expired]}
    {:dispatch [:api-error type]}))

(rf/reg-event-fx :http-error http-error)
