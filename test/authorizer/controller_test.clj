(ns authorizer.controller-test
  (:require
   [clojure.test :refer :all]
   [schema.core :as s]
   [authorizer.components.storage :as storage]
   [authorizer.db.saving :as db]
   [authorizer.test-builders :as b]
   [authorizer.controller :as l]))

(s/set-fn-validation! true)
(defn new-in-memory-with [state]
  (storage/->InMemoryStorage (atom state)))

(deftest process-input!-test
  (testing "The transaction amount should not exceed available limit"
    (let [state (->> b/initial-state (b/with-availableLimit 100))
          storage (new-in-memory-with state)
          tx1 (->> b/a-transaction
                   (b/tx-with-amount 20)
                   (b/tx-with-merchant "Burguer King")
                   (b/tx-as-input))
          tx2 (->> b/a-transaction
                   (b/tx-with-amount 90)
                   (b/tx-with-merchant "other merchant")
                   (b/tx-as-input))
          expected {:account {:activeCard true :availableLimit 100}
                    :transactions [{:merchant "Burguer King"
                                    :amount 20
                                    :time  "2019-01-01T10:00:00.000Z"}]}]
      (l/process-input! storage tx1)
      (l/process-input! storage tx2)
      (is (= expected (db/get-account storage)))))

  (testing "No transaction should be accepted when the card is not active"
    (let [state (->> b/initial-state (b/with-availableLimit 100) (b/inactive))
          storage (new-in-memory-with state)
          tx (->> b/a-transaction
                  (b/tx-with-amount 10)
                  (b/tx-with-merchant "Burguer King") (b/tx-as-input))
          expected {:account {:activeCard false :availableLimit 100}
                    :transactions []}]
      (l/process-input! storage tx)
      (is (= expected (db/get-account storage)))))

  (testing "There should not be more than 3 transactions on a 2 minute interval"
    (let [state (->> b/initial-state (b/with-availableLimit 100))
          storage (new-in-memory-with state)
          tx1 (->> b/a-transaction
                   (b/tx-with-amount 10)
                   (b/tx-with-merchant "Burguer King 1") (b/tx-as-input))
          tx2 (->> b/a-transaction
                   (b/tx-with-amount 10)
                   (b/tx-with-merchant "Burguer King 2") (b/tx-as-input))
          tx3 (->> b/a-transaction
                   (b/tx-with-amount 10)
                   (b/tx-with-merchant "Burguer King 3") (b/tx-as-input))
          tx4 (->> b/a-transaction
                   (b/tx-with-amount 10)
                   (b/tx-with-merchant "Burguer King 4") (b/tx-as-input))
          expected {:account {:activeCard true :availableLimit 100}
                    :transactions [{:merchant "Burguer King 1" :amount 10 :time  "2019-01-01T10:00:00.000Z"}
                                   {:merchant "Burguer King 2" :amount 10 :time  "2019-01-01T10:00:00.000Z"}
                                   {:merchant "Burguer King 3" :amount 10 :time  "2019-01-01T10:00:00.000Z"}]}]
      (l/process-input! storage tx1)
      (l/process-input! storage tx2)
      (l/process-input! storage tx3)
      (l/process-input! storage tx4)
      (is (= expected (db/get-account storage)))))

  (testing "There should have 2 similar transactions (same amount and merchant) in a 2 minutes interval"
    (let [state (->> b/initial-state (b/with-availableLimit 100))
          storage (new-in-memory-with state)
          tx1 (->> b/a-transaction
                   (b/tx-with-amount 10)
                   (b/tx-with-merchant "Burguer King") (b/tx-as-input))
          tx2 (->> b/a-transaction
                   (b/tx-with-amount 10)
                   (b/tx-with-merchant "Burguer King") (b/tx-as-input))
          expected {:account {:activeCard true :availableLimit 100}
                    :transactions [{:merchant "Burguer King" :amount 10 :time  "2019-01-01T10:00:00.000Z"}
                                   {:merchant "Burguer King" :amount 10 :time  "2019-01-01T10:00:00.000Z"}]}]
      (l/process-input! storage tx1)
      (l/process-input! storage tx2)
      (is (= expected (db/get-account storage)))))

  (testing "There should not be more than 2 similar transactions (same amount and merchant) in a 2 minutes interval"
    (let [state (->> b/initial-state (b/with-availableLimit 100))
          storage (new-in-memory-with state)
          tx (->> b/a-transaction
                  (b/tx-with-amount 10)
                  (b/tx-with-merchant "Burguer King") (b/tx-as-input))
          expected {:account {:activeCard true :availableLimit 100}
                    :transactions [{:merchant "Burguer King" :amount 10 :time  "2019-01-01T10:00:00.000Z"}
                                   {:merchant "Burguer King" :amount 10 :time  "2019-01-01T10:00:00.000Z"}]}]
      (l/process-input! storage tx)
      (l/process-input! storage tx)
      (l/process-input! storage tx)
      (is (= expected (db/get-account storage)))))

  (testing "Once created, the account should not be updated or recreated"
    (let [state (->> b/initial-state (b/with-availableLimit 100) (b/active))
          storage (new-in-memory-with state)
          account-input (->> b/an-account (b/account-with-limit 10) (b/account-inactive))
          expected {:account {:activeCard true :availableLimit 100}
                    :transactions []}]
      (l/process-input! storage account-input)
      (is (= expected (db/get-account storage))))))

