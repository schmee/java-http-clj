(ns java-http-clj.websocket
  (:refer-clojure :exclude [send])
  (:require [java-http-clj.core :as core]
            [java-http-clj.util :as util :refer [add-docstring]])
  (:import [java.net URI]
           [java.net.http
            HttpClient
            WebSocket
            WebSocket$Builder
            WebSocket$Listener]
           [java.nio ByteBuffer]
           [java.util.concurrent CompletableFuture]))

(set! *warn-on-reflection* true)

(defn websocket-builder
  (^WebSocket$Builder [] (websocket-builder {}))
  (^WebSocket$Builder [{:keys [client connect-timeout headers subprotocols]}]
   (let [^HttpClient client (or client @core/default-client)
         builder (.newWebSocketBuilder client)]
     (if headers
       (doseq [[k v] headers]
         (if (sequential? v)
           (run! #(.header builder k %) v)
           (.header builder k v))))
     (cond-> builder
       connect-timeout (.connectTimeout (util/convert-timeout connect-timeout))
       subprotocols (.subprotocols (first subprotocols) (into-array String (rest subprotocols)))))))

(defn- byte-buffer->byte-array [^ByteBuffer byte-buffer]
  (let [ba (byte-array (.capacity byte-buffer))]
    (.get byte-buffer ba)
    ba))

(defn websocket-listener
  (^WebSocket$Listener [{:keys [on-binary on-close on-error on-open on-ping on-pong on-text]}]
   (let [that (reify WebSocket$Listener)]
     (reify WebSocket$Listener
       (onBinary [this ws byte-buffer last?]
         (if on-binary
           (on-binary ws (byte-buffer->byte-array byte-buffer) last?)
           (.onBinary that ws byte-buffer last?)))
       (onClose [this ws status-code reason]
         (if on-close
           (on-close ws status-code reason)
           (.onClose that ws status-code reason)))
       (onError [this ws throwable]
         (if on-error
           (on-error ws throwable)
           (.onError that ws throwable)))
       (onOpen [this ws]
         (if on-open
           (on-open ws)
           (.onOpen that ws)))
       (onPing [this ws byte-buffer]
         (if on-ping
           (on-ping ws (byte-buffer->byte-array byte-buffer))
           (.onPing that ws byte-buffer)))
       (onPong [this ws byte-buffer]
         (if on-pong
           (on-pong ws (byte-buffer->byte-array byte-buffer))
           (.onPong that ws byte-buffer)))
       (onText [this ws char-seq last?]
         (if on-text
           (on-text ws (.toString char-seq) last?)
           (.onText that ws char-seq last?)))))))

(defn- wrap-listener-fns [listener-fns]
  (let [non-receive-methods #{:on-close :on-error}
        inc-and-nil (fn [f]
                      (fn [& args]
                        (let [^WebSocket ws (first args)]
                          (.request ws 1)
                          (apply f args)
                          nil)))
        return-nil (fn [f]
                     (fn [& args]
                       (apply f args)
                       nil))]
    (into {}
      (for [[k f] listener-fns]
        (if (contains? non-receive-methods k)
          [k (return-nil f)]
          [k (inc-and-nil f)])))))

(defn build-websocket-async
  (^CompletableFuture [uri listener-fns]
   (build-websocket-async uri listener-fns {}))
  (^CompletableFuture [uri listener-fns builder-opts]
   (.buildAsync
     (websocket-builder builder-opts)
     (URI/create uri)
     (websocket-listener (wrap-listener-fns listener-fns)))))

(defprotocol ^:no-doc Send
  (-send [this ws last?]))

(extend-protocol Send
  (Class/forName "[B")
  (-send [this ^WebSocket ws last?]
    (.sendBinary ws (ByteBuffer/wrap this) last?))

  ByteBuffer
  (-send [this ^WebSocket ws last?]
    (.sendBinary ws this last?))

  String
  (-send [this ^WebSocket ws last?]
    (.sendText ws this last?))

  Object
  (-send [this ^WebSocket ws last?]
    (.sendText ws (str this) last?)))

(defn send-async
  (^CompletableFuture [ws payload]
   (send-async ws payload true))
  (^CompletableFuture [^WebSocket ws payload last?]
   (-send payload ws last?)))

(defn- join [^CompletableFuture cf]
  (.join cf))

(def
  ^{:arglists '([uri listener-fns]
                [uri listener-fns builder-opts])
    :tag WebSocket}
  build-websocket
  (comp join build-websocket-async))

