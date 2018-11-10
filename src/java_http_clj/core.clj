(ns java-http-clj.core
  (:refer-clojure :exclude [send get])
  (:require [clojure.string :as str]
            [clojure.spec.alpha :as s])
  (:import [java.net CookieHandler ProxySelector URI]
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
           [java.util.concurrent CompletableFuture Executor]
           [java.util.function Function Supplier]
           [javax.net.ssl SSLContext SSLParameters]))

(set! *warn-on-reflection* true)

(defn- convert-timeout [t]
  (if (integer? t)
    (Duration/ofMillis t)
    t))

(defn- version-keyword->version-enum [version]
  (case version
    :http1.1 HttpClient$Version/HTTP_1_1
    :http2   HttpClient$Version/HTTP_2))

(defn- convert-follow-redirect [redirect]
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

(defn build-client
  ([] (.build (client-builder)))
  ([opts] (.build (client-builder opts))))

(def ^HttpClient default-client
  (delay (HttpClient/newHttpClient)))

(def ^:private byte-array-class
  (Class/forName "[B"))

(defn- input-stream-supplier [s]
  (reify Supplier
    (get [this] s)))

(defn- convert-body-publisher [body]
  (cond
    (nil? body)
    (HttpRequest$BodyPublishers/noBody)

    (string? body)
    (HttpRequest$BodyPublishers/ofString body)

    (instance? java.io.InputStream body)
    (HttpRequest$BodyPublishers/ofInputStream (input-stream-supplier body))

    (instance? byte-array-class body)
    (HttpRequest$BodyPublishers/ofByteArray body)))

(def ^:private convert-headers-xf
  (mapcat
    (fn [[k v :as p]]
      (if (sequential? v)
        (interleave (repeat k) v)
        p))))

(defn- method-keyword->str [method]
  (str/upper-case (name method)))

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
      method                   (.method (method-keyword->str method) (convert-body-publisher body))
      timeout                  (.timeout (convert-timeout timeout))
      uri                      (.uri (URI/create uri))
      version                  (.version (version-keyword->version-enum version)))))

(defn build-request
  ([] (.build (request-builder {})))
  ([req-map] (.build (request-builder req-map))))

(def ^:private bh-of-string (HttpResponse$BodyHandlers/ofString))
(def ^:private bh-of-input-stream (HttpResponse$BodyHandlers/ofInputStream))
(def ^:private bh-of-byte-array (HttpResponse$BodyHandlers/ofByteArray))

(defn- convert-body-handler [mode]
  (case mode
    nil bh-of-string
    :string bh-of-string
    :input-stream bh-of-input-stream
    :byte-array bh-of-byte-array))

(defn- version-enum->version-keyword [^HttpClient$Version version]
  (case (.name version)
    "HTTP_1_1" :http1.1
    "HTTP_2"   :http2))

(defn response->map [^HttpResponse resp]
  {:status (.statusCode resp)
   :body (.body resp)
   :version (-> resp .version version-enum->version-keyword)
   :headers (into {}
                  (map (fn [[k v]] [k (if (> (count v) 1) (vec v) (first v))]))
                  (.map (.headers resp)))})

(defn- clj-fn->function ^Function [f]
  (reify Function
    (apply [this x] (f x))))

(def ^:private ^Function resp->ring-function
  (clj-fn->function response->map))

(defn- convert-request [req]
  (cond
    (map? req) (build-request req)
    (string? req) (build-request {:uri req})
    (instance? HttpRequest req) req))

