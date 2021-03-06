(defproject finagle-clojure/http "0.3.1-SNAPSHOT"
  :description "A light wrapper around Finagle HTTP for Clojure"
  :url "https://github.com/twitter/finagle-clojure"
  :license {:name "Apache License, Version 2.0"
            :url "https://www.apache.org/licenses/LICENSE-2.0"}
  :scm {:name "git" :url "http://github.com/finagle/finagle-clojure"}
  :plugins [[lein-midje "3.1.3"]]
  :profiles {:test {:dependencies [[midje "1.6.3" :exclusions [org.clojure/clojure]]]}
             :dev [:test {:dependencies [[org.clojure/clojure "1.6.0"]]}]
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0-alpha5"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}}
  :dependencies [[finagle-clojure/core "0.3.1-SNAPSHOT"]
                 [com.twitter/finagle-http_2.10 "6.24.0"]])
