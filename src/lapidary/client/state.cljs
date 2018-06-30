(ns lapidary.client.state
  (:require
   [clojure.walk :as walk]
   [reagent.core :as reagent :refer [atom]]
   [lapidary.utils :as utils]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)]))

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
