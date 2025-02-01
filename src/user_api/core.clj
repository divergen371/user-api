(ns user-api.core
  (:gen-class)
  (:require
    [muuntaja.core :as m]
    [reitit.ring :as ring]
    [retit.ring.middleware.muuntaja :as muuntaja]
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
