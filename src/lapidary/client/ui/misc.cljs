(ns lapidary.client.ui.misc
  (:require
   [lapidary.client.router :as router]
   [lapidary.utils :as utils]
   [lapidary.sugar :as sugar]
   [re-frame.core :as rf]
   [clojure.string :as str]
   [reagent.core :as reagent :refer [atom]]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)]))

(def icons-fa
  {:info               [:i.fas.fa-info]
   :expanded           [:i.fas.fa-caret-down]
   :collapsed          [:i.fas.fa-caret-right]
   :list               [:i.fas.fa-list]
   :settings           [:i.fas.fa-cog]
   :tables             [:i.fas.fa-table]
   :order-ascend       [:i.fas.fa-chevron-down]
   :order-descend      [:i.fas.fa-chevron-up]
   :dropdown           [:i.fa.fa-angle-down]
   :checkbox-checked   [:i.fas.fa-check]
   :checkbox-unchecked " "
   :value-copy         [:i.fas.fa-copy]
   :value-require      [:i.fas.fa-check]
   :value-exclude      [:i.fas.fa-times]
   :query-saved        [:i.fas.fa-bookmark]
   :query-add          [:i.fas.fa-plus]
   :column-add         [:i.fas.fa-plus]
   :column-left        [:i.fas.fa-arrow-left]
   :column-right       [:i.fas.fa-arrow-right]
   :column-remove      [:i.fas.fa-minus]
   :type-string        [:i.fas.fa-font]
   :type-integer       [:i.fas.fa-list-ol]
   :type-number        [:i.fas.fa-list-ol]
   :type-timestamp     [:i.far.fa-clock]
   :type-ltree         [:i.fas.fa-ellipsis-h]
   :type-unknown       [:i.fas.fa-question]})


(def icons-material-icons
  {:info               [:i.material-icons "info"]
   :expanded           [:i.material-icons "caret-down"]
   :collapsed          [:i.material-icons "caret-right"]
   :list               [:i.material-icons "list"]
   :settings           [:i.material-icons "settings"]
   :tables             [:i.material-icons "table"]
   :order-ascend       [:i.material-icons "chevron-down"]
   :order-descend      [:i.material-icons "chevron-up"]
   :dropdown           [:i.material-icons "angle-down"]
   :checkbox-checked   [:i.material-icons "check"]
   :checkbox-unchecked " "
   :value-copy         [:i.material-icons "copy"]
   :value-require      [:i.material-icons "check"]
   :value-exclude      [:i.material-icons "times"]
   :query-saved        [:i.material-icons "bookmark"]
   :query-add          [:i.material-icons "plus"]
   :column-add         [:i.material-icons "plus"]
   :column-left        [:i.material-icons "arrow-left"]
   :column-right       [:i.material-icons "arrow-right"]
   :column-remove      [:i.material-icons "minus"]
   :type-string        [:i.material-icons "font"]
   :type-integer       [:i.material-icons "list-ol"]
   :type-number        [:i.material-icons "list-ol"]
   :type-timestamp     [:i.material-icons "clock"]
   :type-ltree         [:i.material-icons "ellipsis-h"]
   :type-unknown       [:i.material-icons "question"]})


