(ns qahira.repl
  (:require
   [clojure.java.io :as io]
   [com.stuartsierra.component.repl :as c.repl]
   [qahira.systems :as qhr.sys]))

(defn- make-system
  ([system-kind]
   (qhr.sys/make-system (io/resource "qahira/config.edn") system-kind))
  ([]
   (make-system :app/dev)))

(comment

  (c.repl/set-init (fn [_] (make-system)))

  (c.repl/start)

  (c.repl/stop)


  )
