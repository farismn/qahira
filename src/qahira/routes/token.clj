(ns qahira.routes.token
  (:require
   [qahira.edge.token-encoder :as qhr.edge.token-enc]
   [qahira.handlers.token :as qhr.handlers.token]
   [qahira.middleware.util :as qhr.mdw.u]
   [ring.util.http-status :as http.sta]))

(def ^:private AcceptedKinds
  [:enum :reset :restore])

(defn target-routes
  [ctx]
  (letfn [(run-middlewares [middlewares]
            (qhr.mdw.u/run-middlewares ctx middlewares))]
    ["/api/token/:kind/:username"
     {:name ::target
      :get  {:responses  {http.sta/ok {:body [:map
                                              [:token
                                               [:map-of
                                                [:and
                                                 qhr.edge.token-enc/Kind
                                                 AcceptedKinds]
                                                qhr.edge.token-enc/Token]]]}}
             :middleware (run-middlewares
                           [[:qahira-token-authentication-middleware :api]
                            :authenticated-middleware
                            :permission-path-username-middleware])
             :handler    (qhr.handlers.token/request-token-handler ctx)}}]))
