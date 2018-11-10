(ns ^:skip-test java-http-clj.test-server
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [mount.core :as mount]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.defaults :refer :all]))

(defroutes app
  (GET "/" [] "ROOT")
  (GET "/echo" [message] (or message "no message"))
  (POST "/echo" r (slurp (io/reader (:body r))))
  (PUT "/echo" r (slurp (io/reader (:body r))))
  (DELETE "/echo" [] "deleted")
  (GET "/redir" [] {:status 302 :headers {"Location" "/target"}})
  (GET "/target" [] "did redirect"))

(mount/defstate server
  :start
  (let [{:keys [port] :or {port 8080}} (mount/args)]
    (println "Starting test server on port" port)
    (jetty/run-jetty (wrap-defaults app api-defaults) {:port port :join? false}))
  :stop
  (do
    (println "Stopping test server")
    (.stop server)))
