(ns java-http-clj.core-test
  (:refer-clojure :exclude [send get])
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [clojure.spec.test.alpha :as st]
            [java-http-clj.core :refer :all])
  (:import [java.net CookieManager ProxySelector URI]
           [java.net.http
            HttpClient$Redirect
            HttpClient$Version
            HttpHeaders
            HttpRequest$BodyPublisher
            HttpRequest$BodyPublishers
            HttpResponse]
           [java.time Duration]
           [java.util.concurrent Executors]
           [java.util.function BiPredicate]
           [javax.net.ssl SSLContext SSLParameters]))

(set! *warn-on-reflection* true)

(st/instrument)

(defn build-fake-response [{:keys [status body version headers]}]
  (reify HttpResponse
    (body [this] body)
    (headers [this] headers)
    (statusCode [this] status)
    (version [this] version)))

(def always-true-filter
  (reify BiPredicate
    (test [_ _ _] true)))

(def fake-response
  (build-fake-response
    {:status 200
     :body "text, text everywhere"
     :version HttpClient$Version/HTTP_2
     :headers (HttpHeaders/of
                {"content-type" ["application/json"]
                 "accept" ["deflate" "gzip"]}
                always-true-filter)}))

(deftest response->map-test
  (let [{:keys [status body version headers]} (response->map fake-response)]
    (is (= 200 status))
    (is (= :http2 version))
    (is (= {"content-type" "application/json"
            "accept" ["deflate" "gzip"]}
           headers))
    (is (= "text, text everywhere" body))))

(deftest build-request-test
  (let [req-map
        {:expect-continue? true
         :uri "http://www.google.com"
         :method :get
         :headers {"content-type" "application/json"
                   "accept" ["deflate" "gzip"]}
         :timeout 2000
         :version :http2}
        r (build-request req-map)]
    (is (= true (.expectContinue r)))
    (is (= "http://www.google.com" (-> r .uri .toString)))
    (is (= "GET" (.method r)))
    (is (= {"content-type" ["application/json"]
            "accept" ["deflate" "gzip"]}
           (into {} (-> r .headers .map))))
    (is (= (Duration/ofMillis 2000) (-> r .timeout .get)))
    (is (= (Duration/ofMillis 3000)
           (-> req-map (assoc :timeout (Duration/ofMillis 3000)) build-request .timeout .get)))
    (is (= HttpClient$Version/HTTP_2 (-> r .version .get)))))

(deftest build-client-test
  (let [cookie-handler (CookieManager.)
        executor (Executors/newSingleThreadExecutor)
        proxy (proxy [ProxySelector] []
                (connectFailed [_ _ _])
                (select [_ _]))
        ssl-context (SSLContext/getInstance "TLS")
        ssl-parameters (SSLParameters. (into-array String ["TLS_DH_anon_WITH_AES_128_CBC_SHA"]))
        opts {:connect-timeout 2000
              :cookie-handler cookie-handler
              :executor executor
              :follow-redirects :always
              :priority 123 ;; There is no getter for priority, so good luck testing that
              :proxy proxy
              :ssl-context ssl-context
              :ssl-parameters ssl-parameters
              :version :http1.1}
        c (build-client opts)]
    (is (= (Duration/ofMillis 2000) (-> c .connectTimeout .get)))
    (is (= (Duration/ofMillis 3000)
           (-> opts (assoc :connect-timeout (Duration/ofMillis 3000)) build-client .connectTimeout .get)))
    (is (identical? cookie-handler (-> c .cookieHandler .get)))
    (is (identical? executor (-> c .executor .get)))
    (is (= HttpClient$Redirect/ALWAYS (-> c .followRedirects)))
    (is (identical? proxy (-> c .proxy .get)))
    (is (identical? ssl-context (-> c .sslContext)))
    (is (= ["TLS_DH_anon_WITH_AES_128_CBC_SHA"] (-> c .sslParameters .getCipherSuites vec)))
    (is (= HttpClient$Version/HTTP_1_1 (-> c .version)))))
