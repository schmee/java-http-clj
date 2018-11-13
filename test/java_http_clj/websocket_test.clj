(ns ^:integration java-http-clj.websocket-test
  (:refer-clojure :exclude [send])
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [clojure.spec.test.alpha :as st]
            [java-http-clj.test-server]
            [java-http-clj.websocket :refer :all]
            [mount.core :as mount])
  (:import [java.net.http
            HttpClient
            WebSocket
            WebSocket$Builder
            WebSocket$Listener]
           [java.nio ByteBuffer]
           [java.time Duration]
           [java.util Arrays]))

(st/instrument)

(def port 8787)

(defn received []
  (zipmap
    [:on-binary :on-close :on-error :on-open :on-ping :on-pong :on-text]
    (repeatedly promise)))

(def responses (atom (received)))

(defn deliver-response [k v]
  (swap! responses update k deliver v))

(defn ^WebSocket build-a-websocket
 ([] (build-a-websocket {}))
 ([fns]
  (build-websocket
    "ws://localhost:8787/ws/"
    (merge
      {:on-binary (fn [_ data last?] (deliver-response :on-binary data))
       :on-text (fn [ws data last?] (deliver-response :on-text data))
       :on-error (fn [ws throwable] (deliver-response :on-error throwable))
       :on-ping (fn [ws data] (deliver-response :on-ping data))
       :on-pong (fn [ws data] (deliver-response :on-pong data))
       :on-open (fn [ws] (deliver-response :on-open "did open"))
       :on-close (fn [ws status-code reason] (deliver-response :on-close [status-code reason]))}
      fns))))

(mount/defstate ^WebSocket ws
  :start (build-a-websocket)
  :stop (.abort ^WebSocket ws))

(use-fixtures :once
  (fn [f]
    (-> (mount/only [#'java-http-clj.test-server/server])
        (mount/with-args {:port port})
        mount/start)
    (f)
    (mount/stop)))

(use-fixtures :each
  (fn [f]
    (reset! responses (received))
    (mount/start #'java-http-clj.websocket-test/ws)
    (f)
    (mount/stop #'java-http-clj.websocket-test/ws)))

(defn deref* [x]
  (deref x 100 ::timeout))

(deftest string
  (send ws "abc")
  (is (= "SERVER: abc" (-> @responses :on-text deref*))))

(deftest non-string
  (send ws 123)
  (is (= "SERVER: 123" (-> @responses :on-text deref*))))

(deftest some-bytes
  (send ws (byte-array [1 2 3]))
  (is (= [1 2 3] (-> @responses :on-binary deref* vec))))

(deftest byte-buffer
  (send ws (ByteBuffer/wrap (byte-array [4 5 6])))
  (is (= [4 5 6] (-> @responses :on-binary deref* vec))))

(deftest pong-argument-conversion
  (.sendPing ws (ByteBuffer/wrap (byte-array [1 2 3])))
  (is (Arrays/equals (byte-array [1 2 3]) (-> @responses :on-pong deref*))))

(deftest open-it
  (is (= "did open" (-> @responses :on-open deref*))))

(deftest errors
  (let [ws (build-a-websocket
             {:on-text (fn [_ _ _] (throw (Exception. "BOOM")))})]
    (send ws "abc")
    (let [e (-> @responses :on-error deref*)]
      (is (instance? Exception e))
      (is (= "BOOM" (.getMessage e))))))

(deftest close-it
  (close ws)
  (is (.isOutputClosed ws))
  (is (= [1000 ""] (-> @responses :on-close deref*))))

(deftest close-it-with-status-code
  (close ws 3333)
  (is (.isOutputClosed ws))
  (is (= [3333 ""] (-> @responses :on-close deref*))))

(deftest close-it-with-status-code-and-reason
  (close ws 4444 "I'm out!")
  (is (.isOutputClosed ws))
  (is (= [4444 "I'm out!"] (-> @responses :on-close deref*))))

(defn update-arg! [args k arg]
  (swap! args update k (fnil conj []) arg))

(defrecord FakeBuilder [args]
  WebSocket$Builder
  (header [this name value]
    (update-arg! args :header [name value]) this)
  (subprotocols [this most-preferred lesser-preferred]
    (update-arg! args :subprotocols [most-preferred lesser-preferred]) this)
  (connectTimeout [this timeout]
    (update-arg! args :connectTimeout [timeout]) this))

(defn fake-client [builder]
  (proxy [HttpClient] []
    (newWebSocketBuilder [] builder)))

(deftest builder
  (let [args (atom {})
        fake-client (fake-client (->FakeBuilder args))]
    (websocket-builder {:client fake-client
                        :connect-timeout 3000
                        :subprotocols ["foo" "bar"]
                        :headers {"a" "b"
                                  "c" ["d" "e"]}})
    (is (= [[(Duration/ofSeconds 3)]] (-> @args :connectTimeout)))
    (is (= [["a" "b"] ["c" "d"] ["c" "e"]] (-> @args :header)))
    (is (= "foo" (-> @args :subprotocols ffirst)))
    (is (Arrays/equals (into-array String ["bar"])
                       (-> @args :subprotocols first second)))))
