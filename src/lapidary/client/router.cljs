(ns lapidary.client.router
  (:require
   [lapidary.transit :as transit]
   [lapidary.utils :as utils]
   [lapidary.client.db :as db]
   [mount.core :as mount :refer [defstate]]
   [re-frame.core :as rf]
   [bide.core :as bide]
   [taoensso.timbre :as timbre
    :refer-macros [tracef debugf infof warnf errorf]]))

(def routes [["/" :lapidary/list-tables]
             ["/table/:table" :lapidary/query-table]])

(def router (bide/router routes))

(defn on-navigate
  "A function which will be called on each route change."
  [name params query]
  #_(debugf "Router changed to: %s" [name params query])

  (let [query (some-> query :t str transit/str->clj)
        view  {:name name :params params :query query}]
    #_(debugf "New view: %s" view)
    (rf/dispatch [:view-update view])))

(defn navigate!
  ([name params query]
   (debugf "Navigating to: %s" [name params query])
   (bide/navigate! router name params
                   (when-not (empty? query)
                     {:t (transit/clj->str query)})))
  ([{:keys [name params query] :as view}]
   (navigate! name params query)))

(defn query-navigate! [table query]
  (navigate! :lapidary/query-table
             {:table table}
             (utils/subtract-map query db/default-query)))

(defn start-router []
  (infof "Start")
  (bide/start! router {:default     :lapidary/list-tables
                       :on-navigate on-navigate
                       :html5?      false}))

(defstate router :start (start-router))
