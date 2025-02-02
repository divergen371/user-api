(ns user-api.core-test
  (:require
   [clojure.test :refer :all]
   [ring.mock.request :as mock]
   [user-api.core :refer :all]))

;; テストの前にユーザーデータをリセットする
(defn reset-users-fixture
  [f]
  (reset! users {})
  (f))

(use-fixtures :each reset-users-fixture)

(deftest test-string-handler
  (testing "GET /"
    (let [response (string-handler (mock/request :get "/"))]
      (is (= 200 (:status response)))
      (is (= "text/html" (get-in response [:headers "Content-Type"])))
      (is (clojure.string/includes? (:body response) "Welcome to User Management API")))))

(deftest test-create-user
  (testing "POST /users with valid data"
    (let [request (-> (mock/request :post "/users")
                      (mock/json-body {:name "Alice" :email "alice@example.com"}))
          response (create-user {:body-params {:name "Alice" :email "alice@example.com"}})]
      (is (= 201 (:status response)))
      (is (contains? (:body response) :id))
      (is (= "Alice" (get-in (:body response) [:name])))
      (is (= "alice@example.com" (get-in (:body response) [:email])))))

  (testing "POST /users with invalid data"
    (let [response (create-user {:body-params {:name "Bob" :email "invalid-email"}})]
      (is (= 400 (:status response)))
      (is (= {:error "Invalid user data"} (:body response))))))

(deftest test-get-users
  (testing "GET /users with no users"
    (let [response (get-users {})]
      (is (= 200 (:status response)))
      (is (= {} (:body response)))))

  (testing "GET /users with users"
    (swap! users assoc "1" {:id "1" :name "Alice" :email "alice@example.com"})
    (let [response (get-users {})]
      (is (= 200 (:status response)))
      (is (= {"1" {:id "1" :name "Alice" :email "alice@example.com"}} (:body response))))))

(deftest test-get-user
  (testing "GET /users/:id with existing user"
    (swap! users assoc "1" {:id "1" :name "Alice" :email "alice@example.com"})
    (let [response (get-user {:path-params {:id "1"}})]
      (is (= 200 (:status response)))
      (is (= {:id "1" :name "Alice" :email "alice@example.com"} (:body response)))))

  (testing "GET /users/:id with non-existing user"
    (let [response (get-user {:path-params {:id "2"}})]
      (is (= 404 (:status response)))
      (is (= {:error "User not found"} (:body response))))))

(deftest test-update-user
  (testing "PUT /users/:id with existing user"
    (swap! users assoc "1" {:id "1" :name "Alice" :email "alice@example.com"})
    (let [response (update-user {:path-params {:id "1"}
                                 :body-params {:name "Alice Updated" :email "alice.updated@example.com"}})]
      (is (= 200 (:status response)))
      (is (= {:id "1" :name "Alice Updated" :email "alice.updated@example.com"} (:body response)))))

  (testing "PUT /users/:id with non-existing user"
    (let [response (update-user {:path-params {:id "2"}
                                 :body-params {:name "Bob" :email "bob@example.com"}})]
      (is (= 404 (:status response)))
      (is (= {:error "User not found"} (:body response))))))

(deftest test-delete-user
  (testing "DELETE /users/:id with existing user"
    (swap! users assoc "1" {:id "1" :name "Alice" :email "alice@example.com"})
    (let [response (delete-user {:path-params {:id "1"}})]
      (is (= 204 (:status response)))
      (is (nil? (get @users "1")))))

  (testing "DELETE /users/:id with non-existing user"
    (let [response (delete-user {:path-params {:id "2"}})]
      (is (= 404 (:status response)))
      (is (= {:error "User not found"} (:body response))))))

(deftest test-start-stop-server
  (testing "Start and stop server"
    (start)
    (is (not (nil? @server)))
    (stop)
    (is (nil? @server))))

(deftest test-create-user-edge-cases
  (testing "POST /users with empty name"
    (let [response (create-user {:body-params {:name "" :email "alice@example.com"}})]
      (is (= 400 (:status response)))
      (is (= {:error "Invalid user data"} (:body response)))))

  (testing "POST /users with empty email"
    (let [response (create-user {:body-params {:name "Alice" :email ""}})]
      (is (= 400 (:status response)))
      (is (= {:error "Invalid user data"} (:body response)))))

  (testing "POST /users with invalid email format"
    (let [response (create-user {:body-params {:name "Alice" :email "invalid-email"}})]
      (is (= 400 (:status response)))
      (is (= {:error "Invalid user data"} (:body response)))))

  (testing "POST /users with duplicate email"
    (swap! users assoc "1" {:id "1" :name "Alice" :email "alice@example.com"})
    (let [response (create-user {:body-params {:name "Bob" :email "alice@example.com"}})]
      (is (= 400 (:status response)))
      (is (= {:error "Email already exists"} (:body response))))))

(deftest test-update-user-edge-cases
  (testing "PUT /users/:id with non-existing user"
    (let [response (update-user {:path-params {:id "999"}
                                 :body-params {:name "Bob" :email "bob@example.com"}})]
      (is (= 404 (:status response)))
      (is (= {:error "User not found"} (:body response)))))

  (testing "PUT /users/:id with invalid email format"
    (swap! users assoc "1" {:id "1" :name "Alice" :email "alice@example.com"})
    (let [response (update-user {:path-params {:id "1"}
                                 :body-params {:name "Alice" :email "invalid-email"}})]
      (is (= 400 (:status response)))
      (is (= {:error "Invalid user data"} (:body response))))))

(deftest test-delete-user-edge-cases
  (testing "DELETE /users/:id with non-existing user"
    (let [response (delete-user {:path-params {:id "999"}})]
      (is (= 404 (:status response)))
      (is (= {:error "User not found"} (:body response))))))

(deftest test-server-edge-cases
  (testing "Start server on already used port"
    (start)
    (let [response (start)]
      (is (nil? response))
      (is (not (nil? @server))))
    (stop)))
