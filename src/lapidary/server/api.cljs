(ns lapidary.server.api
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [lapidary.utils :as utils :refer [error?]]
   [lapidary.server.pg :as pg]
   [lapidary.server.pg-pool :refer [pg-pool]]
   [lapidary.server.stats :as stats]
   [lapidary.server.jwt :as jwt]
   [lapidary.server.ldap-auth :as ldap-auth]
   [clojure.string :as str]
   [cljs.core.async
    :refer [<! chan put! close! onto-chan to-chan]
    :as async]
   [ca.gt0.theasp.macchiato-core-async :as m-async]
   [lapidary.server.config :as config :refer [env]]
   [lapidary.server.auth-middleware :as auth]
   [lapidary.server.response :as response]
   [macchiato.util.response :as r]
   [macchiato.middleware.anti-forgery :as csrf]
   [mount.core :refer [defstate]]
   [taoensso.timbre :as timbre
    :refer-macros [tracef debugf infof warnf errorf]]))

(defn ->debugf [data fmt & [process-fn]]
  (debugf fmt ((or process-fn identity) data))
  data)

(defn api-query-execute [req client sql]
  (let [start-time (js/Date.now)
        result     (pg/execute! client sql)]
    (go
      (let [result   (<! result)
            end-time (js/Date.now)
            time     (- end-time start-time)]
        (cond
          (not result)    (response/internal-server-error req)
          (error? result) (response/bad-request req {:execute sql
                                                     :msg     (str "SQL error: " result)})
          :default        (r/ok {:result result :time time}))))))

(def begin-transaction ["BEGIN TRANSACTION"])
(def rollback-transaction ["ROLLBACK TRANSACTION"])
(def commit-transaction ["COMMIT TRANSACTION"])

(defn api-query-transaction [req client all-sql]
  (go
    (let [start-time (js/Date.now)
          result     (<! (pg/execute! client begin-transaction))]
      (if (error? result)
        (response/internal-server-error req {:sql begin-transaction
                                             :msg (str "Error beginning transaction: " result)})
        (let [results (loop [results []
                             all-sql all-sql]
                        (if-let [sql (first all-sql)]
                          (let [result (<! (pg/execute! client sql))]
                            #_(debugf "Execute: %s" sql)
                            #_(debugf "Got result: %s" (type result))
                            (if (error? result)
                              result
                              (recur (conj results result)
                                     (rest all-sql))))
                          results))]
          (if (error? results)
            (do
              (<! (pg/execute! client rollback-transaction))
              (response/internal-server-error req {:transaction all-sql
                                                   :msg         (str "Error while executing query: " results)}))
            (let [result (<! (pg/execute! client commit-transaction))]
              (if (error? result)
                (response/internal-server-error req (str "Error commiting transaction: " result))
                (r/ok {:result results
                       :time   (- (js/Date.now) start-time)})))))))))

(defn api-query []
  (-> (fn [req]
        #_(debugf "api-query")
        (let [body        (:body req)
              execute     (:execute body)
              transaction (:transaction body)]
          (if (and (nil? execute) (nil? transaction))
            (response/bad-request req "Query requires execute or transaction")
            (go
              (let [[client done err] (-> @pg-pool :pool pg/connect! <!)]
                (if (or (nil? client) (some? err))
                  (do
                    (done)
                    (response/internal-server-error req
                                                    {:msg (str "Error acquiring DB connection from pool: " (or err "unknown"))}))
                  (let [result (cond
                                 (some? execute)     (api-query-execute req client execute)
                                 (some? transaction) (api-query-transaction req client transaction))
                        result (if (m-async/read-port? result)
                                 (<! result)
                                 result)]
                    (done)
                    result)))))))
      (m-async/wrap-async)
      (auth/wrap-authorization #{:admin})
      (auth/wrap-authentication @env)
      (stats/wrap-stats :api-query)))



(defn api-login-sign-ok [req res raise identity token]
  (debugf "Login ok: %s" identity)
  (-> (assoc identity :jwt token)
      (r/ok)
      (assoc :identity identity)
      (res)))

(defn api-login-sign-error [req res raise identity err]
  (errorf "Error while signing identity: %s %s" identity err)
  (-> (response/internal-server-error req)
      (res)))

(defn api-login-sign-identity [{:keys [expire audience secret]}]
  (fn [req res raise identity]
    (jwt/sign identity secret {:expiresIn expire :audience audience}
              (fn [err token]
                #_(debugf "jwt-sign-result: %s" [err token])
                (if err
                  (api-login-sign-error req res raise identity err)
                  (api-login-sign-ok req res raise identity token))))))

(defn auth-result [req res raise sign-fn identity]
  (debugf "Got identity: %s" identity)
  (cond
    (nil? identity)            (res (response/internal-server-error req "Login returned nil"))
    (not identity)             (res (response/unauthorized req "Login failed"))
    (= :forbidden identity)    (res (response/forbidden req "Access denied"))
    (= :unauthorized identity) (res (response/unauthorized req "Login failed"))
    :default                   (sign-fn req res raise identity)))

(defn api-login []
  (let [env     @env
        auth-fn (case (get-in env [:auth :method] :users)
                  :ldap   ldap-auth/ldap-auth
                  :static auth/static-auth
                  :users  auth/static-auth
                  :user   auth/static-auth)
        sign-fn (api-login-sign-identity (:jwt env))]
    (-> (fn [{:keys [body] :as req} res raise]
          (debugf "Login attempt")
          (auth-fn (:username body) (:password body)
                   #(auth-result req res raise sign-fn %)))
        (auth/wrap-session-save)
        (stats/wrap-stats :api-login))))

(def api-handlers {:api-query (api-query)
                   :api-login (api-login)})
