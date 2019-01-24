(ns lapidary.server.web.handler
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [clojure.string :as str]
   [cljs.nodejs :as nodejs]
   [macchiato.server :as http]
   [ca.gt0.theasp.macchiato-core-async :as m-async]
   [macchiato.middleware.defaults :as defaults]
   [macchiato.middleware.restful-format :as restful-format]
   [macchiato.middleware.resource :as resource]
   [macchiato.middleware.session.cookie :as session-cookie]
   [macchiato.auth.backends.session :refer [session-backend]]
   [macchiato.util.response :as r]
   [mount.core :refer [defstate]]
   [bide.core :as bide]
   [lapidary.routes :as routes]
   [lapidary.server.config :refer [env]]
   [lapidary.server.web.api :as web-api]
   [lapidary.server.web.pages :as web-pages]
   [lapidary.server.web.router :as web-router]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)]))

(debugf "Web handler loaded")

(defn wrap-middleware [handler]
  (-> handler
      (restful-format/wrap-restful-format {:keywordize? true})
      (resource/wrap-resource "resources/public")
      (defaults/wrap-defaults (-> defaults/site-defaults
                                  (assoc-in [:security :anti-forgery] false)))))

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

(defn start-web-handler [{:keys [enabled?] :as config}]
  (when enabled?
    (infof "Starting HTTP server")
    (let [props        {:title "Lapidary"}
          ring-handler (-> (merge web-pages/handlers web-api/handlers)
                           (web-router/route-handler)
                           (wrap-middleware))]
      {:stop-fn (start-http config ring-handler)})))

(defn stop-web-handler [{:keys [stop-fn]}]
  (when stop-fn
    (infof "Stopping HTTP server")
    (stop-fn)))

(defstate web-handler
  :start (start-web-handler (:http @env))
  :stop (stop-web-handler @web-handler))
