(ns lapidary.client.events.field
  (:require
   [ajax.core :as ajax]
   [clojure.walk :as walk]
   [lapidary.client.query :as query]
   [lapidary.utils :as utils]
   [lapidary.client.db :as db]
   [lapidary.client.router :as router]
   [lapidary.client.sugar :as sugar]
   [re-frame.core :as rf]
   [lapidary.client.api :as api]
   [cljs.core.async :refer [<! chan put! close! promise-chan] :as async]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]))

(defn field-load [{:keys [db]} [_ table field]]
  (when (and (not (get-in db [:query table :field-values field :loading?]))
             (db/login-ok? db))
    (let [db (-> db
                 (assoc-in [:query table :field-values field :loading?] true)
                 (update-in [:query table :field-values field :id] inc))

          {:keys [query-str start-str end-str]} (get-in db [:query table])

          field-values (get-in db [:query table :field-values field])
          all-values?  (get field-values :all-values? false)
          field-id     (get field-values :id)
          page-size    (get field-values :page-size 20)
          page         (get field-values :page 0)
          filters      (when-not all-values?
                         (query/filters->where (get-in db [:query table :filters])))
          offset       (* page page-size)
          start-time   (sugar/parse-time start-str)
          end-time     (sugar/parse-time end-str)
          where        (->> (when-not all-values? (query/query->where query-str))
                            (query/query-filters nil start-time end-time filters))]
      #_(debugf "WHERE: %s" where)
      (api/query-field-values table field offset page-size where (get-in db [:login :jwt])
                              #(rf/dispatch [:field-load-ok table field field-id %])
                              #(rf/dispatch [:field-load-error table field field-id %]))
      {:db db})))

(def field-keys [:page :page-size :all-values?])

(defn field-query-equal? [a b]
  (= (select-keys a field-keys)
     (select-keys b field-keys)))

(defn field-init [{:keys [db]} [_ table field]]
  (let [cur-query (-> (get-in db [:query table :field-values field])
                      (select-keys field-keys))
        new-query (merge cur-query (-> (get-in db [:view :query :show-field])
                                       (select-keys field-keys)))]
    (when (not= cur-query new-query)
      (let [new-query (merge new-query
                             {:values   nil
                              :loading? false
                              :highest  -1})]
        {:db       (assoc-in db [:query table :field-values field] new-query)
         :dispatch [:field-load table field]}))))

(defn field-load-ok [db [_ table field id result]]
  #_(debugf "field-load-ok: %s" (-> result :result :rows first ))
  (if (= id (get-in db [:query table :field-values field :id]))
    (let [result (-> result :result :rows first walk/keywordize-keys)]
      (update-in db [:query table :field-values field] merge
                 {:count    (:count result)
                  :values   (:body result)
                  :time     (js/Date.now)
                  :error    nil
                  :loading? false}))
    db))

(defn field-load-error [{:keys [db]} [_ table field id error]]
  (errorf "field-load-error: %s %s %s" table field error)
  (when (= id (get-in db [:query table :field-values field :id]))
    {:dispatch (if (= 403 (:status error))
                 [:jwt-expired]
                 [:api-error :field-load error])
     :db       db}))

(defn field-refresh [{:keys [db]} [_ table field]]
  (let [{:keys [time loading?]} (get-in db [:query table :field-values field])]
    (when (and (not loading?)
               (utils/expired? time (js/Date.now) (* 120 1000)))
      {:dispatch [:field-load table field]})))

(defn field-page [db [_ table field page]]
  (router/query-navigate! table
                          (-> (get-in db [:view :query])
                              (assoc-in [:show-field :name] field)
                              (assoc-in [:show-field :page] page)))
  db)

(defn field-all-values [db [_ table field all-values]]
  (router/query-navigate! table
                          (-> (get-in db [:view :query])
                              (assoc-in [:show-field :name] field)
                              (assoc-in [:show-field :all-values?] all-values)))
  db)

(rf/reg-event-fx :field-load field-load)
(rf/reg-event-fx :field-init field-init)
(rf/reg-event-fx :field-refresh field-refresh)
(rf/reg-event-db :field-load-ok field-load-ok)
(rf/reg-event-fx :field-load-error field-load-error)
(rf/reg-event-db :field-page field-page)
(rf/reg-event-db :field-all-values field-all-values)
