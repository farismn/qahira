(ns qahira.middleware.auth
  (:require
   [buddy.auth]
   [buddy.auth.backends :as buddy.backends]
   [buddy.auth.middleware :as buddy.mdw]
   [qahira.edge.db :as qhr.edge.db]
   [qahira.edge.token-encoder :as qhr.edge.token-enc]
   [ring.util.http-response :as http.res]
   [taoensso.encore :as e :refer [catching]]))

(def authenticated-middleware
  {:name ::authenticated
   :wrap (fn [handler]
           (fn [request]
             (if (buddy.auth/authenticated? request)
               (handler request)
               (http.res/unauthorized!
                 {:error {:category :unauthenticated
                          :message  "unauthenticated"}}))))})

(defn- wrap-permission
  [handler pred]
  (fn [request]
    (if (or (not (buddy.auth/authenticated? request))
            (and (buddy.auth/authenticated? request) (pred request)))
      (handler request)
      (http.res/forbidden!
        {:error {:category :unauthorized
                 :message  "forbidden"}}))))

(def permission-path-username-middleware
  (letfn [(checker [{:keys [identity parameters]}]
            (let [path-username (-> parameters :path :username)
                  self-username (:username identity)]
              (or (nil? path-username)
                  (e/some= path-username self-username))))]
    {:name ::permission-path-username
     :wrap #(wrap-permission % checker)}))

(def ^:private backends-map
  {:basic   buddy.backends/basic
   :session buddy.backends/session
   :token   buddy.backends/token
   :jws     buddy.backends/jws
   :jwe     buddy.backends/jwe})

(defn- wrap-authentication
  [handler {:keys [backend] :as opts}]
  (let [backend-fn (get backends-map backend)
        new-opts   (dissoc opts :backend)
        backend    (backend-fn new-opts)]
    (buddy.mdw/wrap-authentication handler backend)))

(defn basic-authentication-middleware
  [{:keys [database]}]
  {:name ::basic-authentication
   :wrap (fn [handler]
           (let [authfn   (fn [_ credential]
                            (catching
                              (e/when-let [username (:username credential)
                                           password (:password credential)]
                                (qhr.edge.db/login-user database username password))))
                 settings {:backend :basic :authfn authfn}]
             (wrap-authentication handler settings)))})

(defn qahira-token-authentication-middleware
  [{:keys [api-token-encoder auth-token-encoder config]}]
  {:name ::qahira-token-authentication
   :wrap (fn [handler kind]
           (let [encoder  (if (= :api kind)
                            api-token-encoder
                            auth-token-encoder)
                 authfn   (fn [_ token]
                            (catching
                              (qhr.edge.token-enc/read-token encoder token kind)))
                 settings (e/merge
                            {:backend    :token
                             :authfn     authfn
                             :token-name "QahiraToken"}
                            (select-keys config [:token-name]))]
             (wrap-authentication handler settings)))})
