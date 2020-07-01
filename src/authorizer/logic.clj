(ns authorizer.logic
  (:require [clojure.data.json :as j]))

(defn parse-json-input [json] (try
                                (j/read-str json :key-fn keyword)
                                (catch Exception _ nil)))

(defn is-account? [input] (contains? input :account))
(defn is-transaction? [input] (contains? input :transaction))

(defn add-violation [validation-state violation]
  (update validation-state :violations conj violation))

(defn create-account [current-state account-info]
  (if (:state current-state)
    (add-violation current-state :account-already-initialized)
    {:state account-info, :violations []}))

(defn apply-violation [should-apply violation-name validation-state]
  (if should-apply
    (add-violation validation-state violation-name)
    validation-state))

(defn has-no-limit? [amount account]
  (-> account
      :account :availableLimit
      (< amount)))

(defn validate-limit [validation-state transaction]
  (let [account (:state validation-state)]
    (-> transaction :transaction :amount
        (has-no-limit? account)
        (apply-violation :insufficient-limit validation-state))))

(defn card-is-not-active? [account]
  (-> account :account :activeCard (not)))

(defn validate-active-card [validation-state]
  (let [account (:state validation-state)]
    (-> account
        (card-is-not-active?)
        (apply-violation :card-not-active validation-state))))
