(ns qahira.edge.db-test
  (:require
   [buddy.hashers :as buddy.hash]
   [clojure.test :as t]
   [clojure.test.check.generators :as t.gen]
   [expectations.clojure.test :refer [defexpect expecting expect in]]
   [malli.generator :as ml.gen]
   [orchid.test.fixtures :as orc.t.fxt]
   [qahira.edge.db :as qhr.edge.db]
   [qahira.test.fixtures :as qhr.t.fxt]))

(t/use-fixtures :once
  (orc.t.fxt/with-system-fixture #(qhr.t.fxt/make-system :db)))

(def ^:private username-generator
  (ml.gen/generator qhr.edge.db/Username))

(def ^:private password-generator
  (ml.gen/generator qhr.edge.db/Password))

(def ^:private new-user-generator
  (ml.gen/generator qhr.edge.db/NewUser))

(defexpect register-user!-test
  (expecting "user registered"
    (let [db   (qhr.t.fxt/get-component! :database)
          user (t.gen/generate new-user-generator)]
      (expecting "success"
        (expect {:username (:username user)}
          (in (qhr.edge.db/register-user! db user))))
      (expecting "unique constraint"
        (expect org.postgresql.util.PSQLException
          (qhr.edge.db/register-user! db user))))))

(defexpect login-user-test
  (expecting "user found"
    (let [db   (qhr.t.fxt/get-component! :database)
          user (t.gen/generate new-user-generator)]
      (qhr.edge.db/register-user! db user)
      (expecting "missing"
        (expect nil?
          (let [username (t.gen/generate username-generator)
                password (:password user)]
            (qhr.edge.db/login-user db username password))))
      (expecting "incorrect password"
        (expect nil?
          (let [username (:username user)
                password (t.gen/generate password-generator)]
            (qhr.edge.db/login-user db username password))))
      (expecting "success"
        (expect {:username (:username user)}
          (in (let [username (:username user)
                    password (:password user)]
                (qhr.edge.db/login-user db username password))))))))

(defexpect delete-user!-test
  (expecting "user deleted"
    (let [db   (qhr.t.fxt/get-component! :database)
          user (t.gen/generate new-user-generator)]
      (qhr.edge.db/register-user! db user)
      (expecting "missing"
        (expect {:database/happened? false}
          (in (let [username (t.gen/generate username-generator)]
                (qhr.edge.db/delete-user! db username)))))
      (expecting "success"
        (expect {:database/happened? true}
          (in (qhr.edge.db/delete-user! db (:username user)))))
      (expecting "already deleted"
        (expect {:database/happened? false}
          (in (qhr.edge.db/delete-user! db (:username user))))))))

(defexpect restore-user!-test
  (expecting "user restored"
    (let [db   (qhr.t.fxt/get-component! :database)
          user (t.gen/generate new-user-generator)]
      (qhr.edge.db/register-user! db user)
      (qhr.edge.db/delete-user! db (:username user))
      (expecting "missing"
        (expect nil?
          (let [username (t.gen/generate username-generator)]
            (qhr.edge.db/restore-user! db username))))
      (expecting "success"
        (expect {:username (:username user)}
          (in (qhr.edge.db/restore-user! db (:username user)))))
      (expecting "already restored"
        (expect nil?
          (qhr.edge.db/restore-user! db (:username user)))))))

(defexpect reset-user-password!-test
  (expecting "password changed"
    (let [db   (qhr.t.fxt/get-component! :database)
          user (t.gen/generate new-user-generator)]
      (qhr.edge.db/register-user! db user)
      (expecting "missing"
        (expect nil?
          (let [username     (t.gen/generate username-generator)
                new-password (t.gen/generate password-generator)]
            (qhr.edge.db/reset-user-password! db username new-password))))
      (expecting "success"
        (with-redefs [buddy.hash/derive #(str % "!")]
          (let [new-password (t.gen/generate password-generator)]
            (expect {:username (:username user)
                     :password (buddy.hash/derive new-password)}
              (in (let [username (:username user)]
                    (qhr.edge.db/reset-user-password! db username new-password))))))))))

(defexpect update-user-password!-test
  (expecting "password changed"
    (let [db   (qhr.t.fxt/get-component! :database)
          user (t.gen/generate new-user-generator)]
      (qhr.edge.db/register-user! db user)
      (expecting "missing"
        (expect nil?
          (let [username     (t.gen/generate username-generator)
                old-password (t.gen/generate password-generator)
                new-password (t.gen/generate password-generator)]
            (qhr.edge.db/update-user-password! db
                                               username
                                               old-password
                                               new-password))))
      (expecting "incorrect old password"
        (expect nil?
          (let [username     (:username user)
                old-password (t.gen/generate password-generator)
                new-password (t.gen/generate password-generator)]
            (qhr.edge.db/update-user-password! db
                                               username
                                               old-password
                                               new-password))))
      (expecting "success"
        (with-redefs [buddy.hash/derive #(str % "!")]
          (let [new-password (t.gen/generate password-generator)]
            (expect {:username (:username user)
                     :password (buddy.hash/derive new-password)}
              (in (let [username     (:username user)
                        old-password (:password user)]
                    (qhr.edge.db/update-user-password! db
                                                       username
                                                       old-password
                                                       new-password))))))))))
