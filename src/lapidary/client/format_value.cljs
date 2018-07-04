(ns lapidary.client.format-value
  (:require
   [lapidary.utils :as utils]
   ["sugar-date" :as sugar-date]
   [lapidary.client.sugar :as sugar]
   [clojure.string :as str]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)]))

(defn format-value [fmt value]
  (try
    (goog.string/format fmt value)
    (catch js/Object e
      (warnf "Bad format: %s %s" fmt e)
      (str value))))

(defn format-timestamp [fmt time]
  (try
    (when (some? time)
      (let [time (sugar-date/Date.create time)]
        (.format time fmt)))
    (catch js/Object e
      (warnf "Unable to format time: %s" e)
      (str time))))

(defn format-timestamp-utc [fmt time]
  (try
    (when (some? time)
      (let [time (sugar-date/Date.create time #js {:fromUTC true :setUTC true})]
        (.format time fmt)))
    (catch js/Object e
      (warnf "Unable to format time: %s" e)
      (str time))))

(def formats
  {:auto          {:name "Auto"
                   :fn   format-value
                   :fmt  "%s"}
   :string        {:name "String"
                   :fn   format-value
                   :fmt  "%s"}
   :boolean       {:name "Boolean"
                   :fn   format-value
                   :fmt  "%s"}
   :number        {:name "Number"
                   :fn   format-value
                   :fmt  "%f"}
   :integer       {:name "Number"
                   :fn   format-value
                   :fmt  "%d"}
   :timestamp     {:name "Timestamp"
                   :fn   format-timestamp
                   :fmt  "{yyyy}-{MM}-{dd} {HH}:{mm}:{ss}.{SSS} {Z}"}
   :timestamp-utc {:name "Timestamp (UTC)"
                   :fn   format-timestamp-utc
                   :fmt  "{yyyy}-{MM}-{dd} {HH}:{mm}:{ss}.{SSS} UTC"}})

(defn format [value value-type fmt]
  (let [format    (get formats value-type)
        format-fn (:fn format)]
    #_(debugf "Value-Type %s  Object %s" value-type (type value))
    (format-fn (if (or (nil? fmt) (= fmt ""))
                 (:fmt format)
                 fmt)
               value)))
