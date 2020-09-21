(ns qahira.edge.qahira-client-test
  (:require
   [clojure.test :as t]
   [clojure.test.check.generators :as t.gen]
   [expectations.clojure.test :refer [defexpect expecting expect from-each]]
   [malli.generator :as ml.gen]
   [malli.util :as ml.u]
   [orchid.test.fixtures :as orc.t.fxt]
   [qahira.edge.db :as qhr.edge.db]
   [qahira.edge.qahira-client :as qhr.edge.qhrc]
   [qahira.test.fixtures :as qhr.t.fxt]
   [qahira.test.generator :as qhr.t.gen]
   [ring.util.http-predicates :as http.pred]))

(t/use-fixtures :once
  (orc.t.fxt/with-system-fixture #(qhr.t.fxt/make-system :app)))

(defn- fixed-password-updatee-generator
  [password]
  (ml.gen/generator
    (ml.u/update qhr.edge.db/PasswordUpdatee
                 :old-password
                 ml.u/update-properties assoc :gen/gen (t.gen/return password))))

(defexpect request-condition-test
  (expecting "app condition fetched"
    (let [qahira-client (qhr.t.fxt/get-component! :qahira-client)]
      (expecting "success"
        (expect http.pred/ok?
          (qhr.edge.qhrc/request-condition qahira-client))))))

(defexpect request-token-test
  (expecting "token fetched"
    (let [qahira-client (qhr.t.fxt/get-component! :qahira-client)
          user          (t.gen/generate qhr.t.gen/new-user-generator)]
      (qhr.edge.qhrc/register-user qahira-client {:form-params {:user user}})
      (expecting "unknown kind"
        (expect http.pred/bad-request?
          (let [kind     (t.gen/generate qhr.t.gen/kind-generator)
                username (:username user)]
            (qhr.edge.qhrc/request-token qahira-client kind username {}))))
      (expecting "unauthenticated"
        (expect http.pred/unauthorized?
          (from-each [kind [:reset :restore]]
            (let [username (:username user)]
              (qhr.edge.qhrc/request-token qahira-client kind username {})))))
      (expecting "not enough permission"
        (expect http.pred/forbidden?
          (from-each [kind [:reset :restore]]
            (let [username (t.gen/generate qhr.t.gen/username-generator)
                  params   {:qahira-api-token-auth user}]
              (qhr.edge.qhrc/request-token qahira-client kind username params)))))
      (expecting "success"
        (expect http.pred/ok?
          (from-each [kind     [:reset :restore]
                      username [(:username user)
                                (t.gen/generate qhr.t.gen/username-generator)]]
            (let [params {:qahira-api-token-auth (assoc user :username username)}]
              (qhr.edge.qhrc/request-token qahira-client kind username params))))))))

(defexpect register-user-test
  (expecting "user registered"
    (let [qahira-client (qhr.t.fxt/get-component! :qahira-client)
          user          (t.gen/generate qhr.t.gen/new-user-generator)]
      (expecting "malformed body"
        (expect http.pred/bad-request?
          (qhr.edge.qhrc/register-user qahira-client {})))
      (expecting "success"
        (expect http.pred/ok?
          (qhr.edge.qhrc/register-user qahira-client {:form-params {:user user}})))
      (expecting "unique constraint"
        (expect http.pred/conflict?
          (qhr.edge.qhrc/register-user qahira-client {:form-params {:user user}}))))))

(defexpect login-user-test
  (expecting "received auth token"
    (let [qahira-client (qhr.t.fxt/get-component! :qahira-client)
          user          (t.gen/generate qhr.t.gen/new-user-generator)]
      (qhr.edge.qhrc/register-user qahira-client {:form-params {:user user}})
      (expecting "unauthenticated"
        (expect http.pred/unauthorized?
          (let [username (:username user)]
            (qhr.edge.qhrc/login-user qahira-client username {})))
        (expect http.pred/unauthorized?
          (let [username (t.gen/generate qhr.t.gen/username-generator)
                password (t.gen/generate qhr.t.gen/password-generator)
                request  {:basic-auth {:username username :password password}}]
            (qhr.edge.qhrc/login-user qahira-client username request))))
      (expecting "not enough permission"
        (expect http.pred/forbidden?
          (let [username (t.gen/generate qhr.t.gen/username-generator)
                request  {:basic-auth user}]
            (qhr.edge.qhrc/login-user qahira-client username request))))
      (expecting "success"
        (expect http.pred/ok?
          (let [username (:username user)
                request  {:basic-auth user}]
            (qhr.edge.qhrc/login-user qahira-client username request)))))))

(defexpect delete-user-test
  (expecting "user deleted"
    (let [qahira-client (qhr.t.fxt/get-component! :qahira-client)
          user          (t.gen/generate qhr.t.gen/new-user-generator)]
      (qhr.edge.qhrc/register-user qahira-client {:form-params {:user user}})
      (expecting "unauthenticated"
        (expect http.pred/unauthorized?
          (let [username (:username user)]
            (qhr.edge.qhrc/delete-user qahira-client username {}))))
      (expecting "not enough permisison"
        (expect http.pred/forbidden?
          (let [username (t.gen/generate qhr.t.gen/username-generator)
                request  {:qahira-auth-token-auth user}]
            (qhr.edge.qhrc/delete-user qahira-client username request))))
      (expecting "non-existent user"
        (expect http.pred/bad-request?
          (let [username (t.gen/generate qhr.t.gen/username-generator)
                request  {:qahira-auth-token-auth {:username username}}]
            (qhr.edge.qhrc/delete-user qahira-client username request))))
      (expecting "success"
        (expect http.pred/no-content?
          (let [username (:username user)
                request  {:qahira-auth-token-auth user}]
            (qhr.edge.qhrc/delete-user qahira-client username request)))))))

(defexpect update-user-password-test
  (expecting "user's password updated"
    (let [qahira-client (qhr.t.fxt/get-component! :qahira-client)
          user          (t.gen/generate qhr.t.gen/new-user-generator)]
      (qhr.edge.qhrc/register-user qahira-client {:form-params {:user user}})
      (expecting "malformed body"
        (expect http.pred/bad-request?
          (let [username (:username user)]
            (qhr.edge.qhrc/update-user-password qahira-client username {}))))
      (expecting "unauthenticated"
        (expect http.pred/unauthorized?
          (let [username (:username user)
                updatee  (t.gen/generate qhr.t.gen/password-updatee-generator)
                request  {:form-params {:user updatee}}]
            (qhr.edge.qhrc/update-user-password qahira-client username request))))
      (expecting "not enough permission"
        (expect http.pred/forbidden?
          (let [username (t.gen/generate qhr.t.gen/username-generator)
                updatee  (t.gen/generate qhr.t.gen/password-updatee-generator)
                request  {:qahira-auth-token-auth user
                          :form-params            {:user updatee}}]
            (qhr.edge.qhrc/update-user-password qahira-client username request))))
      (expecting "non-existent user"
        (expect http.pred/bad-request?
          (let [username (t.gen/generate qhr.t.gen/username-generator)
                updatee  (t.gen/generate qhr.t.gen/password-updatee-generator)
                request  {:qahira-auth-token-auth {:username username}
                          :form-params            {:user updatee}}]
            (qhr.edge.qhrc/update-user-password qahira-client username request))))
      (expecting "incorrect old password"
        (expect http.pred/bad-request?
          (let [username     (:username user)
                old-password (t.gen/generate qhr.t.gen/password-generator)
                updatee      (t.gen/generate (fixed-password-updatee-generator old-password))
                request      {:qahira-auth-token-auth user
                              :form-params            {:user updatee}}]
            (qhr.edge.qhrc/update-user-password qahira-client username request))))
      (expecting "success"
        (expect http.pred/ok?
          (let [username     (:username user)
                old-password (:password user)
                updatee      (t.gen/generate (fixed-password-updatee-generator old-password))
                request      {:qahira-auth-token-auth user
                              :form-params            {:user updatee}}]
            (qhr.edge.qhrc/update-user-password qahira-client username request)))))))

(defexpect restore-user-test
  (expecting "user restored"
    (let [qahira-client (qhr.t.fxt/get-component! :qahira-client)
          user          (t.gen/generate qhr.t.gen/new-user-generator)]
      (qhr.edge.qhrc/register-user qahira-client {:form-params {:user user}})
      (qhr.edge.qhrc/delete-user qahira-client (:username user) {:qahira-auth-token-auth user})
      (expecting "unauthenticated"
        (expect http.pred/unauthorized?
          (let [username (:username user)]
            (qhr.edge.qhrc/restore-user qahira-client username {}))))
      (expecting "not enough permission"
        (expect http.pred/forbidden?
          (let [username (t.gen/generate qhr.t.gen/username-generator)
                request  {:qahira-restore-token-auth user}]
            (qhr.edge.qhrc/restore-user qahira-client username request))))
      (expecting "non-existent user"
        (expect http.pred/bad-request?
          (let [username (t.gen/generate qhr.t.gen/username-generator)
                request  {:qahira-restore-token-auth {:username username}}]
            (qhr.edge.qhrc/restore-user qahira-client username request))))
      (expecting "success"
        (expect http.pred/ok?
          (let [username (:username user)
                request  {:qahira-restore-token-auth user}]
            (qhr.edge.qhrc/restore-user qahira-client username request)))))))

(defexpect reset-user-password-test
  (expecting "user's password reseted"
    (let [qahira-client (qhr.t.fxt/get-component! :qahira-client)
          user          (t.gen/generate qhr.t.gen/new-user-generator)]
      (qhr.edge.qhrc/register-user qahira-client {:form-params {:user user}})
      (expecting "malformed body"
        (expect http.pred/bad-request?
          (let [username (:username user)]
            (qhr.edge.qhrc/reset-user-password qahira-client username {}))))
      (expecting "unauthenticated"
        (expect http.pred/unauthorized?
          (let [username (:username user)
                resetee  (t.gen/generate qhr.t.gen/password-resetee-generator)
                request  {:form-params {:user resetee}}]
            (qhr.edge.qhrc/reset-user-password qahira-client username request))))
      (expecting "not enough permission"
        (expect http.pred/forbidden?
          (let [username (t.gen/generate qhr.t.gen/username-generator)
                resetee  (t.gen/generate qhr.t.gen/password-resetee-generator)
                request  {:qahira-reset-token-auth user
                          :form-params             {:user resetee}}]
            (qhr.edge.qhrc/reset-user-password qahira-client username request))))
      (expecting "non-existent user"
        (expect http.pred/bad-request?
          (let [username (t.gen/generate qhr.t.gen/username-generator)
                resetee  (t.gen/generate qhr.t.gen/password-resetee-generator)
                request  {:qahira-reset-token-auth {:username username}
                          :form-params             {:user resetee}}]
            (qhr.edge.qhrc/reset-user-password qahira-client username request))))
      (expecting "success"
        (expect http.pred/ok?
          (let [username (:username user)
                resetee  (t.gen/generate qhr.t.gen/password-resetee-generator)
                request  {:qahira-reset-token-auth user
                          :form-params             {:user resetee}}]
            (qhr.edge.qhrc/reset-user-password qahira-client username request)))))))
