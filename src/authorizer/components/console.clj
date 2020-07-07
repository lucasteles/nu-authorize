(ns authorizer.components.console
  (:require [authorizer.logic :as l]
            [authorizer.protocols.storage-client :as storage-client]
            [authorizer.controller :as controller]

            [com.stuartsierra.component :as component]
            [schema.core :as s]))

(s/defn main-loop!
  "interact with user and process the new state"
  [storage :- storage-client/IStorageClient]
  (when-let [input (l/parse-json-input (read-line))]
    (controller/process-input! storage input)
    (recur storage)))

(defrecord ConsoleProgram []
  component/Lifecycle
  (start [this] (do (main-loop! (:storage this)) this))
  (stop  [this]  this))

(defn new-program [] (->ConsoleProgram))