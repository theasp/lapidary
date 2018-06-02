(ns lapidary.client.ui.dialog
  (:require
   [lapidary.utils :as utils]
   ["dialog-polyfill"]
   [clojure.string :as str]
   [reagent.core :as reagent :refer [atom]]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)]))

(defn trigger
  "Returns a reagent class that can be used to easily add triggers
  from the map in `props`, such as :component-did-mount.  See
  `reagent.core/create-class` for more information."
  [props content]
  (-> {:display-name "trigger"}
      (merge props)
      (assoc :reagent-render (fn [_ content] content))
      (reagent/create-class )))

(defn show-modal [cancel-fn node]
  (let [dialog (reagent/dom-node node)]
    (when-not (.-showModal dialog)
      (.registerDialog js/dialogPolyfill dialog))
    (.addEventListener dialog "cancel" cancel-fn)
    (.showModal dialog)))

(defn dialog [cancel-fn dialog]
  [trigger {:component-did-mount (fn [node] (show-modal cancel-fn node))} dialog])
