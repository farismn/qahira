(ns qahira.routes.user
  (:require
   [orchid.components.reitit-ring :as orc.c.reitring]
   [qahira.edge.db :as qhr.edge.db]
   [qahira.edge.token-encoder :as qhr.edge.tokenenc]
   [qahira.handlers.user :as qhr.handlers.user]
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

(defn anon-routes
  [ctx]
  ["/api/user"
   {:name ::anon
    :post {:responses  {http.sta/ok {:body [:map [:token WrappedAuthToken]]}}
           :parameters {:body [:map [:user Registree]]}
           :handler    (qhr.handlers.user/register-handler ctx)}}])

(defn target-routes
  [ctx]
  (letfn [(extract-middlewares [middlewares]
            (orc.c.reitring/extract-middlewares ctx middlewares))]
    ["/api/user/:username"
     {:name       ::target
      :parameters {:path [:map [:username qhr.edge.db/Username]]}
      :post       {:responses  {http.sta/ok {:body [:map [:token WrappedAuthToken]]}}
                   :middleware (extract-middlewares
                                 [:basic-authentication-middleware
                                  :authenticated-middleware
                                  :permission-path-username-middleware])
                   :handler    (qhr.handlers.user/login-handler ctx)}
      :put        {:responses  {http.sta/ok {:body [:map [:token WrappedAuthToken]]}}
                   :parameters {:body [:map [:user PasswordUpdatee]]}
                   :middleware (extract-middlewares
                                 [[:qahira-token-authentication-middleware :auth]
                                  :authenticated-middleware
                                  :permission-path-username-middleware])
                   :handler    (qhr.handlers.user/update-password-handler ctx)}
      :delete     {:middleware (extract-middlewares
                                 [[:qahira-token-authentication-middleware :auth]
                                  :authenticated-middleware
                                  :permission-path-username-middleware])
                   :handler    (qhr.handlers.user/delete-handler ctx)}}]))

(defn restore-routes
  [ctx]
  (letfn [(extract-middlewares [middlewares]
            (orc.c.reitring/extract-middlewares ctx middlewares))]
    ["/api/user/:username/restore"
     {:name       ::restore
      :parameters {:path [:map [:username qhr.edge.db/Username]]}
      :post       {:responses  {http.sta/ok {:body [:map [:token WrappedAuthToken]]}}
                   :middleware (extract-middlewares
                                 [[:qahira-token-authentication-middleware :restore]
                                  :authenticated-middleware
                                  :permission-path-username-middleware])
                   :handler    (qhr.handlers.user/restore-handler ctx)}}]))

(defn reset-routes
  [ctx]
  (letfn [(extract-middlewares [middlewares]
            (orc.c.reitring/extract-middlewares ctx middlewares))]
    ["/api/user/:username/reset"
     {:name       ::reset
      :parameters {:path [:map [:username qhr.edge.db/Username]]}
      :put        {:responses  {http.sta/ok {:body [:map [:token WrappedAuthToken]]}}
                   :parameters {:body [:map [:user PasswordResetee]]}
                   :middleware (extract-middlewares
                                 [[:qahira-token-authentication-middleware :reset]
                                  :authenticated-middleware
                                  :permission-path-username-middleware])
                   :handler    (qhr.handlers.user/reset-password-handler ctx)}}]))
