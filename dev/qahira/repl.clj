(ns qahira.repl
  (:require
   [clojure.java.io :as io]
   [com.stuartsierra.component.repl :as c.repl]
   [qahira.systems :as qhr.sys]))

(defn make-system
  [system-kind]
  (qhr.sys/make-system (io/resource "qahira/config.edn") system-kind))

(defn get-component!
  [system component-kw]
  (or (get system component-kw)
      (throw (ex-info "missing component" {:component component-kw}))))

(comment

  (c.repl/set-init (fn [_] (make-system :app/dev)))

  (c.repl/set-init (fn [_] (make-system :db/dev)))

  (c.repl/start)

  (c.repl/stop)


  )
