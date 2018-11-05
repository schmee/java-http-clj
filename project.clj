(defproject java-http-clj "0.1.0-SNAPSHOT"
  :description "A lightweight Clojure wrapper for java.net.http"
  :url "http://www.github.com/schmee/java-http-clj"
  :license {:name "MIT"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.10.0-beta4"]]
  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]]
                   :source-paths ["src" "env/dev/clj" "test"]}}
  :repl-options {:init-ns 'java-http-clj.core})
