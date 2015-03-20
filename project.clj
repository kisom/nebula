(defproject nebula "0.1.0-alpha"
  :description "Object-capable content-addressable data store"
  :url "https://lambda.kyleisom.net/notebook/nebula.html"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [compojure "1.3.1"]
                 [honeysql "0.5.1"]
                 [org.xerial/sqlite-jdbc "3.7.15-M1"]
                 [org.clojure/java.jdbc "0.3.0"]
                 [prismatic/schema "0.4.0"]
                 [com.taoensso/timbre "3.4.0"]
                 [cheshire "5.4.0"]
                 [byte-streams "0.2.0-alpha8"]
                 [hiccup "1.0.5"]
                 [pandect "0.5.1"]
                 [ring/ring-defaults "0.1.2"]]
  :plugins [[lein-ring "0.8.13"]
            [lein-marginalia "0.8.0"]
            [lein-kibit "0.0.8"]
            [codox "0.8.11"]]
  :ring {:handler nebula.handler/app}
  :license {:name "MIT License"
            :url "http://opensource.org/licenses/MIT"
            :distribution :repo}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring-mock "0.1.5"]]}})
