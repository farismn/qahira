(ns qahira.systems
  (:require
   [aero.core :as aero]
   [com.stuartsierra.component :as c]
   [muuntaja.core :as mtj]
   [orchid.components.hikari-cp :as orc.c.hikari]
   [orchid.components.http-client :as orc.c.httpclt]
   [orchid.components.http-kit :as orc.c.httpk]
   [orchid.components.jwt-encoder :as orc.c.jwtenc]
   [orchid.components.migratus :as orc.c.migratus]
   [orchid.components.reitit-ring :as orc.c.reitring]
   [orchid.components.timbre :as orc.c.timbre]
   [qahira.middleware.auth :as qhr.mdw.auth]
   [qahira.middleware.cors :as qhr.mdw.cors]
   [qahira.middleware.exception :as qhr.mdw.ex]
   [qahira.middleware.log :as qhr.mdw.log]
   [qahira.routes.meta :as qhr.routes.meta]
   [qahira.routes.token :as qhr.routes.token]
   [qahira.routes.user :as qhr.routes.user]
   [reitit.coercion.malli :as reit.coerce.ml]
   [reitit.ring.coercion :as reit.ring.coerce]
   [reitit.ring.middleware.muuntaja :as reit.ring.mdw.mtj]
   [reitit.ring.middleware.parameters :as reit.ring.mdw.params]))

(defn- as-fn
  [v]
  (if (fn? v) v (constantly v)))

(defn- make-http-listener-system
  [config]
  (letfn [(router-options [component]
            {:data {:muuntaja   mtj/instance
                    :coercion   reit.coerce.ml/coercion
                    :middleware (orc.c.reitring/extract-middlewares
                                  component
                                  [:parameters-middleware
                                   :format-middleware
                                   :exception-middleware
                                   :log-request-middleware
                                   :cors-middleware
                                   :coerce-exceptions-middleware
                                   :coerce-request-middleware
                                   :coerce-response-middleware])}})]
    (-> (c/system-map
          :http-server  (orc.c.httpk/make-http-server (:qahira/http-server config))
          :ring-handler (orc.c.reitring/make-ring-handler)
          :ring-router  (orc.c.reitring/make-ring-router)
          :ring-options (orc.c.reitring/make-ring-options router-options))
        (c/system-using
          {:http-server  {:handler :ring-handler}
           :ring-handler {:router :ring-router}
           :ring-router  {:options :ring-options}}))))

(defn- make-ring-middleware-system
  [_config]
  (letfn [(make-ring-middleware
            ([middleware config]
             (-> (as-fn middleware)
                 (orc.c.reitring/make-ring-middleware)
                 (assoc :config config)))
            ([middleware]
             (make-ring-middleware middleware {})))]
    (c/system-map
      :parameters-middleware                  (make-ring-middleware reit.ring.mdw.params/parameters-middleware)
      :format-middleware                      (make-ring-middleware reit.ring.mdw.mtj/format-middleware)
      :exception-middleware                   (make-ring-middleware qhr.mdw.ex/exception-middleware)
      :log-request-middleware                 (make-ring-middleware qhr.mdw.log/log-request-middleware)
      :cors-middleware                        (make-ring-middleware qhr.mdw.cors/cors-middleware)
      :coerce-exceptions-middleware           (make-ring-middleware reit.ring.coerce/coerce-exceptions-middleware)
      :coerce-request-middleware              (make-ring-middleware reit.ring.coerce/coerce-request-middleware)
      :coerce-response-middleware             (make-ring-middleware reit.ring.coerce/coerce-response-middleware)
      :authenticated-middleware               (make-ring-middleware qhr.mdw.auth/authenticated-middleware)
      :permission-path-username-middleware    (make-ring-middleware qhr.mdw.auth/permission-path-username-middleware)
      :basic-authentication-middleware        (make-ring-middleware qhr.mdw.auth/basic-authentication-middleware)
      :qahira-token-authentication-middleware (make-ring-middleware qhr.mdw.auth/qahira-token-authentication-middleware))))

