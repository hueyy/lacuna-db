(ns input.utils.log
  (:require [taoensso.timbre :as timbre]))

(defmacro debug [& args] `(timbre/debug ~@args))

(defmacro error [& args] `(timbre/error ~@args))