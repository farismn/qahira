(ns qahira.edge.qahira-client-test
  (:require
   [clojure.test :as t]
   [clojure.test.check.generators :as t.gen]
   [expectations.clojure.test :refer [defexpect expecting expect from-each]]
   [malli.generator :as ml.gen]
   [malli.util :as ml.u]
   [orchid.test.fixtures :as orc.t.fxt]
   [qahira.edge.db :as qhr.edge.db]
   [qahira.edge.qahira-client :as qhr.edge.qhrclt]
   [qahira.test.fixtures :as qhr.t.fxt]
   [qahira.test.generator :as qhr.t.gen]
   [ring.util.http-predicates :as http.pred]))

(t/use-fixtures :once
  (orc.t.fxt/with-system-fixture #(qhr.t.fxt/make-system :app)))

(defn- fixed-password-updatee-generator
  [password]
  (ml.gen/generator
    (ml.u/update-in qhr.edge.db/PasswordUpdatee
                    [:old-password]
                    ml.u/update-properties
                    assoc :gen/gen (t.gen/return password))))

(defn- request-condition
  []
  (qhr.edge.qhrclt/request-condition
    (qhr.t.fxt/get-component! :qahira-client)))

(defn- register-user
  [request]
  (qhr.edge.qhrclt/register-user
    (qhr.t.fxt/get-component! :qahira-client)
    request))

(defn- request-token
  [kind username request]
  (qhr.edge.qhrclt/request-token
    (qhr.t.fxt/get-component! :qahira-client)
    kind
    username
    request))

(defn- login-user
  [username request]
  (qhr.edge.qhrclt/login-user
    (qhr.t.fxt/get-component! :qahira-client)
    username
    request))

(defn- delete-user
  [username request]
  (qhr.edge.qhrclt/delete-user
    (qhr.t.fxt/get-component! :qahira-client)
    username
    request))

(defn- update-user-password
  [username request]
  (qhr.edge.qhrclt/update-user-password
    (qhr.t.fxt/get-component! :qahira-client)
    username
    request))

(defn- restore-user
  [username request]
  (qhr.edge.qhrclt/restore-user
    (qhr.t.fxt/get-component! :qahira-client)
    username
    request))

(defn- reset-user-password
  [username request]
  (qhr.edge.qhrclt/reset-user-password
    (qhr.t.fxt/get-component! :qahira-client)
    username
    request))

(defexpect request-condition-test
  (expecting "app condition fetched"
    (expecting "success"
      (expect http.pred/ok?
        (request-condition)))))

(defexpect request-token-test
  (expecting "token fetched"
    (let [user (t.gen/generate qhr.t.gen/new-user-generator)]
      (register-user {:form-params {:user user}})
      (expecting "unknown kind"
        (expect http.pred/bad-request?
          (let [kind     (t.gen/generate qhr.t.gen/kind-generator)
                username (:username user)]
            (request-token kind username {}))))
      (expecting "unauthenticated"
        (expect http.pred/unauthorized?
          (from-each [kind [:reset :restore]
                      :let [username (:username user)]]
            (request-token kind username {}))))
      (expecting "not enough permission"
        (expect http.pred/forbidden?
          (from-each [kind [:reset :restore]
                      :let [username (t.gen/generate qhr.t.gen/username-generator)
                            params   {:qahira-api-token-auth user}]]
            (request-token kind username params))))
      (expecting "success"
        (expect http.pred/ok?
          (from-each [kind     [:reset :restore]
                      username [(:username user)
                                (t.gen/generate qhr.t.gen/username-generator)]
                      :let     [auth   (assoc user :username username)
                                params {:qahira-api-token-auth auth}]]
            (request-token kind username params)))))))

(defexpect register-user-test
  (expecting "user registered"
    (let [user (t.gen/generate qhr.t.gen/new-user-generator)]
      (expecting "malformed body"
        (expect http.pred/bad-request?
          (register-user {})))
      (expecting "success"
        (expect http.pred/ok?
          (register-user {:form-params {:user user}})))
      (expecting "unique constraint"
        (expect http.pred/conflict?
          (register-user {:form-params {:user user}}))))))

(defexpect login-user-test
  (expecting "received auth token"
    (let [user (t.gen/generate qhr.t.gen/new-user-generator)]
      (register-user {:form-params {:user user}})
      (expecting "unauthenticated"
        (expect http.pred/unauthorized?
          (let [username (:username user)]
            (login-user username {})))
        (expect http.pred/unauthorized?
          (let [username (t.gen/generate qhr.t.gen/username-generator)
                password (t.gen/generate qhr.t.gen/password-generator)
                request  {:basic-auth {:username username :password password}}]
            (login-user username request))))
      (expecting "not enough permission"
        (expect http.pred/forbidden?
          (let [username (t.gen/generate qhr.t.gen/username-generator)
                request  {:basic-auth user}]
            (login-user username request))))
      (expecting "success"
        (expect http.pred/ok?
          (let [username (:username user)
                request  {:basic-auth user}]
            (login-user username request)))))))

(defexpect delete-user-test
  (expecting "user deleted"
    (let [user (t.gen/generate qhr.t.gen/new-user-generator)]
      (register-user {:form-params {:user user}})
      (expecting "unauthenticated"
        (expect http.pred/unauthorized?
          (let [username (:username user)]
            (delete-user username {}))))
      (expecting "not enough permisison"
        (expect http.pred/forbidden?
          (let [username (t.gen/generate qhr.t.gen/username-generator)
                request  {:qahira-auth-token-auth user}]
            (delete-user username request))))
      (expecting "non-existent user"
        (expect http.pred/bad-request?
          (let [username (t.gen/generate qhr.t.gen/username-generator)
                request  {:qahira-auth-token-auth {:username username}}]
            (delete-user username request))))
      (expecting "success"
        (expect http.pred/no-content?
          (let [username (:username user)
                request  {:qahira-auth-token-auth user}]
            (delete-user username request)))))))

(defexpect update-user-password-test
  (expecting "user's password updated"
    (let [user (t.gen/generate qhr.t.gen/new-user-generator)]
      (register-user {:form-params {:user user}})
      (expecting "malformed body"
        (expect http.pred/bad-request?
          (let [username (:username user)]
            (update-user-password username {}))))
      (expecting "unauthenticated"
        (expect http.pred/unauthorized?
          (let [username (:username user)
                updatee  (t.gen/generate qhr.t.gen/password-updatee-generator)
                request  {:form-params {:user updatee}}]
            (update-user-password username request))))
      (expecting "not enough permission"
        (expect http.pred/forbidden?
          (let [username (t.gen/generate qhr.t.gen/username-generator)
                updatee  (t.gen/generate qhr.t.gen/password-updatee-generator)
                request  {:qahira-auth-token-auth user
                          :form-params            {:user updatee}}]
            (update-user-password username request))))
      (expecting "non-existent user"
        (expect http.pred/bad-request?
          (let [username (t.gen/generate qhr.t.gen/username-generator)
                updatee  (t.gen/generate qhr.t.gen/password-updatee-generator)
                request  {:qahira-auth-token-auth {:username username}
                          :form-params            {:user updatee}}]
            (update-user-password username request))))
      (expecting "incorrect old password"
        (expect http.pred/bad-request?
          (let [username     (:username user)
                old-password (t.gen/generate qhr.t.gen/password-generator)
                updatee      (t.gen/generate (fixed-password-updatee-generator old-password))
                request      {:qahira-auth-token-auth user
                              :form-params            {:user updatee}}]
            (update-user-password username request))))
      (expecting "success"
        (expect http.pred/ok?
          (let [username     (:username user)
                old-password (:password user)
                updatee      (t.gen/generate (fixed-password-updatee-generator old-password))
                request      {:qahira-auth-token-auth user
                              :form-params            {:user updatee}}]
            (update-user-password username request)))))))

(defexpect restore-user-test
  (expecting "user restored"
    (let [user (t.gen/generate qhr.t.gen/new-user-generator)]
      (register-user {:form-params {:user user}})
      (delete-user (:username user) {:qahira-auth-token-auth user})
      (expecting "unauthenticated"
        (expect http.pred/unauthorized?
          (let [username (:username user)]
            (restore-user username {}))))
      (expecting "not enough permission"
        (expect http.pred/forbidden?
          (let [username (t.gen/generate qhr.t.gen/username-generator)
                request  {:qahira-restore-token-auth user}]
            (restore-user username request))))
      (expecting "non-existent user"
        (expect http.pred/bad-request?
          (let [username (t.gen/generate qhr.t.gen/username-generator)
                request  {:qahira-restore-token-auth {:username username}}]
            (restore-user username request))))
      (expecting "success"
        (expect http.pred/ok?
          (let [username (:username user)
                request  {:qahira-restore-token-auth user}]
            (restore-user username request)))))))

(defexpect reset-user-password-test
  (expecting "user's password reseted"
    (let [user (t.gen/generate qhr.t.gen/new-user-generator)]
      (register-user {:form-params {:user user}})
      (expecting "malformed body"
        (expect http.pred/bad-request?
          (let [username (:username user)]
            (reset-user-password username {}))))
      (expecting "unauthenticated"
        (expect http.pred/unauthorized?
          (let [username (:username user)
                resetee  (t.gen/generate qhr.t.gen/password-resetee-generator)
                request  {:form-params {:user resetee}}]
            (reset-user-password username request))))
      (expecting "not enough permission"
        (expect http.pred/forbidden?
          (let [username (t.gen/generate qhr.t.gen/username-generator)
                resetee  (t.gen/generate qhr.t.gen/password-resetee-generator)
                request  {:qahira-reset-token-auth user
                          :form-params             {:user resetee}}]
            (reset-user-password username request))))
      (expecting "non-existent user"
        (expect http.pred/bad-request?
          (let [username (t.gen/generate qhr.t.gen/username-generator)
                resetee  (t.gen/generate qhr.t.gen/password-resetee-generator)
                request  {:qahira-reset-token-auth {:username username}
                          :form-params             {:user resetee}}]
            (reset-user-password username request))))
      (expecting "success"
        (expect http.pred/ok?
          (let [username (:username user)
                resetee  (t.gen/generate qhr.t.gen/password-resetee-generator)
                request  {:qahira-reset-token-auth user
                          :form-params             {:user resetee}}]
            (reset-user-password username request)))))))
