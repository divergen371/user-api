(ns user-api.core
  (:require
    [ring.adapter.jetty :as jetty]))


(defn handler
  [request]
  {:starus 200
   :headers {"Content-Type" "text/html"}
   :body "Hello!! I'm a Clojure API"})


(defn start
  []
  (jetty/run-jetty handler {:port 3000 :join? false}))


(def server (start))
(.stop server)
