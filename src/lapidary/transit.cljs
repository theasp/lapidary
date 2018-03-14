(ns lapidary.transit
  (:require
   [cognitect.transit :as transit]
   [goog.crypt.base64 :as base64]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)]))

(def transit-writer (transit/writer :json))
(def transit-reader (transit/reader :json))

(defn clj->transit [clj]
  (transit/write transit-writer clj))

(defn transit->clj [t]
  (transit/read transit-reader t))

(defn base64w->str [b64]
  (base64/decodeString b64 true))

(defn str->base64w [s]
  (base64/encodeString s true))

(defn clj->str [clj]
  (-> clj clj->transit str->base64w))

(defn str->clj [s]
  (-> s base64w->str transit->clj))
