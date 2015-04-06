(ns gen.core)

(defmacro do-or-exit [& body]
  `(try
     ~@body
     (catch js/Error ~'err
       (.error js/console "ERR: %s" (str ~'err))
       (.exit js/process 1))))
