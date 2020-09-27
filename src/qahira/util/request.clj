(ns qahira.util.request
  (:require
   [reitit.ring :as reit.ring]
   [ring.util.http-response :as http.res]))

(defn route-data
  [request]
  (-> request reit.ring/get-match :data))

(defn get-service!
  [request kw]
  (or (-> request route-data (get-in [:services kw]))
      (http.res/internal-server-error!
        {:error {:category :fault
                 :message  "missing service"
                 :data     {:service kw}}})))
