# java-http-clj

Today [clj-http](https://github.com/dakrone/clj-http) is the de-facto standard HTTP client for Clojure. It is an excellent library, but it is also a large dependency since it is based on [Apache HTTP](https://hc.apache.org/httpcomponents-client-ga/). It also doesn't support HTTP/2 (yet).

Enter java-http-clj. It is inspired by both clj-http and [Ring](https://github.com/ring-clojure/ring/blob/master/SPEC) and built on `java.net.http` that ships with with Java 11. As such it comes with _no_ extra dependencies if you're already using Java 11, and it fully supports HTTP/2 out of the box.

## Installation

`[java-http-clj "0.4.0"]`

java-http-clj requires Clojure 1.9+ and Java 11+.

## Documentation

- [API documentation](https://schmee.github.io/java-http-clj/)

- [Specs](https://github.com/schmee/java-http-clj/blob/master/src/java_http_clj/core.clj#L203)

- [java.net.http Javadoc](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/package-summary.html)

## Examples

First, require the library:

```clj
(:require [java-http-clj.core :as http])
```

The most common HTTP methods (GET, POST, PUT, HEAD, DELETE) have a function of the same name. This function takes three arguments (where the last two are optional): a URL, a request and an options map (refer to [send](https://schmee.github.io/java-http-clj/java-http-clj.core.html#var-send) docs for details).

- GET requests

```clj
;; If you don't specify any options defaults are provided
(http/get "http://www.google.com")

;; With request options
(http/get "http://www.google.com" {:headers {"Accept" "application/json"
                                             "Accept-Encoding" ["gzip" "deflate"]}
                                   :timeout 2000})
```

- POST/PUT requests

```clj
(http/post "http://www.google.com" {:body "{\"foo\":\"bar\"}"})

;; The request body can be a string, an input stream or a byte array...
(http/post "http://www.google.com" {:body (.getBytes "{\"foo\":\"bar\"}")})

;; ...and you can choose the response body format with the `:as` option
(http/post "http://www.google.com" {:body "{\"foo\":\"bar\"}"} {:as :byte-array})
```

- Async requests

To make an async request, use the `send-async` function (currently there is no sugar for async requests):

```clj
;; Returns a java.util.concurrent.CompletableFuture
(http/send-async {:uri "http://www.google.com" :method :get})

;; Takes an optional callback and exception handler
(http/send-async {:uri "http://www.google.com" :method :get})
                 (fn [r] (do-something-with-response r))
                 (fn [e] (println "oops, something blew up"))

```

- Options

All request functions take an `opts` map for customization (refer to [send](https://schmee.github.io/java-http-clj/java-http-clj.core.html#var-send) docs for details).

```clj
;; Provide a custom client
(def client (build-client {:follow-redirects :always}))
(http/send {:uri "http://www.google.com" :method :get} {:client client})

;; Skip map conversion and return the java.net.http.HttpResponse object
user=> (http/send {:uri "http://www.google.com" :method :get} {:raw? true})
object[jdk.internal.net.http.HttpResponseImpl "0x88edd90" "(GET http://www.google.com) 200"]
```

## WebSockets

java-http-clj also includes a WebSocket API. The WebSocket API in java.net.http is based around CompletableFuture and functional interfaces which interop poorly with Clojure. Hence, java-http-clj presents a simplified, synchronous API that covers the basic use-cases.

The API consists of three methods: `build-websocket`, `send` and `close`. The Java API requires you to maintain a request counter for each invocation, but java-http-clj manages this for you automatically.

```clj
;; Create a websocket
(def ws
  (build-websocket
    "ws://localhost:8080/ws"
    {:on-text (fn [ws string last?]
                (println "Received some text!" string))
     :on-binary (fn [ws byte-array last?]
                  (println "Got some bytes!" (vec byte-array)))
     :on-error (fn [ws throwable]
                 (println "Uh oh!" (.getMessage throwable)))}))

;; Send some data (strings, ByteBuffers, byte arrays or something that can be coerced to a string)
   and return the websocket
(-> ws
    (send "abc")
    (send (byte-array [1 2 3]))
    (send 123))

;; Close the output of websocket when you are done
(close ws)
```

There is also `build-websocket-async` and `send-async` that return a `CompletableFuture`. These functions are probably best used together with some asynchronous framework that allows you to compose `CompletableFutures` (for example [Manifold](https://github.com/ztellman/manifold)).

## Design

### Goals

- Lightweight: zero dependencies and a small codebase
- Performant: minimal overhead compared to direct interop
- Flexible: make everyday use-cases easy and advanced use-cases possible

### Non-goals

- Hide away the Java
  - Common use-cases should not require interop, but more advanced uses might require dipping in to the underlying Java API.
- Completeness
  - Not everything will be provided through Clojure. For example, most of the classes in `HttpResponse.BodySubscribers` are not available directly through Clojure, since Clojure has its own stream-processing facilities that are more idiomatic than the Java equivalents.
- Input/output conversion
  - java-http-clj does not have any built-in facilities for formats such as JSON, Transit or YAML. It provides the choice of "raw data" through the `:as` option but leaves it up to the user to parse the data as they see fit

If any of this is deal-breaker for you, I recommend clj-http which is a much more fully-featured HTTP client.

## How to contribute

Bug fixes are always welcome, just open a PR! If you want to implement a new feature or make substantial changes to existing ones, please open an issue first and outline your ideas.

## License

Copyright © 2018-2019 John Schmidt

Released under the MIT License: http://www.opensource.org/licenses/mit-license.php
