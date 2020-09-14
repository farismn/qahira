(ns qahira.middleware.util)

(defn run-middlewares
  [component middlewares]
  (letfn [(get! [m k]
            (or (get m k)
                (throw (ex-info "missing middleware" {:middleware k}))))]
    (into []
          (keep (fn [middleware]
                  (if (vector? middleware)
                    (let [mdw (:middleware (get! component (first middleware)))]
                      (into [mdw] (next middleware)))
                    (:middleware (get! component middleware)))))
          middlewares)))

(comment

  (run-middlewares
    {:a {:middleware {:foo "bar"}}
     :b {}
     :c {:middleware {:bar "bar"}}}
    [:a :b :c])

  )
