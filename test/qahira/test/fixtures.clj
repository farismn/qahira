(ns qahira.test.fixtures
  (:require
   [orchid.test.fixtures :as orc.t.fxt]
   [qahira.repl :as qhr.repl]
   [taoensso.encore :as e]))

(defn make-system
  [kind]
  (qhr.repl/make-system (e/merge-keywords [kind "test"])))

(defn get-component!
  ([system component-kw]
   (qhr.repl/get-component! system component-kw))
  ([component-kw]
   (get-component! orc.t.fxt/*system* component-kw)))
