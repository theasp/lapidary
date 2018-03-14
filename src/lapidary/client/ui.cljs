(ns lapidary.client.ui
  (:require
   [lapidary.client.events]
   [lapidary.client.subs]
   [lapidary.client.state :as state]
   [lapidary.client.ui.tables :as tables]
   [lapidary.client.ui.query :as query]
   [lapidary.client.ui.login :as login]
   [clojure.string :as str]
   [re-frame.core :as rf]
   [reagent.core :as reagent :refer [atom]]
   [cljs.core.async :refer [<! chan put! close! promise-chan] :as async]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]))

(defn app []
  (let [view      @(rf/subscribe [:view])
        login-ok? @(rf/subscribe [:login-ok?])]
    (if login-ok?
      (case (get view :name :lapidary/list-tables)
        :lapidary/list-tables [tables/list-tables view]
        :lapidary/query-table [query/view-query view]

        (do (warnf "Unknown view: %s" view)
            [tables/list-tables view]))
      [login/login])))

(defn start! [state]
  (rf/dispatch-sync [:initialize])
  (reagent/render-component [app] (js/document.getElementById "app")))
