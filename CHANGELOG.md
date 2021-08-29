## 0.4.3

- Move specs from `java-http-clj.core` and `java-http-clj.websocket` to `java-http-clj.specs` to make java-http-clj easier to use with [Babashka](https://github.com/babashka/babashka/).

## 0.4.2

- Use `ifn?` for callbacks instead of `fn?`

## 0.4.1

- Add type hints to functions that return Java objects
- Set Clojure dependency as `:scope "provided"`
- Small bugfixes for WebSocket and specs

## 0.4.0

- Add WebSocket API

## 0.3.1

- Add specs for all API functions

## 0.3.0

- Fix NPE for requests without bodies

## 0.2.0

- Rename a few methods
  - `make-client` -> `build-client`
  - `make-request` -> `build-request`
  - `resp->ring` -> `response->map`

## 0.1.0

- Initial release
