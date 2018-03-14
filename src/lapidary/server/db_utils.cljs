(ns lapidary.server.db-utils
  (:require
   [cljs.core.async
    :refer [<! chan put! close! onto-chan to-chan]]
   [lapidary.utils :as utils]
   [taoensso.timbre :as timbre
    :refer-macros [tracef debugf infof warnf errorf]])
  (:require-macros
   [cljs.core.async.macros :as m :refer [go]]))

(defn sql-result [result]
  (when-not (utils/exception? result)
    result))

(defn result->rows [result]
  (-> result sql-result))

(defn result->row [result]
  (-> result sql-result first))

(defn query->rows [result]
  (go (-> result <! sql-result)))

(defn query->row [result]
  (go (-> result <! sql-result first)))

(defn add-times [x]
  (let [now (utils/now)]
    (if (some? (:created x))
      (assoc x :modified now)
      (assoc x :created now :modified now))))
