(ns qahira.edge.db-test
  (:require
   [buddy.hashers :as buddy.hash]
   [clojure.test :as t]
   [clojure.test.check.generators :as t.gen]
   [expectations.clojure.test :refer [defexpect expecting expect in]]
   [orchid.test.fixtures :as orc.t.fxt]
   [qahira.edge.db :as qhr.edge.db]
   [qahira.test.fixtures :as qhr.t.fxt]
   [qahira.test.generator :as qhr.t.gen]))

(t/use-fixtures :once
  (orc.t.fxt/with-system-fixture #(qhr.t.fxt/make-system :db)))

(defn- register-user!
  [new-user]
  (qhr.edge.db/register-user!
    (qhr.t.fxt/get-component! :database)
    new-user))

(defn- login-user
  [username password]
  (qhr.edge.db/login-user
    (qhr.t.fxt/get-component! :database)
    username
    password))

(defn- delete-user!
  [username]
  (qhr.edge.db/delete-user!
    (qhr.t.fxt/get-component! :database)
    username))

(defn- restore-user!
  [username]
  (qhr.edge.db/restore-user!
    (qhr.t.fxt/get-component! :database)
    username))

(defn- reset-user-password!
  [username new-password]
  (qhr.edge.db/reset-user-password!
    (qhr.t.fxt/get-component! :database)
    username
    new-password))

(defn- update-user-password!
  [username old-password new-password]
  (qhr.edge.db/update-user-password!
    (qhr.t.fxt/get-component! :database)
    username
    old-password
    new-password))

(defexpect register-user!-test
  (expecting "user registered"
    (let [user (t.gen/generate qhr.t.gen/new-user-generator)]
      (expecting "success"
        (expect {:username (:username user)}
          (in (register-user! user))))
      (expecting "unique constraint"
        (expect org.postgresql.util.PSQLException
          (register-user! user))))))

(defexpect login-user-test
  (expecting "user found"
    (let [user (t.gen/generate qhr.t.gen/new-user-generator)]
      (register-user! user)
      (expecting "missing"
        (expect nil?
          (let [username (t.gen/generate qhr.t.gen/username-generator)
                password (:password user)]
            (login-user username password))))
      (expecting "incorrect password"
        (expect nil?
          (let [username (:username user)
                password (t.gen/generate qhr.t.gen/password-generator)]
            (login-user username password))))
      (expecting "success"
        (expect {:username (:username user)}
          (in (let [username (:username user)
                    password (:password user)]
                (login-user username password))))))))

(defexpect delete-user!-test
  (expecting "user deleted"
    (let [user (t.gen/generate qhr.t.gen/new-user-generator)]
      (register-user! user)
      (expecting "missing"
        (expect {:database/happened? false}
          (in (let [username (t.gen/generate qhr.t.gen/username-generator)]
                (delete-user! username)))))
      (expecting "success"
        (expect {:database/happened? true}
          (in (delete-user! (:username user)))))
      (expecting "already deleted"
        (expect {:database/happened? false}
          (in (delete-user! (:username user))))))))

(defexpect restore-user!-test
  (expecting "user restored"
    (let [user (t.gen/generate qhr.t.gen/new-user-generator)]
      (register-user! user)
      (delete-user! (:username user))
      (expecting "missing"
        (expect nil?
          (let [username (t.gen/generate qhr.t.gen/username-generator)]
            (restore-user! username))))
      (expecting "success"
        (expect {:username (:username user)}
          (in (restore-user! (:username user)))))
      (expecting "already restored"
        (expect nil?
          (restore-user! (:username user)))))))

(defexpect reset-user-password!-test
  (expecting "password changed"
    (let [user (t.gen/generate qhr.t.gen/new-user-generator)]
      (register-user! user)
      (expecting "missing"
        (expect nil?
          (let [username     (t.gen/generate qhr.t.gen/username-generator)
                new-password (t.gen/generate qhr.t.gen/password-generator)]
            (reset-user-password! username new-password))))
      (expecting "success"
        (with-redefs [buddy.hash/derive #(str % "!")]
          (let [new-password (t.gen/generate qhr.t.gen/password-generator)]
            (expect {:username (:username user)
                     :password (buddy.hash/derive new-password)}
              (in (let [username (:username user)]
                    (reset-user-password! username new-password))))))))))

(defexpect update-user-password!-test
  (expecting "password changed"
    (let [user (t.gen/generate qhr.t.gen/new-user-generator)]
      (register-user! user)
      (expecting "missing"
        (expect nil?
          (let [username     (t.gen/generate qhr.t.gen/username-generator)
                old-password (t.gen/generate qhr.t.gen/password-generator)
                new-password (t.gen/generate qhr.t.gen/password-generator)]
            (update-user-password! username old-password new-password))))
      (expecting "incorrect old password"
        (expect nil?
          (let [username     (:username user)
                old-password (t.gen/generate qhr.t.gen/password-generator)
                new-password (t.gen/generate qhr.t.gen/password-generator)]
            (update-user-password! username old-password new-password))))
      (expecting "success"
        (with-redefs [buddy.hash/derive #(str % "!")]
          (let [new-password (t.gen/generate qhr.t.gen/password-generator)]
            (expect {:username (:username user)
                     :password (buddy.hash/derive new-password)}
              (in (let [username     (:username user)
                        old-password (:password user)]
                    (update-user-password! username old-password new-password))))))))))
