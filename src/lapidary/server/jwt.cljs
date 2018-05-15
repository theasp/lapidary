(ns lapidary.server.jwt
  (:require
   [clojure.string :as str]
   [cljs.nodejs :as nodejs]
   [cljs.core.async
    :refer [<! chan put! close! onto-chan to-chan]
    :as async]
   [lapidary.server.config :refer [env]]
   ["jsonwebtoken" :as jwt]
   [taoensso.timbre :as timbre
    :refer-macros [tracef debugf infof warnf errorf]])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]))

(defn result-chan [f & args]
  (let [result   (async/promise-chan)
        callback (fn [err token]
                   (put! result {:ok?    (not err)
                                 :result (or err token)})
                   (close! result))]
    (apply f (concat args [callback]))
    result))

(defn sign
  ([payload secret opts]
   (result-chan jwt/sign
                (clj->js payload)
                (str secret)
                (clj->js opts)))
  ([payload secret opts result-fn]
   (jwt/sign (clj->js payload)
             (str secret)
             (clj->js opts)
             result-fn)))

(defn verify
  ([token secret opts]
   (result-chan jwt/verify
                (str token)
                (str secret)
                (clj->js opts)))

  ([token secret opts result-fn]
   (jwt/verify (str token)
               (str secret)
               (clj->js opts)
               result-fn)))

(defn decode [token & [opts]]
  (-> (jwt/decode token (clj->js (merge (:jwt env) opts)))
      (js->clj :keywordize-keys true)))
