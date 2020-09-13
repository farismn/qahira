(ns qahira.systems
  (:require
   [aero.core :as aero]
   [com.stuartsierra.component :as c]
   [muuntaja.core :as mtj]
   [orchid.components.hikari-cp :as orc.c.hikari]
   [orchid.components.http-kit :as orc.c.httpk]
   [orchid.components.jwt-encoder :as orc.c.jwt-enc]
   [orchid.components.migratus :as orc.c.migratus]
   [orchid.components.reitit-ring :as orc.c.reit-ring]
   [orchid.components.timbre :as orc.c.timbre]
   [qahira.middleware.exception :as qhr.mdw.ex]
   [qahira.routes.meta :as qhr.routes.meta]
   [reitit.coercion.malli :as reit.coerce.ml]
   [reitit.ring.coercion :as reit.ring.coerce]
   [reitit.ring.middleware.muuntaja :as reit.ring.mdw.mtj]
   [reitit.ring.middleware.parameters :as reit.ring.mdw.params]))

(defn- make-http-listener-system
  [config]
  (letfn [(router-options [component]
            {:data {:muuntaja   mtj/instance
                    :coercion   reit.coerce.ml/coercion
                    :middleware (into []
                                      (comp
                                        (map #(get component %))
                                        (keep :middleware))
                                      [:parameters-middleware
                                       :format-middleware
                                       :exception-middleware
                                       :coerce-exception-middleware
                                       :coerce-request-middleware
                                       :coerce-response-middleware])}})]
    (-> (c/system-map
          :http-server  (orc.c.httpk/make-http-server (:qahira/http-server config))
          :ring-handler (orc.c.reit-ring/make-ring-handler)
          :ring-router  (orc.c.reit-ring/make-ring-router)
          :ring-options (orc.c.reit-ring/make-ring-options router-options))
        (c/system-using
          {:http-server  {:handler :ring-handler}
           :ring-handler {:router :ring-router}
           :ring-router  {:options :ring-options}}))))

(defn- make-ring-middleware-system
  [_config]
  (letfn [(make-ring-middleware-const [middleware]
            (orc.c.reit-ring/make-ring-middleware (constantly middleware)))]
    (c/system-map
      :parameters-middleware       (make-ring-middleware-const reit.ring.mdw.params/parameters-middleware)
      :format-middleware           (make-ring-middleware-const reit.ring.mdw.mtj/format-middleware)
      :exception-middleware        (make-ring-middleware-const qhr.mdw.ex/exception-middleware)
      :coerce-exception-middleware (make-ring-middleware-const reit.ring.coerce/coerce-exceptions-middleware)
      :coerce-request-middleware   (make-ring-middleware-const reit.ring.coerce/coerce-request-middleware)
      :coerce-response-middleware  (make-ring-middleware-const reit.ring.coerce/coerce-response-middleware))))

(defn- make-ring-routes-system
  [_config]
  (c/system-map
    :meta-anon-routes (orc.c.reit-ring/make-ring-routes qhr.routes.meta/anon-routes)))

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
    :auth-token-encoder (orc.c.jwt-enc/make-jwt-encoder (:qahira/auth-token-encoder config))
    :api-token-encoder  (orc.c.jwt-enc/make-jwt-encoder (:qahira/api-token-encoder config))))

(defn- make-logger-system
  [config]
  (-> (c/system-map
        :logger         (orc.c.timbre/make-timbre-logger (:qahira/logger config))
        :logger-println (orc.c.timbre/make-timbre-println-logger-appenders (:qahira/logger-println config)))
      (c/system-using
        {:logger [:logger-println]})))

(defn- make-app-base-system
  [config]
  (-> (merge (make-http-listener-system config)
             (make-ring-middleware-system config)
             (make-ring-routes-system config)
             (make-database-system config)
             (make-token-encoder-system config)
             (make-logger-system config))
      (c/system-using
        {:ring-router         [:meta-anon-routes]
         :ring-router-options [:parameters-middleware
                               :format-middleware
                               :exception-middleware
                               :coerce-exception-middleware
                               :coerce-request-middleware
                               :coerce-response-middleware]})))

(def ^:private systems-map
  {:app/prod make-app-base-system
   :app/dev  make-app-base-system})

(defn make-system
  [source system-kind]
  (let [profile   (-> system-kind name keyword)
        system-fn (get systems-map system-kind)
        config    (aero/read-config source {:profile profile})]
    (system-fn config)))
