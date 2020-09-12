(ns qahira.edge.db
  (:require
   [buddy.hashers :as buddy.hash]
   [camel-snake-kebab.core :as csk]
   [camel-snake-kebab.extras :as cske]
   [clj-time.core :as time]
   [clj-time.jdbc]
   [clojure.java.jdbc :as jdbc]
   [clojure.set :as set]
   [honeysql.core :as sql]
   [honeysql-postgres.format]
   [honeysql.helpers :as sql.help]
   [honeysql-postgres.helpers :as psql.help]
   [orchid.components.hikari-cp]
   [taoensso.encore :as e]))

(defprotocol UserDb
  (-create-user! [user-db new-user])
  (-find-user [user-db username])
  (-update-user! [user-db username attrs])
  (-delete-user! [user-db username])
  (-restore-user! [user-db username]))

(defn register-user!
  [user-db new-user]
  (-create-user! user-db (-> new-user
                             (update :passwoord buddy.hash/derive)
                             (assoc :id (java.util.UUID/randomUUID)
                                    :date-created (time/now)
                                    :exists? true))))
(defn login-user
  [user-db username password]
  (e/when-let [user          (-find-user user-db username)
               user-password (:password user)
               _             (e/catching
                               (buddy.hash/check password user-password))]
    user))

(defn delete-user!
  [user-db username]
  (-delete-user! user-db username))

(defn restore-user!
  [user-db username]
  (-restore-user! user-db username))

(defn reset-user-password!
  [user-db username new-password]
  (let [attrs {:password (buddy.hash/derive new-password)}]
    (-update-user! user-db username attrs)))

(defn update-user-password!
  [user-db username old-password new-password]
  (when (some? (login-user user-db username old-password))
    (reset-user-password! user-db username new-password)))

(defn- transform-keys
  [m]
  (cske/transform-keys csk/->kebab-case m))

(defn- fetch-one
  ([spec statement opts]
   (transform-keys (first (jdbc/query spec (sql/format statement) opts))))
  ([spec statement]
   (transform-keys (first (jdbc/query spec (sql/format statement))))))

(defn- as-write-result
  [result]
  {:database/result    result
   :database/happened? (-> result first pos-int?)})

(defn- exec!
  ([spec statement opts]
   (as-write-result (jdbc/execute! spec (sql/format statement) opts)))
  ([spec statement]
   (as-write-result (jdbc/execute! spec (sql/format statement)))))

(def ^:private app-user-key->sql-user-key
  {:exist? :is-exist})

(def ^:private sql-user-key->app-user-key
  (e/invert-map app-user-key->sql-user-key))

(extend-protocol UserDb
  orchid.components.hikari_cp.HikariCP
  (-create-user! [user-db new-user]
    (let [attrs   (set/rename-keys new-user app-user-key->sql-user-key)
          stmt    (-> (sql.help/insert-into :users)
                      (sql.help/values [attrs])
                      (psql.help/returning :*))
          fetched (fetch-one user-db stmt)]
      (set/rename-keys fetched sql-user-key->app-user-key)))
  (-find-user [user-db username]
    (let [stmt    (-> (sql.help/select :*)
                      (sql.help/from :users)
                      (sql.help/where [:= :username username]
                                      [:= :is-exist true]))
          fetched (fetch-one user-db stmt)]
      (set/rename-keys fetched sql-user-key->app-user-key)))
  (-update-user! [user-db username attrs]
    (let [new-attrs (set/rename-keys attrs app-user-key->sql-user-key)
          stmt      (-> (sql.help/update :users)
                        (sql.help/sset new-attrs)
                        (sql.help/where [:= :username username]
                                        [:= :is-exist true])
                        (psql.help/returning :*))
          fetched   (fetch-one user-db stmt)]
      (set/rename-keys fetched sql-user-key->app-user-key)))
  (-delete-user! [user-db username]
    (let [stmt   (-> (sql.help/update :users)
                     (sql.help/sset {:is-exist false})
                     (sql.help/where [:= :username username]
                                     [:= :is-exist true]))
          result (exec! user-db stmt)]
      result))
  (-restore-user! [user-db username]
    (let [stmt    (-> (sql.help/update :users)
                      (sql.help/sset {:is-exist true})
                      (sql.help/where [:= :username username]
                                      [:= :is-exist false])
                      (psql.help/returning :*))
          fetched (fetch-one user-db stmt)]
      (set/rename-keys fetched sql-user-key->app-user-key))))

(extend-protocol jdbc/IResultSetReadColumn
  org.postgresql.util.PGobject
  (result-set-read-column [this _ _]
    (let [kind  (.getType this)
          value (.getValue this)]
      (case kind
        "citext" (str value)
        :else    value))))
