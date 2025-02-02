(defproject user-api "0.1.0-SNAPSHOT"
  :description "User Management API"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}

  :dependencies [[org.clojure/clojure "1.12.0"]
                 [ring "1.9.4"]
                 [metosin/muuntaja "0.6.11"]
                 [metosin/reitit "0.7.2" :exclusions [cheshire jsonista]]
                 [org.clojure/tools.logging "1.3.0"]
                 [ring/ring-jetty-adapter "1.9.4"]
                 [buddy/buddy-auth "3.0.323" :exclusions [cheshire]]
                 [buddy/buddy-sign "3.6.1-359" :exclusions [cheshire]]
                 [buddy/buddy-hashers "2.0.167"]
                 [cheshire "5.12.0"]  ; Updated version
                 [clj-http "3.12.3"]
                 [com.fasterxml.jackson.core/jackson-core "2.15.3"]
                 [com.fasterxml.jackson.core/jackson-databind "2.15.3"]
                 [environ "1.2.0"]]

  :plugins [[lein-cloverage "1.2.4"]
            [lein-environ "1.2.0"]]

  :repositories [["central" "https://repo1.maven.org/maven2/"]
                 ["clojars" "https://repo.clojars.org/"]]

  :profiles {:dev {:dependencies [[ring/ring-mock "0.4.0"]]
                   :env {:jwt-secret "dev-secret"}
                   :test {:dependencies [[ring/ring-mock "0.4.0"]]}}
             :extra-paths ["test"]
             :cljstyle {:dependencies [[mvxcvi/cljstyle "0.17.642" :exclusions [org.clojure/clojure]]]}}

  :aliases {"cljstyle" ["with-profile" "+cljstyle" "run" "-m" "cljstyle.main"]}

  ;; Remove or update dependency-overrides section
  :dependency-overrides [[cheshire "5.12.0"]
                         [com.fasterxml.jackson.core/jackson-core "2.15.3"]
                         [com.fasterxml.jackson.core/jackson-annotations "2.15.3"]
                         [com.fasterxml.jackson.core/jackson-databind "2.15.3"]]

  :main user-api.core

  :repl-options {:init-ns user-api.core})
