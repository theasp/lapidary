(ns lapidary.client.ui.misc
  (:require
   [lapidary.client.router :as router]
   [lapidary.utils :as utils]
   [lapidary.client.sugar :as sugar]
   [re-frame.core :as rf]
   [clojure.string :as str]
   [reagent.core :as reagent :refer [atom]]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)]))


(def battery-boxes
  [[:i.fas.fa-battery-empty]
   [:i.fas.fa-battery-quarter]
   [:i.fas.fa-battery-half]
   [:i.fas.fa-battery-three-quarters]
   [:i.fas.fa-battery-full]])

(def battery-colors
  ["has-text-danger"
   "has-text-warning"
   "has-text-warning"
   "has-text-warning"
   "has-text-success"])

(def battery-count (count battery-boxes))

(defn battery-icon [p]
  (let [c (-> (- battery-count 1)
              (* p)
              (int))]
    [:span {:class (str "icon " (get battery-colors c))
            :title (goog.string.format "%0.2f%" (* 100 p))}
     (get battery-boxes c)]))

(defn format-label [n value]
  (if (< n (count value))
    (-> (subs value 0 (- n 1))
        (str "…"))
    value))

(defn format-value-label [value]
  [:span {:title (str value)}
   (cond (nil? value) [:i "<nil>"]
         (= value "") [:i "<empty string>"]
         :default     (format-label 24 (str value)))])

(def colors ["dark" "info" "success" "warning" "danger"])

(defn popularity [p]
  (let [p     (if (js/isNaN p) 1 p)
        color (get colors (js/Math.floor (* p (dec (count colors)))))]
    [:progress {:title (goog.string.format "%0.1f%" (* 100 p))
                :class (str "progress is-short is-" color)
                :value p
                :max   1}]))

(def time-format-long sugar/format-long)

(def type-formats {:timestamptz {:short time-format-long
                                 :long  time-format-long}
                   :timestamp   {:short time-format-long
                                 :long  time-format-long}})

(def field-type-names
  {:string    "s"
   :integer   "i"
   :number    "#"
   :timestamp "t"
   :ltree     "."
   :unknown   "?"})

(def id-column :id)

(defn format-value [value type size]
  (let [format-fn (get-in type-formats [type size] str)]
    (try (format-fn value)
         (catch js/Object e
           (warnf "Formatter caught error: %s %s" e
                  {:type  (type value)
                   :value value})))))

(def escape-chars {"\"" "\\\""
                   "."  nil})

(defn needs-escape? [s]
  (->> (keys escape-chars)
       (some #(str/includes? s %))))

(defn format-path-element [e]
  (let [e (name e)]
    (if (needs-escape? e)
      (str "\"" (str/escape e escape-chars) "\"")
      e)))

(defn format-path [path]
  (if (= :record (first path))
    (->> (rest path)
         (map format-path-element)
         (str/join "."))
    (->> path
         (map format-path-element)
         (str/join ".")
         (str "@"))))

(defn trigger
  "Returns a reagent class that can be used to easily add triggers
  from the map in `props`, such as :component-did-mount.  See
  `reagent.core/create-class` for more information."
  [props content]
  (-> {:display-name "trigger"}
      (merge props)
      (assoc :reagent-render (fn [_ content] content))
      (reagent/create-class)))
