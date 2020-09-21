(ns qahira.test.generator
  (:require
   [malli.generator :as ml.gen]
   [qahira.edge.db :as qhr.edge.db]
   [qahira.edge.token-encoder :as qhr.edge.token-enc]))

(def username-generator
  (ml.gen/generator qhr.edge.db/Username))

(def password-generator
  (ml.gen/generator qhr.edge.db/Password))

(def new-user-generator
  (ml.gen/generator qhr.edge.db/NewUser))

(def password-resetee-generator
  (ml.gen/generator qhr.edge.db/PasswordResetee))

(def password-updatee-generator
  (ml.gen/generator qhr.edge.db/PasswordUpdatee))

(def kind-generator
  (ml.gen/generator qhr.edge.token-enc/Kind))
