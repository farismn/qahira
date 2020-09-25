(ns qahira.handlers.user
  (:require
   [qahira.edge.db :as qhr.edge.db]
   [qahira.edge.token-encoder :as qhr.edge.tokenenc]
   [ring.util.http-response :as http.res]))

(defn- render-auth-token
  [{:keys [auth-token-encoder]} attrs]
  (let [token (qhr.edge.tokenenc/make-token auth-token-encoder attrs :auth)]
    {:token {:auth token}}))

(defn register-handler
  [{:keys [database] :as ctx}]
  (fn [{:keys [parameters]}]
    (let [attrs (-> parameters :body :user)
          user  (qhr.edge.db/register-user! database attrs)]
      (http.res/ok (render-auth-token ctx user)))))

(defn login-handler
  [ctx]
  (fn [{:keys [parameters]}]
    (http.res/ok (render-auth-token ctx (:path parameters)))))

(defn delete-handler
  [{:keys [database]}]
  (fn [{:keys [parameters]}]
    (let [username (-> parameters :path :username)
          result   (qhr.edge.db/delete-user! database username)]
      (when-not (:database/happened? result)
        (http.res/bad-request!
          {:error {:category :incorrect
                   :message  "can't delete the user"}}))
      (http.res/no-content))))

(defn update-password-handler
  [{:keys [database] :as ctx}]
  (fn [{:keys [parameters]}]
    (let [username     (-> parameters :path :username)
          new-password (-> parameters :body :user :new-password)
          old-password (-> parameters :body :user :old-password)
          user         (qhr.edge.db/update-user-password!
                         database
                         username
                         old-password
                         new-password)]
      (when (nil? user)
        (http.res/bad-request!
          {:error {:category :incorrect
                   :message  "can't update the user's password"}}))
      (http.res/ok (render-auth-token ctx user)))))

(defn restore-handler
  [{:keys [database] :as ctx}]
  (fn [{:keys [parameters]}]
    (let [username (-> parameters :path :username)
          user     (qhr.edge.db/restore-user! database username)]
      (when (nil? user)
        (http.res/bad-request!
          {:error {:category :incorrect
                   :message  "can't restore the user"}}))
      (http.res/ok (render-auth-token ctx user)))))

(defn reset-password-handler
  [{:keys [database] :as ctx}]
  (fn [{:keys [parameters]}]
    (let [username     (-> parameters :path :username)
          new-password (-> parameters :body :user :new-password)
          user         (qhr.edge.db/reset-user-password!
                         database
                         username
                         new-password)]
      (when (nil? user)
        (http.res/bad-request!
          {:error {:category :incorrect
                   :message  "can't reset the user's password"}}))
      (http.res/ok (render-auth-token ctx user)))))
