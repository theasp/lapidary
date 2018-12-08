(ns lapidary.server.web-pages
  (:require
   [hiccups.runtime :as hiccups]
   [macchiato.util.response :as r]
   [taoensso.timbre :as timbre
    :refer-macros [tracef debugf infof warnf errorf]])
  (:require-macros
   [hiccups.core :as hiccups :refer [html]]))

(def bulma-ver "0.7.2")
(def fa-ver "5.3.1")
(def mdi-ver "3.0.39")

(def bulma-url
  (str "//cdnjs.cloudflare.com/ajax/libs/bulma/"
       bulma-ver
       "/css/bulma.min.css"))

(def fa-url
  (str "//use.fontawesome.com/releases/v"
       fa-ver
       "/css/all.css"))

(def material-icons-url
  "//fonts.googleapis.com/icon?family=Material+Icons")

(def mdi-url
  (str
   "https://cdn.materialdesignicons.com/"
   mdi-ver
   "/css/materialdesignicons.min.css"))

(defn ->html5 [v]
  (str "<!DOCTYPE html>" (hiccups/html v)))

(defn include-css [url]
  [:link {:rel "stylesheet" :type "text/css" :href url}])

(defn include-js [url]
  [:script {:src url}])

(defn html-headers [{:keys [path-for config title subtitle] :as props}]
  [:head
   [:title (if subtitle (str title " - " subtitle) title)]
   [:meta {:charset "utf-8"}]
   [:meta {:name                         "viewport"
           :content                      "width=device-width, initial-scale=1.0"
           :mobile-web-app-capable       "yes"
           :apple-mobile-web-app-capable "yes"}]
   (include-css bulma-url)
   (include-css (str (path-for :css nil) "style.css"))
   #_(include-css fa-url)
   (include-css mdi-url)
   [:link {:rel "manifest" :href "manifest.json"}]
   [:link {:rel "icon" :type "image/svg" :href "/icon.png"}]
   [:link {:rel "icon" :type "image/png" :href "/icon-32.png"}]])


(defn page-template [{:keys [config] :as props} content]
  [:html.has-navbar-fixed-top
   (html-headers props)
   [:body
    (:google-tag config)
    [:div {:id "app"}
     [:div.content content]]
    (when (:js props)
      (:js props))]])

(defn app-page [{:keys [path-for js-path] :as props}]
  (if-let [js-path (str (path-for :js) "app.js")]
    (let [props (assoc props :js (include-js js-path))]
      (fn [req]
        (-> (page-template props nil)
            (->html5)
            (r/ok)
            (r/content-type "text/html"))))
    (errorf "Unable to determine route for :js!")))

(defn not-found [props]
  (fn [req]
    (-> (page-template props
                       [:main.mdl-layout__content
                        [:h1
                         (str "File Not Found: " (:uri req) "\n")]])
        (->html5)
        (r/not-found)
        (r/content-type "text/html"))))
