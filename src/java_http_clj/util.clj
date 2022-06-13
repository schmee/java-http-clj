(ns ^:no-doc java-http-clj.util
  (:import [java.time Duration]
           [java.util.function Function]))

(set! *warn-on-reflection* true)

(defmacro add-docstring [var docstring]
  `(alter-meta! ~var #(assoc % :doc ~docstring)))

(defn convert-timeout [t]
  (if (integer? t)
    (Duration/ofMillis t)
    t))

(defmacro clj-fn->function ^Function [f]
  `(reify Function
    (~'apply [_# x#] (~f x#))))

(def shorthands [:get :head :post :put :delete :connect :options :trace :patch])
