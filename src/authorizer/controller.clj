(ns authorizer.controller
  (:require [authorizer.logic :as l]
            [authorizer.model :as m]
            [authorizer.db.saving :as db]
            [authorizer.protocols.storage-client :as storage-client]
            [schema.core :as s]))

(defn print-account [storage validation]
  (-> storage
      (db/get-account)
      (l/get-updated-account)
      (select-keys [:account])
      (l/get-account-json (:violations validation))
      (println)))

(s/defn update-state!
  [storage :- storage-client/IStorageClient
   validation :- m/HandlerResult
   input :- (s/either m/Account m/TransactionInput)]
  (let [violations (:violations validation)
        type (:type validation)
        should-save (empty? violations)]
    (when should-save
      (case type
        :account     (db/save-account! input storage)
        :transaction (db/add-transaction! input storage)))))

(defn process-input! [storage input]
  (let [current-account (db/get-account storage)
        validation (l/action-handler current-account input)]
    (update-state! storage validation input)
    (print-account storage validation)))