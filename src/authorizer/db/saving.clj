(ns authorizer.db.saving
  (:require
   [authorizer.protocols.storage-client :as storage]
   [authorizer.model :as m]
   [schema.core :as s]))

(s/defn save!
  [state :- m/State
   storage :- storage/IStorageClient]
  (storage/put! storage state))

(s/defn get-account [storage :- storage/IStorageClient]
  (storage/read-all storage))

