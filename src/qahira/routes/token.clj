(ns qahira.routes.token
  (:require
   [qahira.edge.token-encoder :as qhr.edge.token-enc]
   [qahira.handlers.token :as qhr.handlers.token]
   [ring.util.http-status :as http.sta]))

(def ^:private AcceptedKinds
  [:enum :reset :restore])

(defn target-routes
  [ctx]
  ["/api/token/:kind/:username"
   {:name ::target
    :get  {:responses  {http.sta/ok {:body [:map
                                            [:token
                                             [:map-of
                                              [:and
                                               qhr.edge.token-enc/Kind
                                               AcceptedKinds]
                                              qhr.edge.token-enc/Token]]]}}
           :middleware []
           :handler    (qhr.handlers.token/request-token-handler ctx)}}])
