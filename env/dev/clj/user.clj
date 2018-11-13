(ns ^:no-doc user
  (:require [clojure.spec.test.alpha :as st]
            [clojure.tools.namespace.repl :as tn]
            [java-http-clj.websocket-test]
            [mount.core :as mount]))

(defn refresh []
  (let [r (tn/refresh)]
    (st/instrument)
    r))

(defn restart []
  (mount/stop)
  (refresh)
  (mount/start-without #'java-http-clj.websocket-test/ws))
