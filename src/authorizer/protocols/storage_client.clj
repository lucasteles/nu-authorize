(ns authorizer.protocols.storage-client
  (:require [schema.core :as s]))

(defprotocol StorageClient
  (read-all   [storage])
  (put!       [storage value]))

(def IStorageClient (s/protocol StorageClient))
