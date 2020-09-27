(ns qahira.handlers.user
  (:require
   [qahira.edge.db :as qhr.edge.db]
   [qahira.edge.token-encoder :as qhr.edge.tokenenc]
   [qahira.util.request :as qhr.u.req]
   [ring.util.http-response :as http.res]))

(defn- render-auth-token
  [request attrs]
  (let [encoder (qhr.u.req/get-service! request :auth-token-encoder)
        token   (qhr.edge.tokenenc/make-token encoder attrs :auth)]
    {:token {:auth token}}))

(defn register-handler
  [{:keys [parameters] :as request}]
  (let [db    (qhr.u.req/get-service! request :database)
        attrs (-> parameters :body :user)
        user  (qhr.edge.db/register-user! db attrs)]
    (http.res/ok (render-auth-token request user))))

(defn login-handler
  [{:keys [parameters] :as request}]
  (http.res/ok (render-auth-token request (:path parameters))))

(defn delete-handler
  [{:keys [parameters] :as request}]
  (let [db       (qhr.u.req/get-service! request :database)
        username (-> parameters :path :username)
        result   (qhr.edge.db/delete-user! db username)]
    (when-not (:database/happened? result)
      (http.res/bad-request! {:error {:category :incorrect
                                      :message  "can't delete the user"}}))
    (http.res/no-content)))

(defn update-password-handler
  [{:keys [parameters] :as request}]
  (let [db           (qhr.u.req/get-service! request :database)
        username     (-> parameters :path :username)
        new-password (-> parameters :body :user :new-password)
        old-password (-> parameters :body :user :old-password)
        user         (qhr.edge.db/update-user-password!
                       db
                       username
                       old-password
                       new-password)]
    (when (nil? user)
      (http.res/bad-request!
        {:error {:category :incorrect
                 :message  "can't update the user's password"}}))
    (http.res/ok (render-auth-token request user))))

(defn restore-handler
  [{:keys [parameters] :as request}]
  (let [db       (qhr.u.req/get-service! request :database)
        username (-> parameters :path :username)
        user     (qhr.edge.db/restore-user! db username)]
    (when (nil? user)
      (http.res/bad-request! {:error {:category :incorrect
                                      :message  "can't restore the user"}}))
    (http.res/ok (render-auth-token request user))))

(defn reset-password-handler
  [{:keys [parameters] :as request}]
  (let [db           (qhr.u.req/get-service! request :database)
        username     (-> parameters :path :username)
        new-password (-> parameters :body :user :new-password)
        user         (qhr.edge.db/reset-user-password!
                       db
                       username
                       new-password)]
    (when (nil? user)
      (http.res/bad-request!
        {:error {:category :incorrect
                 :message  "can't reset the user's password"}}))
    (http.res/ok (render-auth-token request user))))
