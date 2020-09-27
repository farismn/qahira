(ns qahira.routes.user
  (:require
   [qahira.edge.db :as qhr.edge.db]
   [qahira.edge.token-encoder :as qhr.edge.tokenenc]
   [qahira.handlers.user :as qhr.handlers.user]
   [qahira.middleware.auth :as qhr.mdw.auth]
   [ring.util.http-status :as http.sta]))

(def ^:private WrappedAuthToken
  [:map [:auth qhr.edge.tokenenc/Token]])

(def Registree
  [:map
   [:username qhr.edge.db/Username]
   [:password qhr.edge.db/Password]])

(def PasswordUpdatee
  [:map
   [:new-password qhr.edge.db/Password]
   [:old-password string?]])

(def PasswordResetee
  [:map
   [:new-password qhr.edge.db/Password]])

(def routes
  [["/api/user"
    {:name ::anon
     :post {:responses  {http.sta/ok {:body [:map [:token WrappedAuthToken]]}}
            :parameters {:body [:map [:user Registree]]}
            :handler    qhr.handlers.user/register-handler}}]
   ["/api/user/:username"
    {:name       ::target
     :parameters {:path [:map [:username qhr.edge.db/Username]]}
     :post       {:responses  {http.sta/ok {:body [:map [:token WrappedAuthToken]]}}
                  :middleware [qhr.mdw.auth/basic-authentication-middleware
                               qhr.mdw.auth/authenticated-middleware
                               qhr.mdw.auth/permission-path-username-middleware]
                  :handler    qhr.handlers.user/login-handler}
     :put        {:responses  {http.sta/ok {:body [:map [:token WrappedAuthToken]]}}
                  :parameters {:body [:map [:user PasswordUpdatee]]}
                  :middleware [[qhr.mdw.auth/qahira-token-authentication-middleware :auth]
                               qhr.mdw.auth/authenticated-middleware
                               qhr.mdw.auth/permission-path-username-middleware]
                  :handler    qhr.handlers.user/update-password-handler}
     :delete     {:middleware [[qhr.mdw.auth/qahira-token-authentication-middleware :auth]
                               qhr.mdw.auth/authenticated-middleware
                               qhr.mdw.auth/permission-path-username-middleware]
                  :handler    qhr.handlers.user/delete-handler}}]
   ["/api/user/:username/restore"
    {:name       ::restore
     :parameters {:path [:map [:username qhr.edge.db/Username]]}
     :post       {:responses  {http.sta/ok {:body [:map [:token WrappedAuthToken]]}}
                  :middleware [[qhr.mdw.auth/qahira-token-authentication-middleware :restore]
                               qhr.mdw.auth/authenticated-middleware
                               qhr.mdw.auth/permission-path-username-middleware]
                  :handler    qhr.handlers.user/restore-handler}}]
   ["/api/user/:username/reset"
    {:name       ::reset
     :parameters {:path [:map [:username qhr.edge.db/Username]]}
     :put        {:responses  {http.sta/ok {:body [:map [:token WrappedAuthToken]]}}
                  :parameters {:body [:map [:user PasswordResetee]]}
                  :middleware [[qhr.mdw.auth/qahira-token-authentication-middleware :reset]
                               qhr.mdw.auth/authenticated-middleware
                               qhr.mdw.auth/permission-path-username-middleware]
                  :handler    qhr.handlers.user/reset-password-handler}}]])
