(ns authorizer.db.saving
  (:require
   [authorizer.protocols.storage-client :as storage]
   [authorizer.model :as m]
   [schema.core :as s]))

(s/defn save-account!
  [account :- m/Account
   storage :- storage/IStorageClient]
  (let [account (:account account)]
    (storage/put! storage #(assoc % :account account))))

(s/defn add-transaction!
  [transaction :- m/TransactionInput
   storage :- storage/IStorageClient]
  (storage/put! storage #(update % :transactions conj (:transaction transaction))))

(s/defn get-account [storage :- storage/IStorageClient]
  (storage/read-all storage))

