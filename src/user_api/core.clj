(ns user-api.core
  (:gen-class)
  (:require
    [muuntaja.core :as m]
    [reitit.ring :as ring]
    [reitit.ring.middleware.muuntaja :as muuntaja]
    [ring.adapter.jetty :as jetty]))


(defn string-handler
  [_]
  {:starus 200
   :headers {"Content-Type" "text/html"}
   :body "Hello!! I'm a Clojure API"})


(def app
  (ring/ring-handler
    (ring/router
      ["/"
       ["" string-handler]]
      {:data {:muuntaja m/instance
              :middleware [muuntaja/format-middleware]}})))


(defn start
  []
  (jetty/run-jetty app {:port 3000 :join? false}))


(def server (start))
(.stop server)
