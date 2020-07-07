(ns authorizer.components.console
  (:require [authorizer.logic :as l]
            [com.stuartsierra.component :as component]
            [authorizer.protocols.storage-client :as storage-client]
            [authorizer.db.saving :as db]
            [schema.core :as s]))

(s/defn main-loop!
  "interact with user and process the new state"
  [storage :- storage-client/IStorageClient]
  (let [input (read-line)
        current-account (db/get-account storage)
        validation-state (l/action-handler current-account input)]
    (when validation-state
      (println (l/get-account-json validation-state))
      (db/save! (dissoc validation-state :violations) storage)
      (recur storage))))

(defrecord ConsoleProgram []
  component/Lifecycle
  (start [this] (do (main-loop! (:storage this)) this))
  (stop  [this]  this))

(defn new-program [] (->ConsoleProgram))