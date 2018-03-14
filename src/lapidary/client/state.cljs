(ns lapidary.client.state
  (:require
   [clojure.walk :as walk]
   [reagent.core :as reagent :refer [atom]]
   [lapidary.utils :as utils]
   [cljs.core.async :refer [<! chan put! close! promise-chan] :as async]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]))

(def default-order "time.desc")

(def default-query-form
  {:query      ""
   :start-time "2 hours ago"
   :end-time   "now"})


(def default {:view       :tables
              :query-form default-query-form})

(defonce app-state (atom default))

(defn cursor [path]
  (reagent/cursor app-state path))

(def settings   (cursor [:settings]))
(def view       (cursor [:view]))
(def query-form (cursor [:query-form]))

(def cached-cursor (memoize cursor))

(defn detect-type [v]
  (cond
    (nil? v)     :nil
    (boolean? v) :boolean
    (string? v)  :string
    (integer? v) :integer
    (number? v)  :number
    (inst? v)    :timestamp
    (vector? v)  :vector
    (seq? v)     :seq
    (map? v)     :map
    (object? v)  :object
    :default     :unknown))

(defn update-type [cur v]
  (if (or (nil? cur) (= :nil cur))
    (detect-type v)
    cur))
