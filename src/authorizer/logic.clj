(ns authorizer.logic
  (:require [clojure.data.json :as j]))

(defn parse-json-input [json] (try
                                (j/read-str json :key-fn keyword)
                                (catch Exception _ nil)))

(defn is-account? [input] (contains? input :account))
(defn is-transaction? [input] (contains? input :transaction))

(defn add-violation [current-state violation]
  (update current-state :violations conj violation))

(defn create-account [current-state account-info]
  (if (:state current-state)
    (add-violation current-state :account-already-initialized)
    {:state account-info, :violations []}))

(defn has-no-limit? [account amount] (< (get-in account [:account :availableLimit]) amount))

(defn apply-violation [violation-name current-state should-apply]
  (if should-apply
    (add-violation current-state violation-name)
    current-state))

(defn validate-limit [current-state transaction]
  (let [account (:state current-state)]
    (->> transaction :transaction :amount
         (has-no-limit? account)
         (apply-violation :insufficient-limit current-state))))