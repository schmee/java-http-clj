(defproject java-http-clj "0.4.2"
  :description "A lightweight Clojure wrapper for java.net.http"
  :url "http://www.github.com/schmee/java-http-clj"
  :license {:name "MIT"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.10.1" :scope "provided"]]
  :profiles {:dev {:dependencies [[com.cemerick/url "0.1.1"]
                                  [compojure "1.6.1"]
                                  [info.sunng/ring-jetty9-adapter "0.12.2"]
                                  [mount "0.1.14"]
                                  [org.clojure/tools.namespace "0.2.11"]
                                  [pjstadig/humane-test-output "0.8.3"]
                                  [ring "1.7.1"]
                                  [ring/ring-defaults "0.3.2"]]
                   :source-paths ["src" "env/dev/clj" "test"]
                   :plugins [[lein-codox "0.10.5"]]
                   :injections [(require 'pjstadig.humane-test-output)
                                (pjstadig.humane-test-output/activate!)]
                   :codox {:metadata {:doc/format :markdown}
                           :output-path "codox"
                           :source-paths ["src"]}}
             :kaocha {:dependencies [[lambdaisland/kaocha "0.0-529"]]}}
  :repl-options {:init-ns java-http-clj.core}
  :aliases {"kaocha" ["with-profile" "+kaocha" "run" "-m" "kaocha.runner"]})
