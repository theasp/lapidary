(ns lapidary.server.web-handler
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [clojure.string :as str]
   [cljs.nodejs :as nodejs]
   [com.stuartsierra.component :as component]
   [ca.gt0.theasp.macchiato-core-async :as m-async]
   [macchiato.server :as http]
   [macchiato.middleware.defaults :as defaults]
   [macchiato.middleware.restful-format :as restful-format]
   [macchiato.middleware.resource :as resource]
   [macchiato.middleware.session.cookie :as session-cookie]
   [macchiato.auth.backends.session :refer [session-backend]]
   [macchiato.auth.middleware :refer [wrap-authentication]]
   [macchiato.util.response :as r]
   [bide.core :as bide]
   [lapidary.routes :as routes]
   [lapidary.utils :as utils :refer [obj->map]]
   [lapidary.server.web-pages :as pages]
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
          method                       (:method req)
          handler-fn                   (get-handler-fn handlers handler method)]
      #_(debugf "Request: %s" (select-keys req [:handler :request-method :websocket? :uri :params :session :route-params]))
      (if (fn? handler-fn)
        (handler-fn req res raise)
        (do
          (warnf "Unable to find handler %s for method %s" handler method)
          (if-let [handler-fn (get-handler-fn handlers :not-found method)]
            (handler req res raise)
            (res (r/not-found))))))))

(defn wrap-middleware [handler config]
  (-> handler
      (wrap-authentication (session-backend))
      (restful-format/wrap-restful-format)
      (resource/wrap-resource "resources/public")
      (defaults/wrap-defaults (-> defaults/site-defaults
                                  (assoc-in [:security :anti-forgery] false)))))

(defn wrap-map-with [m f]
  (reduce-kv #(assoc %1 %2 (f %3)) nil m))

(defn stop-http [server]
  (.close server #(debugf "HTTP server no longer listening")))

(defn start-http [{:keys [http-port http-address secret]} handler]
  (let [host       (or http-address "127.0.0.1")
        port       (or http-port 3000)
        on-success #(debugf "HTTP server listening on %s:%s" host port)
        server     (http/start {:handler     handler
                                :host        host
                                :port        port
                                :on-success  on-success
                                :websockets? true})]
    (http/start-ws server handler)
    #(stop-http server)))

(defrecord Handler [sente api web-router web-routes config stop-fn]
  component/Lifecycle
  (start [this]
    (infof "Starting")
    (let [props        {:path-for (partial bide/resolve routes/web-router)
                        :title    "LogView"}
          app-handler  (-> props pages/app-page)
          handlers     (-> {:app       app-handler
                            :not-found (pages/not-found props)}
                           (merge (:ring-handlers api))
                           (wrap-map-with m-async/wrap-async))
          ring-handler (-> (make-router handlers)
                           (wrap-middleware config))]
      (assoc this :stop-fn (start-http config ring-handler))))

  (stop [this]
    (infof "Stopping")
    (when stop-fn
      (stop-fn))
    (dissoc this :server :stop-fn)))

(defn new-web-handler []
  (-> (map->Handler {})
      (component/using [:api :config])))
