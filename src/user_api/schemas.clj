(ns user-api.schemas
  (:require
   [malli.core :as m]))

;; ユーザー作成用スキーマ
(def user-create-schema
  [:map
   [:name [:string {:error/message "Name is required and must be a string"}]]
   [:email [:string {:error/message "Valid email is required"}]]
   [:password [:string {:min 8
                        :error/message "Password is required and must be at least 8 characters"}]]
   [:role {:optional true} :string]])

;; ユーザー更新用スキーマ
(def user-update-schema
  [:map
   [:name {:optional true} [:string]]
   [:email {:optional true} [:string]]
   [:password {:optional true} [:string {:min 8}]]])

;; ユーザー情報レスポンス用スキーマ
(def user-response-schema
  [:map
   [:id :string]
   [:name :string]
   [:email :string]
   [:role :string]])

;; ログインリクエストスキーマ
(def login-schema
  [:map
   [:email [:string {:error/message "Valid email is required"}]]
   [:password [:string {:error/message "Password is required"}]]])

;; ログインレスポンススキーマ
(def login-response-schema
  [:map
   [:token :string]])
