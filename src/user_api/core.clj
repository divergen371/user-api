(ns user-api.core
  (:gen-class)
  (:require
   [buddy.auth.backends :as backends]
   [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
   [buddy.hashers :as hashers]
   [buddy.sign.jwt :as jwt]
   [cheshire.core :as json]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [environ.core :refer [env]]
   [muuntaja.core :as m]
   [reitit.coercion.malli :as malli]
   [reitit.ring :as ring]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [ring.adapter.jetty :as jetty]
   [ring.middleware.params :as params]))

;; 設定
(def config
  {:server {:port (Integer/parseInt (or (env :server-port) "3000"))}
   :api {:version "1.0"}
   :jwt {:secret (or (env :jwt-secret)
                     (throw (ex-info "JWT_SECRETが設定されていません。" {})))}})

(defonce server (atom nil))
(defonce users (atom {}))

;; レスポンス形式を統一するためのヘルパー関数
(defn format-response [response]
  (if (= 204 (:status response))
    response  ; 204レスポンスはボディを持たない
    (update response :body json/generate-string)))

;; バリデーション関数
(defn validate-user-create [user]
  (try
    (and (string? (:name user))
         (not (str/blank? (:name user)))
         (string? (:email user))
         (not (str/blank? (:email user)))
         (re-matches #"[^@]+@[^@]+\.[^@]+" (:email user))
         (string? (:password user))
         (>= (count (:password user)) 8))
    (catch Exception _
      false)))

(defn validate-user-update [user]
  (try
    (and (or (not (contains? user :name))
             (and (string? (:name user))
                  (not (str/blank? (:name user)))))
         (or (not (contains? user :email))
             (and (string? (:email user))
                  (not (str/blank? (:email user)))
                  (re-matches #"[^@]+@[^@]+\.[^@]+" (:email user))))
         (or (not (contains? user :password))
             (and (string? (:password user))
                  (>= (count (:password user)) 8))))
    (catch Exception _
      false)))

(defn email-exists? [email]
  (some #(= email (:email %)) (vals @users)))

;; ログインハンドラー
(defn login-handler [{:keys [body-params]}]
  (format-response
   (let [{:keys [email password]} body-params]
     (if-let [user (some #(when (= email (:email %)) %) (vals @users))]
       (if (hashers/check password (:password user))
         (let [token (jwt/sign {:user-id (:id user)
                                :email (:email user)
                                :role (:role user "user")
                                :exp (-> (java.time.Instant/now)
                                         (.getEpochSecond)
                                         (+ 3600))}
                               (get-in config [:jwt :secret])
                               {:alg :hs256})]
           {:status 200
            :body {:token token}})
         {:status 401
          :body {:error "Invalid credentials"}})
       {:status 401
        :body {:error "Invalid credentials"}}))))

;; ユーザー作成ハンドラー
(defn create-user [{:keys [body-params] :as request}]
  (format-response
   (try
     (let [user body-params]
       (cond
         (not (validate-user-create user))
         {:status 400
          :body {:error "Invalid user data"}}

         (email-exists? (:email user))
         {:status 400
          :body {:error "Email already exists"}}

         :else
         (let [id (str (java.util.UUID/randomUUID))
               hashed-password (hashers/derive (:password user))
               new-user (-> user
                            (assoc :id id)
                            (assoc :password hashed-password)
                            (assoc :role (get user :role "user")))]
           (swap! users assoc id new-user)
           {:status 201
            :body (dissoc new-user :password)})))
     (catch Exception e
       (log/error "Error creating user:" (.getMessage e))
       {:status 500
        :body {:error "Internal server error"}}))))

;; ユーザー取得ハンドラー（一覧）
(defn get-users [{:keys [identity]}]
  (format-response
   {:status 200
    :body (into {} (map (fn [[k v]] [k (dissoc v :password)]) @users))}))

;; ユーザー取得ハンドラー（個別）
(defn get-user [{{:keys [id]} :path-params}]
  (format-response
   (if-let [user (get @users id)]
     {:status 200
      :body (dissoc user :password)}
     {:status 404
      :body {:error "User not found"}})))

;; ユーザー更新ハンドラー
(defn update-user [{{:keys [id]} :path-params user-update :body-params :keys [identity]}]
  (format-response
   (if-let [existing-user (get @users id)]
     (let [updated-user (merge existing-user (select-keys user-update [:name :email :password]))]
       (cond
         (not (validate-user-update user-update))
         {:status 400
          :body {:error "Invalid user data"}}

         (and (:email user-update)
              (not= (:email existing-user) (:email user-update))
              (email-exists? (:email user-update)))
         {:status 400
          :body {:error "Email already exists"}}

         :else
         (let [final-user (if (:password user-update)
                            (assoc updated-user :password (hashers/derive (:password user-update)))
                            updated-user)]
           (swap! users assoc id final-user)
           {:status 200
            :body (dissoc final-user :password)})))
     {:status 404
      :body {:error "User not found"}})))

;; ユーザー削除ハンドラー
(defn delete-user [{{:keys [id]} :path-params}]
  (if (get @users id)
    (do
      (swap! users dissoc id)
      {:status 204})
    (format-response
     {:status 404
      :body {:error "User not found"}})))

;; 認証バックエンド設定
(def auth-backend
  (backends/jws
   {:secret (get-in config [:jwt :secret])
    :options {:alg :hs256}
    :token-name "Bearer"
    :on-error (fn [_ _]
                (format-response
                 {:status 401
                  :body {:error "Unauthorized"}}))
    :unauthorized-handler (fn [_ _]
                            (format-response
                             {:status 401
                              :body {:error "Unauthorized"}}))}))

;; 管理者権限チェック
(defn wrap-admin-required [handler]
  (fn [request]
    (if-let [identity (:identity request)]
      (if (= "admin" (:role identity))
        (handler request)
        (format-response
         {:status 403
          :body {:error "Forbidden"}}))
      (format-response
       {:status 401
        :body {:error "Unauthorized"}}))))

;; 文字列ハンドラー
(defn string-handler [_]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body "Welcome to User Management API!"})

;; アプリケーションルーティングとミドルウェア
(def router-config
  [["/login" {:post {:handler login-handler}}]
   ["/users" {:get {:handler (wrap-admin-required get-users)}
              :post {:handler create-user}}]
   ["/users/:id" {:get {:handler get-user}
                  :put {:handler update-user}
                  :delete {:handler (wrap-admin-required delete-user)}}]
   ["/" {:get {:handler string-handler}}]])

(def app-config
  {:data {:muuntaja m/instance
          :middleware [params/wrap-params
                       muuntaja/format-middleware]}})

(defonce app
  (-> (ring/ring-handler
       (ring/router router-config app-config))
      (wrap-authentication auth-backend)
      (wrap-authorization auth-backend)))

;; サーバー起動
(defn start []
  (when-not @server
    (let [port (get-in config [:server :port])]
      (log/info "Starting server on port" port)
      (try
        (reset! server (jetty/run-jetty app {:port port :join? false}))
        (catch Exception e
          (log/error "Failed to start server:" (.getMessage e)))))))

;; サーバー停止
(defn stop []
  (when @server
    (try
      (log/info "Stopping server")
      (.stop @server)
      (reset! server nil)
      (catch Exception e
        (log/error "Error stopping server:" (.getMessage e))))))

;; メインエントリーポイント
(defn -main [& _args]
  (start)
  (log/info "Server started on port" (get-in config [:server :port])))
