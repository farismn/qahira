(ns qahira.routes.user
  (:require
   [qahira.edge.db :as qhr.edge.db]
   [qahira.edge.token-encoder :as qhr.edge.token-enc]
   [qahira.handlers.user :as qhr.handlers.user]
   [ring.util.http-status :as http.sta]))

(def ^:private WrappedAuthToken
  [:map [:auth qhr.edge.token-enc/Token]])

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

(defn anon-routes
  [_ctx]
  ["/api/user"
   {:name ::anon
    :post {:responses  {http.sta/ok {:body [:map [:token WrappedAuthToken]]}}
           :parameters {:body [:map [:user Registree]]}
           :handler    qhr.handlers.user/register-handler}}])

(defn target-routes
  [_ctx]
  ["/api/user/:username"
   {:name       ::target
    :parameters {:path [:map [:username qhr.edge.db/Username]]}
    :post       {:responses      {http.sta/ok {:body [:map [:token WrappedAuthToken]]}}
                 #_#_:middleware [jeum.mdw.auth/basic-authentication-middleware
                                  jeum.mdw.auth/authenticated-middleware
                                  jeum.mdw.auth/permission-path-username-middleware]
                 :handler        qhr.handlers.user/login-handler}
    :put        {:responses      {http.sta/ok {:body [:map [:token WrappedAuthToken]]}}
                 :parameters     {:body [:map [:user PasswordUpdatee]]}
                 #_#_:middleware [[jeum.mdw.auth/jeumpa-token-authentication-middleware :auth]
                                  jeum.mdw.auth/authenticated-middleware
                                  jeum.mdw.auth/permission-path-username-middleware]
                 :handler        qhr.handlers.user/update-password-handler}
    :delete     {#_#_:middleware [[jeum.mdw.auth/jeumpa-token-authentication-middleware :auth]
                                  jeum.mdw.auth/authenticated-middleware
                                  jeum.mdw.auth/permission-path-username-middleware]
                 :handler        qhr.handlers.user/delete-handler}}])

(defn restore-routes
  [_ctx]
  ["/api/user/:username/restore"
   {:name       ::restore
    :parameters {:path [:map [:username qhr.edge.db/Username]]}
    :post       {:responses      {http.sta/ok {:body [:map [:token WrappedAuthToken]]}}
                 #_#_:middleware [[jeum.mdw.auth/jeumpa-token-authentication-middleware :restore]
                                  jeum.mdw.auth/authenticated-middleware
                                  jeum.mdw.auth/permission-path-username-middleware]
                 :handler        qhr.handlers.user/restore-handler}}])

(defn reset-routes
  [_ctx]
  ["/api/user/:username/reset"
   {:name       ::reset
    :parameters {:path [:map [:username qhr.edge.db/Username]]}
    :put        {:responses      {http.sta/ok {:body [:map [:token WrappedAuthToken]]}}
                 :parameters     {:body [:map [:user PasswordResetee]]}
                 #_#_:middleware [[jeum.mdw.auth/jeumpa-token-authentication-middleware :reset]
                                  jeum.mdw.auth/authenticated-middleware
                                  jeum.mdw.auth/permission-path-username-middleware]
                 :handler        qhr.handlers.user/reset-password-handler}}])
