(ns lapidary.server.web.router
  (:require
   [macchiato.util.response :as r]
   [bide.core :as bide]
   [lapidary.routes :as routes]
   [taoensso.timbre :as timbre
    :refer-macros [tracef debugf infof warnf errorf]])
  (:require-macros
   [hiccups.core :as hiccups :refer [html]]))

(defn get-handler-fn [handlers handler method]
  (let [handler-fn (get handlers handler (:page/not-found handlers))]
    (if (map? handler-fn)
      (get handler-fn method)
      handler-fn)))

(defn route-handler [handlers]
  (fn [req res raise]
    (let [[handler route-params query] (bide/match routes/router (:uri req))
          handler                      (or handler :page/not-found)
          req                          (assoc req :handler handler :route-params route-params)
          method                       (:request-method req)
          handler-fn                   (get-handler-fn handlers handler method)]
      #_(debugf "Request: %s" (select-keys req [:handler :request-method :websocket? :uri :params :session :route-params]))
      (if (fn? handler-fn)
        (handler-fn req res raise)
        (do
          (warnf "Unable to find handler %s for method %s" handler method)
          (if-let [handler-fn (get-handler-fn handlers :page/not-found method)]
            (handler req res raise)
            (res (r/not-found))))))))
