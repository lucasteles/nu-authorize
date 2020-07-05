(ns authorizer.logic
  (:require [clojure.data.json :as j]
            [clj-time.format :as f]
            [authorizer.model :as m]
            [schema.core :as s]
            [clj-time.core :as t]))

(def time-window 2)
(def max-transaction-count 3)

(defn parse-json-input  [json] (try
                                 (j/read-str json :key-fn keyword)
                                 (catch Exception _ nil)))

(defn is-account? [input] (contains? input :account))
(defn is-transaction? [input] (contains? input :transaction))

(s/defn add-violation :- m/ValidationState
  "add the violation in the violations array of the state"
  [validation-state :- m/ValidationState
   violation :- s/Keyword]
  (update validation-state :violations conj violation))

(s/defn as-validation :- m/ValidationState
  [state :- m/State] (assoc state :violations []))

(s/defn create-account :- m/ValidationState
  "returns a new state with the account created"
  [current-state :- m/State
   account-info :- m/Account]
  (if (:account current-state)
    (add-violation (as-validation current-state) :account-already-initialized)
    (merge account-info {:transactions []  :violations []})))

(s/defn apply-violation :- m/ValidationState
  "if the codition is true apply the add-validation function"
  [should-apply :- s/Bool
   violation-name :- s/Keyword
   validation-state :- m/ValidationState]
  (if should-apply
    (add-violation validation-state violation-name)
    validation-state))

(s/defn get-updated-account :- m/ValidationState
  "return a new account with all transactions applied to it"
  [state :- m/ValidationState]
  (let [debit (->> state :transactions (map :amount) (reduce +))]
    (update-in state [:account :availableLimit] - debit)))

(s/defn has-no-limit? :- s/Bool
  "checks if the account has limit to debit the amount"
  [amount :- s/Int
   account :- m/Account]
  (-> account
      :account :availableLimit
      (< amount)))

(s/defn is-card-not-active? :- s/Bool
  "checks if the card of the account is active"
  [account :- m/Account]
  (-> account :account :activeCard (not)))

(s/defn get-time
  "get the parsed time from a transaction"
  [tx :- m/Transaction] (-> tx :time (f/parse)))

(s/defn have-enough-transactions? :- s/Bool
  [applied-transactions :- [m/Transaction]]
  (>= (count applied-transactions) (dec max-transaction-count)))

(s/defn minutes-before-transaction
  "returns a datetime minutes earlier"
  [tx :- m/Transaction]
  (-> tx (get-time) (t/plus (t/minutes (- time-window)))))

(s/defn after-or-equal? :- s/Bool
  "checks if a datetime is after other datetime or if they are equal"
  [this date]
  (or (t/after? this date) (t/equal? this date)))

(s/defn have-more-transactions-than-allowed? :- s/Bool
  "checks if the number of transactions in a period of time is more then allowed"
  [new-transaction :- m/Transaction
   applied-transactions :- [m/Transaction]]
  (let [minutes-earlier (minutes-before-transaction new-transaction)]
    (->> applied-transactions
         (map get-time)
         (filter #(after-or-equal? % minutes-earlier))
         (count)
         (<= max-transaction-count))))

(s/defn is-high-frequency? :- s/Bool
  "checks if there are the number of transactions applied and are more than allowed"
  [applied-transactions :- [m/Transaction]
   new-transaction :- m/Transaction]
  (and (have-enough-transactions? applied-transactions)
       (have-more-transactions-than-allowed? new-transaction applied-transactions)))

(s/defn is-doubled? :- s/Bool
  "checks if the new transaction has the same merchant and amount as the last"
  [last-transaction :- (s/maybe m/Transaction)
   new-transaction :- m/Transaction]
  (let [get-submap #(-> %  (select-keys [:merchant :amount]))
        minutes-earlier (minutes-before-transaction new-transaction)]
    (and
     (= (get-submap new-transaction) (get-submap last-transaction))
     (-> last-transaction
         (get-time)
         (after-or-equal? minutes-earlier)))))

(s/defn select-account :- m/Account
  "get the account from the current validation state"
  [state :- m/ValidationState]
  (select-keys state [:account]))

(s/defn validate-limit :- m/ValidationState
  "validate the account limit for the transaction"
  [validation-state :- m/ValidationState
   transaction :- m/Transaction]
  (-> transaction
      :amount
      (has-no-limit? (select-account validation-state))
      (apply-violation :insufficient-limit validation-state)))

(s/defn validate-active-card :- m/ValidationState
  "add violation if the account is active"
  [validation-state :- m/ValidationState]
  (-> validation-state
      (select-account)
      (is-card-not-active?)
      (apply-violation :card-not-active validation-state)))

(s/defn validate-transaction-frequency
  "add violation if the transaction is more frequent than allowed"
  [validation-state :- m/ValidationState
   transaction :- m/Transaction]
  (-> validation-state
      :transactions
      (is-high-frequency? transaction)
      (apply-violation :high-frequency-small-interval validation-state)))

(s/defn validate-doubled-transaction :- m/ValidationState
  "add violation if the transaction is doubled"
  [validation-state :- m/ValidationState
   transaction :- m/Transaction]
  (-> validation-state
      :transactions
      (last)
      (is-doubled? transaction)
      (apply-violation :doubled-transaction validation-state)))

(s/defn save-transaction :- m/State
  "adds the transaction in the current state"
  [current-state :- m/State
   transaction :- m/Transaction]
  (update current-state :transactions conj transaction))

(s/defn has-violations? :- s/Bool
  "checks if has violations in the state"
  [validation-state :- m/ValidationState]
  (-> validation-state
      :violations
      seq
      boolean))

(s/defn get-account-json :- s/Str
  "return a json representation of the current state"
  [validation-state :- m/ValidationState]
  (j/write-str (select-keys (get-updated-account validation-state) [:account :violations])))

(s/defn validate-transaction :- m/Violations
  "return all violations from apply the transaction in the current state"
  [app-state :- m/State
   transaction :- m/Transaction]
  (-> app-state
      (as-validation)
      (get-updated-account)
      (validate-active-card)
      (validate-limit transaction)
      (validate-doubled-transaction transaction)
      (validate-transaction-frequency transaction)
      (:violations)))

(s/defn process-transaction :- m/ValidationState
  "process the transaction and apply it to the state if there are no violations"
  [app-state :- m/State
   transaction :- m/TransactionInput]
  (let [transaction-data (:transaction transaction)
        violations (validate-transaction app-state transaction-data)]
    (if (empty? violations)
      (as-validation (save-transaction app-state transaction-data))
      (assoc app-state :violations violations))))
