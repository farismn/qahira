(ns qahira.routes.meta
  (:require
   [qahira.handlers.meta :as qhr.handlers.meta]
   [ring.util.http-status :as http.sta]))

(def routes
  [["/api/meta"
    {:name ::anon
     :get  {:responses {http.sta/ok {:body [:map
                                            [:meta
                                             [:map
                                              [:condition
                                               [:map
                                                [:name string?]
                                                [:healthy? boolean?]]]]]]}}
            :handler   qhr.handlers.meta/request-condition-handler}}]])
