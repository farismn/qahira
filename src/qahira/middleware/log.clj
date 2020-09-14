(ns qahira.middleware.log
  (:require
   [ring.logger :as ring.mdw.logger]
   [orchid.edge.logger :as orc.edge.logger]))

(defn- log-data
  [logger {:keys [level throwable message]}]
  (let [data (if (nil? throwable) message throwable)]
    (orc.edge.logger/log logger level data)))

(defn log-request-middleware
  [component]
  {:name    ::log-request
   :compile (fn [context _]
              (when-let [logger (or (:logger component) (:logger context))]
                (fn [handler]
                  (let [log-fn #(log-data logger %)
                        opts   {:log-fn log-fn}]
                    (ring.mdw.logger/wrap-with-logger handler opts)))))})
