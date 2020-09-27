(ns qahira.handlers.token
  (:require
   [qahira.edge.token-encoder :as qhr.edge.tokenenc]
   [qahira.util.request :as qhr.u.req]
   [ring.util.http-response :as http.res]))

(defn request-token-handler
  [{:keys [parameters] :as request}]
  (let [encoder (qhr.u.req/get-service! request :auth-token-encoder)
        params  (-> parameters :path (dissoc :kind))
        kind    (-> parameters :path :kind)
        token   (qhr.edge.tokenenc/make-token encoder params kind)]
    (http.res/ok {:token {kind token}})))
