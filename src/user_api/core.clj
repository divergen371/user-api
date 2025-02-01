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


(defonce server (atom nil))

(def users (atom {}))


(defn create-user
  [{user :body-params}]
  (let [id (str (java.util.UUID/randomUUID))
        new-user (assoc user :id id)]  ; Fixed user creation
    (swap! users assoc id new-user)    ; Correctly update the users atom
    {:status 201
     :body new-user}))                 ; Return the created user

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
      ["/"
       ["" string-handler]
       ["users" {:get get-users
                 :post create-user}]
       ["users/:id" {:get get-user
                     :put update-user
                     :delete delete-user}]]
      {:data {:muuntaja m/instance
              :middleware [muuntaja/format-middleware]}})))


(defn start
  []
  (when-not @server  ; サーバーが起動していない場合のみ起動
    (reset! server (jetty/run-jetty app {:port 3000 :join? false}))))


(defn stop
  []
  (when @server
    (.stop @server)
    (reset! server nil)))


(start)

(stop)
@server
