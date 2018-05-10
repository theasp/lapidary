(ns lapidary.server.api
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [lapidary.server.pg :as pg]
   [lapidary.server.pg-pool :refer [pg-pool]]
   [lapidary.server.jwt :as jwt]
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

(defn error? [e]
  (instance? js/Error e))

(defn sql-response [req result]
  (go (if-let [result (<! result)]
        (do
          #_(debugf "SQL RESULT: %s" result)
          (if (error? result)
            (response/bad-request req (str "SQL error: " result))
            (r/ok {:result result})))
        (response/internal-server-error req))))

(defn api-query-execute [req client sql]
  (sql-response req (pg/execute! client sql)))

(defn api-query-transaction [req client all-sql]
  (go
    (let [start-transaction (<! (pg/execute! client ["BEGIN TRANSACTION"]))]
      (if (error? start-transaction)
        (response/internal-server-error req (str "Error beginning transaction: " start-transaction))
        (let [results (loop [results []
                             all-sql all-sql]
                        (if-let [sql (first all-sql)]
                          (do
                            #_(debugf "Execute: %s" sql)
                            (let [result (<! (pg/execute! client sql))]
                              #_(debugf "Got result: %s" (type result))
                              (if (error? result)
                                result
                                (recur (conj results result)
                                       (rest all-sql)))))
                          results))]
          (if (error? results)
            (do
              (<! (pg/execute! client ["ROLLBACK TRANSACTION"]))
              (response/internal-server-error req (str "Error while executing query: " results)))
            (let [commit-transaction (<! (pg/execute! client ["COMMIT TRANSACTION"]))]
              (if (error? commit-transaction)
                (response/internal-server-error req (str "Error commiting transaction: " commit-transaction))
                (r/ok {:result results})))))))))

(defn api-query []
  (-> (fn [req]
        #_(debugf "api-query")
        (let [body        (:body req)
              execute     (:execute body)
              transaction (:transaction body)]
          (if (and (nil? execute) (nil? transaction))
            (response/bad-request req "Query requires execute or transaction")
            (go
              (let [[client done err] (<! (pg/connect! @pg-pool))]
                (if (or (nil? client) (some? err))
                  (do
                    (done)
                    (response/internal-server-error req (str "Error acquiring DB connection from pool: " (or err "unknown"))))
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
      (auth/wrap-authentication @env)))

(defn jwt-sign-result [req err token]
  (if err
    (response/internal-server-error req (str "Problem signing token: " (:result token)))
    (-> (:result token)
        (jwt/decode)
        (assoc :token (:result token))
        (r/ok))))

(defn api-login []
  (let [{:keys [jwt auth]}               @env
        {:keys [expire audience secret]} jwt]
    (-> (fn [req res raise]
          (let [body     (:body req)
                username (:username body)
                password (:password body)]
            (if (and (= username (:admin-username auth))
                     (= password (:admin-password auth)))
              (let [identity {:username username
                              :password password
                              :role     :admin}]
                (jwt/sign identity secret {:expiresIn expire
                                           :audience  audience}
                          (fn [err token]
                            (debugf "Login: %s" identity)
                            (-> (jwt-sign-result req err token)
                                (assoc :identity identity)
                                (res)))))
              (res (response/unauthorized req "Wrong username or password")))))
        (auth/wrap-session-save))))

(def api-handlers {:api-query (api-query)
                   :api-login (api-login)})
