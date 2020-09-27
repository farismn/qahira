(ns qahira.middleware.cors
  (:require
   [ring.middleware.cors :as ring.mdw.cors]
   [taoensso.encore :as e]))

(defn- cors-settings
  [config]
  (letfn [(as-regexes [xs]
            (into [] (map re-pattern) xs))]
    (-> config
        (select-keys [:access-control-allow-headers
                      :access-control-allow-methods
                      :access-control-allow-origin])
        (e/update-in [:access-control-allow-origin] [] as-regexes)
        (as-> <> (e/remove-vals empty? <>)))))

(def cors-middleware
  {:name    ::cors
   :compile (fn [{:keys [config]} _]
              (when-let [config (some-> config ::cors cors-settings not-empty)]
                (fn [handler]
                  (let [args (into [] (mapcat identity) config)]
                    (apply ring.mdw.cors/wrap-cors handler args)))))})
