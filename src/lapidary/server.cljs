(ns lapidary.server
  (:require
   [cljs.nodejs :as nodejs]
   [cljs.core.async :refer [<! chan put! close! onto-chan to-chan]]
   [clojure.string :as str]
   [lapidary.server.web-handler]
   [lapidary.server.pg-pool]
   [lapidary.server.api :as api]
   [lapidary.server.pg-schema :as pg-schema]
   [lapidary.server.config :refer [env]]
   [mount.core :as mount :refer [defstate]]
   [taoensso.timbre :as timbre :refer-macros [tracef debugf infof warnf errorf]]
   ["death" :as on-death])
  (:require-macros
   [cljs.core.async.macros :as m :refer [go]]))

(enable-console-print!)

(set! js/console.debug js/console.log)

(timbre/set-level! :debug)

(defn death-handler [signal err]
  (if (some? err)
    (do
      (errorf "Uncaught exception: %s" err)
      (tracef "Cause:")
      (.exit js/process 1))
    (do
      (infof "Recieved signal: %s" signal)
      (.exit js/process 0))))

(on-death death-handler)

(defn start! []
  (infof "Lapidary starting...")
  (mount/start)
  (infof "Lapidary running"))

(defn stop! []
  (infof "Lapidary stopping...")
  (mount/stop)
  (infof "Lapidary stopped"))

#_(defonce current-system (atom nil))

#_(defn parse-int [data] (int data))

#_(defn- keywordize [s]
    (-> (str/lower-case s)
        (str/replace "_" "-")
        (str/replace "." "-")
        (keyword)))

#_(defn- unkeywordize [s]
    (-> (name s)
        (str/upper-case)
        (str/replace "-" "_")))

#_(defn env
    "Returns the value of the environment variable k,
   or raises if k is missing from the environment."
    [k & [default]]
    #_(tracef "Getting env: %s=%s" (unkeywordize k) (aget (.-env nodejs/process) (unkeywordize k)))
    (or (aget (.-env nodejs/process) (unkeywordize k)) default))

#_(def config
    (let [web-proto (env :web-proto "https")
          web-fqdn  (env :web-fqdn "localhost")
          web-root  (env :web-root "")
          web-url   (env :web-url (str web-proto "://" web-fqdn web-root))
          http-port (parse-int (env :http-port (env :port 3001)))]
      {:log-level     :debug
       :http-port     http-port
       :http-address  (env :http-address "127.0.0.1")
       :web-proto     web-proto
       :web-fqdn      web-fqdn
       :web-root      web-root
       :web-url       web-url
       :auth-method   (keyword (env :auth-method "password"))
       :ldap          {:url             (env :ldap-url "ldapi:///")
                       :bindDN          (env :ldap-admin-dn)
                       :bindCredentials (env :ldap-admin-password)
                       :searchBase      (env :ldap-base-dn "dc=example,dc=com")
                       :searchFilter    (env :ldap-search-filter "(uid={{username}})")
                       :reconnect       (= "true" (env :ldap-reconnect "true"))
                       :tlsOptions      {:rejectUnauthorized (= "true" (env :ldap-tls-verify "true"))}}
       :jwt           {:secret   (env :jwt-secret "5cf447c9-779c-47bd-bc10-f581ef85fe3a")
                       :audience (env :jwt-audience "lapidary")
                       :expire   (env :jwt-expire "7d")}
       :cookie-secret (env :cookie-secret "a56d91fe526ab7d7")
       :development   (= (env :development "false") "true")
       :admin         {:username (env :admin-username "admin")
                       :password (env :admin-password "ChangeMe!")}
       :db            {:make-pool? true
                       :pool-size  10
                       :db         (env :db-name "lapidary")
                       :user       (env :db-user "lapidary")
                       :password   (env :db-password "lapidary")
                       :host       (env :db-host "localhost")
                       :port       (parse-int (env :db-port "5432"))}}))
