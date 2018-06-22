(ns lapidary.client.ui.confirm-dialog
  (:require
   [lapidary.client.ui.dialog :as dialog]
   [lapidary.utils :as utils]
   [clojure.string :as str]
   [reagent.core :as reagent :refer [atom]]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)]))

(defn dialog-header [on-cancel]
  [:header.modal-card-head
   [:p.modal-card-title "Confirm delete?"]
   [:button.delete {:on-click on-cancel}]])

(defn dialog-body [confirm-atom confirm-str]
  (let [ok? (= confirm-str @confirm-atom)]
    [:section.modal-card-body
     [:p "Enter \"" confirm-str "\" below to confirm"]
     [:div.field
      [:div.control
       [:input
        {:auto-focus  true
         :class       (str "input " (if ok? "is-success" "is-danger"))
         :value       @confirm-atom
         :placeholder confirm-str
         :on-change   #(reset! confirm-atom (-> % .-target .-value))}]]]]))

(defn dialog [{:keys [confirm-str on-cancel on-ok]}]
  (debugf "confirm-dialog: %s" confirm-str)
  (let [confirm-atom (atom "")]
    (fn [{:keys [confirm-str]}]
      (let [ok? (= confirm-str @confirm-atom)]
        (debugf "confirm-dialog: %s" ok?)
        [:form {:on-submit (fn [event]
                             (if ok?
                               (on-ok)
                               (debugf "Why cancel???"))
                             (.preventDefault event))}
         [dialog/dialog on-cancel
          [:dialog
           [:div.modal.is-active
            [:div.modal-background]
            [:div.modal-card
             [dialog-header on-cancel]
             [dialog-body confirm-atom confirm-str]
             [:footer.modal-card-foot
              {:style {:justify-content :flex-end}}
              [:button.button.is-pulled-right
               {:on-click on-cancel}
               "Cancel"]
              [:button
               {:class    (str "button is-pulled-right" (when ok? " is-primary"))
                :disabled (not ok?)
                :on-click on-ok}
               "Ok"]]]]]]]))))
