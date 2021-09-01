(defproject java-http-clj "0.4.3"
  :description "A lightweight Clojure wrapper for java.net.http"
  :url "http://www.github.com/schmee/java-http-clj"
  :license {:name "MIT"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.10.3" :scope "provided"]]
  :profiles {:dev {:dependencies [[com.cemerick/url "0.1.1"]
                                  [compojure "1.6.2"]
                                  [info.sunng/ring-jetty9-adapter "0.14.2"]
                                  [mount "0.1.16"]
                                  [org.clojure/tools.namespace "1.1.0"]
                                  [pjstadig/humane-test-output "0.11.0"]
                                  [ring "1.9.4"]
                                  [ring/ring-defaults "0.3.3"]]
                   :source-paths ["src" "env/dev/clj" "test"]
                   :injections [(require 'pjstadig.humane-test-output)
                                (pjstadig.humane-test-output/activate!)]}
             :kaocha {:dependencies [[lambdaisland/kaocha "1.0.861"]]}}
  :repl-options {:init-ns java-http-clj.core}
  :aliases {"kaocha" ["with-profile" "+kaocha" "run" "-m" "kaocha.runner"]})
