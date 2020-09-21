(ns qahira.edge.qahira-client
  (:refer-clojure :exclude [if-let])
  (:require
   [clojure.string :as str]
   [orchid.components.http-client :as orc.c.httpc]
   [orchid.edge.logger :refer [debug]]
   [qahira.edge.token-encoder :as qhr.edge.token-enc]
   [qahira.routes.meta :as qhr.routes.meta]
   [qahira.routes.token :as qhr.routes.token]
   [qahira.routes.user :as qhr.routes.user]
   [reitit.core :as reit]
   [taoensso.encore :as e :refer [if-let catching]]))

(defprotocol QahiraClient
  (request-condition [qahira-client])
  (request-token [qahira-client kind username request])
  (register-user [qahira-client request])
  (login-user [qahira-client username request])
  (delete-user [qahira-client username request])
  (update-user-password [qahira-client username request])
  (restore-user [qahira-client username request])
  (reset-user-password [qahira-client username request]))

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
  (let [content-type (:content-type config)]
    (e/assoc-when request
                  :accept content-type
                  :as content-type
                  :content-type (when (some? (:form-params request))
                                  content-type))))

(defn- wrap-basic-auth
  [request]
  (if-let [basic-auth (:basic-auth request)
           username   (:username basic-auth)
           password   (:password basic-auth)]
    (assoc request :basic-auth [username password])
    request))

(defn- assoc-authz-token
  [request scheme token]
  (assoc-in request [:headers "authorization"] (str scheme " " token)))

(defn- wrap-qahira-token-auth
  [request token-encoder kind]
  (if-let [kw    (keyword (str/join "-" ["qahira" (name kind) "token" "auth"]))
           auth  (get request kw)
           token (if (map? auth)
                   (qhr.edge.token-enc/make-token token-encoder auth kind)
                   auth)]
    (-> request
        (dissoc kw)
        (assoc-authz-token "QahiraToken" token))
    request))

(defn- run-request
  [qahira-client http-method target request]
  (let [uri                (build-uri (:ring-router qahira-client) target)
        api-token-encoder  (:api-token-encoder qahira-client)
        auth-token-encoder (:auth-token-encoder qahira-client)
        new-request        (-> request
                               (wrap-body (:config qahira-client))
                               (wrap-basic-auth)
                               (wrap-qahira-token-auth auth-token-encoder :auth)
                               (wrap-qahira-token-auth auth-token-encoder :reset)
                               (wrap-qahira-token-auth auth-token-encoder :restore)
                               (wrap-qahira-token-auth api-token-encoder :api))
        logger             (:logger qahira-client)]
    (debug logger ::run-request {:uri uri :request new-request})
    (catching
      (orc.c.httpc/request qahira-client http-method uri new-request)
      err
      (do (debug (:logger qahira-client) ::run-request err)
          (e/error-data err)))))

(extend-protocol QahiraClient
  orchid.components.http_client.HttpClient
  (request-condition [qahira-client]
    (run-request qahira-client :get {:name ::qhr.routes.meta/anon} {}))
  (request-token [qahira-client kind username request]
    (let [target {:name   ::qhr.routes.token/target
                  :params {:kind kind :username username}}]
      (run-request qahira-client :get target request)))
  (register-user [qahira-client request]
    (let [target {:name ::qhr.routes.user/anon}]
      (run-request qahira-client :post target request)))
  (login-user [qahira-client username request]
    (let [target {:name   ::qhr.routes.user/target
                  :params {:username username}}]
      (run-request qahira-client :post target request)))
  (delete-user [qahira-client username request]
    (let [target {:name   ::qhr.routes.user/target
                  :params {:username username}}]
      (run-request qahira-client :delete target request)))
  (update-user-password [qahira-client username request]
    (let [target {:name   ::qhr.routes.user/target
                  :params {:username username}}]
      (run-request qahira-client :put target request)))
  (restore-user [qahira-client username request]
    (let [target {:name   ::qhr.routes.user/restore
                  :params {:username username}}]
      (run-request qahira-client :post target request)))
  (reset-user-password [qahira-client username request]
    (let [target {:name   ::qhr.routes.user/reset
                  :params {:username username}}]
      (run-request qahira-client :put target request))))
