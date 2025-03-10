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
   ;; muuntaja を mu にエイリアス
   [malli.core :as m]
   [muuntaja.core :as mu]
   ;; malli を m にエイリアス
   [reitit.coercion.malli]
   [reitit.ring :as ring]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.swagger :as swagger]
   [reitit.swagger-ui :as swagger-ui]
   [ring.adapter.jetty :as jetty]
   [ring.middleware.params :as params]
   [user-api.schemas :as schemas]))

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
  (m/validate schemas/user-create-schema user))

(defn validate-user-update [user]
  (m/validate schemas/user-update-schema user))

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
       (log/error e "Error creating user")
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
(defn update-user [{{:keys [id]} :path-params
                    :keys [body-params identity]}]
  (format-response
   (if-let [existing-user (get @users id)]
     (let [updated-user (merge existing-user (select-keys body-params [:name :email :password]))]
       (cond
         (not (validate-user-update body-params))
         {:status 400
          :body {:error "Invalid user data"}}

         (and (:email body-params)
              (not= (:email existing-user) (:email body-params))
              (email-exists? (:email body-params)))
         {:status 400
          :body {:error "Email already exists"}}

         :else
         (let [final-user (if (:password body-params)
                            (assoc updated-user :password (hashers/derive (:password body-params)))
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
  [["/swagger.json" {:get {:no-doc true
                           :swagger {:info {:title "User Management API"
                                            :description "API documentation for User Management"
                                            :version "1.0"}}
                           :handler swagger/create-swagger-handler}}]
   ["/swagger-ui/*" {:get {:no-doc true
                           :handler (swagger-ui/create-swagger-ui-handler {:url "/swagger.json"})}}]
   ["/login" {:post {:handler login-handler
                     :swagger {:tags ["Authentication"]
                               :summary "ユーザーログイン"
                               :parameters {:body schemas/login-schema}
                               :responses {200 {:description "ログイン成功"
                                                :schema schemas/login-response-schema}
                                           401 {:description "認証失敗"}}}}}]
   ["/users" {:get {:handler (wrap-admin-required get-users)
                    :swagger {:tags ["Users"]
                              :summary "ユーザー一覧取得"
                              :responses {200 {:description "成功"
                                               :schema {:type "object"
                                                        :additionalProperties schemas/user-response-schema}}}
                              :security [{:Bearer []}]}}
              :post {:handler create-user
                     :swagger {:tags ["Users"]
                               :summary "新規ユーザー作成"
                               :parameters {:body schemas/user-create-schema}
                               :responses {201 {:description "ユーザー作成成功"
                                                :schema schemas/user-response-schema}
                                           400 {:description "入力データ不正"}
                                           500 {:description "内部サーバーエラー"}}
                               :security [{:Bearer []}]}}}]
   ["/users/:id" {:get {:handler get-user
                        :swagger {:tags ["Users"]
                                  :summary "ユーザー詳細取得"
                                  :parameters {:path {:id string?}}
                                  :responses {200 {:description "成功"
                                                   :schema schemas/user-response-schema}
                                              404 {:description "ユーザーが見つからない"}}
                                  :security [{:Bearer []}]}}
                  :put {:handler update-user
                        :swagger {:tags ["Users"]
                                  :summary "ユーザー情報更新"
                                  :parameters {:path {:id string?}
                                               :body schemas/user-update-schema}
                                  :responses {200 {:description "更新成功"
                                                   :schema schemas/user-response-schema}
                                              400 {:description "入力データ不正"}
                                              404 {:description "ユーザーが見つからない"}}
                                  :security [{:Bearer []}]}}
                  :delete {:handler (wrap-admin-required delete-user)
                           :swagger {:tags ["Users"]
                                     :summary "ユーザー削除"
                                     :parameters {:path {:id string?}}
                                     :responses {204 {:description "削除成功"}
                                                 404 {:description "ユーザーが見つからない"}}
                                     :security [{:Bearer []}]}}}]
   ["/" {:get {:handler string-handler
               :swagger {:tags ["Home"]
                         :summary "ウェルカムメッセージ"
                         :responses {200 {:description "成功"
                                          :schema [:map [:body string?]]}}}}}]])

(def app-config
  {:data {:muuntaja mu/instance
          :middleware [muuntaja/format-middleware
                       params/wrap-params
                       swagger/swagger-feature]
          :coercion reitit.coercion.malli/coercion
          :swagger {:info {:title "User Management API"
                           :description "API documentation for User Management"
                           :version "1.0"}
                    :securityDefinitions {:Bearer {:type "apiKey"
                                                   :name "Authorization"
                                                   :in "header"}}}}})

(defonce app
  (-> (ring/ring-handler
       (ring/router router-config app-config)
       (ring/create-default-handler))
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
          (log/error e "Failed to start server"))))))

;; サーバー停止
(defn stop []
  (when @server
    (try
      (log/info "Stopping server")
      (.stop @server)
      (reset! server nil)
      (catch Exception e
        (log/error e "Error stopping server")))))

;; メインエントリーポイント
(defn -main [& _args]
  (start)
  (log/info "Server started on port" (get-in config [:server :port])))
