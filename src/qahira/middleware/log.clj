(ns qahira.middleware.log
  (:require
   [ring.logger :as ring.mdw.logger]
   [orchid.edge.logger :as orc.edge.logger]))

(defn- log-data
  [logger {:keys [level throwable message]}]
  (let [data (if (nil? throwable) message throwable)]
    (orc.edge.logger/log logger level data)))

(def log-request-middleware
  {:name    ::log-request
   :compile (fn [{:keys [logger]} _]
              (when (some? logger)
                (fn [handler]
                  (let [log-fn #(log-data logger %)
                        opts   {:log-fn log-fn}]
                    (ring.mdw.logger/wrap-with-logger handler opts)))))})