(def
  ^{:arglists '([ws payload] [ws payload last?])}
  send
  (comp join send-async))

(defn ^CompletableFuture close
 (^CompletableFuture [^WebSocket ws] (.sendClose ws WebSocket/NORMAL_CLOSURE ""))
 (^CompletableFuture [^WebSocket ws status-code] (.sendClose ws status-code ""))
 (^CompletableFuture [^WebSocket ws status-code reason] (.sendClose ws status-code reason)))


;; ==============================  DOCSTRINGS  ==============================


(add-docstring #'build-websocket
  "Builds a new [WebSocket](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/WebSocket.html) with the provided options.

  - `uri` - The URI to connect to
  - `listener-fns` - see [[websocket-listener]]
  - `builder-opts` - see [[websocket-builder]]")

(add-docstring #'build-websocket-async
  "Same as [[build-websocket]], but returns a `CompletableFuture<WebSocket>` instead of `WebSocket`.")

(add-docstring #'websocket-listener
  "Builds a [Websocket.Listener](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/WebSocket.Listener.html).

  listener-fns is a map of keyword to function:

  - `:on-binary` - See [onBinary](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/WebSocket.Listener.html#onBinary%28java.net.http.WebSocket,java.nio.ByteBuffer,boolean%29). The `data` argument is converted from a `ByteBuffer` to a byte array before being passed to the function.
  - `:on-close` - See [onClose](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/WebSocket.Listener.html#onClose%28java.net.http.WebSocket,int,java.lang.String%29)
  - `:on-error` - See [onError](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/WebSocket.Listener.html#onError%28java.net.http.WebSocket,java.lang.Throwable%29)
  - `:on-open` - See [onOpen](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/WebSocket.Listener.html#onOpen%28java.net.http.WebSocket%29)
  - `:on-ping` - See [onPing](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/WebSocket.Listener.html#onPing%28java.net.http.WebSocket,java.nio.ByteBuffer%29). The `data` argument is converted from a `ByteBuffer` to a byte array before being passed to the function.
  - `:on-pong` - See [onPong](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/WebSocket.Listener.html#onPong%28java.net.http.WebSocket,java.nio.ByteBuffer%29). The `data` argument is converted from a `ByteBuffer` to a byte array before being passed to the function.
  - `:on-text` - See [onText](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/WebSocket.Listener.html#onText%28java.net.http.WebSocket,java.lang.CharSequence,boolean%29). The `data` argument is converted from a `CharSequence` to a string before being passed to the function.")

(add-docstring #'websocket-builder
  "Builds a [Websocket.Builder](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/WebSocket.Builder.html).

  `opts` is a map containing one of the following keywords:

  - `:client` - the [HttpClient](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/HttpClient.html) to create the connect with, defaults to [[default-client]]
  - `:connect-timeout` - connection timeout in milliseconds or a `java.time.Duration`
  - `:headers` - the HTTP headers, a map where keys are strings and values are strings or a list of strings
  - `:subprotocols` - a string sequence of subprotocols to use in order of preferences")

(add-docstring #'send
  "Synchronously send data over the websocket and then returns the websocket. `payload` can be any of:

   - a string - sent with [sendText](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/WebSocket.html#sendText%28java.lang.CharSequence,boolean%29)
   - a byte array - converted into a ByteBuffer and sent with [sendBinary](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/WebSocket.html#sendBinary%28java.nio.ByteBuffer,boolean%29)
   - a ByteBufer - sent with [sendBinary](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/WebSocket.html#sendBinary%28java.nio.ByteBuffer,boolean%29)

   Any other argument type will be coerced to a string with `str` and sent with [sendText](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/WebSocket.html#sendText%28java.lang.CharSequence,boolean%29).

  `last?` is a boolean that indicates whether this invocation completes the message. Defaults to `true`.")

(add-docstring #'send-async
  "Same as [[send]], but returns a `CompletableFuture<WebSocket>` instead of `WebSocket`.")

(add-docstring #'close
  "Closes the output of the websocket with the supplied status code and reason. If not provided, `status-code` defaults to 1000 (normal closure) and `reason` defaults to empty string.

  Equivalent to [sendClose](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/WebSocket.html#sendClose%28int,java.lang.String%29).")
