(ns lapidary.client.events.searches
  (:require
   [clojure.spec.alpha :as s]
   [clojure.walk :as walk]
   [ajax.core :as ajax]
   [clojure.string :as str]
   [lapidary.utils :as utils]
   [lapidary.client.state :as state]
   [lapidary.client.db :as db]
   [lapidary.client.router :as router]
   [re-frame.core :as rf]
   [lapidary.client.api :as api]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)]))

(defn keywordize-vec [c]
  (vec (map keyword c)))

(defn keywordize-all [c]
  (vec (map keywordize-vec c)))

(defn searches-refresh [{:keys [db]} [_ table]]
  (let [{:keys [time loading?]} (get-in db [:query table :searches])]
    (when (and (not loading?)
               (utils/expired? time (* 120 1000)))
      {:dispatch [:searches-load table]})))

(defn searches-load [{:keys [db]} [_ table]]
  (debugf "searches-load: %s" table)
  (when (and (not (get-in db [:query table :searches :loading?]))
             (db/login-ok? db))
    (let [db (-> db
                 (update-in [:connections :http :searches-load] inc)
                 (update-in [:query table :searches :id] inc)
                 (assoc-in [:query table :searches :loading?] true))
          id (get-in db [:query table :searches :id])]
      {:db         db
       :http-xhrio (-> (api/get-searches table)
                       (merge {:on-success [:searches-load-ok id table]
                               :on-failure [:searches-load-error id table]}))})))

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

(defn build-column-options [options columns]
  (reduce (fn [m k] (update-in m [k :type] keyword))
          (zipmap columns options)
          columns))

(defn json->column-options [search]
  (update search :column-options build-column-options (:columns search)))

(defn add-search [searches search]
  (let [name   (:search_name search)
        search (-> (:options search)
                   (update :columns keywordize-all)
                   (update :columns vec)
                   (update :sort-column #(-> (map keyword %)
                                             (vec)))
                   (json->column-options)
                   (update :filters json->filters)
                   (db/conform-search))]
    (if (not= ::s/invalid search)
      (assoc searches name search)
      (do
        (warnf "add-search: %s" (s/explain-str :table/search search))
        searches))))

(defn result->searches [result]
  (reduce add-search {} (-> (get-in result [:result :rows])
                            (first)
                            (walk/keywordize-keys)
                            (:searches))))

(defn searches-load-ok [{:keys [db]} [_ id table result]]
  (debugf "searches-load-ok: %s" result)
  (when (= id (get-in db [:query table :searches :id]))
    {:db (-> db
             (update-in [:connections :http :searches-load] dec)
             (update-in [:query table :searches] merge
                        {:saved    (result->searches result)
                         :time     (js/Date.now)
                         :loading? false
                         :error    nil}))}))

(defn searches-load-error [{:keys [db]} [_ id table error]]
  (errorf "searches-load-error: %s" error)
  (when (= id (get-in db [:query table :searches :id]))
    {:dispatch [:http-error :searches-load error]
     :db       (-> db
                   (update-in [:connections :http :searches-load] dec)
                   (update-in [:query table :searches] merge
                              {:time     (js/Date.now)
                               :loading? false
                               :error    error}))}))

(defn search->json [search]
  (-> (select-keys search db/save-search-keys)
      (update :filters filters->json)
      (assoc :column-options (-> (:column-options search)
                                 (map (:columns search))))))

(defn searches-save [{:keys [db]} [_ table name]]
  (debugf "searches-save: %s %s" table name)
  (when (and (some? table)
             (not (str/blank? table))
             (some? name)
             (not (str/blank? name))
             (db/login-ok? db))
    (let [query   (get-in db [:query table])
          options (search->json query)]
      {:http-xhrio (merge (api/save-search table name options)
                          {:on-success [:searches-save-ok table name options]
                           :on-failure [:searches-save-error table name]})
       :db         (update-in db [:connections :http :searches-save] inc)})))

(defn searches-save-ok [{:keys [db]} [_ table name options result]]
  (debugf "searches-save-ok: %s %s" table name)
  {:db       (-> db
                 (update-in [:connections :http :searches-save] dec)
                 (assoc-in [:query table :searches :saved name] options))
   :dispatch [:table-load table]})

(defn searches-save-error [{:keys [db]} [_ table name error]]
  (errorf "searches-save-error: %s %s %s" table name error)
  {:db       (-> db
                 (update-in [:connections :http :searches-save] dec)
                 (update-in [:query table :searches :saved] dissoc name))
   :dispatch [:http-error :searches-save error]})

(rf/reg-event-fx :searches-refresh searches-refresh)
(rf/reg-event-fx :searches-load searches-load)
(rf/reg-event-fx :searches-load-ok searches-load-ok)
(rf/reg-event-fx :searches-load-error searches-load-error)

(rf/reg-event-fx :searches-save searches-save)
(rf/reg-event-fx :searches-save-ok searches-save-ok)
(rf/reg-event-fx :searches-save-error searches-save-error)