(defn send
  ([req]
   (send req {}))
  ([req {:keys [as client raw?] :as opts}]
   (let [^HttpClient client (or client @default-client)
         req' (convert-request req)
         resp (.send client req' (convert-body-handler as))]
     (if raw? resp (response->map resp)))))

(defn send-async
  ([req]
   (send-async req {} nil nil))
  ([req opts]
   (send-async req opts nil nil))
  ([req {:keys [as client raw?] :as opts} callback ex-handler]
   (let [^HttpClient client (or client @default-client)
         req' (convert-request req)]
     (cond-> (.sendAsync client req' (convert-body-handler as))
       (not raw?)  (.thenApply resp->ring-function)
       callback    (.thenApply (clj-fn->function callback))
       ex-handler  (.exceptionally (clj-fn->function ex-handler))))))

(defn- shorthand-docstring [method]
  (str "Sends a " (method-keyword->str method) " request to `uri`.

   See [[send]] for a description of `req-map` and `opts`."))

(defn- defshorthand [method]
  `(defn ~(symbol (name method))
     ~(shorthand-docstring method)
     (~['uri]
       (send ~{:uri 'uri :method method} {}))
     (~['uri 'req-map]
       (send (merge ~'req-map ~{:uri 'uri :method method}) {}))
     (~['uri 'req-map 'opts]
       (send (merge ~'req-map ~{:uri 'uri :method method}) ~'opts))))

(defmacro ^:private def-all-shorthands []
  `(do ~@(map #(defshorthand %) [:get :head :post :put :delete])))

(def-all-shorthands)


;; ==============================  SPECS  ==============================


(defn instance-pred [c]
  #(instance? c %))

(s/def ::expect-continue? boolean?)
(s/def ::headers (s/map-of string? (s/or :string string? :seq-of-strings (s/+ string?))))
(s/def ::method #{:get :head :post :put :delete :connect :options :trace :patch})
(s/def ::timeout (s/or :millis pos-int? :duration (instance-pred Duration)))
(s/def ::uri string?)
(s/def ::version #{:http1.1 :http2})

(s/def ::req-map
  (s/keys :req-un [::uri]
          :opt-un [::expect-continue? ::headers ::method ::timeout ::version]))

(s/fdef request-builder
  :args (s/cat :req-map (s/? ::req-map))
  :ret #(instance? HttpRequest$Builder %))

(s/fdef build-request
  :args (s/cat :req-map (s/? ::req-map))
  :ret #(instance? HttpRequest %))

(s/def ::connect-timeout ::timeout)
(s/def ::cookie-handler (instance-pred CookieHandler))
(s/def ::executor (instance-pred Executor))
(s/def ::follow-redirects #{:always :default :never})
(s/def ::priority (s/int-in 1 257))
(s/def ::proxy (instance-pred ProxySelector))
(s/def ::ssl-context (instance-pred SSLContext))
(s/def ::ssl-parameters (instance-pred SSLParameters))

(s/def ::client-opts
  (s/keys :opt-un
          [::connect-timeout ::cookie-handler ::executor
           ::follow-redirects ::priority ::proxy
           ::ssl-context ::ssl-parameters]))

(s/fdef client-builder
  :args (s/cat :opts (s/? ::client-opts))
  :ret #(instance? HttpClient$Builder %))

(s/fdef build-client
  :args (s/cat :opts (s/? ::client-opts))
  :ret #(instance? HttpClient %))

(s/def ::request
  (s/or :uri ::uri
        :req-map ::req-map
        :raw (instance-pred HttpRequest)))

(s/def ::as #{:byte-array :input-stream :string})
(s/def ::client (instance-pred HttpClient))
(s/def ::raw? boolean?)

(s/def ::send-opts
  (s/keys :opt-un [::as ::client ::raw]))

(s/def ::body
  (s/or :byte-array bytes?
        :input-stream (instance-pred java.io.InputStream)
        :string string?))

(s/def ::status
  (s/int-in 0 500))

(s/def ::response-map
  (s/keys :req-un [::body ::headers ::status ::version]))

(s/def ::response
  (s/or :map ::response-map
        :raw (instance-pred HttpResponse)))

(s/fdef send
  :args (s/cat :req ::request
               :opts (s/? ::send-opts))
  :ret ::response)

(s/fdef send-async
  :args (s/alt :req
               (s/cat :req ::request)

               :req+opts
               (s/cat :req ::request
                      :opts ::send-opts)

               :req+opts+callbacks
               (s/cat :req ::request
                      :opts ::send-opts
                      :callback (s/fspec
                                  :args (s/cat :response ::response)
                                  :ret any?)
                      :ex-handler (s/fspec
                                    :args (s/cat :exception (instance-pred Throwable))
                                    :ret any?)))
  :ret (instance-pred CompletableFuture))


;; ==============================  DOCSTRINGS  ==============================


(defmacro ^:private add-docstring [var docstring]
  `(alter-meta! ~var #(assoc % :doc ~docstring)))

(add-docstring #'default-client
  "Used for requests unless a client is explicitly passed. Equal to [HttpClient.newHttpClient()](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/HttpClient.html#newHttpClient%28%29).")

(add-docstring #'client-builder
  "Same as [[build-client]], but returns a [HttpClient.Builder](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/HttpClient.Builder.html) instead of a [HttpClient](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/HttpClient.html).

  See [[build-client]] for a description of `opts`.")


(add-docstring #'build-client
  "Builds a client with the supplied options. See [HttpClient.Builder](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/HttpClient.Builder.html) for a more detailed description of the options.

  The `opts` map takes the following keys:

  - `:connect-timeout` - connection timeout in milliseconds or a `java.time.Duration`
  - `:cookie-handler` - a [java.net.CookieHandler](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/net/CookieHandler.html)
  - `:executor` - a [java.util.concurrent.Executor](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/concurrent/Executor.html)
  - `:follow-redirects` - one of `:always`, `:never` and `:normal`. Maps to the corresponding [HttpClient.Redirect](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/HttpClient.Redirect.html) enum.
  - `:priority` - the [priority](https://developers.google.com/web/fundamentals/performance/http2/#stream_prioritization) of the request (only used for HTTP/2 requests)
  - `:proxy` - a [java.net.ProxySelector](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/net/ProxySelector.html)
  - `:ssl-context` - a [javax.net.ssl.SSLContext](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/javax/net/ssl/SSLContext.html)
  - `:ssl-parameters` - a [javax.net.ssl.SSLParameters](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/javax/net/ssl/SSLParameters.html)
  - `:version` - the HTTP protocol version, one of `:http1.1` or `:http2`

  Equivalent to `(.build (client-builder opts))`.")

(add-docstring #'request-builder
  "Same as [[build-request]], but returns a [HttpRequest.Builder](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/HttpRequest.Builder.html) instead of a [HttpRequest](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/HttpRequest.html).")

(add-docstring #'build-request
  "Builds a [java.net.http.HttpRequest](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/HttpRequest.html) from a map.

  See [[send]] for a description of `req-map`.

  Equivalent to `(.build (request-builder req-map))`.")

(add-docstring #'send
  "Sends a HTTP request and blocks until a response is returned or the request
  takes longer than the specified `timeout`. If the request times out, a [HttpTimeoutException](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/HttpTimeoutException.html)
  is thrown.

  The `req` parameter can be a either string URL, a request map, or a [java.net.http.HttpRequest](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/HttpRequest.html).

  The request map takes the following keys:

  - `:body` - the request body. Can be a string, a primitive Java byte array or a java.io.InputStream.
  - `:expect-continue?` - See the [javadoc](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/HttpRequest.Builder.html#expectContinue%28boolean%29)
  - `:headers` - the HTTP headers, a map where keys are strings and values are strings or a list of strings
  - `:method` - the HTTP method as a keyword (e.g `:get`, `:put`, `:post`)
  - `:timeout` - the request timeout in milliseconds or a `java.time.Duration`
  - `:uri` - the request uri
  - `:version` - the HTTP protocol version, one of `:http1.1` or `:http2`

  `opts` is a map containing one of the following keywords:

  - `:as` - converts the response body to one of the following formats:
      - `:string` - a java.lang.String (default)
      - `:byte-array` - a Java primitive byte array.
      - `:input-stream` - a java.io.InputStream.

  - `:client` - the [HttpClient](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/HttpClient.html) to use for the request. If not provided the [[default-client]] will be used.

  - `:raw?` - if true, skip the Ring format conversion and return the [HttpResponse](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/HttpResponse.html")

(add-docstring #'send-async
  "Sends a request asynchronously and immediately returns a [CompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html). Converts the
   eventual response to a map as per [[response->map]].

  See [[send]] for a description of `req` and `opts`.

  `callback` is a one argument function that will be applied to the response on completion.

  `ex-handler` is a one argument function that will be called if an exception is thrown anywhere during the request.")

(add-docstring #'response->map
  "Converts a [HttpResponse](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/HttpResponse.html) into a map.

  The response map contains the following keys:

   - `:body` - the response body
   - `:headers` - the HTTP headers, a map where keys are strings and values are strings or a list of strings
   - `:status` - the HTTP status code
   - `:version` - the HTTP protocol version, one of `:http1.1` or `:http2`")
