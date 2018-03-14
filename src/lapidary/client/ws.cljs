(ns lapidary.client.ws
  (:require
   [lapidary.client.state :as state]
   [lapidary.utils :as utils]
   [lapidary.client.api :as api]
   [cognitect.transit :as t]
   [cljs.core.async :refer [<! chan put! close! promise-chan] :as async]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]))

(def transit-reader (t/reader :json))
(defn transit-parse [msg]
  (t/read transit-reader msg))

(defn close-ok? [code]
  (= 1000 code))

(defn fib [pos]
  (->> [0 1]
       (iterate (fn [[a b]] [b (+ a b)]))
       (map first)
       (take pos)
       (last)))

(defn attempt-sleep-time [attempt]
  (fib (+ attempt 2)))

(defn ws-db-open [db]
  (-> db
      (update-in [:stats :ws :open] inc)
      (assoc-in [:ws :attempt] 0)
      (assoc-in [:ws :state] :open)))

(defn ws-db-close [db ok?]
  (let [state (if ok? :close :error)]
    (-> db
        (update-in [:stats :ws state] inc)
        (update-in [:ws :attempt] (if ok? identity inc))
        (assoc-in [:ws :socket] nil)
        (assoc-in [:ws :stop-fn] nil)
        (assoc-in [:ws :state] state))))

(defn make-on-open [db name running? log-input]
  (defn on-open [e]
    (debugf "WS channel %s: Connected" name)
    (swap! db ws-db-open)
    ;; The socket will only have new logs since connect, get any missing
    ;; logs now
    (let [highest (:highest @db)
          logs    (api/query nil false)]
      (debugf "WS channel %s: Requesting logs after %s" name highest)
      (go
        (when-let [logs (<! logs)]
          (debugf "WS channel %s: Query returned %s logs" name (count logs))
          (async/onto-chan log-input logs false))))))

(defn make-on-message [name log-input]
  (fn on-message [e]
    (tracef "WS channel %s: Message: %s" name (.-data e))
    (when-let [log (transit-parse (.-data e))]
      (put! log-input log))))

(defn make-on-close [db name running? reconnect-fn]
  (fn on-close [e]
    (if (close-ok? (.-code e))
      (debugf "WS channel %s: Closed" name)
      (warnf "WS channel %s: Closed with error code: %s" name (.-code e)))

    (let [db         (swap! db ws-db-close (close-ok? (.-code e)))
          attempt    (get-in db [:ws :attempt])
          sleep-time (attempt-sleep-time attempt)]
      (when @running?
        (infof "WS channel %s: Reconnect attempt %s, waiting %s seconds" name attempt sleep-time)
        (go
          (<! (async/timeout (* 1000 sleep-time)))
          (when @running?
            (reconnect-fn)))))))

(defn ws-close [db name ws]
  (debugf "WS channel %s: Disconnecting" name)
  (swap! db ws-db-close false)
  (.close ws))

(defn ws-connect! [db url log-input name running?]
  (infof "WS channel %s: Connecting" name)
  (let [ws           (new js/WebSocket url)
        reconnect-fn #(ws-connect! db url log-input name running?)
        stop-fn      (comp #(debugf "WS channel %s: Removed WS event handlers" name)
                           #(ws-close db name ws)
                           (utils/on-event ws "close" (make-on-close db name running? reconnect-fn))
                           (utils/on-event ws "message" (make-on-message name log-input))
                           (utils/on-event ws "open" (make-on-open db name running? log-input)))]
    (swap! db update :ws assoc :socket ws :stop-fn stop-fn :state :connect)))

(defn connected? [{:keys [state]}]
  (= :open state))

(defn ws-start [db log-input name]
  (debugf "WS channel %s: Starting" name)
  (let [url      (str api/base-ws "/" api/auth-token)
        running? (atom true)]
    (ws-connect! db url log-input name running?)

    {:stop-fn (fn []
                (debugf "WS channel %s: Stopping" name)
                (reset! running? false)
                (when-let [stop-fn (get-in @db [:ws :stop-fn])]
                  (stop-fn)))}))
