(ns gen.fs
  (:require-macros [gen.core :refer [exit-if-error]]))

(def fs (js/require "fs"))
(def path (js/require "path"))
(def mkdirp (js/require "mkdirp"))

(defn file-seq [dir]
  (lazy-seq
    (let [{subdirs true files false} (->> (.readdirSync fs dir)
                                          (map #(.join path dir %))
                                          (group-by #(.isDirectory (.statSync fs %))))]
      (concat files (mapcat file-seq subdirs)))))

(defn copy-dir [in out content-transform path-transform]
  (letfn
    [(remove-base-dir [file] (subs file (inc (count in))))
     (copy-file [file]
       (let [from (.join path in file)
             to (.join path out (path-transform file))]
         (.sync mkdirp (.dirname path to))
         (->> (.readFileSync fs from #js {:encoding "utf8"})
              (content-transform)
              (.writeFileSync fs to))))]
    (exit-if-error
      (doseq [file (map remove-base-dir (file-seq in))]
        (copy-file file)))))
