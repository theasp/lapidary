(ns lapidary.server.config
  (:require
   [clojure.string :as str]
   [lapidary.utils :as utils]
   [clojure.set :refer [map-invert]]
   [macchiato.env :as config]
   [mount.core :refer [defstate]]
   [taoensso.timbre :as timbre
    :refer-macros [tracef debugf infof warnf errorf]]))

(def defaults
  {:jwt       {:secret   nil
               :audience "lapidary"
               :expire   "7d"}
   :auth      {:method "users"
               :secret "a56d91fe526ab7d7"}
   :http      {:address "127.0.0.1"
               :port    8080}
   :users     {:admin {:password "ChangeMe!"
                       :role     "admin"}}
   :ldap      {:url           "ldapi:///"
               :bind-dn       "cn=readonly,dc=example,dc=com"
               :bind-password "readonly"
               :user-attr     :uid
               :group-attr    :memberOf
               :user-base-dn  "ou=users,dc=example,dc=com"
               :user-filter   "(uid={{username}})"
               :role-mappings {:read  "cn=lapidary,cn=groups,dc=example,dc=com"
                               :admin "cn=lapidary-admin,cn=groups,dc=example,dc=com"}
               :reconnect     {:initialDelay (* 1 1000)
                               :maxDelay     (* 2 60 1000)}
               :tls-verify    true}
   :db        {:pool-size 10
               :hostname  "lapidary"
               :username  "lapidary"
               :password  "lapidary"
               :database  "lapidary"
               :port      5432}
   :provision {:tables []}})

(defn csv [v]
  (if (string? v)
    (map str/trim (str/split v ","))
    v))

(defn env* []
  (-> (config/deep-merge defaults (config/env))
      (select-keys (keys defaults))
      (update :users utils/update-map-keys name)
      (update :users utils/update-map update :role keyword)
      (update-in [:auth :method] keyword)
      (update-in [:ldap :user-attr] keyword)
      (update-in [:ldap :group-attr] keyword)
      (update-in [:ldap :role-mappings] map-invert)
      (update-in [:provision :tables] csv)))

(defstate env :start (env*))
