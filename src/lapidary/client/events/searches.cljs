(ns lapidary.client.events.searches
  (:require
   [clojure.walk :as walk]
   [ajax.core :as ajax]
   [clojure.string :as str]
   [lapidary.utils :as utils]
   [lapidary.client.state :as state]
   [lapidary.client.db :as db]
   [lapidary.client.query :as query]
   [lapidary.client.router :as router]
   [re-frame.core :as rf]
   [lapidary.client.api :as api]
   [cljs.core.async :refer [<! chan put! close! promise-chan] :as async]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]))

(defn keywordize-vec [c]
  (vec (map keyword c)))

(defn keywordize-all [c]
  (vec (map keywordize-vec c)))

(defn searches-refresh [{:keys [db]} [_ table]]
  (let [{:keys [time loading?]} (get-in db [:query table :searches])]
    (when (and (not loading?)
               (utils/expired? time (js/Date.now) (* 120 1000)))
      {:dispatch [:searches-load table]})))

(defn searches-load [{:keys [db]} [_ table]]
  (when (and (not (get-in db [:query table :searches :loading?]))
             (db/login-ok? db))
    (let [db (-> db
                 (update-in [:query table :searches :id] inc)
                 (assoc-in [:query table :searches :loading?] true))
          id (get-in db [:query table :searches :id])]
      (api/get-searches! table
                         (get-in db [:login :jwt])
                         #(rf/dispatch [:searches-load-ok id table %])
                         #(rf/dispatch [:searches-load-error id table %]))
      {:db db})))

(defn json->filter [coll [field values]]
  (assoc coll
         (into [] (map keyword field))
         values))

(defn filter->json [coll [field values]]
  [field values])

(defn json->filters [filters]
  (-> filters
      (update :exclude #(reduce json->filter nil %))
      (update :require #(reduce json->filter nil %))))

(defn filters->json [filters]
  (-> filters
      (update :exclude #(->> %
                             (remove (comp empty? second))
                             (map identity)))
      (update :require #(->> %
                             (remove (comp empty? second))
                             (map identity)))))

(defn add-search [searches search]
  (assoc searches (:search_name search)
         (-> (:options search)
             (update :columns keywordize-all)
             (update :filters json->filters))))

(defn result->searches [result]
  (reduce add-search {} (-> (get-in result [:result :rows])
                            (first)
                            (walk/keywordize-keys)
                            (:searches))))

(defn searches-load-ok [db [_ id table result]]
  #_(debugf "searches-load-ok: %s" result)
  (if (= id (get-in db [:query table :searches :id]))
    (update-in db [:query table :searches] merge
               {:saved    (result->searches result)
                :time     (js/Date.now)
                :loading? false
                :error    nil})
    db))

(defn searches-load-error [{:keys [db]} [_ id table error]]
  #_(debugf "searches-load-error: %s" error)
  (when (= id (get-in db [:query table :searches :id]))
    {:dispatch (when (= 403 (:status error)) [:jwt-expired])
     :db       (update-in db [:query table :searches] merge
                          {:time     (js/Date.now)
                           :loading? false
                           :error    error})}))

(defn search->json [search]
  (-> search
      (update :filters filters->json)))

(defn searches-save [{:keys [db]} [_ table name]]
  (when (and (some? table)
             (not (str/blank? table))
             (some? name)
             (not (str/blank? name))
             (db/login-ok? db))
    (let [query   (get-in db [:query table])
          options (-> (select-keys query [:query-str :start-str :end-str :columns :filters])
                      (search->json))]
      (api/save-search! table name options (get-in db [:login :jwt])
                        #(rf/dispatch [:searches-save-ok table name options])
                        #(rf/dispatch [:searches-save-error table name %]))
      {})))

(defn searches-save-ok [db [_ table name options result]]
  (assoc-in db [:query table :searches :saved name] options))

(defn searches-save-error [{:keys [db]} [_ table name error]]
  (errorf "searches-save: %s %s %s" table name error)
  {:dispatch (if (= 403 (:status error))
               [:jwt-expired]
               [:api-error :searches-save error])
   :db       (update-in db [:query table :searches :saved] dissoc name)})

(rf/reg-event-fx :searches-refresh searches-refresh)
(rf/reg-event-fx :searches-load searches-load)
(rf/reg-event-db :searches-load-ok searches-load-ok)
(rf/reg-event-fx :searches-load-error searches-load-error)

(rf/reg-event-fx :searches-save searches-save)
(rf/reg-event-db :searches-save-ok searches-save-ok)
(rf/reg-event-fx :searches-save-error searches-save-error)
