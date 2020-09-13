(ns qahira.middleware.exception
  (:require
   [reitit.ring.middleware.exception :as reit.ring.mdw.ex]
   [ring.util.http-response :as http.res]
   [taoensso.encore :as e]))

(defn- pg-ex-handler
  [ex _]
  (case (.getSQLState (.getServerErrorMessage ex))
    "23505" (http.res/conflict {:error {:category :conflict
                                        :message  "unique constraint"}})
    (http.res/internal-server-error {:error {:category :unknown-error
                                             :message  "unknown error"}})))

(def exception-middleware
  (reit.ring.mdw.ex/create-exception-middleware
    (e/merge
      reit.ring.mdw.ex/default-handlers
      {org.postgresql.util.PSQLException pg-ex-handler})))
