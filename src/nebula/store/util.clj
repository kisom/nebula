(ns nebula.store.util
  (:require [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

(defn log-info
  "Log the value as an informational message."
  [prologue body]
  (info (str prologue body))
  body)
