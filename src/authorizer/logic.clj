(ns authorizer.logic
  (:require [clojure.data.json :as j]
            [clj-time.format :as f]
            [clj-time.core :as t]))

(def time-window 2)
(def max-transaction-count 3)

(defn parse-json-input [json] (try
                                (j/read-str json :key-fn keyword)
                                (catch Exception _ nil)))

(defn is-account? [input] (contains? input :account))
(defn is-transaction? [input] (contains? input :transaction))

(defn add-violation [validation-state violation]
  (update validation-state :violations conj violation))

(defn create-account [current-state account-info]
  (if (:account current-state)
    (add-violation current-state :account-already-initialized)
    (merge account-info {:transactions []  :violations []})))

(defn apply-violation [should-apply violation-name validation-state]
  (if should-apply
    (add-violation validation-state violation-name)
    validation-state))

(defn get-updated-account [state]
  (let [debit (->> state :transactions (map :amount) (reduce +))]
    (update-in state [:account :availableLimit] - debit)))

(defn has-no-limit? [amount account]
  (-> account
      :account :availableLimit
      (< amount)))

(defn is-card-not-active? [account]
  (-> account :account :activeCard (not)))

(defn get-time [tx] (-> tx :time (f/parse)))

(defn- have-enouth-transactions? [applied-transactions]
  (>= (count applied-transactions) (dec max-transaction-count)))

(defn minutes-before-transaction [tx] (-> tx (get-time) (t/plus (t/minutes (- time-window)))))
(defn after-or-equal? [this date] (or (t/after? this date) (t/equal? this date)))

(defn- have-more-transactions-than-allowed? [new-transaction applied-transactions]
  (let [minutes-earlier (minutes-before-transaction new-transaction)]
    (->> applied-transactions
         (map get-time)
         (filter #(after-or-equal? % minutes-earlier))
         (count)
         (<= max-transaction-count))))

(defn is-high-frequency? [applied-transactions new-transaction]
  (and (have-enouth-transactions? applied-transactions)
       (have-more-transactions-than-allowed? new-transaction applied-transactions)))

(defn is-doubled? [last-transaction new-transaction]
  (let [get-submap #(-> %  (select-keys [:merchant :amount]))
        minutes-earlier (minutes-before-transaction new-transaction)]
    (and
     (= (get-submap new-transaction) (get-submap last-transaction))
     (-> last-transaction
         (get-time)
         (after-or-equal? minutes-earlier)))))

(defn validate-limit [validation-state transaction]
  (-> transaction
      :amount
      (has-no-limit? validation-state)
      (apply-violation :insufficient-limit validation-state)))

(defn validate-active-card [validation-state]
  (-> validation-state
      (is-card-not-active?)
      (apply-violation :card-not-active validation-state)))

(defn validate-transaction-frequency [validation-state transaction]
  (-> validation-state
      :transactions
      (is-high-frequency? transaction)
      (apply-violation :high-frequency-small-interval validation-state)))

(defn validate-doubled-transaction [validation-state transaction]
  (-> validation-state
      :transactions
      (last)
      (is-doubled? transaction)
      (apply-violation :doubled-transaction validation-state)))

(defn save-transaction [current-state transaction]
  (update current-state :transactions conj transaction))

(defn has-violations? [state] 
  (-> state :violations (empty?) (not))) 

(defn process-transaction [app-state transaction]
  (-> app-state
      (get-updated-account)
      (merge {:violations []})
      (validate-active-card)
      (validate-limit transaction)
      (validate-doubled-transaction transaction)
      (validate-transaction-frequency transaction)))