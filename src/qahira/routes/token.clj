(ns qahira.routes.token
  (:require
   [qahira.edge.db :as qhr.edge.db]
   [qahira.edge.token-encoder :as qhr.edge.tokenenc]
   [qahira.handlers.token :as qhr.handlers.token]
   [qahira.middleware.auth :as qhr.mdw.auth]
   [ring.util.http-status :as http.sta]))

(def ^:private AcceptedKinds
  [:enum :reset :restore])

(def routes
  [["/api/token/:kind/:username"
    {:name       ::target
     :parameters {:path [:map
                         [:kind [:and qhr.edge.tokenenc/Kind AcceptedKinds]]
                         [:username qhr.edge.db/Username]]}
     :get        {:responses  {http.sta/ok {:body [:map
                                                   [:token
                                                    [:map-of
                                                     qhr.edge.tokenenc/Kind
                                                     qhr.edge.tokenenc/Token]]]}}
                  :middleware [[qhr.mdw.auth/qahira-token-authentication-middleware :api]
                               qhr.mdw.auth/authenticated-middleware
                               qhr.mdw.auth/permission-path-username-middleware]
                  :handler    qhr.handlers.token/request-token-handler}}]])
