(ns lapidary.server.ldap-auth
  (:require
   [clojure.walk :refer [postwalk]]
   ["ldapauth-fork" :as LdapAuth]
   [lapidary.server.config :refer [env]]
   [mount.core :refer [defstate]]
   [taoensso.timbre :as timbre
    :refer-macros [tracef debugf infof warnf errorf]]))

(defn remove-nils [m]
  (let [f (fn [[k v]] (when v [k v]))]
    (postwalk (fn [x] (if (map? x) (into {} (map f x)) x)) m)))


(defn make-options [opts]
  (-> {:url             (get opts :url "ldapi:///")
       :bindDN          (get opts :bind-dn nil)
       :bindCredentials (get opts :bind-password nil)
       :searchBase      (get opts :user-base-dn "uid=readonly,cn=users,cn=accounts,dc=gt0,dc=ca")
       :searchFilter    (get opts :user-filter "(uid={{username}})")
       :reconnect       (get opts :reconnect true)
       :tlsOptions      {:rejectUnauthorized (get opts :tls-verify true)}}
      (remove-nils)))


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

(defstate ldap-auth
  :start (start-ldapauth @env)
  :end (stop-ldapauth ldapauth))
