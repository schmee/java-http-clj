(defproject java-http-clj "0.4.0-SNAPSHOT"
  :description "A lightweight Clojure wrapper for java.net.http"
  :url "http://www.github.com/schmee/java-http-clj"
  :license {:name "MIT"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.10.0-beta4"]]
  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]]
                   :source-paths ["env/dev/clj" "test"]
                   :plugins [[lein-codox "0.10.5"]]
                   :codox {:metadata {:doc/format :markdown}
                           :output-path "codox"}}}
  :repl-options {:init-ns 'java-http-clj.core})
