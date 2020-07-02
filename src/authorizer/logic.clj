(ns authorizer.logic
  (:require [clojure.data.json :as j]
            [clj-time.format :as f]
            [clj-time.core :as t]))

(def time-window 2)
(def max-transactions-window 3)

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

; TODO : testar esse cara e adicionar ele no has-no-limit
(defn get-updated-account [state]
  (let [debit (->> state :transactions (map :amount) (reduce +))]
    (update-in state [:account :availableLimit] - debit)))

(defn has-no-limit? [amount account]
  (-> account
      :account :availableLimit
      (< amount)))

(defn is-card-not-active? [account]
  (-> account :account :activeCard (not)))

(defn get-time [tx] (-> tx :transaction :time (f/parse)))
(defn diff-time
  ([t1 t2] (t/in-millis (t/interval t1 t2)))
  ([[t1 t2]] (diff-time t1 t2)))
(defn milis->minutes [miliseconds] (/ (/ miliseconds 1000) 60))

(defn- have-enouth-transactions? [applied-transactions]
  (>= (count applied-transactions) (dec max-transactions-window)))

(defn- have-more-transactions-than-allowed? [new-transaction applied-transactions]
  (->> new-transaction
       (conj applied-transactions)
       (map get-time)
       (sort t/before?)
       (take max-transactions-window)
       (partition 2 1)
       (map diff-time)
       (reduce +)
       (milis->minutes)
       (>= time-window)))

(defn is-high-frequency? [applied-transactions new-transaction]
  (and (have-enouth-transactions? applied-transactions)
       (have-more-transactions-than-allowed? new-transaction applied-transactions)))

(defn is-doubled? [new-transaction last-transaction]
  (let [get-submap #(-> % :transaction (select-keys [:merchant :amount]))]
    (and
     (= (get-submap new-transaction) (get-submap last-transaction))
     (-> new-transaction
         (get-time)
         (diff-time (get-time last-transaction))
         (milis->minutes)
         (<= time-window)))))

(defn validate-limit [validation-state transaction]
  (-> transaction
      :transaction :amount
      (has-no-limit? validation-state)
      (apply-violation :insufficient-limit validation-state))
      )

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