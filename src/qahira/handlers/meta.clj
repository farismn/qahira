(ns qahira.handlers.meta
  (:require
   [ring.util.http-response :as http.res]))

(defn request-condition-handler
  [_]
  (fn [_]
    (http.res/ok {:meta {:condition {:name     "qahira"
                                     :healthy? true}}})))
