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

(defn get-time [tx] (-> tx :transaction :time (f/parse)))
(defn diff-time-in-minutes [[t1 t2]] (t/in-minutes (t/interval t1 t2)))

(defn is-high-frequency? [new-transaction applied-transactions]
  (->> new-transaction
       (conj applied-transactions)
       (map get-time)
       (sort >)
       (take max-transactions-window)
       (partition 2 1)
       (map diff-time-in-minutes)
       (reduce +)
       (> time-window)))

(def t1 {:transaction {:merchant "Burger King", :amount 20, :time "2019-01-1T10:01:00.000Z"}})
(def t2 {:transaction {:merchant "Burger King", :amount 20, :time "2019-01-1T10:02:00.000Z"}})
(def t3 {:transaction {:merchant "Burger King", :amount 20, :time "2019-01-1T10:03:00.000Z"}})
(def ts [t1 t2])

(diff-time-in-minutes [(get-time t1) (get-time t2)])

; (is-high-frequency? t3 ts)
  (->> t3
       (conj ts)
       (map get-time)
       (sort t/before?)
       (take max-transactions-window)
       (partition 2 1)
       (map diff-time-in-minutes)
       (reduce +)
       (< time-window)
       )