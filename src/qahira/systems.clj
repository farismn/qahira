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
   [qahira.middleware.cors :as qhr.mdw.cors]
   [qahira.middleware.exception :as qhr.mdw.ex]
   [qahira.middleware.log :as qhr.mdw.log]
   [qahira.routes.meta :as qhr.routes.meta]
   [qahira.routes.token :as qhr.routes.token]
   [qahira.routes.user :as qhr.routes.user]
   [reitit.coercion.malli :as reit.coerce.ml]
   [reitit.ring.coercion :as reit.ring.coerce]
   [reitit.ring.middleware.muuntaja :as reit.ring.mdw.mtj]
   [reitit.ring.middleware.parameters :as reit.ring.mdw.params]
   [taoensso.encore :as e]
   [taoensso.timbre :as timbre]))

(def ^:private services-kws
  [:database
   :api-token-encoder
   :auth-token-encoder
   :logger])

(def ^:private app-routes
  [qhr.routes.meta/routes
   qhr.routes.token/routes
   qhr.routes.user/routes])

(defmulti make-timbre-appenders :timbre.appenders/kind)

(defmethod make-timbre-appenders :default
  [_]
  {})

(defmethod make-timbre-appenders :println
  [config]
  (e/merge (timbre/println-appender config)
           (select-keys config [:min-level :enabled?])))

(defn- make-http-listener-system
  [{:qahira/keys [http-server ring-options]}]
  (letfn [(router-options [{:keys [config] :as component}]
            {:data {:services   (select-keys component services-kws)
                    :config     config
                    :muuntaja   mtj/instance
                    :coercion   reit.coerce.ml/coercion
                    :middleware [reit.ring.mdw.params/parameters-middleware
                                 reit.ring.mdw.mtj/format-middleware
                                 qhr.mdw.ex/exception-middleware
                                 qhr.mdw.log/log-request-middleware
                                 qhr.mdw.cors/cors-middleware
                                 reit.ring.coerce/coerce-exceptions-middleware
                                 reit.ring.coerce/coerce-request-middleware
                                 reit.ring.coerce/coerce-response-middleware]}})]
    (-> (c/system-map
          :http-server
          (orc.c.httpk/make-http-server http-server)

          :ring-handler
          (orc.c.reitring/make-ring-handler)

          :ring-router
          (orc.c.reitring/make-ring-router)

          :ring-options
          (orc.c.reitring/make-ring-options router-options ring-options)

          :ring-routes
          (orc.c.reitring/make-ring-routes (constantly app-routes)))
        (c/system-using
          {:http-server  {:handler :ring-handler}
           :ring-handler {:router :ring-router}
           :ring-router  {:options :ring-options
                          :routes  :ring-routes}}))))

(defn- make-database-system
  [{:qahira/keys [database database-migration]}]
  (-> (c/system-map
        :database-implementation
        (orc.c.hikari/make-hikari-cp-impl database)

        :database
        (orc.c.hikari/make-hikari-cp)

        :database-migration
        (orc.c.migratus/make-migratus-migrate database-migration))
      (c/system-using
        {:database           {:impl :database-implementation}
         :database-migration {:db :database}})))

(defn- make-token-encoder-system
  [{:qahira/keys [auth-token-encoder api-token-encoder]}]
  (c/system-map
    :auth-token-encoder
    (orc.c.jwtenc/make-jwt-encoder auth-token-encoder)

    :api-token-encoder
    (orc.c.jwtenc/make-jwt-encoder api-token-encoder)))

(defn- make-logger-system
  [{:qahira/keys [logger logger-appenders]}]
  (letfn [(appenders-settings [{:keys [config]}]
            (->> config
                 (e/map-vals make-timbre-appenders)
                 (e/remove-vals empty?)))]
    (-> (c/system-map
          :logger
          (orc.c.timbre/make-timbre-logger logger)

          :logger-appenders
          (orc.c.timbre/make-timbre-appenders
            appenders-settings
            logger-appenders))
        (c/system-using
          {:logger {:appenders :logger-appenders}}))))

(defn- make-qahira-client-system
  [{:qahira/keys [qahira-client]}]
  (c/system-map
    :qahira-client
    (orc.c.httpclt/make-http-client qahira-client)))

(defn- make-app-base-system
  [config]
  (-> (merge (make-http-listener-system config)
             (make-database-system config)
             (make-token-encoder-system config)
             (make-logger-system config))
      (c/system-using
        {:database-implementation [:logger]
         :database                [:logger]
         :database-migration      [:logger]
         :http-server             [:logger]
         :auth-token-encoder      [:logger]
         :api-token-encoder       [:logger]
         :ring-options            services-kws})))

(defn- make-app-dev-system
  [config]
  (-> (merge (make-app-base-system config)
             (make-qahira-client-system config))
      (c/system-using
        {:qahira-client [:ring-router
                         :api-token-encoder
                         :auth-token-encoder
                         :logger]})))

(defn- make-db-dev-system
  [config]
  (-> (merge (make-database-system config)
             (make-logger-system config))
      (c/system-using
        {:database-implementation [:logger]
         :database                [:logger]
         :database-migration      [:logger]})))

(def ^:private systems-map
  {:app/prod make-app-base-system
   :app/dev  make-app-dev-system
   :app/test make-app-dev-system
   :db/dev   make-db-dev-system
   :db/test  make-db-dev-system})

(defn make-system
  [source system-kind]
  (let [system-fn (or (get systems-map system-kind)
                      (throw (ex-info "unknown system kind"
                                      {:system-kind system-kind})))
        profile   (-> system-kind name keyword)
        config    (if (map? source)
                    source
                    (aero/read-config source {:profile profile}))]
    (system-fn config)))
