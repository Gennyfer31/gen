(ns gen.fs
  (:require-macros [gen.core :refer [do-or-exit]]))

(def fs (js/require "fs"))
(def path (js/require "path"))
(def mkdirp (js/require "mkdirp"))

(defn file-seq [dir]
  (lazy-seq
    (let [{subdirs true files false} (->> (.readdirSync fs dir)
                                          (map #(.join path dir %))
                                          (group-by #(.isDirectory (.statSync fs %))))]
      (concat files (mapcat file-seq subdirs)))))

(defn copy-dir [in out transformer]
  (let [base-dir-offset (inc (count in))]
    (letfn
      [(remove-base-dir [file]
         (subs file base-dir-offset))
       (copy-file [file]
         (.sync mkdirp (.dirname path (.join path out file)))
         (let [from (.join path in file)
               to (.join path out file)]
           (->> (.readFileSync fs from)
                (transformer)
                (.writeFileSync fs to))))]
      (do-or-exit
        (doseq [file (map remove-base-dir (file-seq in))]
          (copy-file file))))))
