(ns lapidary.server.auth-middleware
  (:require
   [clojure.string :as str]
   [lapidary.server.config :refer [env]]
   [lapidary.server.jwt :as jwt]
   [lapidary.server.response :as response]
   [mount.core :refer [defstate]]
   [macchiato.auth.backends.session :as session]
   [macchiato.auth.middleware :as m-auth]
   [taoensso.timbre :as timbre
    :refer-macros [tracef debugf infof warnf errorf]]))

(defn wrap-jwt-auth [handler {:keys [secret audience]}]
  (fn [req res raise]
    (let [[bearer token] (some-> (get-in req [:headers "authorization"])
                                 (str/split #"\s+"))]
      (if (and (= bearer "Bearer") (not (str/blank? token)))
        (jwt/verify token secret {:audience audience}
                    (fn [err token]
                      (if err
                        (response/unauthorized req (str "Invalid JWT: " err))
                        (let [payload  (:payload token)
                              identity {:method      :jwt
                                        :username    (:username payload)
                                        :role        (:role payload)
                                        :jwt-payload payload}]
                          (if (and (:role payload) (:username payload))
                            (-> (assoc req :identity identity)
                                (handler res raise))
                            (response/forbidden req "Access denied"))))))
        (handler req res raise)))))

(defn wrap-session-auth [handler]
  (fn [req res raise]
    (if-let [identity (get-in req [:session :identity])]
      (handler (assoc req :identity identity) res raise)
      (handler req res raise))))

(defn wrap-session-save [handler]
  (fn [req res raise]
    (handler (update req :session dissoc :identity)
             (fn [result]
               (if-let [identity (or (:identity result) (:identity req))]
                 (res (assoc result :session
                             (-> (or (:session result) (:session req))
                                 (assoc :identity identity))))
                 (res (update result :session dissoc :identity))))
             raise)))

(defn wrap-authentication [handler options]
  (-> handler
      (wrap-session-save)
      (wrap-session-auth)
      (wrap-jwt-auth (:jwt options))))

(defn static-auth [username password result-fn]
  (debugf "Static auth %s" username)
  (let [users      (:users @env)
        check-user (get users username)]
    (if (and check-user (= password (:password check-user)))
      (result-fn (-> check-user
                     (dissoc :password)
                     (assoc :usernaame username)))
      (result-fn false))))

(defn has? [value coll]
  (contains? coll value))

(defn highest-mapping [mappings]
  (let [roles (-> mappings vals set)]
    (condp has? roles
      :admin :admin
      :write :write
      :read  :read
      nil)))

(defn wrap-authorization [handler roles]
  (fn [req res raise]
    (let [identity (:identity req)
          roles    (set roles)
          role     (keyword (:role identity))]
      (cond
        (not identity)         (res (response/unauthorized req))
        (contains? roles role) (handler req res raise)
        :default               (res (response/forbidden req))))))
