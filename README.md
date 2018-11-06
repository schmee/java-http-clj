# java-http-clj

Today [clj-http](https://github.com/dakrone/clj-http) is the de-facto standard HTTP client for Clojure. It is an excellent library, but it is also a large dependency since it is based on [Apache HTTP](https://hc.apache.org/httpcomponents-client-ga/). It also doesn't support HTTP/2 (yet).

Enter java-http-clj. It is inspired by both clj-http and [Ring](https://github.com/ring-clojure/ring/blob/master/SPEC), and built on the `java.net.http` package shipped with JDK starting with Java 11. As such it comes with _no_ extra dependencies if you're already using Java 11. It also fully supports HTTP/2 out of the box.

java-http-clj is alpha quality. Expect better testing and specs soon!

## Installation

`[java-http-clj "0.3.0"]`

java-http-clj requires Java 11 or later.

## Documentation

- [API documentation](https://schmee.github.io/java-http-clj/)

- [java.net.http Javadoc](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/package-summary.html)

## Examples

First, require the library:

`(:require [java-http-clj.core :as http])`

The most common HTTP methods (GET, POST, PUT, HEAD, DELETE) have a function of the same name. This function takes three arguments (where the last two are optional): a URL, request parameters and a map of options (described below).

- GET requests

```
;; If you don't specify any options defaults are provided
(http/get "http://www.google.com")

;; With request options
(http/get "http://www.google.com" {:headers {"Accept" "application/json"
                                             "Accept-Encoding ["gzip" "deflate"]}
                                   :timeout 2000})
```

- POST/PUT requests
```
(http/post "http://www.google.com" {:body "{\"foo\":\"bar\"}"})

;; The body can be a string, an input stream or a byte array
(http/post "http://www.google.com" {:body (.getBytes "{\"foo\":\"bar\"}")})

;; Return the response body as a byte array
(http/post "http://www.google.com" {:body "{\"foo\":\"bar\"}"} {:as :byte-array})
```

- Async requests

To make an async request, use the `send-async` function (currently there is no sugar for async requests):

```
;; Returns a future
(http/send-async {:uri "http://www.google.com" :method :get})

;; Takes an optional callback and exception handler
(http/send-async {:uri "http://www.google.com" :method :get})
                 (fn [r] (do-something-with-response r))
                 (fn [e] (println "oops, something blew up"))

```

### Advanced

All functions take a map of options as the last argument. The map accepts three arguments: `:client`, `:as` and `:raw?`.

The `:client` option allows you to send the request with a custom client (by default, java-http-clj reuses a default client for each request). This is useful if you want to configure advanced options on the client such as SSL contexts or cookie stores.

The `:as` option converts the response body to the chosen format. It supports `:string`, `:byte-array` and `:input-stream` which correspond to their respective Java classes.

If the `:raw?` option is true, the Ring conversion of the response is skipped and instead the underlying `HttpResponse` object is returned directly.

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

Released under the MIT License: http://www.opensource.org/licenses/mit-license.php
