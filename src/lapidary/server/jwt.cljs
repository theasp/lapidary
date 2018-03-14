(ns lapidary.server.jwt
  (:require
   [clojure.string :as str]
   [cljs.nodejs :as nodejs]
   [cljs.core.async
    :refer [<! chan put! close! onto-chan to-chan]
    :as async]
   [taoensso.timbre :as timbre
    :refer-macros [tracef debugf infof warnf errorf]])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]))

(def jwt (nodejs/require "jsonwebtoken"))

(defn result-chan [f & args]
  (let [result   (async/promise-chan)
        callback (fn [err token]
                   (put! result {:ok?    (not err)
                                 :result (or err token)})
                   (close! result))]
    (apply f (concat args [callback]))
    result))

(defn sign [payload secret & [opts]]
  (result-chan jwt.sign
               (clj->js payload)
               (str secret)
               (clj->js opts)))
(defn verify [token secret & [opts]]
  (result-chan jwt.verify
               (str token)
               (str secret)
               (clj->js opts)))

(defn decode [token & [opts]]
  (-> (jwt.decode token (clj->js opts))
      (js->clj :keywordize-keys true)))
