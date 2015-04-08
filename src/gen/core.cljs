(ns gen.core
  (:require-macros [gen.core :refer [exit-if-error]]
                   [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.nodejs :as nodejs]
            [cljs.tools.cli :refer [parse-opts]]
            [clojure.string :as string]
            [cljs.core.async :refer [chan <! >!]]
            [gen.fs :refer [copy-dir]]))

(def fs (js/require "fs"))
(def path (js/require "path"))
(def hogan (js/require "hogan.js"))
(def readline (js/require "readline"))

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
  (exit-if-error
    (let [home (or (-> js/process .-env .-HOME)
                   (-> js/process .-env .-USERPROFILE))]
      (->> (.join path home ".genappconfig")
           (#(.readFileSync fs % #js {:encoding "utf8"}))
           (.parse js/JSON)
           (.-roots)
           (map (fn [root] (string/replace root #"^~" home)))))))

(defn list-bundles []
  (letfn
    [(extract-bundle-names []
       (exit-if-error
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
             (.exit js/process 0)))))
     (mustache-transform [context template]
       (-> hogan (.compile template) (.render context)))
     (construct-context [spec]
       (let [rl (.createInterface readline #js{:input (.-stdin js/process)
                                               :output (.-stdout js/process)})
             question (fn [query]
                        (let [c (chan)]
                          (.question rl query (fn [answer] (go (>! c answer))))
                          c))
             parse-context (fn parse-context [spec prefix]
                             (go-loop [spec spec ctx {} prefix prefix]
                               (if-let [[k v] (first spec)]
                                 (cond
                                   (string? v) (recur (rest spec)
                                                      (assoc ctx k (<! (question (str prefix k "? "))))
                                                      prefix)
                                   (number? v) (let [n (js/parseInt (<! (question (str prefix k "? (number) "))))]
                                                 (if (js/isNaN n)
                                                   (do
                                                     (print "It's not number.")
                                                     (recur spec ctx prefix))
                                                   (recur (rest spec) (assoc ctx k n) prefix)))
                                   (= (type v) js/Boolean) (let [s (string/lower-case (<! (question (str prefix k "? (yes/no) "))))]
                                                             (cond
                                                               (or (= s "yes") (= s "y")) (recur (rest spec) (assoc ctx k true) prefix)
                                                               (or (= s "no") (= s "n")) (recur (rest spec) (assoc ctx k false) prefix)
                                                               :else (do
                                                                       (print "yes or no please.")
                                                                       (recur spec ctx prefix))))
                                   (= (type v) js/Function) (recur (rest spec) (assoc ctx k v) prefix)
                                   (map? v) (recur (rest spec)
                                                   (assoc ctx k (<! (parse-context (js->clj v) (str k "> "))))
                                                   prefix))
                                 ctx)))]
         (go
           (try
             (<! (parse-context spec ""))
             (finally (.close rl))))))
     (post-process-context [post-process-fn context]
       (let [c (chan)]
         (if (instance? js/Function post-process-fn)
           (post-process-fn context #(go (>! c context)))
           (go (>! c context)))
         c))]
    (go
      (exit-if-error
        (let [bundle-dir (find-bundle-dir bundle)
              bundle-context (js/require (str bundle-dir ".js"))
              context (->> (.-context bundle-context)
                           (js->clj)
                           (construct-context)
                           (<!)
                           (clj->js)
                           (post-process-context (.-postProcess bundle-context))
                           (<!))]
          (copy-dir bundle-dir (or dir ".") (partial mustache-transform context)))))))

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
