(ns lapidary.server.pg-notify
  (:require
   [com.stuartsierra.component :as component]
   [cljs.core.async
    :refer [<! chan put! close! onto-chan to-chan]]
   [lapidary.utils :as utils]
   [lapidary.server.pg :as pg]
   [lapidary.server.db-utils :as db-utils
    :refer [result->rows result->row query->row query->rows add-times]]
   [lapidary.server.api :as api]
   [cognitect.transit :as t]
   [clojure.walk :as walk]
   [taoensso.timbre :as timbre
    :refer-macros [tracef debugf infof warnf errorf]])
  (:require-macros
   [cljs.core.async.macros :as m :refer [go]]))

(def reader (t/reader :json))
(defn read-json [msg]
  (-> (t/read reader msg)
      (walk/keywordize-keys)))

(defn game-update-event [game]
  [:five/game #_(api/select-game-keys game)])

(defn send-game-update [{:keys [uid1 uid2] :as event} send!]
  (let [event (game-update-event event)]
    (doseq [uid #{uid1 uid2}]
      (when (some? uid)
        (send! uid event)))))

(defn user-update-event [user]
  [:five/user #_(api/select-user-keys user)])

(defn send-user-update [{:keys [uid] :as event} send!]
  (when (some? uid)
    (send! uid (user-update-event event))))

(defn register [state database send!]
  (let [event-fn (fn [event]
                   (let [id      (-> event .-channel keyword)
                         payload (-> event .-payload read-json)
                         table   (-> payload :table keyword)
                         data    (:data payload)]
                     (when (= :events id)
                       (condp = table
                         :games (send-game-update data send!)
                         :users (send-user-update data send!)
                         (debugf "Event for unknown table: %s" table)))))
        error-fn (fn [err]
                   (let [{:keys [tries]} (swap! state update :tries inc)]
                     (errorf "Problem with database: %s (try %s)" err (:tries @state))
                     (utils/backoff (:tries @state) #(register state database send!))))]
    (go
      (when (:active? @state)
        (debugf "Adding listener")
        (let [[client done err] (-> database :db pg/connect! <!)]
          (if err
            (error-fn err)
            (let [done (comp (utils/on-event client :notification event-fn)
                             (utils/on-event client :error error-fn)
                             done)]
              (swap! state assoc :tries 0 :client client :done done)
              (<! (pg/execute! client ["LISTEN events"])))))))))
