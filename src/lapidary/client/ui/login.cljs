(ns lapidary.client.ui.login
  (:require
   [lapidary.utils :as utils]
   [lapidary.client.ui.navbar :as navbar]
   [lapidary.client.state :as state]
   [lapidary.client.db :as db]
   [clojure.string :as str]
   [reagent.core :as reagent :refer [atom]]
   [re-frame.core :as rf]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]))

(defn login []
  (let [username (atom "")
        password (atom "")
        submit   (fn [e]
                   (rf/dispatch [:login @username @password])
                   (.preventDefault e))
        login    (rf/subscribe [:login])]
    (fn []
      (let [{:keys [loading? error]} @login]
        [:div
         [navbar/navbar state/app-state nil]
         [:section.section
          [:div.columns.is-centered
           [:div.column.is-narrow
            [:form {:on-submit (if loading? #(.preventDefault %) submit)}
             [:div {:class (str "hero is-rounded " (if error "is-danger" "is-light"))}
              [:div.hero-body
               [:div.field
                #_[:label.label "Username"]
                [:div.control.has-icons-left
                 [:input.input {:placeholder   "Username"
                                :auto-complete :username
                                :auto-focus    true
                                :value         @username
                                :on-change     #(reset! username (-> % .-target .-value))}]
                 [:span.icon.is-small.is-left
                  [:i.fas.fa-user]]]]
               [:div.field
                #_[:label.label "Password"]
                [:div.control.has-icons-left
                 [:input.input {:type          :password
                                :placeholder   "Password"
                                :auto-complete :current-password
                                :value         @password
                                :on-change     #(reset! password (-> % .-target .-value))}]
                 [:span.icon.is-small.is-left
                  [:i.fas.fa-lock]]]]

               [:div.field
                [:div.control
                 (when error
                   [:p (or (get-in error [:response :error]) (:status error) "Unknown error.")])]]

               [:div.field.is-grouped.is-grouped-right
                [:div.control
                 [:button {:class    (str "button is-primary" (when loading? " is-loading"))
                           :disabled loading?}
                  [:span "Login"]]]]]]]]]]]))))
