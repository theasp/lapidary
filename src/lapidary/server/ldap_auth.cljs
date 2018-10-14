(ns lapidary.server.ldap-auth
  (:require
   [clojure.walk :refer [postwalk]]
   ["ldapauth-fork" :as LdapAuth]
   [lapidary.server.auth-middleware :as auth]
   [lapidary.server.config :refer [env]]
   [mount.core :refer [defstate]]
   ;;   ["bunyan" :as bunyan]
   [taoensso.timbre :as timbre
    :refer-macros [tracef debugf infof warnf errorf]]))

;;(def log (bunyan/createLogger #js {:name "lapidary"}))
;;(.level log "trace")

(defn remove-nils [m]
  (let [f (fn [[k v]] (when v [k v]))]
    (postwalk (fn [x] (if (map? x) (into {} (map f x)) x)) m)))

(defn make-options [opts]
  (-> {:url             (get opts :url "ldapi:///")
       :bindDN          (get opts :bind-dn nil)
       :bindCredentials (get opts :bind-password nil)
       :searchBase      (get opts :user-base-dn "cn=users,cn=accounts,dc=example,dc=com")
       :bindProperty    (get opts :bind-property :dn)
       :searchFilter    (get opts :user-filter "(uid={{username}})")
       :timeount        (get opts :timeout (* 30 1000))
       :connectTimeout  (get opts :connectTimeout (* 30 1000))
       :idleTimeout     (get opts :idleTimeout (* 30 1000))
       :reconnect       (get opts :reconnect true)
       ;;:log             log
       :tlsOptions      {:rejectUnauthorized (get opts :tls-verify true)}}
      ))

(defn start-ldapauth [config]
  (when (= :ldap (get-in config [:auth :method]))
    (infof "Starting")
    (try
      (let [opts     (-> config :ldap make-options)
            ldapauth (new LdapAuth (clj->js opts))]
        (.on ldapauth "error"
             (fn [err]
               (errorf "LDAP connection problem: %s" err)))
        ldapauth)
      (catch js/Object e
        (errorf "Unable to start LDAP: %s" e)))))

(defn stop-ldapauth [ldapauth]
  (when ldapauth
    (debugf "Stopping")
    (.close ldapauth)))

(defstate connection
  :start (start-ldapauth @env)
  :end (stop-ldapauth ldapauth))

(defn ldap-auth-error [result-fn username err]
  (warnf "Unable to authenticate %s: %s" username err)
  (result-fn :unauthorized))

(defn ldap-auth-ok [{:keys [user-attr group-attr role-mappings]} result-fn result]
  (let [result   (js->clj result :keywordize-keys true)
        username (get result user-attr)
        groups   (get result group-attr)
        role     (-> (select-keys role-mappings groups)
                     (auth/highest-mapping))
        user     (if role
                   {:username username
                    :role     role}
                   :forbidden)]
    (result-fn user)))

(defn ldap-auth-result [options result-fn username err result]
  #_(debugf "LDAP result for %s: %s" username (or err (js->clj result)))
  (if err
    (ldap-auth-error result-fn username err)
    (ldap-auth-ok options result-fn result)))

(defn ldap-auth [username password result-fn]
  (let [options (:ldap @env)]
    (.authenticate @connection username password #(ldap-auth-result options result-fn username %1 %2))))
