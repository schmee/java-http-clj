(ns ^:skip-test java-http-clj.test-utils
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as st]))

;; Disable instrumentation for send-async due to all the jank
;; around specs with fspecs... for instance
;; https://dev.clojure.org/jira/browse/CLJ-1936 and
;; https://dev.clojure.org/jira/browse/CLJ-2217
(defn instrument []
  (st/instrument
    (disj (st/instrumentable-syms) 'java-http-clj.core/send-async)))
