(ns qahira.systems
  (:require
   [aero.core :as aero]
   [com.stuartsierra.component :as c]
   [muuntaja.core :as mtj]
   [orchid.components.http-kit :as orc.c.httpk]
   [orchid.components.reitit-ring :as orc.c.reit-ring]
   [reitit.coercion.malli :as reit.coerce.ml]))

(defn- make-http-listener-system
  [config]
  (letfn [(router-options [component]
            {:data {:instance   mtj/instance
                    :coercion   reit.coerce.ml/coercion
                    :middleware (some-> component :middleware :middlewares)}})
          (router-middleware [_component]
            [])]
    (-> (c/system-map
          :http-server            (orc.c.httpk/make-http-server (:qahira/http-server config))
          :ring-handler           (orc.c.reit-ring/make-ring-handler)
          :ring-router            (orc.c.reit-ring/make-ring-router)
          :ring-router-options    (orc.c.reit-ring/make-ring-options router-options)
          :ring-router-middleware (orc.c.reit-ring/make-ring-middlewares router-middleware))
        (c/system-using
          {:http-server         {:handler :ring-handler}
           :ring-handler        {:router :ring-router}
           :ring-router         {:options :ring-router-options}
           :ring-router-options {:middleware :ring-router-middleware}}))))

(defn- make-app-base-system
  [config]
  (-> (merge (make-http-listener-system config))))

(def ^:private systems-map
  {:app/prod make-app-base-system})

(defn make-system
  [source system-kind]
  (let [profile   (-> system-kind name keyword)
        system-fn (get systems-map system-kind)
        config    (aero/read-config source {:profile profile})]
    (system-fn config)))
