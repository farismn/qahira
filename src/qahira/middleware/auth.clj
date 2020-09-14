(ns qahira.middleware.auth)

;; (:require
;;  [buddy.auth]
;;  [buddy.auth.backends :as buddy.backends]
;;  [buddy.auth.middleware :as buddy.mdw]
;;  [jeumpa.api.database :as jeum.api.db]
;;  [jeumpa.api.token-encoder :as jeum.api.token-enc]
;;  [jeumpa.data.user :as jeum.data.user]
;;  [jeumpa.util.request :as jeum.u.req]
;;  [ring.util.http-response :as http.res]
;;  [taoensso.encore :as e])


;; (def authenticated-middleware
;;   {:name ::authenticated
;;    :wrap (fn [handler]
;;            (fn [request]
;;              (if (buddy.auth/authenticated? request)
;;                (handler request)
;;                (http.res/unauthorized!
;;                  {:error {:category :unauthenticated
;;                           :message  "unauthenticated"}}))))})

;; (defn- wrap-permission
;;   [handler pred]
;;   (fn [request]
;;     (if (or (not (buddy.auth/authenticated? request))
;;             (and (buddy.auth/authenticated? request) (pred request)))
;;       (handler request)
;;       (http.res/forbidden!
;;         {:error {:category :unauthorized
;;                  :message  "forbidden"}}))))

;; (def permission-path-username-middleware
;;   (letfn [(checker [{:keys [identity parameters]}]
;;             (let [path-username (-> parameters :path :username)
;;                   self-username (:username identity)]
;;               (or (nil? path-username)
;;                   (e/some= path-username self-username))))]
;;     {:name ::permission-path-username
;;      :wrap #(wrap-permission % checker)}))

;; (def ^:private backends-map
;;   {:basic   buddy.backends/basic
;;    :session buddy.backends/session
;;    :token   buddy.backends/token
;;    :jws     buddy.backends/jws
;;    :jwe     buddy.backends/jwe})

;; (defn- wrap-authentication
;;   [handler {:keys [backend] :as opts}]
;;   (let [backend-fn (get backends-map backend)
;;         new-opts   (dissoc opts :backend)
;;         backend    (backend-fn new-opts)]
;;     (buddy.mdw/wrap-authentication handler backend)))

;; (def basic-authentication-middleware
;;   {:name ::basic-authentication
;;    :wrap (fn [handler]
;;            (let [authfn   (fn [request credential]
;;                             (e/catching
;;                               (e/when-let [database (jeum.u.req/get-service request :database)
;;                                            username (:username credential)
;;                                            password (:password credential)
;;                                            user     (jeum.api.db/find-user database username)]
;;                                 (jeum.data.user/authenticate user password))))
;;                  settings {:backend :basic :authfn authfn}]
;;              (wrap-authentication handler settings)))})

;; (def jeumpa-token-authentication-middleware
;;   {:name ::jeumpa-token-authentication
;;    :wrap (fn [handler kind]
;;            (let [authfn   (fn [request token]
;;                             (e/catching
;;                               (some-> request
;;                                       (jeum.u.req/get-service :auth-token-factory)
;;                                       (jeum.api.token-enc/read-token token kind))))
;;                  settings {:backend    :token
;;                            :authfn     authfn
;;                            :token-name "JeumpaToken"}]
;;              (wrap-authentication handler settings)))})
