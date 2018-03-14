(ns lapidary.server
  (:require
   [cljs.nodejs :as nodejs]
   [cljs.core.async
    :refer [<! chan put! close! onto-chan to-chan]]
   [clojure.string :as str]
   [com.stuartsierra.component :as component]
   [lapidary.server.web-handler :refer [new-web-handler]]
   [lapidary.server.pg-pool :as pg-pool]
   [lapidary.server.api :as api]
   [lapidary.server.pg-schema :as pg-schema]
   [taoensso.timbre :as timbre
    :refer-macros [tracef debugf infof warnf errorf]])
  (:require-macros
   [cljs.core.async.macros :as m :refer [go]]))

(enable-console-print!)

(defonce current-system (atom nil))

(defn parse-int [data] (int data))

(defn- keywordize [s]
  (-> (str/lower-case s)
      (str/replace "_" "-")
      (str/replace "." "-")
      (keyword)))

(defn- unkeywordize [s]
  (-> (name s)
      (str/upper-case)
      (str/replace "-" "_")))

(defn env
  "Returns the value of the environment variable k,
   or raises if k is missing from the environment."
  [k]
  #_(tracef "Getting env: %s=%s" (unkeywordize k) (aget (.-env nodejs/process) (unkeywordize k)))
  (aget (.-env nodejs/process) (unkeywordize k)))

(let [web-proto (or (env :web-proto) "https")
      web-fqdn  (or (env :web-fqdn) "localhost")
      web-root  (or (env :web-root) "")
      web-url   (or (env :web-url) (str web-proto "://" web-fqdn web-root))
      http-port (parse-int (or (env :http-port) (env :port) 3001))]
  (def config
    {:log-level     :debug
     :http-port     http-port
     :http-address  (or (env :http-address) "127.0.0.1")
     :web-proto     web-proto
     :web-fqdn      web-fqdn
     :web-root      web-root
     :web-url       web-url
     :jwt           {:secret   (or (env :jwt-secret) "5cf447c9-779c-47bd-bc10-f581ef85fe3a")
                     :audience (or (env :jwt-audience) "lapidary")
                     :expire   (or (env :jwt-expire) "7d")}
     :cookie-secret (or (env :cookie-secret) "a56d91fe526ab7d7")
     :development   (= (env :development) "true")
     :admin         {:username (or (env :admin-username) "admin")
                     :password (or (env :admin-password) "ChangeMe!")}
     :db            {:make-pool? true
                     :pool-size  10
                     :db         (or (env :db-name) "lapidary")
                     :user       (or (env :db-user) "lapidary")
                     :password   (or (env :db-password) "lapidary")
                     :host       (or (env :db-host) "localhost")
                     :port       (parse-int (or (env :db-port) 5432))}}))

(set! js/console.debug js/console.log)

(defn system []
  (component/system-map
   :config config
   :state (atom nil)
   :pg-schema (pg-schema/new-pg-schema)
   :database (pg-pool/new-pg-pool)
   :api (api/new-api-handler)
   :web-handler (new-web-handler)))

(def dev-system system)
(def prod-system system)

(defn start! []
  (timbre/set-level! (get config :log-level :info))
  (when (nil? @current-system)
    (infof "Starting")
    (->> (prod-system)
         (component/start)
         (reset! current-system))))

(defn stop! []
  (when (some? @current-system)
    (infof "Stopping")
    (component/stop @current-system)
    (reset! current-system nil)))

(defn restart! []
  (stop!)
  (start!))

(defn -main [& args]
  (start!))

(def on-death (nodejs/require "death" #js {:SIGHUP true
                                           :debug  true}))

(defn death-handler [signal err]
  (if (some? err)
    (do
      (errorf "Uncaught exception: %s" err)
      (tracef "Cause:")
      (.exit js/process 1))
    (do
      (infof "Recieved signal: %s" signal)
      (stop!)
      (.exit js/process 0))))

(on-death death-handler)

(set! *main-cli-fn* -main)
