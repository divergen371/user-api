(ns user-api.core-test
  (:require
   [buddy.hashers :as hashers]
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.test :refer :all]
   [user-api.core :as core]))

(def base-url "http://localhost:3000")
(def admin-token (atom nil))
(def user-token (atom nil))

(defn parse-body [response]
  (json/parse-string (:body response) true))

(defn with-auth-header [token]
  {"Authorization" (str "Bearer " token)})

;; テストデータ
(def test-admin
  {:name "Admin User"
   :email "admin@example.com"
   :password "adminpass123"
   :role "admin"})

(def test-user
  {:name "Test User"
   :email "test@example.com"
   :password "testpass123"})

(defn setup-test-data []
  ;; サーバーを起動
  (core/start)

  ;; 管理者ユーザーを直接作成（認証をバイパス）
  (let [admin-id (str (java.util.UUID/randomUUID))
        hashed-password (buddy.hashers/derive (:password test-admin))
        admin-user (-> test-admin
                       (assoc :id admin-id)
                       (assoc :password hashed-password))]
    (swap! core/users assoc admin-id admin-user))

  ;; 管理者でログイン
  (let [response (client/post (str base-url "/login")
                              {:body (json/generate-string (select-keys test-admin [:email :password]))
                               :content-type :json
                               :throw-exceptions false})
        body (parse-body response)]
    (reset! admin-token (:token body)))

  ;; 通常ユーザーを直接作成
  (let [user-id (str (java.util.UUID/randomUUID))
        hashed-password (buddy.hashers/derive (:password test-user))
        normal-user (-> test-user
                        (assoc :id user-id)
                        (assoc :password hashed-password))]
    (swap! core/users assoc user-id normal-user))

  ;; 通常ユーザーでログイン
  (let [response (client/post (str base-url "/login")
                              {:body (json/generate-string (select-keys test-user [:email :password]))
                               :content-type :json
                               :throw-exceptions false})
        body (parse-body response)]
    (reset! user-token (:token body))))

(defn cleanup-test-data []
  (reset! core/users {})
  (core/stop))

(use-fixtures :each (fn [f]
                      (setup-test-data)
                      (f)
                      (cleanup-test-data)))

;; テストケース

(deftest test-user-creation
  (testing "正常なユーザー作成"
    (let [new-user {:name "New User"
                    :email "new@example.com"
                    :password "newpass123"}
          response (client/post (str base-url "/users")
                                {:body (json/generate-string new-user)
                                 :content-type :json
                                 :throw-exceptions false})
          body (parse-body response)]
      (is (= 201 (:status response)))
      (is (string? (:id body)))
      (is (= (:name new-user) (:name body)))
      (is (= (:email new-user) (:email body)))
      (is (nil? (:password body)))))

  (testing "重複メールアドレスによるユーザー作成の失敗"
    (let [duplicate-user (assoc test-user :name "Different Name")
          response (client/post (str base-url "/users")
                                {:body (json/generate-string duplicate-user)
                                 :content-type :json
                                 :throw-exceptions false})
          body (parse-body response)]
      (is (= 400 (:status response)))
      (is (= "Email already exists" (:error body))))))

(deftest test-user-authentication
  (testing "正常なログイン"
    (let [response (client/post (str base-url "/login")
                                {:body (json/generate-string
                                        {:email (:email test-user)
                                         :password (:password test-user)})
                                 :content-type :json
                                 :throw-exceptions false})
          body (parse-body response)]
      (is (= 200 (:status response)))
      (is (string? (:token body)))))

  (testing "無効な認証情報によるログインの失敗"
    (let [response (client/post (str base-url "/login")
                                {:body (json/generate-string
                                        {:email (:email test-user)
                                         :password "wrongpassword"})
                                 :content-type :json
                                 :throw-exceptions false})
          body (parse-body response)]
      (is (= 401 (:status response)))
      (is (= "Invalid credentials" (:error body))))))

(deftest test-user-retrieval
  (testing "管理者による全ユーザー取得"
    (let [response (client/get (str base-url "/users")
                               {:headers (with-auth-header @admin-token)
                                :throw-exceptions false})
          body (parse-body response)]
      (is (= 200 (:status response)))
      (is (map? body))
      (is (>= (count body) 2))))

  (testing "権限のないユーザーによる全ユーザー取得の失敗"
    (let [response (client/get (str base-url "/users")
                               {:headers (with-auth-header @user-token)
                                :throw-exceptions false})
          body (parse-body response)]
      (is (= 403 (:status response)))
      (is (= "Forbidden" (:error body))))))

(deftest test-user-update
  (testing "ユーザー情報の更新"
    (let [user-id (some (fn [[k v]]
                          (when (= (:email v) (:email test-user)) k))
                        @core/users)
          update-data {:name "Updated Name"}
          response (client/put (str base-url "/users/" user-id)
                               {:body (json/generate-string update-data)
                                :content-type :json
                                :headers (with-auth-header @user-token)
                                :throw-exceptions false})
          body (parse-body response)]
      (is (= 200 (:status response)))
      (is (= "Updated Name" (:name body)))
      (is (= (:email test-user) (:email body))))))

(deftest test-user-deletion
  (testing "管理者によるユーザー削除"
    (let [user-to-delete-id (str (java.util.UUID/randomUUID))
          user-to-delete (-> test-user
                             (assoc :id user-to-delete-id)
                             (assoc :password (buddy.hashers/derive (:password test-user))))
          _ (swap! core/users assoc user-to-delete-id user-to-delete)
          response (client/delete (str base-url "/users/" user-to-delete-id)
                                  {:headers (with-auth-header @admin-token)
                                   :throw-exceptions false})]
      (is (= 204 (:status response)))
      (is (nil? (get @core/users user-to-delete-id)))))

  (testing "権限のないユーザーによるユーザー削除の失敗"
    (let [admin-id (some (fn [[k v]]
                           (when (= (:email v) (:email test-admin)) k))
                         @core/users)
          response (client/delete (str base-url "/users/" admin-id)
                                  {:headers (with-auth-header @user-token)
                                   :throw-exceptions false})
          body (parse-body response)]
      (is (= 403 (:status response)))
      (is (= "Forbidden" (:error body))))))
