(ns qahira.test.generator
  (:require
   [malli.generator :as ml.gen]
   [qahira.edge.db :as qhr.edge.db]
   [qahira.edge.token-encoder :as qhr.edge.tokenenc]))

(def ^:private default-generator-options
  {:size 20
   :seed 25})

(def username-generator
  (ml.gen/generator qhr.edge.db/Username default-generator-options))

(def password-generator
  (ml.gen/generator qhr.edge.db/Password default-generator-options))

(def new-user-generator
  (ml.gen/generator qhr.edge.db/NewUser default-generator-options))

(def password-resetee-generator
  (ml.gen/generator qhr.edge.db/PasswordResetee default-generator-options))

(def password-updatee-generator
  (ml.gen/generator qhr.edge.db/PasswordUpdatee default-generator-options))

(def kind-generator
  (ml.gen/generator qhr.edge.tokenenc/Kind default-generator-options))
