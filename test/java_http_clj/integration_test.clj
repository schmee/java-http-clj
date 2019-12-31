(ns ^:integration java-http-clj.integration-test
  (:refer-clojure :exclude [send get])
  (:require [cemerick.url :refer [url]]
            [clojure.java.io :as io]
            [clojure.test :refer :all]
            [clojure.spec.test.alpha :as st]
            [java-http-clj.core :refer :all]
            [mount.core :as mount])
  (:import [java.net.http HttpResponse]
           [java.util Arrays]
           [java.util.concurrent CompletableFuture]))

(set! *warn-on-reflection* true)

(st/instrument)

(def port 8787)

(defn wrap-setup [f]
  (mount/start (mount/with-args {:port port}))
  (f)
  (mount/stop))

(use-fixtures :once wrap-setup)

(def base-url
  (assoc (url "http://localhost") :port port))

(defn make-url
  ([]
   (str base-url))
  ([path]
   (str (url base-url path)))
  ([path params]
   (str (assoc (url base-url path) :query params))))

(def ^String s "some boring test string")

(defn all-tests [f]
  (testing "request"
    (let [{:keys [body headers status version]} (send (make-url))]
      (is (= "ROOT" body))
      ; (is (= ["content-length" "content-type" "date" "server"] (-> headers keys sort)))
      (is (= 200 status))
      (is (= :http1.1 version))))

  (testing "request-body-types"
    (let [send-and-get-body
          (fn [body]
            (:body (f {:uri (make-url "echo")
                       :method :post
                       :body body})))]
      (is (= "ROOT" (:body (send (make-url)))))
      (is (= s (send-and-get-body s)))
      (is (= s (send-and-get-body (.getBytes s))))
      (is (= s (send-and-get-body (io/input-stream (.getBytes s)))))))

  (testing "response-body-types"
    (let [send-echo (fn [opts] (f (make-url "echo" {:message s}) opts))]
      (is (= s (:body (send-echo {:as :string}))))
      (is (Arrays/equals (.getBytes s) (:body (send-echo {:as :byte-array}))))
      (is (= s (-> (send-echo {:as :input-stream}) :body slurp)))))

  (testing "raw-opt"
    (is (instance? HttpResponse (f (make-url) {:raw? true}))))

  (testing "client-opt"
    ;; default client doesn't follow redirects
    (is (= 302 (:status (f (make-url "redir")))))
    (let [client (build-client {:follow-redirects :always})
          {:keys [body status]} (f (make-url "redir") {:client client})]
      (is (= 200 status))
      (is (= "did redirect" body)))))

(deftest test-send
  (all-tests send))

(deftest test-send-async
  (all-tests (comp deref send-async)))

(deftest async-stuff
  (let [f (send-async (make-url))
        r (.join f)]
    (is (instance? CompletableFuture f))
    (is (map? r)))

  (testing "callback"
    (let [r (.join (send-async (make-url) {} #(assoc % :call :back) nil))]
      (is (= :back (:call r)))))

  (testing "ex-handler"
    (let [f (send-async
              (make-url)
              {}
              (fn [_] (throw (Exception. "oops!")))
              (fn [_] :exception))
          r (.join f)]
      (is (= :exception r)))))
