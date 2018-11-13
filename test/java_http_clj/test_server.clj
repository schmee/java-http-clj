(ns ^:skip-test java-http-clj.test-server
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [mount.core :as mount]
            [ring.adapter.jetty9 :as jetty]
            [ring.middleware.defaults :refer :all]))

(defroutes app
  (GET "/" [] "ROOT")
  (GET "/echo" [message] (or message "no message"))
  (POST "/echo" r (slurp (io/reader (:body r))))
  (PUT "/echo" r (slurp (io/reader (:body r))))
  (DELETE "/echo" [] "deleted")
  (GET "/redir" [] {:status 302 :headers {"Location" "/target"}})
  (GET "/target" [] "did redirect"))

(def ws-handler {:on-connect (fn [ws])
                 :on-error (fn [ws e])
                 :on-close (fn [ws status-code reason])
                 :on-text
                 (fn [ws text-message]
                   (jetty/send! ws (str "SERVER: " text-message)))
                 :on-bytes
                 (fn [ws bs offset len]
                   (jetty/send! ws bs))})

(mount/defstate server
  :start
  (let [{:keys [port] :or {port 8080}} (mount/args)]
    (jetty/run-jetty
      (wrap-defaults app api-defaults)
      {:port port
       :websockets {"/ws" ws-handler}
       :join? false}))
  :stop (.stop server))