(def icons-mdi
  {:info               [:i.mdi.mdi-24px.mdi-eye]
   :info-sm            [:i.mdi.mdi-18px.mdi-eye]
   :expanded           [:i.mdi.mdi-24px.mdi-chevron-down]
   :collapsed          [:i.mdi.mdi-24px.mdi-chevron-right]
   :expanded-sm        [:i.mdi.mdi-18px.mdi-chevron-down]
   :collapsed-sm       [:i.mdi.mdi-18px.mdi-chevron-right]
   :list               [:i.mdi.mdi-24px.mdi-view-list]
   :settings           [:i.mdi.mdi-24px.mdi-settings]
   :tables             [:i.mdi.mdi-24px.mdi-table]
   :order-ascend       [:i.mdi.mdi-24px.mdi-chevron-down]
   :order-descend      [:i.mdi.mdi-24px.mdi-chevron-up]
   :dropdown           [:i.mdi.mdi-24px.mdi-menu-down]
   :checkbox-checked   [:i.mdi.mdi-24px.mdi-check]
   :checkbox-unchecked " "
   :value-copy         [:i.mdi.mdi-24px.mdi-content-copy]
   :value-copy-sm      [:i.mdi.mdi-18px.mdi-content-copy]
   :value-require      [:i.mdi.mdi-24px.mdi-check]
   :value-exclude      [:i.mdi.mdi-24px.mdi-close]
   :value-require-sm   [:i.mdi.mdi-18px.mdi-check]
   :value-exclude-sm   [:i.mdi.mdi-18px.mdi-close]
   :query-saved        [:i.mdi.mdi-24px.mdi-bookmark]
   :query-add          [:i.mdi.mdi-24px.mdi-bookmark-plus]
   :query-saved-sm     [:i.mdi.mdi-18px.mdi-bookmark]
   :query-add-sm       [:i.mdi.mdi-18px.mdi-bookmark-plus]
   :column-add         [:i.mdi.mdi-24px.mdi-plus]
   :column-left        [:i.mdi.mdi-24px.mdi-arrow-left]
   :column-right       [:i.mdi.mdi-24px.mdi-arrow-right]
   :column-remove      [:i.mdi.mdi-24px.mdi-minus]
   :column-add-sm      [:i.mdi.mdi-18px.mdi-plus]
   :column-left-sm     [:i.mdi.mdi-18px.mdi-arrow-left]
   :column-right-sm    [:i.mdi.mdi-18px.mdi-arrow-right]
   :column-remove-sm   [:i.mdi.mdi-18px.mdi-minus]
   :type-string        [:i.mdi.mdi-24px.mdi-format-text]
   :type-integer       [:i.mdi.mdi-24px.mdi-numeric]
   :type-number        [:i.mdi.mdi-24px.mdi-numeric]
   :type-timestamp     [:i.mdi.mdi-24px.mdi-clock-outline]
   :type-ltree         [:i.mdi.mdi-24px.mdi-dots-horizontal]
   :type-unknown       [:i.mdi.mdi-24px.mdi-help]
   :type-string-sm     [:i.mdi.mdi-18px.mdi-format-text]
   :type-integer-sm    [:i.mdi.mdi-18px.mdi-numeric]
   :type-number-sm     [:i.mdi.mdi-18px.mdi-numeric]
   :type-timestamp-sm  [:i.mdi.mdi-18px.mdi-clock-outline]
   :type-ltree-sm      [:i.mdi.mdi-18px.mdi-dots-horizontal]
   :type-unknown-sm    [:i.mdi.mdi-18px.mdi-help]
   :search             [:i.mdi.mdi-24px.mdi-table-search]
   :search-lg          [:i.mdi.mdi-36px.mdi-table-search]
   :search-sm          [:i.mdi.mdi-18px.mdi-table-search]
   :star-sm            [:i.mdi.mdi-18px.mdi-star]
   :table-create       [:i.mdi.mdi-24px.mdi-table-plus]
   :table-create-lg    [:i.mdi.mdi-36px.mdi-table-plus]
   :filter-enabled     [:i.mdi.mdi-24px.mdi-filter]
   :filter-disabled    [:i.mdi.mdi-24px.mdi-filter-outline]
   :trash-sm           [:i.mdi.mdi-24px.mdi-delete]
   :arrow-up-sm        [:i.mdi.mdi-18px.mdi-arrow-up]
   :arrow-down-sm      [:i.mdi.mdi-18px.mdi-arrow-down]
   :spinner-plus-sm    [:i.mdi.mdi-18px.mdi-plus]
   :spinner-minus-sm   [:i.mdi.mdi-18px.mdi-minus]
   :login-user-sm      [:i.mdi.mdi-18px.mdi-account]
   :login-password-sm  [:i.mdi.mdi-18px.mdi-lock]
   :information        [:i.mdi.mid-24px.mdi-information-variant]
   :fields-visible     [:i.mdi.mdi-24px.mdi-format-list-bulleted]
   :fields-hidden      [:i.mdi.mdi-24px.mdi-format-list-bulleted]
   :table              [:i.mdi.mdi-24px.mdi-table]})

(def icons icons-mdi)

(def battery-boxes-fa
  [[:i.fas.fa-battery-empty]
   [:i.fas.fa-battery-quarter]
   [:i.fas.fa-battery-half]
   [:i.fas.fa-battery-three-quarters]
   [:i.fas.fa-battery-full]])

(def battery-boxes-material
  [[:i.fas.fa-battery-empty]
   [:i.fas.fa-battery-quarter]
   [:i.fas.fa-battery-half]
   [:i.fas.fa-battery-three-quarters]
   [:i.fas.fa-battery-full]])

(def battery-boxes-mdi
  [[:i.mdi.mdi-18px.mdi-battery-outline]
   [:i.mdi.mdi-18px.mdi-battery-10]
   [:i.mdi.mdi-18px.mdi-battery-20]
   [:i.mdi.mdi-18px.mdi-battery-30]
   [:i.mdi.mdi-18px.mdi-battery-40]
   [:i.mdi.mdi-18px.mdi-battery-50]
   [:i.mdi.mdi-18px.mdi-battery-60]
   [:i.mdi.mdi-18px.mdi-battery-70]
   [:i.mdi.mdi-18px.mdi-battery-80]
   [:i.mdi.mdi-18px.mdi-battery-90]
   [:i.mdi.mdi-18px.mdi-battery]])

(def battery-boxes battery-boxes-mdi)

(def battery-colors
  ["has-text-danger"
   "has-text-danger"
   "has-text-danger"
   "has-text-warning"
   "has-text-warning"
   "has-text-warning"
   "has-text-dark"
   "has-text-dark"
   "has-text-dark"
   "has-text-success"
   "has-text-success"
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

(defn has-to-string? [obj]
  (and (object? obj)
       (some? (.-toString obj))))

(defn format-value-label [value]
  [:span {:title (str value)}
   (cond (nil? value)                     [:i "<nil>"]
         (= value "")                     [:i "<empty string>"]
         (has-to-string? (clj->js value)) (format-label 24 (.toString (clj->js value)))
         :default                         (format-label 24 (str value)))])

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
                                 :long  time-format-long}
                   :default     {:short str
                                 :log   str}})

(def field-type-names
  {:string    (:type-string icons)
   :integer   (:type-integer icons)
   :number    (:type-number icons)
   :timestamp (:type-timestamp icons)
   :ltree     (:type-ltree icons)
   :unknown   (:type-unknown icons)})

(def field-type-names-sm
  {:string    (:type-string-sm icons)
   :integer   (:type-integer-sm icons)
   :number    (:type-number-sm icons)
   :timestamp (:type-timestamp-sm icons)
   :ltree     (:type-ltree-sm icons)
   :unknown   (:type-unknown-sm icons)})

(def id-column :id)

(defn format-value [value type size]
  (let [format-fn (or (get-in type-formats [type size])
                      (get-in type-formats [:default size]))]
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
