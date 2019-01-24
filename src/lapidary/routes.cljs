(ns lapidary.routes
  (:require
   [bide.core :as bide]
   [taoensso.timbre :as timbre
    :refer-macros [tracef debugf infof warnf errorf]]))

(def routes [["/api/query" :api/query]
             ["/api/login" :api/login]
             ["/" :page/app]
             ["/index.html" :page/app]
             ["/css/" :css]
             ["/css/*" :css]
             ["/js/" :js]
             ["/js/*" :js]
             ["/app" :page/app]])

(def router (bide/router routes))

(def path-for (partial bide/resolve router))
