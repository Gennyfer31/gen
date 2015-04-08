(ns gen.core)

(defmacro exit-if-error [& body]
  `(try
     ~@body
     (catch js/Error ~'err
       (.error js/console "ERR: %s" (str ~'err))
       (.exit js/process 1))))
