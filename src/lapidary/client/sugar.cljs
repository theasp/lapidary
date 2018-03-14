(ns lapidary.client.sugar
  (:require
   ["sugar-date" :as sugar-date]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)]))

(sugar-date/Date.extend)

(defn format-short [time]
  (.format time "%F %T %Z"))

(defn format-long [time]
  (.format time "{yyyy}-{MM}-{dd} {HH}:{mm}:{ss}.{SSS} %Z"))

(defn parse-valid? [time]
  (sugar-date/Date.isValid time))

(defn parse-time [time]
  (try
    (sugar-date/Date.create time)
    (catch js/Object e
      (debugf "Unable to parse '%s': %s" time e))))
