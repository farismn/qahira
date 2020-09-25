(ns qahira.routes.token
  (:require
   [orchid.components.reitit-ring :as orc.c.reitring]
   [qahira.edge.db :as qhr.edge.db]
   [qahira.edge.token-encoder :as qhr.edge.tokenenc]
   [qahira.handlers.token :as qhr.handlers.token]
   [ring.util.http-status :as http.sta]))

(def ^:private AcceptedKinds
  [:enum :reset :restore])

(defn target-routes
  [ctx]
  (letfn [(extract-middlewares [middlewares]
            (orc.c.reitring/extract-middlewares ctx middlewares))]
    ["/api/token/:kind/:username"
     {:name       ::target
      :parameters {:path [:map
                          [:kind [:and qhr.edge.tokenenc/Kind AcceptedKinds]]
                          [:username qhr.edge.db/Username]]}
      :get        {:responses  {http.sta/ok {:body [:map
                                                    [:token
                                                     [:map-of
                                                      qhr.edge.tokenenc/Kind
                                                      qhr.edge.tokenenc/Token]]]}}
                   :middleware (extract-middlewares
                                 [[:qahira-token-authentication-middleware :api]
                                  :authenticated-middleware
                                  :permission-path-username-middleware])
                   :handler    (qhr.handlers.token/request-token-handler ctx)}}]))
