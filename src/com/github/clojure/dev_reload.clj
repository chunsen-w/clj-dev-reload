(ns com.github.clojure.dev-reload
  (:require [clojure.core.async :refer [timeout go <! thread-call]]
            [nextjournal.beholder :as beholder]
            [clojure.tools.namespace.track :as track]
            [clojure.tools.namespace.dir :as dir]
            [clojure.tools.namespace.reload :as reload]))

(defn- load-enabled? [sym]
  (let [ns-load-meta (:hot-reload/load (meta (find-ns sym)))]
    (not (false? ns-load-meta))))

(defn- unload-enabled? [opts sym]
  (let [ns-unload-meta (:hot-reload/unload (meta (find-ns sym)))]
    (or
     (true? ns-unload-meta)
     (and (:unload? opts)
          (not (false? ns-unload-meta))))))


(defn update-load [{::track/keys [unload load] :as tracker} opts]
  (assoc tracker
         ::track/unload (filter (partial unload-enabled? opts) unload)
         ::track/load (filter load-enabled? load)))

(defn- print-pending-reloads [tracker]
  (when-let [r (seq (::track/unload tracker))]
    (prn "[dev-relaod] " :unload r))
  (when-let [r (seq (::track/load tracker))]
    (prn "[dev-relaod] " :load r)))

(def global-tracker (atom {}))

(defn- run-reload [{:keys [dirs after-reload] :as opts}]
  (let [tracker @global-tracker
        new-tracker (dir/scan-dirs tracker dirs)
        new-tracker (update-load new-tracker opts)]
    (print-pending-reloads new-tracker)
    (reset! global-tracker (reload/track-reload new-tracker))
    (when (::reload/error @global-tracker)
      (println "[dev-relaod] error loading ns" (::reload/error-ns @global-tracker))
      (println (::reload/error @global-tracker)))
    (doseq [ns-sym (::track/load new-tracker)]
      (let [vars (vals (ns-publics ns-sym))
            on-reload (filter (fn [symbol] (:on-load (meta symbol))) vars)]
        (doseq [v on-reload]
          (println "executin reload hook " v)
          (v))))
    (when after-reload (after-reload))))

(defn debounce
  "a simple debounce function"
  [fun delay]
  (let [index (atom 0)]
    (fn [& args]
      (let [exec-index (swap! index inc)]
        (go
          (<! (timeout delay))
          (when (= exec-index @index)
            (thread-call #(apply fun args))))
        nil))))

(defn start
  "Start the hot-reload, return a function to stop the watcher
   
  options: 
    :unload? - a boolean to indicate if unload the changed namespaces before load, default false
    :dirs - a list of directories to watch  
    :after-reload - a function to be called after each reload"
  [{:keys [dirs] :as opt}]
  (let [watcher (apply beholder/watch (debounce (fn [_] (run-reload opt))
                                                1000) dirs)]
    (println "hot-relaod started, waching dirs " dirs " ...")
    (run-reload opt)
    (fn [] (beholder/stop watcher))))
