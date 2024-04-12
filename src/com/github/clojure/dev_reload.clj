(ns com.github.clojure.dev-reload
  (:require [clojure.core.async :refer [timeout go <! thread-call]]
            [nextjournal.beholder :as beholder]
            [clojure.tools.namespace.track :as track]
            [clojure.tools.namespace.dir :as dir]
            [clojure.tools.namespace.reload :as reload]))


(defn- load-disabled? [sym]
  (false? (:hot-reload/load (meta (find-ns sym)))))

(defn- unload-disabled? [sym]
  (not (:hot-reload/unload (meta (find-ns sym)))))


(defn update-load [{::track/keys [unload load] :as tracker}]
  (assoc tracker
         ::track/unload (remove unload-disabled? unload)
         ::track/load (remove load-disabled? load)))

(defn- print-pending-reloads [tracker]
  (when-let [r (seq (::track/unload tracker))]
    (prn :unload r))
  (when-let [r (seq (::track/load tracker))]
    (prn :load r)))

(def global-tracker (atom {}))

(defn- run-reload [{:keys [dirs after-reload] :as opts}]
  (let [tracker @global-tracker
        new-tracker (dir/scan-dirs tracker dirs)
        new-tracker (update-load new-tracker)]
    (print-pending-reloads new-tracker)
    (reset! global-tracker (reload/track-reload new-tracker))
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
    :dirs - a list of directories to watch  
    :after-reload - a function to be called after each reload"
  [{:keys [dirs] :as opt}]
  (let [watcher (apply beholder/watch (debounce (fn [_] (run-reload opt))
                                                1000) dirs)]
    (println "hot-relaod started, waching dirs " dirs " ...")
    (run-reload opt)
    (fn [] (beholder/stop watcher))))
