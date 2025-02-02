(ns user-api.core
  (:gen-class)
  (:require
    [clojure.string :as str]
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
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body "Welcome to User Management API!

Available Endpoints:

GET    /users      - List all users
POST   /users      - Create a new user
        Required body: {\"name\": \"string\", \"email\": \"string\"}
GET    /users/:id  - Get user by ID
PUT    /users/:id  - Update user by ID
        Required body: {\"name\": \"string\", \"email\": \"string\"}
DELETE /users/:id  - Delete user by ID

Example:
curl -X POST -H \"Content-Type: application/json\" -d '{\"name\":\"Alice\",\"email\":\"alice@example.com\"}' http://localhost:3000/users"})


;; バリデーション関数
(defn validate-user
  [user]
  (and (string? (:name user))
       (not (str/blank? (:name user)))
       (string? (:email user))
       (not (str/blank? (:email user)))
       (re-matches #"[^@]+@[^@]+\.[^@]+" (:email user))))


(defonce server (atom nil))

(def users (atom {}))


(defn email-exists?
  [email]
  (some #(= email (:email %)) (vals @users)))


(defn create-user
  [{user :body-params}]
  (try
    (cond
      (email-exists? (:email user))
      {:status 400
       :body {:error "Email already exists"}}

      (validate-user user)
      (let [id (str (java.util.UUID/randomUUID))
            new-user (assoc user :id id)]
        (swap! users assoc id new-user)
        (log/info "Created user:" id)
        {:status 201
         :body new-user})

      :else
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
  (if-let [existing-user (get @users id)]
    (let [updated-user (merge existing-user user-update)]
      (cond
        (not (validate-user updated-user))
        {:status 400
         :body {:error "Invalid user data"}}

        (and (:email user-update)
             (some #(and (not= id (:id %))
                         (= (:email user-update) (:email %)))
                   (vals @users)))
        {:status 400
         :body {:error "Email already exists"}}

        :else
        (do
          (swap! users assoc id updated-user)
          {:status 200
           :body updated-user})))
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
