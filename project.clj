(defproject user-api "0.1.0-SNAPSHOT"
  :description "User Management API"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}

  :dependencies [[org.clojure/clojure "1.12.0"]
                 [ring/ring-core "1.9.4"]
                 [ring/ring-jetty-adapter "1.9.4"]
                 [metosin/reitit "0.6.0"]
                 [metosin/reitit-swagger "0.5.18"]
                 [metosin/reitit-swagger-ui "0.5.18"]
                 [metosin/malli "0.17.0"]
                 [buddy "2.0.0"]
                 [cheshire "5.10.1"]
                 [environ "1.2.0"]
                 [compojure "1.6.2"]
                 [clj-http "3.12.3"]
                 [org.clojure/tools.logging "1.2.4"]
                 [ch.qos.logback/logback-classic "1.4.11"]]
  :plugins [[lein-cloverage "1.2.4"]
            [lein-environ "1.2.0"]]

  :repositories [["central" "https://repo1.maven.org/maven2/"]
                 ["clojars" "https://repo.clojars.org/"]]

  :profiles {:dev {:dependencies [[ring/ring-mock "0.4.0"]
                                  [org.clojure/tools.namespace "1.4.4"]]
                   :env {:jwt-secret "dev-secret"}
                   :extra-paths ["test"]}
             :test {:dependencies [[ring/ring-mock "0.4.0"]]
                    :extra-paths ["test"]}
             :cljstyle {:dependencies [[mvxcvi/cljstyle "0.17.642"
                                        :exclusions [org.clojure/clojure]]]}}

  :aliases {"cljstyle" ["with-profile" "+cljstyle" "run" "-m" "cljstyle.main"]}

  ;; Dependency overrides updated to match new versions or removed if unnecessary
  :dependency-overrides [[cheshire "5.10.1"]]
  :main user-api.core

  :repl-options {:init-ns user-api.core})