(defn- make-ring-routes-system
  [_config]
  (c/system-map
    :meta-anon-routes    (orc.c.reitring/make-ring-routes qhr.routes.meta/anon-routes)
    :token-target-routes (orc.c.reitring/make-ring-routes qhr.routes.token/target-routes)
    :user-anon-routes    (orc.c.reitring/make-ring-routes qhr.routes.user/anon-routes)
    :user-target-routes  (orc.c.reitring/make-ring-routes qhr.routes.user/target-routes)
    :user-restore-routes (orc.c.reitring/make-ring-routes qhr.routes.user/restore-routes)
    :user-reset-routes   (orc.c.reitring/make-ring-routes qhr.routes.user/reset-routes)))

(defn- make-database-system
  [config]
  (-> (c/system-map
        :database-implementation (orc.c.hikari/make-hikari-cp-impl (:qahira/database config))
        :database                (orc.c.hikari/make-hikari-cp)
        :database-migration      (orc.c.migratus/make-migratus-migrate (:qahira/database-migration config)))
      (c/system-using
        {:database           {:impl :database-implementation}
         :database-migration {:db :database}})))

(defn- make-token-encoder-system
  [config]
  (c/system-map
    :auth-token-encoder (orc.c.jwtenc/make-jwt-encoder (:qahira/auth-token-encoder config))
    :api-token-encoder  (orc.c.jwtenc/make-jwt-encoder (:qahira/api-token-encoder config))))

(defn- make-logger-system
  [config]
  (-> (c/system-map
        :logger         (orc.c.timbre/make-timbre-logger (:qahira/logger config))
        :logger-println (orc.c.timbre/make-timbre-println-logger-appenders (:qahira/logger-println config)))
      (c/system-using
        {:logger [:logger-println]})))

(defn- make-qahira-client-system
  [config]
  (c/system-map
    :qahira-client (orc.c.httpclt/make-http-client (:qahira/qahira-client config))))

(defn- make-app-base-system
  [config]
  (-> (merge (make-http-listener-system config)
             (make-ring-middleware-system config)
             (make-ring-routes-system config)
             (make-database-system config)
             (make-token-encoder-system config)
             (make-logger-system config))
      (c/system-using
        {:ring-router                            [:meta-anon-routes
                                                  :token-target-routes
                                                  :user-anon-routes
                                                  :user-target-routes
                                                  :user-restore-routes
                                                  :user-reset-routes]
         :ring-options                           [:parameters-middleware
                                                  :format-middleware
                                                  :exception-middleware
                                                  :log-request-middleware
                                                  :cors-middleware
                                                  :coerce-exceptions-middleware
                                                  :coerce-request-middleware
                                                  :coerce-response-middleware]
         :log-request-middleware                 [:logger]
         :basic-authentication-middleware        [:database]
         :qahira-token-authentication-middleware [:api-token-encoder
                                                  :auth-token-encoder]
         :token-target-routes                    [:auth-token-encoder
                                                  :qahira-token-authentication-middleware
                                                  :authenticated-middleware
                                                  :permission-path-username-middleware]
         :user-anon-routes                       [:database :auth-token-encoder]
         :user-target-routes                     [:database
                                                  :auth-token-encoder
                                                  :basic-authentication-middleware
                                                  :qahira-token-authentication-middleware
                                                  :authenticated-middleware
                                                  :permission-path-username-middleware]
         :user-restore-routes                    [:database
                                                  :auth-token-encoder
                                                  :qahira-token-authentication-middleware
                                                  :authenticated-middleware
                                                  :permission-path-username-middleware]
         :user-reset-routes                      [:database
                                                  :auth-token-encoder
                                                  :qahira-token-authentication-middleware
                                                  :authenticated-middleware
                                                  :permission-path-username-middleware]})))

(defn- make-app-dev-system
  [config]
  (-> (merge (make-app-base-system config)
             (make-qahira-client-system config))
      (c/system-using
        {:qahira-client [:ring-router
                         :api-token-encoder
                         :auth-token-encoder
                         :logger]})))

(def ^:private systems-map
  {:app/prod make-app-base-system
   :app/dev  make-app-dev-system
   :app/test make-app-dev-system
   :db/test  make-database-system})

(defn make-system
  [source system-kind]
  (let [system-fn (or (get systems-map system-kind)
                      (throw (ex-info "unknown system kind" {:system-kind system-kind})))
        profile   (-> system-kind name keyword)
        config    (if (map? source)
                    source
                    (aero/read-config source {:profile profile}))]
    (system-fn config)))
