(ns qahira.systems
  (:require
   [aero.core :as aero]
   [com.stuartsierra.component :as c]
   [muuntaja.core :as mtj]
   [orchid.components.http-kit :as orc.c.httpk]
   [orchid.components.reitit-ring :as orc.c.reit-ring]
   [reitit.coercion.malli :as reit.coerce.ml]
   [reitit.ring.coercion :as reit.ring.coerce]
   [reitit.ring.middleware.exception :as reit.ring.mdw.ex]
   [reitit.ring.middleware.muuntaja :as reit.ring.mdw.mtj]
   [reitit.ring.middleware.parameters :as reit.ring.mdw.params]))

(defn- make-http-listener-system
  [config]
  (letfn [(router-options [component]
            {:data {:instance   mtj/instance
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
          :http-server         (orc.c.httpk/make-http-server (:qahira/http-server config))
          :ring-handler        (orc.c.reit-ring/make-ring-handler)
          :ring-router         (orc.c.reit-ring/make-ring-router)
          :ring-router-options (orc.c.reit-ring/make-ring-options router-options))
        (c/system-using
          {:http-server  {:handler :ring-handler}
           :ring-handler {:router :ring-router}
           :ring-router  {:options :ring-router-options}}))))

(defn- make-ring-middleware-system
  [_config]
  (letfn [(make-ring-middleware-const [middleware]
            (orc.c.reit-ring/make-ring-middleware (constantly middleware)))]
    (c/system-map
      :parameters-middleware       (make-ring-middleware-const reit.ring.mdw.params/parameters-middleware)
      :format-middleware           (make-ring-middleware-const reit.ring.mdw.mtj/format-middleware)
      :exception-middleware        (make-ring-middleware-const reit.ring.mdw.ex/exception-middleware)
      :coerce-exception-middleware (make-ring-middleware-const reit.ring.coerce/coerce-exceptions-middleware)
      :coerce-request-middleware   (make-ring-middleware-const reit.ring.coerce/coerce-request-middleware)
      :coerce-response-middleware  (make-ring-middleware-const reit.ring.coerce/coerce-response-middleware))))

(defn- make-app-base-system
  [config]
  (-> (merge (make-http-listener-system config)
             (make-ring-middleware-system config))))

(def ^:private systems-map
  {:app/prod make-app-base-system})

(defn make-system
  [source system-kind]
  (let [profile   (-> system-kind name keyword)
        system-fn (get systems-map system-kind)
        config    (aero/read-config source {:profile profile})]
    (system-fn config)))
