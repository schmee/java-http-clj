(ns java-http-clj.specs
  (:require [clojure.spec.alpha :as s]
            [java-http-clj.util :as util])
  (:import [java.net CookieHandler ProxySelector]
           [java.net.http
            HttpClient
            HttpClient$Builder
            HttpRequest
            HttpRequest$Builder
            HttpResponse
            WebSocket
            WebSocket$Builder
            WebSocket$Listener]
           [java.nio ByteBuffer]
           [java.time Duration]
           [java.util.concurrent CompletableFuture Executor]
           [javax.net.ssl SSLContext SSLParameters]))


;; ==============================  CORE SPECS  ==============================


(s/def :java-http-clj.core/expect-continue? boolean?)
(s/def :java-http-clj.core/headers (s/map-of string? (s/or :string string? :seq-of-strings (s/+ string?))))
(s/def :java-http-clj.core/method keyword?)
(s/def :java-http-clj.core/timeout (s/or :millis pos-int? :duration #(instance? Duration %)))
(s/def :java-http-clj.core/uri string?)
(s/def :java-http-clj.core/version #{:http1.1 :http2})

(s/def :java-http-clj.core/req-map
  (s/keys :req-un [:java-http-clj.core/uri]
          :opt-un [:java-http-clj.core/expect-continue? :java-http-clj.core/headers :java-http-clj.core/method :java-http-clj.core/timeout :java-http-clj.core/version]))

(s/fdef request-builder
  :args (s/cat :req-map (s/? :java-http-clj.core/req-map))
  :ret #(instance? HttpRequest$Builder %))

(s/fdef build-request
  :args (s/cat :req-map (s/? :java-http-clj.core/req-map))
  :ret #(instance? HttpRequest %))

(s/def :java-http-clj.core/connect-timeout :java-http-clj.core/timeout)
(s/def :java-http-clj.core/cookie-handler #(instance? CookieHandler %))
(s/def :java-http-clj.core/executor #(instance? Executor %))
(s/def :java-http-clj.core/follow-redirects #{:always :default :never})
(s/def :java-http-clj.core/priority (s/int-in 1 257))
(s/def :java-http-clj.core/proxy #(instance? ProxySelector %))
(s/def :java-http-clj.core/ssl-context #(instance? SSLContext %))
(s/def :java-http-clj.core/ssl-parameters #(instance? SSLParameters %))

(s/def :java-http-clj.core/client-opts
  (s/keys :opt-un
          [:java-http-clj.core/connect-timeout
           :java-http-clj.core/cookie-handler
           :java-http-clj.core/executor
           :java-http-clj.core/follow-redirects
           :java-http-clj.core/priority
           :java-http-clj.core/proxy
           :java-http-clj.core/ssl-context
           :java-http-clj.core/ssl-parameters
           :java-http-clj.core/version]))

(s/fdef client-builder
  :args (s/cat :opts (s/? :java-http-clj.core/client-opts))
  :ret #(instance? HttpClient$Builder %))

(s/fdef build-client
  :args (s/cat :opts (s/? :java-http-clj.core/client-opts))
  :ret #(instance? HttpClient %))

(s/def :java-http-clj.core/request
  (s/or :uri :java-http-clj.core/uri
        :req-map :java-http-clj.core/req-map
        :raw #(instance? HttpRequest %)))

(s/def :java-http-clj.core/as #{:byte-array :input-stream :string})
(s/def :java-http-clj.core/client #(instance? HttpClient %))
(s/def :java-http-clj.core/raw? boolean?)

(s/def :java-http-clj.core/send-opts
  (s/keys :opt-un [:java-http-clj.core/as :java-http-clj.core/client :java-http-clj.core/raw?]))

(s/def :java-http-clj.core/body
  (s/or :byte-array bytes?
        :input-stream #(instance? java.io.InputStream %)
        :string string?))

(s/def :java-http-clj.core/status
  (s/int-in 100 600))

(s/def :java-http-clj.core/response-map
  (s/keys :req-un [:java-http-clj.core/body :java-http-clj.core/headers :java-http-clj.core/status :java-http-clj.core/version]))

(s/def :java-http-clj.core/response
  (s/or :map :java-http-clj.core/response-map
        :raw #(instance? HttpResponse %)))

(s/fdef send
  :args (s/cat :req :java-http-clj.core/request
               :opts (s/? :java-http-clj.core/send-opts))
  :ret :java-http-clj.core/response)

;; Use `ifn?` instead of `fspec` for the callbacks due to issues like
;; https://dev.clojure.org/jira/browse/CLJ-1936 and
;; https://dev.clojure.org/jira/browse/CLJ-2217
(s/fdef send-async
  :args (s/alt :req
               (s/cat :req :java-http-clj.core/request)

               :req+opts
               (s/cat :req :java-http-clj.core/request
                      :opts :java-http-clj.core/send-opts)

               :req+opts+callbacks
               (s/cat :req :java-http-clj.core/request
                      :opts :java-http-clj.core/send-opts
                      :callback (s/nilable ifn?)
                      ; :callback (s/fspec
                      ;             :args (s/cat :response :java-http-clj.core/response)
                      ;             :ret any?)
                      :ex-handler (s/nilable ifn?)))
                      ; :ex-handler (s/fspec
                      ;               :args (s/cat :exception #(instance? Throwable %))
                      ;               :ret any?)))
  :ret #(instance? CompletableFuture %))

(s/def :java-http-clj.core/req-map-all-optional
  (s/keys :opt-un [:java-http-clj.core/uri :java-http-clj.core/expect-continue? :java-http-clj.core/headers :java-http-clj.core/method :java-http-clj.core/timeout :java-http-clj.core/version]))

(defn spec-shorthand [method]
  (let [method-sym (symbol (name 'java-http-clj.core) (name method))]
    `(s/fdef ~method-sym
       :args ~'(s/alt :uri (s/cat :uri :java-http-clj.core/uri)
                      :uri+req-map (s/cat :uri :java-http-clj.core/uri
                                          :req-map :java-http-clj.core/req-map-all-optional)
                      :uri+req-map+opts (s/cat :uri :java-http-clj.core/uri
                                               :req-map :java-http-clj.core/req-map-all-optional
                                               :opts :java-http-clj.core/send-opts))
       :ret :java-http-clj.core/response)))

(defmacro ^:private spec-all-shorthands []
  `(do ~@(map spec-shorthand util/shorthands)))

(spec-all-shorthands)


;; ==============================  WEBSOCKET SPECS  ==============================


(s/def :java-http-clj.websocket/websocket
  #(instance? WebSocket %))

(s/def :java-http-clj.websocket/payload
  (s/or :string string?
        :byte-array bytes?
        :byte-buffer #(instance? ByteBuffer %)
        :other any?))

(s/fdef send
  :args (s/cat
          :websocket :java-http-clj.websocket/websocket
          :payload :java-http-clj.websocket/payload
          :last? (s/? boolean?))
  :ret :java-http-clj.websocket/websocket)

(s/def :java-http-clj.websocket/on-binary ifn?)
(s/def :java-http-clj.websocket/on-close ifn?)
(s/def :java-http-clj.websocket/on-error ifn?)
(s/def :java-http-clj.websocket/on-open ifn?)
(s/def :java-http-clj.websocket/on-ping ifn?)
(s/def :java-http-clj.websocket/on-pong ifn?)
(s/def :java-http-clj.websocket/on-text ifn?)

(s/def :java-http-clj.websocket/listener-fns
  (s/keys :opt-un
          [:java-http-clj.websocket/on-binary
           :java-http-clj.websocket/on-close
           :java-http-clj.websocket/on-error
           :java-http-clj.websocket/on-open
           :java-http-clj.websocket/on-ping
           :java-http-clj.websocket/on-pong
           :java-http-clj.websocket/on-text]))

(s/fdef websocket-listener
  :args (s/cat :listener-fns :java-http-clj.websocket/listener-fns)
  :ret #(instance? WebSocket$Listener %))

(s/def :java-http-clj.websocket/subprotocols
  (s/+ string?))

(s/def :java-http-clj.websocket/builder-opts
  (s/keys
    :opt-un [:java-http-clj.core/client
             :java-http-clj.core/connect-timeout
             :java-http-clj.core/headers
             :java-http-clj.websocket/subprotocols]))

(s/fdef websocket-builder
  :args (s/cat :opts (s/? :java-http-clj.websocket/builder-opts))
  :ret #(instance? WebSocket$Builder %))

(s/fdef build-websocket
  :args (s/cat :uri :java-http-clj.core/uri
               :listener-fns :java-http-clj.websocket/listener-fns
               :builder-opts (s/? :java-http-clj.websocket/builder-opts))
  :ret :java-http-clj.websocket/websocket)

(s/def :java-http-clj.websocket/status-code
  (s/int-in 1000 5000))

(s/def :java-http-clj.websocket/reason string?)

(s/def :java-http-clj.websocket/completable-future
  #(instance? CompletableFuture %))

(s/fdef close
  :args (s/alt :default (s/cat :websocket :java-http-clj.websocket/websocket)
               :status-code (s/cat :websocket :java-http-clj.websocket/websocket
                                   :status-code :java-http-clj.websocket/status-code)
               :status-code+reason (s/cat :websocket :java-http-clj.websocket/websocket
                                          :status-code :java-http-clj.websocket/status-code
                                          :reason :java-http-clj.websocket/reason))
  :ret :java-http-clj.websocket/completable-future)
