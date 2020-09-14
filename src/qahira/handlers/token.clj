(ns qahira.handlers.token
  (:require
   [qahira.edge.token-encoder :as qhr.edge.token-enc]
   [ring.util.http-response :as http.res]))

(defn request-token-handler
  [{:keys [auth-token-encoder]}]
  (fn [{:keys [parameters]}]
    (let [params (-> parameters :path (dissoc :kind))
          kind   (-> parameters :path :kind)
          token  (qhr.edge.token-enc/make-token auth-token-encoder params kind)]
      (http.res/ok {:token {kind token}}))))
