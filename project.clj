(defproject gen "0.1.0"
  :description "Customizable file generator."
  :license "MIT"
  :url "https://github.com/sethyuan/gen"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-3196"]
                 [org.clojure/tools.cli "0.3.1"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]]
  :plugins [[lein-cljsbuild "1.0.5" :exclusions [org.clojure/clojure]]]
  :cljsbuild {:builds [{:id "dev"
                        :source-paths ["src"]
                        :compiler {:main gen.core
                                   :output-to "lib/gen.js"
                                   :output-dir "target/dev"
                                   :target :nodejs
                                   :source-map true
                                   :optimizations :none
                                   :cache-analysis true
                                   :pretty-print true}}
                       {:id "release"
                        :source-paths ["src"]
                        :compiler {:main gen.core
                                   :output-to "lib/gen.js"
                                   :output-dir "target/release"
                                   :target :nodejs
                                   :optimizations :simple
                                   :pretty-print false}}]})
