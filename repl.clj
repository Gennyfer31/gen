(require '[cljs.repl :as repl]
         '[cljs.repl.node :as node])

(repl/repl (node/repl-env)
           :watch "src"
           :output-dir "target/dev"
           :optimizations :none)
