(ns java-http-clj.core
  (:refer-clojure :exclude [send get])
  (:require [clojure.string :as str])
  (:import [java.net URI]
           [java.net.http
            HttpClient
            HttpClient$Builder
            HttpClient$Redirect
            HttpClient$Version
            HttpRequest
            HttpRequest$BodyPublishers
            HttpRequest$Builder
            HttpResponse
            HttpResponse$BodyHandlers]
           [java.time Duration]
           [java.util.concurrent CompletableFuture]
           [java.util.function Function Supplier]))

(set! *warn-on-reflection* true)

(defn convert-timeout [t]
  (if (integer? t)
    (Duration/ofMillis t)
    t))

(defn version-keyword->version-enum [version]
  (case version
    :http1.1 HttpClient$Version/HTTP_1_1
    :http2   HttpClient$Version/HTTP_2))

(defn convert-follow-redirect [redirect]
  (case redirect
    :always HttpClient$Redirect/ALWAYS
    :never HttpClient$Redirect/NEVER
    :normal HttpClient$Redirect/NORMAL))

(defn client-builder
  (^HttpClient$Builder []
   (client-builder {}))
  (^HttpClient$Builder [opts]
   (let [{:keys [connect-timeout
                 cookie-handler
                 executor
                 follow-redirects
                 priority
                 proxy
                 ssl-context
                 ssl-parameters
                 version]} opts]
     (cond-> (HttpClient/newBuilder)
       connect-timeout  (.connectTimeout (convert-timeout connect-timeout))
       cookie-handler   (.cookieHandler cookie-handler)
       executor         (.executor executor)
       follow-redirects (.followRedirects (convert-follow-redirect follow-redirects))
       priority         (.priority priority)
       proxy            (.proxy proxy)
       ssl-context      (.sslContext ssl-context)
       ssl-parameters   (.sslParameters ssl-parameters)
       version          (.version (version-keyword->version-enum version))))))

(defn make-client
  ([] (.build (client-builder)))
  ([opts] (.build (client-builder opts))))

(def ^HttpClient default-client
  (delay (make-client)))

(def ^:private byte-array-class
  (Class/forName "[B"))

(defn- input-stream-supplier [s]
  (reify Supplier
    (get [this] s)))

(defn convert-body-publisher [body]
  (cond
    (string? body)
    (HttpRequest$BodyPublishers/ofString body)

    (instance? java.io.InputStream body)
    (HttpRequest$BodyPublishers/ofInputStream (input-stream-supplier body))

    (instance? byte-array-class body)
    (HttpRequest$BodyPublishers/ofByteArray body)))

(def convert-headers-xf
  (mapcat
    (fn [[k v :as p]]
      (if (sequential? v)
        (interleave (repeat k) v)
        p))))

(defn request-builder ^HttpRequest$Builder [opts]
  (let [{:keys [expect-continue?
                headers
                method
                timeout
                uri
                version
                body]} opts]
    (cond-> (HttpRequest/newBuilder)
      (some? expect-continue?) (.expectContinue expect-continue?)
      (seq headers)            (.headers (into-array String (eduction convert-headers-xf headers)))
      method                   (.method (str/upper-case (name method)) (convert-body-publisher body))
      timeout                  (.timeout (convert-timeout timeout))
      uri                      (.uri (URI/create uri))
      version                  (.version (version-keyword->version-enum version)))))

(defn make-request
  ([] (.build (request-builder {})))
  ([opts] (.build (request-builder opts))))

(def ^:private bh-of-string (HttpResponse$BodyHandlers/ofString))
(def ^:private bh-of-input-stream (HttpResponse$BodyHandlers/ofInputStream))
(def ^:private bh-of-byte-array (HttpResponse$BodyHandlers/ofByteArray))

(defn convert-body-handler [mode]
  (case mode
    nil bh-of-string
    :string bh-of-string
    :input-stream bh-of-input-stream
    :byte-array bh-of-byte-array))

(defn resp->ring [^HttpResponse resp]
  {:status (.statusCode resp)
   :body (.body resp)
   :version (-> resp .version .name)
   :headers (into {}
                  (map (fn [[k v]] [k (if (> (count v) 1) (vec v) (first v))]))
                  (.map (.headers resp)))})

(defn clj-fn->function ^Function [f]
  (reify Function
    (apply [this x] (f x))))

(def ^Function resp->ring-function
  (clj-fn->function resp->ring))

(defn convert-request [req]
  (cond
    (map? req) (make-request req)
    (string? req) (make-request {:uri req})
    (instance? HttpRequest req) req))

(defn send
  ([req]
   (send req {}))
  ([req {:keys [client as raw?] :as opts}]
   (let [^HttpClient client (or client @default-client)
         req' (convert-request req)
         resp (.send client req' (convert-body-handler as))]
     (if raw? resp (resp->ring resp)))))

(defn send-async
  ([req]
   (send-async req {} nil nil))
  ([req opts]
   (send-async req opts nil nil))
  ([req {:keys [client as raw?] :as opts} callback ex-handler]
   (let [^HttpClient client (or client @default-client)
         req' (convert-request req)]
     (cond-> (.sendAsync client req' (convert-body-handler as))
       (not raw?)  (.thenApply resp->ring-function)
       callback    (.thenApply (clj-fn->function callback))
       ex-handler  (.exceptionally (clj-fn->function ex-handler))))))

(defn- defshorthand [method]
  (let [n (symbol (name method))]
    `(defn ~n
      ([uri#]
       (send {:uri uri# :method ~method} {}))
      ([uri# req-map#]
       (send (merge req-map# {:uri uri# :method ~method}) {}))
      ([uri# req-map# opts#]
       (send (merge req-map# {:uri uri# :method ~method}) opts#)))))

(defmacro ^:private def-all-shorthands []
  `(do ~@(map #(defshorthand %) [:get :head :post :put :delete])))

(def-all-shorthands)
