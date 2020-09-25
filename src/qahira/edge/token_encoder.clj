(ns qahira.edge.token-encoder
  (:require
   [clj-time.core :as time]
   [malli.core :as ml]
   [malli.transform :as ml.transform]
   [orchid.components.jwt-encoder :as orc.c.jwtenc]
   [taoensso.encore :as e]))

(defprotocol TokenEncoder
  (-encode [token-encoder claims])
  (-decode [token-encoder token]))

(def Kind
  keyword?)

(def Token
  string?)

(def Payload
  [:map [:kind Kind]])

(defn make-token
  [token-encoder claims kind]
  (let [new-claims (-> claims (dissoc :password) (assoc :kind kind))]
    (-encode token-encoder new-claims)))

(defn read-token
  [token-encoder token kind]
  (e/when-let [payload (ml/decode
                         Payload
                         (-decode token-encoder token)
                         ml.transform/json-transformer)
               _       (e/some= (:kind payload) (e/as-?kw kind))]
    payload))

(defn- assoc-time
  [claims duration]
  (let [iat (time/now)
        exp (when (some? duration)
              (time/plus iat (time/seconds duration)))]
    (e/assoc-when claims :iat iat :exp exp)))

(extend-protocol TokenEncoder
  orchid.components.jwt_encoder.SHASigner
  (-encode [token-encoder claims]
    (let [duration   (-> token-encoder :config :duration)
          new-claims (assoc-time claims duration)]
      (orc.c.jwtenc/encode token-encoder new-claims)))
  (-decode [token-encoder token]
    (orc.c.jwtenc/decode token-encoder token))

  orchid.components.jwt_encoder.AsymmetricSigner
  (-encode [token-encoder claims]
    (let [duration   (-> token-encoder :config :duration)
          new-claims (assoc-time claims duration)]
      (orc.c.jwtenc/encode token-encoder new-claims)))
  (-decode [token-encoder token]
    (orc.c.jwtenc/decode token-encoder token)))
