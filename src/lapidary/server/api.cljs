(ns lapidary.server.api
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [lapidary.server.pg :as pg]
   [lapidary.server.jwt :as jwt]
   [clojure.string :as str]
   [cljs.core.async
    :refer [<! chan put! close! onto-chan to-chan]
    :as async]
   [ca.gt0.theasp.macchiato-core-async :as m-async]
   [macchiato.util.response :as r]
   [macchiato.middleware.anti-forgery :as csrf]
   [com.stuartsierra.component :as component]
   [taoensso.timbre :as timbre
    :refer-macros [tracef debugf infof warnf errorf]]))

(defn ->debugf [data fmt & [process-fn]]
  (debugf fmt ((or process-fn identity) data))
  data)

(def req-warn-keys [:handler :method :uri :body])

(defn error? [e]
  (instance? js/Error e))

(defn throw-if-error [obj]
  (if (error? obj)
    (throw obj)
    obj))

(defn not-found [req & [body]]
  (warnf "Not found: %s %s" (select-keys req req-warn-keys) body)
  (tracef "Cause")
  (r/not-found {:error body}))

(defn bad-request [req & [body]]
  (warnf "Bad request: %s %s" (select-keys req req-warn-keys) body)
  (tracef "Cause")
  (r/bad-request {:error body}))

(defn unauthorized [req & [body]]
  (warnf "Uauthorized: %s %s" (select-keys req req-warn-keys) body)
  (tracef "Cause")
  (r/unauthorized {:error body}))

(defn forbidden [req & [body]]
  (warnf "Forbidden: %s %s" (select-keys req req-warn-keys) body)
  (tracef "Cause")
  (r/forbidden {:error body}))

(defn internal-server-error [req & [body]]
  (warnf "Internal server error: %s %s" (select-keys req req-warn-keys) body)
  (tracef "Cause")
  (r/internal-server-error {:error body}))

(defn wrap-check-jwt [f {:keys [secret audience]}]
  (fn [req]
    (let [authorization (some-> (get-in req [:headers "authorization"])
                                (str/split #"\s+"))
          bearer        (first authorization)
          token         (second authorization)]
      (if (and (= bearer "Bearer")
               (not (str/blank? token)))
        (let [verify (jwt/verify token secret {:audience audience})]
          (go
            (let [{:keys [result ok?]} (<! verify)]
              (if ok?
                (f (assoc req :jwt result))
                (forbidden req (str "Invalid JWT: " result))))))
        (unauthorized req "Missing JWT")))))

(defn sql-response [req result]
  (go (if-let [result (<! result)]
        (do
          #_(debugf "SQL RESULT: %s" result)
          (if (error? result)
            (bad-request req (str "SQL error: " result))
            (r/ok {:result result})))
        (internal-server-error req))))

(defn api-query-execute [req db sql]
  (sql-response req (pg/execute! db sql)))

(defn api-query-transaction [req client all-sql]
  (go
    (let [start-transaction (<! (pg/execute! client ["BEGIN TRANSACTION"]))]
      (if (error? start-transaction)
        (internal-server-error req (str "Error beginning transaction: " start-transaction))
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
              (internal-server-error req (str "Error while executing query: " results)))
            (let [commit-transaction (<! (pg/execute! client ["COMMIT TRANSACTION"]))]
              (if (error? commit-transaction)
                (internal-server-error req (str "Error commiting transaction: " commit-transaction))
                (r/ok {:result results})))))))))

(defn api-query [db jwt]
  (-> (fn [req]
        #_(debugf "api-query")
        (let [body        (:body req)
              execute     (:execute body)
              transaction (:transaction body)]
          (if (and (nil? execute) (nil? transaction))
            (bad-request req "Query requires execute or transaction")
            (go
              (let [[client done err] (<! (pg/connect! db))]
                (if (or (nil? client) (some? err))
                  (internal-server-error req (str "Error acquiring DB connection from pool: " (or err "unknown")))
                  (let [result (cond
                                 (some? execute)     (api-query-execute req client execute)
                                 (some? transaction) (api-query-transaction req client transaction))
                        result (if (m-async/read-port? result)
                                 (<! result)
                                 result)]
                    (done)
                    result)))))))
      (wrap-check-jwt jwt)))

(defn api-login [db {:keys [secret audience expire admin-username admin-password]} admin]
  (let [options {:expiresIn expire
                 :audience  audience}]
    (fn [req]
      #_(debugf "api-login: %s" (select-keys req req-warn-keys))
      (let [user    (select-keys (:body req) [:username :password])
            payload (select-keys user [:username])]
        (go
          (if (= user admin)
            (let [token (<! (jwt/sign payload secret options))]
              #_(debugf "token: %s" token)
              (if (:ok? token)
                (-> (:result token)
                    (jwt/decode)
                    (assoc :token (:result token))
                    (r/ok))
                (internal-server-error (str "Problem signing token: "
                                            (:result token)))))
            (unauthorized req "Wrong username or password")))))))

(defrecord ApiHandler [database ring-handlers config]
  component/Lifecycle
  (start [this]
    (infof "Starting")
    (let [db       (:db database)
          jwt      (:jwt config)
          admin    (:admin config)
          handlers {:api-query (api-query db jwt)
                    :api-login (api-login db jwt admin)}]
      (assoc this :ring-handlers handlers)))

  (stop [this]
    (infof "Stopping")
    (dissoc this :handlers)))

(defn new-api-handler []
  (-> (map->ApiHandler {})
      (component/using [:database :config])))
