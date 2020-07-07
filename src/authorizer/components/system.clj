(ns authorizer.components.system
  (:require [com.stuartsierra.component :as component]
            [authorizer.components.storage :as storage]
            [authorizer.components.console :as console]))

(defn- build []
  (component/system-map
   :console  (component/using (console/new-program) [:storage])
   :storage (storage/new-in-memory)))

(defn build-and-start [] (component/start (build)))
