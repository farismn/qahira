(ns qahira.edge.qahira-client
  (:require
   [orchid.components.http-client :as orc.c.httpc]
   [qahira.routes.meta :as qhr.routes.meta]
   [qahira.routes.token :as qhr.routes.token]
   [qahira.routes.user :as qhr.routes.user]
   [reitit.core :as reit]
   [taoensso.encore :as e]))

(defprotocol QahiraClient
  (request-condition [qahira-client])
  (request-token [qahira-client kind username params])
  (register-user [qahira-client params])
  (login-user [qahira-client username params])
  (delete-user [qahira-client username params])
  (update-user-password [qahira-client username params])
  (restore-handler [qahira-client username params])
  (reset-user-password [qahira-client username params]))

(defn- build-uri
  [ring-router path-params]
  (let [router  (:router ring-router)
        ctor    (partial reit/match-by-name router (:name path-params))
        matched (if-let [params (not-empty (:params path-params))]
                  (ctor params)
                  (ctor))]
    (:path matched)))

(defn- wrap-body
  [request config]
  (let [content-type (:content-type config :json)]
    (e/assoc-when request
                  :accept content-type
                  :as content-type
                  :content-type (when (some? (:form-params request))
                                  content-type))))

(defn- wrap-basic-auth
  [request]
  (e/if-let [basic-auth (:basic-auth request)
             username   (:username basic-auth)
             password   (:password basic-auth)]
    (assoc request :basic-auth [username password])
    request))

(defn- assoc-authz-token
  [request scheme token]
  (assoc-in request [:headers "authorization"] (str scheme " " token)))

(defn- wrap-qahira-token-auth
  [request]
  (if-let [qahira-token-auth (:qahira-token-auth request)]
    (-> request
        (dissoc :qahira-token-auth)
        (assoc-authz-token "QahiraToken" qahira-token-auth))
    request))

(defn- run-request
  [qahira-client http-method target request]
  (let [uri         (build-uri (:ring-router qahira-client) target)
        new-request (-> request
                        (wrap-body (:config qahira-client))
                        (wrap-basic-auth)
                        (wrap-qahira-token-auth))]
    (orc.c.httpc/request qahira-client http-method uri new-request)))

(defn- inject-basic-auth
  [request params]
  (e/assoc-when request :basic-auth (:auth params)))

(defn- inject-qahira-token-auth
  [request params]
  (e/assoc-when request :qahira-token-auth (:auth params)))

(defn- inject-form-params
  [request params]
  (e/assoc-when request :form-params (:form-params params)))

(extend-protocol QahiraClient
  orchid.components.http_client.HttpClient
  (request-condition [qahira-client]
    (run-request qahira-client :get {:name ::qhr.routes.meta/anon} {}))
  (request-token [qahira-client kind username params]
    (let [target  {:name   ::qhr.routes.token/target
                   :params {:kind kind :username username}}
          request (inject-qahira-token-auth {} params)]
      (run-request qahira-client :get target request)))
  (register-user [qahira-client params]
    (let [target  {:name ::qhr.routes.user/anon}
          request (inject-form-params {} params)]
      (run-request qahira-client :post target request)))
  (login-user [qahira-client username params]
    (let [target  {:name   ::qhr.routes.user/target
                   :params {:username username}}
          request (inject-basic-auth {} params)]
      (run-request qahira-client :post target request)))
  (delete-user [qahira-client username params]
    (let [target  {:name   ::qhr.routes.user/target
                   :params {:username username}}
          request (inject-qahira-token-auth {} params)]
      (run-request qahira-client :delete target request)))
  (update-user-password [qahira-client username params]
    (let [target  {:name   ::qhr.routes.user/target
                   :params {:username username}}
          request (-> {}
                      (inject-qahira-token-auth params)
                      (inject-form-params params))]
      (run-request qahira-client :put target request)))
  (restore-handler [qahira-client username params]
    (let [target  {:name   ::qhr.routes.user/restore
                   :params {:username username}}
          request (inject-qahira-token-auth {} params)]
      (run-request qahira-client :post target request)))
  (reset-user-password [qahira-client username params]
    (let [target  {:name   ::qhr.routes.user/reset
                   :params {:username username}}
          request (-> {}
                      (inject-qahira-token-auth params)
                      (inject-form-params params))]
      (run-request qahira-client :put target request))))
