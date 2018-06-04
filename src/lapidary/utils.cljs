(ns lapidary.utils
  (:require
   [clojure.string :as str]
   [cljs.core.async
    :refer [<! chan put! close! onto-chan to-chan]]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)])
  (:require-macros
   [cljs.core.async.macros :as m :refer [go]])
  (:import [goog.async Debouncer]))

(defn clj->json [data]
  (js/JSON.stringify data))

(defn json->clj [data]
  (js/JSON.parse data))

(def Null nil)

(defn debounce [f interval]
  (let [dbnc (Debouncer. f interval)]
    ;; We use apply here to support functions of various arities
    (fn [& args] (.apply (.-fire dbnc) dbnc (to-array args)))))

(defn- obj->map
  "Workaround for `TypeError: Cannot convert object to primitive value`s
  caused by `(js->clj (.-body  exp-req) :keywordize-keys true)` apparently
  failing to correctly identify `(.-body exp-req)` as an object. Not sure
  what's causing this problem."
  [o]
  (when (some? o)
    (into {}
          (for [k (.keys js/Object o)]
            [(keyword (.toLowerCase k)) (aget o k)]))))

(defn drop-keys [coll keys]
  (apply dissoc coll keys))

(defn exception? [e]
  (instance? js/Error e))

(defn now []
  (new js/Date))

(def on-event
  (if (= "nodejs" cljs.core/*target*)
    (fn on-event [target event event-fn & [options]]
      (.addListener target (name event) event-fn)
      #(.removeListener target (name event) event-fn))

    (fn on-event [target event event-fn & [options]]
      (.addEventListener target (name event) event-fn (or options false))
      #(.removeEventListener target (name event) event-fn (or options false)))))

(defn backoff [try f]
  (debugf "Backoff: %s" (some? f))
  (js/setTimeout f (* 1000 (js/Math.pow 2 try))))


(defn format
  [fmt & args]
  (apply goog.string/format fmt args))

(defn rcompare [a b]
  (compare b a))

(defn append [c v]
  (conj (vec c) v))

(defn remove= [c v]
  (remove #(= % v) c))

(defn insert [v pos item]
  (let [v (vec v)]
    (apply merge (subvec v 0 pos) item (subvec v pos))))

(defn contains-val? [coll val]
  (reduce #(if (= val %2) (reduced true) %1) false coll))

(defn shorten [s l]
  (if (< l (.-length s))
    (str (subs s 0 (- l 3)) "...")
    s))

(defn default [a b]
  (if (some? a) a b))

(defn highest-key [c k]
  (apply max (map #(get % k) c)))

(defn bool-str-not [s]
  (if (= s "true") "false" "true"))

(defn str->bool [s]
  (or (= s "true")
      (= s "t")
      (= s "yes")
      (= s "on")))

(def bool-str-true? str->bool)

(defn str->int [s]
  (if (and (some? s) (not (str/blank? s)))
    (js/parseInt s)
    0))

(def parse-int str->int)

(defn byte-size-str [size]
  (loop [size   (if (number? size) size (str->int (str size)))
         labels ["B" "KiB" "MiB" "GiB" "TiB" "PiB" "EiB" "ZiB" "YiB"]]
    (if (> size 1024)
      (recur (/ size 1024) (rest labels))
      (if-let [label (first labels)]
        (goog.string.format "%0.1f %s" size label)
        (goog.string.format "%d" size)))))

(defn si-size-str [size]
  (loop [size   (if (number? size) size (str->int (str size)))
         labels [nil "K" "M" "G" "T" "P" "E" "Z" "Y"]]
    (if (> size 1000)
      (recur (/ size 1000) (rest labels))
      (if-let [label (first labels)]
        (goog.string.format "%0.1f %s" size label)
        (goog.string.format "%d" size)))))

;; https://gist.github.com/danielpcox/c70a8aa2c36766200a95
(defn deep-merge [v & vs]
  (if (some identity vs)
    (-> (fn [v1 v2]
          (if (and (map? v1) (map? v2))
            (merge-with deep-merge v1 v2)
            v2))
        (reduce v vs))
    v))

(defn subtract-map [a b]
  (-> (fn [a [key value]]
        (if (= value (get a key))
          (dissoc a key)
          a))
      (reduce a b)))

(defn expired?
  ([before age]
   (expired? before age (js/Date.now)))
  ([before age now]
   (< age (- now before))))

(defn stop-propogation [f]
  (fn [e]
    (.stopPropagation e)
    (f)))

;; https://stackoverflow.com/questions/21768802/how-can-i-get-the-nested-keys-of-a-map-in-clojure
(defn kvpaths-all
  ([m] (kvpaths-all [] m))
  ([prev m]
   (reduce-kv (fn [res k v]
                (if (associative? v)
                  (let [kp (conj prev k)]
                    (conj (into res (kvpaths-all kp v)) kp))
                  (conj res (conj prev k))))
              []
              m)))

(defn update-map [m f & args]
  (-> (fn [m k v]
        (assoc m k (apply f v args)))
      (reduce-kv {} m)))

(defn update-map-keys [m f & args]
  (-> (fn [m k v]
        (assoc m (apply f k args) v))
      (reduce-kv {} m)))
