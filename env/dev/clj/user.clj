(ns ^:no-doc user
  (:require [clojure.tools.namespace.repl :as tn]
            [mount.core :as mount]))

(defn refresh []
  (tn/refresh))

(defn restart []
  (mount/stop)
  (tn/refresh)
  (mount/start))
