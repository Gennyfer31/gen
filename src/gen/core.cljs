(ns gen.core
  (:require-macros [gen.core :refer [do-or-exit]])
  (:require [cljs.nodejs :as nodejs]
            [cljs.tools.cli :refer [parse-opts]]
            [clojure.string :as string]
            [gen.fs :refer [copy-dir]]))

(def fs (js/require "fs"))
(def path (js/require "path"))

(nodejs/enable-util-print!)

(defn usage [summary]
  (->> ["Usage: gen bundle [output]"
        "       gen options"
        ""
        "Options:"
        summary]
       (string/join \newline)))

(defn print-version []
  (print (str "gen v" (.-version (js/require "../package.json")))))

(defn find-roots []
  (do-or-exit
    (let [home (or (-> js/process .-env .-HOME)
                   (-> js/process .-env .-USERPROFILE))]
      (->> (.join path home ".genappconfig")
           (.readFileSync fs)
           (.parse js/JSON)
           (.-roots)
           (map (fn [root] (string/replace root #"^~" home)))))))

(defn list-bundles []
  (letfn
    [(extract-bundle-names []
       (do-or-exit
         (->> (find-roots)
              (mapcat
                (fn [root]
                  (try
                    (filter
                      (fn [file]
                        (and (.isDirectory (.statSync fs (.join path root file)))
                             (not= (.charAt file 0) \.)))
                      (.readdirSync fs root))
                    (catch js/Error err nil))))
              (set))))
     (print-names [names] (doseq [name names] (print name)))]
    (->> (extract-bundle-names) (print-names))))

(defn generate-bundle [[bundle dir]]
  (letfn
    [(find-bundle-dir [bundle]
       (loop [roots (find-roots)]
         (if-let [root (first roots)]
           (try
             (if (some (partial = bundle) (.readdirSync fs root))
               (.join path root bundle)
               (recur (rest roots)))
             (catch js/Error err
               (recur (rest roots))))
           (do
             (.log js/console "'%s' could not be found." bundle)
             (.exit js/process 0)))))]
    (->
      (find-bundle-dir bundle)
      (copy-dir (or dir ".") identity))))

(defn -main [& argv]
  (let [{:keys [options arguments summary]}
        (parse-opts argv [["-l" "--list" "List bundles"]
                          ["-V" "--version" "Version number"]
                          ["-h" "--help"]])]
    (cond
      (:list options) (list-bundles)
      (:version options) (print-version)
      (<= 1 (count arguments) 2) (generate-bundle arguments)
      :else (print (usage summary)))))

(set! *main-cli-fn* -main)
