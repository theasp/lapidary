(ns lapidary.server.response
  (:require
   [macchiato.util.response :as r]
   [taoensso.timbre :as timbre
    :refer-macros [tracef debugf infof warnf errorf]]))

(def req-warn-keys [:handler :request-method :uri :identity])

(defn not-found [req & [body]]
  (warnf "Not found: %s %s" (select-keys req req-warn-keys) body)
  (tracef "Cause")
  (r/not-found {:error body}))

(defn bad-request [req & [body]]
  (warnf "Bad request: %s %s" (select-keys req req-warn-keys) body)
  (tracef "Cause")
  (r/bad-request {:error body}))

(defn unauthorized [req & [body]]
  (warnf "Unauthorized: %s %s" (select-keys req req-warn-keys) body)
  (tracef "Cause")
  (r/unauthorized {:error body}))

(defn forbidden [req & [body]]
  (warnf "Forbidden: %s %s" (select-keys req req-warn-keys) body)
  (tracef "Cause")
  (r/forbidden {:error body}))

(defn internal-server-error [req & [body]]
  (warnf "Internal server error: %s %s" (select-keys req req-warn-keys) body)
  (tracef "Cause")
  (r/internal-server-error {:error body}))
