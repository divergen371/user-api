(defproject user-api "0.1.0-SNAPSHOT"
  :description "User Management API"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [javax.servlet/servlet-api "2.5"]
                 [ring "1.9.4"]
                 [metosin/reitit "0.5.15"]
                 [metosin/muuntaja "0.6.8"]
                 [org.clojure/tools.logging "1.2.4"]]
  :plugins [[lein-cloverage "1.2.4"]]
  :profiles {:dev {:dependencies [[ring/ring-mock "0.4.0"]
                                  [ring/ring-devel "1.9.4"]
                                  [cloverage "1.2.2"]]
                   :extra-paths ["test"]}
             :cljstyle {:dependencies [[mvxcvi/cljstyle "0.17.642" :exclusions [org.clojure/clojure]]]}}
  :aliases {"cljstyle" ["with-profile" "+cljstyle" "run" "-m" "cljstyle.main"]}
  :repl-options {:init-ns user-api.core})
