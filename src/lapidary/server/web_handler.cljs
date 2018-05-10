(ns lapidary.server.web-handler
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [clojure.string :as str]
   [cljs.nodejs :as nodejs]
   [ca.gt0.theasp.macchiato-core-async :as m-async]
   [macchiato.server :as http]
   [macchiato.middleware.defaults :as defaults]
   [macchiato.middleware.restful-format :as restful-format]
   [macchiato.middleware.resource :as resource]
   [macchiato.middleware.session.cookie :as session-cookie]
   [macchiato.auth.backends.session :refer [session-backend]]
   [macchiato.util.response :as r]
   [mount.core :refer [defstate]]
   [bide.core :as bide]
   [lapidary.routes :as routes]
   [lapidary.utils :as utils :refer [obj->map]]
   [lapidary.server.web-pages :as pages]
   [lapidary.server.config :refer [env]]
   [lapidary.server.api :refer [api-handlers]]

   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)]))

(defn get-handler-fn [handlers handler method]
  (let [handler-fn (get handlers handler (:not-found handlers))]
    (if (map? handler-fn)
      (get handler-fn method)
      handler-fn)))

(defn make-router [handlers]
  (fn [req res raise]
    (let [[handler route-params query] (bide/match routes/web-router (:uri req))
          handler                      (or handler :not-found)
          req                          (assoc req :handler handler :route-params route-params)
          method                       (:request-method req)
          handler-fn                   (get-handler-fn handlers handler method)]
      #_(debugf "Request: %s" (select-keys req [:handler :request-method :websocket? :uri :params :session :route-params]))
      (if (fn? handler-fn)
        (handler-fn req res raise)
        (do
          (warnf "Unable to find handler %s for method %s" handler method)
          (if-let [handler-fn (get-handler-fn handlers :not-found method)]
            (handler req res raise)
            (res (r/not-found))))))))

(defn wrap-middleware [handler]
  (-> handler
      (restful-format/wrap-restful-format)
      (resource/wrap-resource "resources/public")
      (defaults/wrap-defaults (-> defaults/site-defaults
                                  (assoc-in [:security :anti-forgery] false)))))

(defn wrap-map-with [m f]
  (reduce-kv #(assoc %1 %2 (f %3)) nil m))

(defn stop-http [server]
  (.close server #(debugf "HTTP server no longer listening")))

(defn start-http [{:keys [port address secret] :as config} handler]
  (let [on-success #(debugf "HTTP server listening on %s:%s" address port)
        server     (http/start {:handler     handler
                                :host        address
                                :port        port
                                :on-success  on-success
                                :websockets? true})]
    (http/start-ws server handler)
    #(stop-http server)))

(defn start-web-handler []
  (infof "Starting")
  (let [props        {:path-for (partial bide/resolve routes/web-router)
                      :title    "Lapidary"}
        app-handler  (-> props pages/app-page)
        handlers     (-> {:app       app-handler
                          :not-found (pages/not-found props)}
                         (wrap-map-with m-async/wrap-async)
                         (merge api-handlers))
        ring-handler (-> (make-router handlers)
                         (wrap-middleware))]
    {:stop-fn (start-http (:http @env) ring-handler)}))

(defn stop-web-handler [web-handler]
  (infof "Stopping")
  (when-let [stop-fn (:stop-fn web-handler)]
    (stop-fn)))

(defstate web-handler
  :start (start-web-handler)
  :stop (stop-web-handler web-handler))
