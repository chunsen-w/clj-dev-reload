## What is this?
This is a libary to help reload clj files automatically when they change.

## How to use it?
```clojure
(require '[com.github.clojure.dev-reload :as reload])
(reload/start {:dirs ["src"]}))
```
This will watch files changes in the src directory and reload them.
#### If you need to do something after a namespace is reload
You can define a function with metadata `:on-load` in the namespace, then this function will be called after the namespace is reloaded.

#### If you need to do something after whatever is reloaded
You can use the optiong `:after-reload` when starting the reloader. eg:
```clojure
(reload/start {:dirs ["src"]
               :after-reload (fn [] (println "reloaded!"))}))
```