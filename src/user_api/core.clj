(ns user-api.core
  (:gen-class)
  (:require
    [clojure.tools.logging :as log]
    [muuntaja.core :as m]
    [reitit.ring :as ring]
    [reitit.ring.middleware.muuntaja :as muuntaja]
    [ring.adapter.jetty :as jetty]
    [ring.middleware.params :as params]))


;; 設定の外部化
(def config
  {:server {:port 3000}
   :api {:version "1.0"}})


(defn string-handler
  [_]
  {:status 200   ; スペルミス修正
   :headers {"Content-Type" "text/html"}
   :body "Hello!! I'm a Clojure API"})


;; バリデーション関数
(defn validate-user
  [user]
  (and (:name user)
       (:email user)
       (re-matches #"[^@]+@[^@]+\.[^@]+" (:email user))))


(defonce server (atom nil))

(def users (atom {}))


(defn create-user
  [{user :body-params}]
  (try
    (if (validate-user user)
      (let [id (str (java.util.UUID/randomUUID))
            new-user (assoc user :id id)]
        (swap! users assoc id new-user)
        (log/info "Created user:" id)
        {:status 201
         :body new-user})
      {:status 400
       :body {:error "Invalid user data"}})
    (catch Exception e
      (log/error "Error creating user:" (.getMessage e))
      {:status 500
       :body {:error "Internal server error"}})))


(defn get-users
  [_]
  {:status 200
   :body @users})


(defn get-user
  [{{:keys [id]} :path-params}]
  (if-let [user (get @users id)]
    {:status 200
     :body user}
    {:status 404
     :body {:error "User not found"}}))


(defn update-user
  [{{:keys [id]} :path-params user-update :body-params}]
  (if (get @users id)
    (let [updated-user (assoc user-update :id id)]
      (swap! users assoc id updated-user)
      {:status 200
       :body updated-user})
    {:status 404
     :body {:error "User not found"}}))


(defn delete-user
  [{{:keys [id]} :path-params}]
  (if (get @users id)
    (do
      (swap! users dissoc id)
      {:status 204})
    {:status 404
     :body {:error "User not found"}}))


(def app
  (ring/ring-handler
    (ring/router
      [["/users" {:get get-users
                  :post create-user}]
       ["/users/:id" {:get get-user
                      :put update-user
                      :delete delete-user}]
       ["/" {:get string-handler}]]
      {:data {:muuntaja m/instance
              :middleware [muuntaja/format-middleware
                           params/wrap-params]}})
    (ring/create-default-handler)))


(defn start
  []
  (when-not @server
    (let [port (get-in config [:server :port])]
      (log/info "Starting server on port" port)
      (try
        (reset! server (jetty/run-jetty app {:port port :join? false}))
        (catch Exception e
          (log/error "Failed to start server:" (.getMessage e)))))))


(defn stop
  []
  (when @server
    (try
      (log/info "Stopping server")
      (.stop @server)
      (reset! server nil)
      (catch Exception e
        (log/error "Error stopping server:" (.getMessage e))))))


(start)

(stop)
@server
