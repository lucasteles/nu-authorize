(ns authorizer.components.storage
  (:require [com.stuartsierra.component :as component]
            [authorizer.protocols.storage-client :as storage-client]
            [authorizer.model :as m]))

(defrecord InMemoryStorage [storage]
  component/Lifecycle
  (start [this] this)
  (stop  [this]
    (reset! storage  m/initial-state)
    this)

  storage-client/StorageClient
  (read-all [_this] @storage)
  (put! [_this value] (reset! storage value)))

(defn new-in-memory []
  (->InMemoryStorage (atom m/initial-state)))