(ns lapidary.routes
  (:require
   [bide.core :as bide]
   [taoensso.timbre :as timbre
    :refer-macros [tracef debugf infof warnf errorf]]))

(def web-router
  (bide/router [["/api/query" :api-query]
                ["/api/login" :api-login]
                ["/" :app]
                ["/index.html" :app]
                ["/css/" :css]
                ["/css/*" :css]
                ["/js/" :js]
                ["/js/*" :js]
                ["/app" :app]]))
