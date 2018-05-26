(ns lapidary.server.config
  (:require
   [clojure.set :refer [map-invert]]
   [macchiato.env :as config]
   [mount.core :refer [defstate]]
   [taoensso.timbre :as timbre
    :refer-macros [tracef debugf infof warnf errorf]]))

(def defaults
  {:jwt  {:secret   nil
          :audience "lapidary"
          :expire   "7d"}
   :auth {:method         "user"
          :admin-username "admin"
          :admin-password "ChangeMe!"
          :secret         "a56d91fe526ab7d7"}
   :http {:address "127.0.0.1"
          :port    8080}
   :ldap {:url           "ldapi:///"
          :bind-dn       nil
          :bind-password nil
          :user-attr     :dn
          :group-attr    :memberOf
          :user-base-dn  "ou=users,dc=example,dc=com"
          :user-filter   "(uid={{username}})"
          :role-mappings {:read  "cn=lapidary,cn=groups,dc=example,dc=com"
                          :admin "cn=lapidary-admin,cn=groups,dc=example,dc=com"}
          :reconnect     true
          :tls-verify    true}
   :db   {:pool-size 10
          :hostname  "lapidary"
          :username  "lapidary"
          :password  "lapidary"
          :database  "lapidary"
          :port      5432}})

(defn env* []
  (-> (config/deep-merge defaults (config/env))
      (select-keys (keys defaults))
      (update-in [:auth :method] keyword)
      (update-in [:ldap :user-attr] keyword)
      (update-in [:ldap :group-attr] keyword)
      (update-in [:ldap :role-mappings] map-invert)))

(defstate env :start (env*))
