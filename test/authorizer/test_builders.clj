(ns authorizer.test-builders
  (:require [schema.core :as s]
            [authorizer.model :as m]))

(def StateLike (merge m/State { (s/optional-key :violations) m/Violations }))

(s/def an-account :- m/Account
  {:account {:activeCard true :availableLimit 0}})

(s/defn account-with-limit :- m/Account
  "return a account copy with the new limit setted"
  [limit :- s/Int, account :- m/Account]
  (assoc-in account [:account :availableLimit] limit))

(s/defn account-active :- m/Account
  "return a account copy with activeCard true"
  [account :- m/Account] (assoc-in account [:account :activeCard] true))

(s/defn account-inactive :- m/Account
  "return a account copy with activeCard false"
  [account :- m/Account]
  (assoc-in account [:account :activeCard] false))

(s/def a-transaction :- m/Transaction
  {:merchant "Merchant name"
   :amount 0
   :time  "2019-01-01T10:00:00.000Z"})

(s/defn tx-with-amount :- m/Transaction
  "return a transaction copy with new amount"
  [amount :- s/Int, transaction :- m/Transaction]
  (assoc-in transaction [:amount] amount))

(s/defn tx-with-merchant :- m/Transaction
  "return a transaction copy with new merchant name"
  [name :- s/Str, transaction :- m/Transaction]
  (assoc-in transaction [:merchant] name))

(s/defn tx-as-input :- m/TransactionInput
  "wraps the transaction in an map with :transaction keyword, used for simulate an user input"
  [tx :- m/Transaction] {:transaction tx})

(s/defn tx-with-time :- m/Transaction
  "return a transaction copy with new time setted"
  [minutes :- s/Int, seconds :- s/Int, transaction :- m/Transaction]
  (assoc-in transaction [:time]
            (format "2019-01-01T10:%02d:%02d.000Z" minutes seconds)))

(s/def initial-state :- m/State
  (merge an-account {:transactions []}))

(s/def initial-validation-state :- m/ValidationState
  (merge initial-state {:violations []}))

(s/defn with-availableLimit :- StateLike
  "return new state with availableLimit setted"
  [limit :- s/Int, current-state :- StateLike]
  (assoc-in current-state [:account :availableLimit] limit))

(s/defn with-activeCard :- StateLike
  "return new state with activeCard setted"
  [active :- s/Bool current-state :- StateLike]
  (assoc-in current-state [:account :activeCard] active))

(s/defn with-transactions :- StateLike
  "return new state with transactions setted"
  [transactions :- [m/Transaction] current-state :- StateLike]
  (assoc-in current-state [:transactions] transactions))

(s/defn with-account :- StateLike
  "return new state with account setted"
   [account :- m/Account  current-state :- StateLike]
  (merge current-state account))

(s/defn add-transaction :- StateLike
  "return a copy of the state with the transaction added"
  [transaction :- m/Transaction current-state :- StateLike]
  (update-in current-state [:transactions] conj transaction))

(s/defn with-violation :- StateLike
  "return a copy of the state with the violation added"
  [violation :- s/Keyword current-state :- StateLike]
  (update current-state :violations conj violation))

(s/defn with-violations :- StateLike
  "return a copy of the state with all violations setted"
  [violations :- m/Violations current-state :- StateLike]
  (assoc current-state :violations violations))

(s/defn active :- StateLike
  "return a copy of the state with activated account"
  [current-state :- StateLike] (assoc-in current-state [:account :activeCard] true))

(s/defn inactive :- StateLike
  "return a copy of the state with inactivated account"
  [current-state :- StateLike] (assoc-in current-state [:account :activeCard] false))

(s/defn remove-violations :- m/State
  "removes the violations keyword, turning the validation state into a state"
  [state :- m/ValidationState]
  (dissoc state :violations))