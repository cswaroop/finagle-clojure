(ns finagle-clojure.futures-test
  (:refer-clojure :exclude [await ensure for map])
  (:import [com.twitter.util NoFuture])
  (:require [finagle-clojure.futures :refer :all]
            [finagle-clojure.scala :as scala]
            [finagle-clojure.duration :refer [->Duration]]
            [midje.sweet :refer :all]))

;; set *warn-on-reflection* after loading midje to skip its reflection warnings
(set! *warn-on-reflection* true)

(fact "flatmap*"
  (await (flatmap* (value "hi") (fn [s] (value (.toUpperCase ^String s))))) => "HI"
  (await (flatmap* (value "hi") (scala/Function [s] (value (.toUpperCase ^String s))))) => "HI")

(fact "flatmap"
  (await (flatmap (value "hi") [s] (value (.toUpperCase ^String s)))) => "HI")

(fact "nested flatmap"
  (await (flatmap (value "hi") [s] (flatmap (value "bob") [t] (value (str s " " t)))))  => "hi bob")

(fact "map*"
  (await (map* (value "hi") (fn [s] (.toUpperCase ^String s)))) => "HI"
  (await (map* (value "hi") (scala/Function [s] (.toUpperCase ^String s)))) => "HI")

(fact "map"
  (await (map (value "hi") [s] (.toUpperCase ^String s))) => "HI")

(fact "for"
  (await
    (for [a (value 1)
          b (value 2)]
      (value (+ a b))))
  => 3)

(fact "for chain"
  (await
    (for [a (value 1)
          b (value (inc a))]
      (value (+ a b))))
  => 3)

(fact "exception"
  (await (exception (Exception.))) => (throws Exception))

(facts "rescue*"
  (await (rescue* (exception (Exception.)) (constantly (value 1)))) => 1
  (await (rescue* (exception (Exception.)) (scala/Function [_] (value 1)))) => 1)

(facts "rescue"
  (await (rescue (exception (Exception.)) [^Throwable t] (value 1))) => 1
  (await (rescue (exception (Exception.)) [t] (value 1))) => 1
  ;; this throws because the domain of the Function passed to rescue doesn't include Throwable
  (await (rescue (exception (Exception.)) [^String s] (value 1))) => (throws Exception))

(facts "handle*"
  (await (handle* (exception (Exception.)) (constantly 1))) => 1
  (await (handle* (exception (Exception.)) (scala/Function [_] 1))) => 1)

(facts "handle"
  (await (handle (exception (Exception.)) [^Throwable t] 1)) => 1
  (await (handle (exception (Exception.)) [t] 1)) => 1
  ;; this throws because the domain of the Function passed to handle doesn't include Throwable
  (await (handle (exception (Exception.)) [^String s] 1)) => (throws Exception))

(fact "collect"
  (await (collect [(value 1) (value 2)])) => [1 2]
  (await (collect [(value 1) (value nil)])) => [1 nil]
  (await (collect '())) => []
  (collect nil) => (throws AssertionError))

(facts "ensure*"
  (await (ensure* (value 1) (constantly 1))) => 1
  (await (ensure* (value 1) (scala/Function0 1))) => 1)

(facts "ensure"
  (await (ensure (value 1) 1)) => 1
  (await (ensure (exception (Exception.)) 1)) => (throws Exception))

(facts "defined?"
  (defined? (value 1)) => true
  (defined? (exception (Exception.))) => true
  (defined? (NoFuture.)) => false)

(facts "select"
  (await (select (NoFuture.) (value 1))) => 1
  (await (select (value 2) (NoFuture.))) => 2)

(let [success-fn-a (fn [v] (value :success-a))
      failure-fn-a (fn [t] (value :failure-a))
      success-fn-b (fn [v] (value :success-b))
      failure-fn-b (fn [t] (value :failure-b))]
  (facts "transform"
    (-> (value true) (transform success-fn-a) await) => :success-a
    (-> (value true) (transform success-fn-a failure-fn-a) await) => :success-a
    (-> (value true) (transform success-fn-a failure-fn-a) (transform success-fn-b) await) => :success-b
    (-> (value true) (transform success-fn-a failure-fn-a) (transform success-fn-b failure-fn-b) await) => :success-b
    (-> (exception (Exception.)) (transform success-fn-a) await) => (throws Exception)
    (-> (exception (Exception.)) (transform success-fn-a failure-fn-a) await) => :failure-a
    (-> (exception (Exception.)) (transform success-fn-a (fn [t] (exception t))) (transform success-fn-b failure-fn-b) await) => :failure-b
    (-> (exception (Exception.)) (transform success-fn-a failure-fn-a) (transform success-fn-b failure-fn-b) await) => :success-b))

(let [marker (atom 0)
      success-fn-a (fn [v] (value :success-a))
      success-fn-b (fn [v] (value (swap! marker inc)))]
  (facts "transform short circuits"
    (-> (exception (Exception.)) (transform success-fn-a) (transform success-fn-b) await) => (throws Exception)
    @marker => 0
    (-> (value true) (transform success-fn-a) (transform success-fn-b) await) => 1
    @marker => 1))

(facts "match-class"
  (-> (IllegalArgumentException.) (match-class Exception :expected)) => :expected
  (-> (IllegalArgumentException.) (match-class IllegalArgumentException :expected Exception :unexpected)) => :expected
  (-> (IllegalArgumentException.) (match-class ClassNotFoundException :unexpected IllegalArgumentException :expected Exception :unexpected)) => :expected)

(facts "within"
  (-> (NoFuture.) (within* (->Duration 1 :ms)) await) => (throws Exception)
  (-> (NoFuture.) (within  1 :ms) await) => (throws Exception))

(let [pipeline #(-> %1
                    (within 1 :ms)
                    (handle [t] -10)
                    (map [v] (%2 v))
                    (handle [t] :second-handle)
                    await)]
  (facts "compose future operations"
    (pipeline (value 1) inc) => 2
    (pipeline (exception (Exception.)) inc) => -9
    (pipeline (NoFuture.) inc) => -9
    (pipeline (value 1) #(throw (Exception.))) => :second-handle))

(let [counter (atom 0)
      pipeline (fn [f] (-> f (on-success [_] (swap! counter inc)) (on-failure [_] (swap! counter dec)) (handle [_] nil) await) @counter)]
  (against-background [(before :facts (reset! counter 0))]
    (fact "on-success"
      (pipeline (value 1)) => 1)
    (fact "on-failure"
      (pipeline (exception (Exception.))) => -1)))
