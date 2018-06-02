(ns lapidary.client.ui.pagination
  (:require
   [clojure.string :as str]
   [reagent.core :as reagent :refer [atom]]
   [taoensso.timbre :as timbre
    :refer-macros (tracef debugf infof warnf errorf)]))

(defn pagination [cur-page set-page & [pages]]
  (let [cur-page   (or cur-page 0)
        last-page? (= cur-page pages)
        prev-page  (when (> cur-page 0)
                     #(set-page (dec cur-page)))
        next-page  (when (< cur-page pages)
                     #(set-page (inc cur-page)))]
    [:nav.pagination.is-small
     [:a.pagination-previous
      {:disabled (nil? prev-page)
       :on-click prev-page}
      "Previous"]
     [:a.pagination-next
      {:disabled (nil? next-page)
       :on-click next-page}
      "Next"]
     [:ul.pagination-list
      (for [page (range (inc (or pages (+ 10 cur-page))))]
        (let [distance (- page cur-page)]
          (if (or (= page 0)
                  (= page cur-page)
                  (< -3 distance 3)
                  (and (-> page (inc) (mod 10) (= 0))
                       (>= distance -10)
                       (<= distance 10))
                  (= page pages))
            ^{:key page}
            [:li [:a.pagination-link
                  {:on-click (when-not (= page cur-page)
                               #(set-page page))
                   :class    (str "pagination-link"
                                  (when (= page cur-page)
                                    " is-current"))}
                  (inc page)]]
            (when (or (= 3 distance)
                      (= -3 distance)
                      (= 11 distance)
                      (= -11 distance))
              ^{:key page}
              [:li [:span.pagination-ellipsis "..."]]))))]]))
