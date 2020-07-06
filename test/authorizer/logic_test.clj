(ns authorizer.logic-test
  (:require
   [clojure.test :refer :all]
   [authorizer.test-builders :as b]
   [schema.core :as s]
   [authorizer.model :as m]
   [authorizer.logic :as l]))

(s/set-fn-validation! true)

(deftest parse-json-input-test
  (testing "should parse a valid account json"
    (let [valid-json "{ \"account\": { \"activeCard\": true, \"availableLimit\": 100 } }"
          expected-map {:account {:activeCard true, :availableLimit 100}}
          parsed-json (l/parse-json-input valid-json)]
      (is (= parsed-json expected-map))))

  (testing "should parse a valid transaction json"
    (let [valid-json "{ \"transaction\": { \"merchant\": \"Burger King\", \"amount\": 20, \"time\": \"2019-02-13T10:00:00.000Z\" } }"
          expected-map {:transaction
                        {:merchant "Burger King"
                         :amount 20
                         :time "2019-02-13T10:00:00.000Z"}}
          parsed-json (l/parse-json-input valid-json)]
      (is (= parsed-json expected-map))))

  (testing "should return nil if is an invalid json"
    (let [invalid-json "{ invalid json }"
          parsed-json (l/parse-json-input invalid-json)]
      (is (= parsed-json nil)))))

(deftest is-account?-test
  (testing "should return true if the input is an account"
    (let [valid-account {:account "account"}]
      (is (l/is-account? valid-account))))

  (testing "should return false if the input is not an account"
    (let [invalid-account {:not-account "account"}]
      (is (not (l/is-account? invalid-account))))))

(deftest is-transaction?-test
  (testing "should return true if the input is a transaction"
    (let [valid-tx {:transaction "account"}]
      (is (l/is-transaction? valid-tx))))

  (testing "should return false if the input is not a transaction"
    (let [invalid-tx {:not-account "account"}]
      (is (not (l/is-transaction? invalid-tx))))))

(deftest create-account-test
  (testing "should create a new account"
    (let [initial-empty-state m/initial-state
          account-input b/an-account
          new-state (l/create-account initial-empty-state account-input)]
      (is (= (:account new-state) (:account account-input)))))

  (testing "should not have violations when an account is created"
    (let [initial-empty-state m/initial-state
          account-input b/an-account
          new-state (l/create-account initial-empty-state account-input)]
      (is (empty? (:violations new-state)))))

  (testing "should return a violation if an account already exists"
    (let [initial-state b/initial-state
          account-input (->> b/an-account (b/account-with-limit 1))
          new-state (l/create-account initial-state account-input)
          expected-violations [:account-already-initialized]]
      (is (= (:violations new-state) expected-violations))))

  (testing "should not change the current account when have a violation"
    (let [account-input b/an-account
          new-state (l/create-account b/initial-state account-input)]
      (is (=  (:account b/initial-state) (:account new-state))))))

(deftest has-no-limit?-test
  (testing "should return true if the account dont have limit"
    (let [account b/an-account
          amount 1]
      (is (l/has-no-limit? amount account))))

  (testing "should return false if the account have the exaclty limit"
    (let [account (->> b/an-account (b/account-with-limit 1))
          amount 0]
      (is (not (l/has-no-limit? amount account)))))

  (testing "should return false if the account have more then the necessary limit"
    (let [account (->> b/an-account (b/account-with-limit 3))
          amount 1]
      (is (not (l/has-no-limit? amount account))))))

(deftest add-violation-test
  (testing "should add violation to empty state"
    (let [validation-state b/initial-validation-state
          expected-state (->> b/initial-validation-state
                              (b/with-violations [:test-violation]))]
      (is (= expected-state
             (l/add-violation validation-state :test-violation)))))

  (testing "should add violation to the current state"
    (let [validation-state (->> b/initial-validation-state
                                (b/with-violations [:current-violation]))
          expected-state (->> b/initial-validation-state
                              (b/with-violations [:current-violation :test-violation]))]
      (is (= expected-state (l/add-violation validation-state :test-violation))))))

(deftest validate-limit-test
  (testing "should add insufficient-limit violation in the state if the account dont have limit"
    (let [validation-state (->> b/initial-validation-state (b/with-availableLimit 0))
          transaction (->> b/a-transaction (b/tx-with-amount 1))
          new-state (l/validate-limit validation-state transaction)]
      (is (some #(= :insufficient-limit %) (:violations new-state)))))

  (testing "should not add insufficient-limit violation in the state if the account have limit"
    (let [validation-state (->> b/initial-validation-state (b/with-availableLimit 1))
          transaction (->> b/a-transaction (b/tx-with-amount 1))
          new-state (l/validate-limit validation-state transaction)]
      (is (empty?  (:violations new-state)))))

  (testing "once created, the account should not be updated or recreated"
    (let [account-input1 (->> b/an-account (b/account-with-limit 100))
          account-input2 (->> b/an-account (b/account-with-limit 350))
          new-state (-> m/initial-state
                        (l/create-account account-input1)
                        (dissoc :violations)
                        (l/create-account account-input2))
          expected {:account {:activeCard true :availableLimit 100}
                    :transactions [] :violations [:account-already-initialized]}]
      (is (= new-state expected)))))

(deftest validate-active-card-test
  (testing "should add card-not-active violation when account card is not active"
    (let [validation-state (->> b/initial-validation-state (b/inactive))
          new-state (l/validate-active-card validation-state)]
      (is (some #(= :card-not-active %) (:violations new-state)))))

  (testing "should not add card-not-active violation when account card is active"
    (let [validation-state b/initial-validation-state
          new-state (l/validate-active-card validation-state)]
      (is (empty? (:violations new-state))))))

(deftest is-high-frequency?-test
  (testing "should return true if had 3 transactions at same time"
    (let [base-transaction (->> b/a-transaction (b/tx-with-time 10 0))
          past-transactions [base-transaction base-transaction base-transaction]]
      (is (l/is-high-frequency? past-transactions base-transaction))))

  (testing "should return true if had 3 transactions in less then 2 minutes"
    (let [tx1 (->> b/a-transaction (b/tx-with-time 10 0))
          tx2 (->> b/a-transaction (b/tx-with-time 11 0))
          tx3 (->> b/a-transaction (b/tx-with-time 11 5))
          tx4 (->> b/a-transaction (b/tx-with-time 12 0))
          past-transactions [tx1 tx2 tx3]]
      (is (l/is-high-frequency? past-transactions tx4))))

  (testing "should return false if had 2 transactions in less then 2 minutes"
    (let [tx1 (->> b/a-transaction (b/tx-with-time 10 0))
          tx2 (->> b/a-transaction (b/tx-with-time 12 0))
          tx3 (->> b/a-transaction (b/tx-with-time 13 0))
          past-transactions [tx1 tx2]]
      (is (not (l/is-high-frequency? past-transactions tx3)))))

  (testing "should return false if is the first transactions"
    (let [tx (->> b/a-transaction (b/tx-with-time 10 0))
          past-transactions []]
      (is (not (l/is-high-frequency? past-transactions tx)))))

  (testing "should return false if is the second transactions"
    (let [tx (->> b/a-transaction (b/tx-with-time 10 0))
          past-transactions [tx]]
      (is (not (l/is-high-frequency? past-transactions tx)))))

  (testing "should return true if had one transaction after 2 minutes of 3 transactions"
    (let [tx (->> b/a-transaction (b/tx-with-time 10 0))
          tx-new (->> b/a-transaction (b/tx-with-time 12 1))
          past-transactions [tx tx tx]]
      (is (not (l/is-high-frequency? past-transactions tx-new))))))

(deftest validate-high-frequency-test
  (testing "should add violation high-frequency-small-interval"
    (let [tx (->> b/a-transaction (b/tx-with-time 10 0))
          past-transactions [tx tx tx]
          validation-state (->> b/initial-validation-state
                                (b/with-transactions past-transactions))
          expected-violations [:high-frequency-small-interval]
          new-state (l/validate-transaction-frequency validation-state tx)]
      (is (= expected-violations (:violations new-state))))))

(deftest equal-transactions?-test
  (testing "should return true when the transactions have the same merchant and amount"
    (let [tx1 (->> b/a-transaction (b/tx-with-time 1 0) (b/tx-with-amount 1) (b/tx-with-merchant "A"))
          tx2 (->> b/a-transaction (b/tx-with-time 2 0) (b/tx-with-amount 1) (b/tx-with-merchant "A"))]
      (is (true? (l/equal-transactions? tx1 tx2)))))

  (testing "should return false when the transactions have the same merchant and different amount"
    (let [tx1 (->> b/a-transaction (b/tx-with-time 1 0) (b/tx-with-amount 1) (b/tx-with-merchant "A"))
          tx2 (->> b/a-transaction (b/tx-with-time 2 0) (b/tx-with-amount 2) (b/tx-with-merchant "A"))]
      (is (false? (l/equal-transactions? tx1 tx2)))))

  (testing "should return false when the transactions have the same amount and different mercheant"
    (let [tx1 (->> b/a-transaction (b/tx-with-time 1 0) (b/tx-with-amount 1) (b/tx-with-merchant "A"))
          tx2 (->> b/a-transaction (b/tx-with-time 2 0) (b/tx-with-amount 1) (b/tx-with-merchant "B"))]
      (is (false? (l/equal-transactions? tx1 tx2)))))

  (testing "should return false when the transactions have the different amount and different mercheant"
    (let [tx1 (->> b/a-transaction (b/tx-with-time 1 0) (b/tx-with-amount 1) (b/tx-with-merchant "A"))
          tx2 (->> b/a-transaction (b/tx-with-time 2 0) (b/tx-with-amount 2) (b/tx-with-merchant "B"))]
      (is (false? (l/equal-transactions? tx1 tx2))))))

(deftest is-doubled?-test
  (testing "should return true when have 3 tx with same amount and merchant in less then 2 minutes"
    (let [tx (->> b/a-transaction
                  (b/tx-with-time 1 0)
                  (b/tx-with-amount 1)
                  (b/tx-with-merchant "A"))
          tx-new (->> b/a-transaction
                      (b/tx-with-time 2 0)
                      (b/tx-with-amount 1)
                      (b/tx-with-merchant "A"))
          past-transactions [tx tx]]
      (is (l/is-doubled? past-transactions tx-new))))

  (testing "should return false when have 3 tx with same amount and diferent merchant in less then 2 minutes"
    (let [tx (->> b/a-transaction
                  (b/tx-with-time 1 0)
                  (b/tx-with-amount 1)
                  (b/tx-with-merchant "A"))
          tx-new (->> b/a-transaction
                      (b/tx-with-time 2 0)
                      (b/tx-with-amount 1)
                      (b/tx-with-merchant "B"))
          past-transactions [tx tx]]
      (is (false? (l/is-doubled? past-transactions tx-new)))))

  (testing "should return false when have 3 tx with same merchant and diferent amount in less then 2 minutes"
    (let [tx (->> b/a-transaction
                  (b/tx-with-time 1 0)
                  (b/tx-with-amount 1)
                  (b/tx-with-merchant "A"))
          tx-new (->> b/a-transaction
                      (b/tx-with-time 2 0)
                      (b/tx-with-amount 2)
                      (b/tx-with-merchant "A"))
          past-transactions [tx tx]]
      (is (false? (l/is-doubled? past-transactions tx-new)))))

  (testing "should return false when have 3 tx with same merchant and amount in more then 2 minutes"
    (let [tx (->> b/a-transaction
                  (b/tx-with-time 1 0)
                  (b/tx-with-amount 1)
                  (b/tx-with-merchant "A"))
          tx-new (->> b/a-transaction
                      (b/tx-with-time 3 1)
                      (b/tx-with-amount 1)
                      (b/tx-with-merchant "A"))
          past-transactions [tx tx]]
      (is (false? (l/is-doubled? past-transactions tx-new))))))

(deftest validate-doubled-transaction-test
  (testing "should add violation doubled-transaction"
    (let [tx (->> b/a-transaction
                  (b/tx-with-time 1 0)
                  (b/tx-with-amount 1)
                  (b/tx-with-merchant "A"))
          tx-new (->> tx (b/tx-with-time 2 0))
          transactions [tx tx]
          validation-state (->> b/initial-validation-state
                                (b/with-transactions transactions))
          expected-violations [:doubled-transaction]
          new-state (l/validate-doubled-transaction validation-state tx-new)]
      (is (= expected-violations (:violations new-state)))))

  (testing "should not add violation doubled-transaction"
    (let [tx1 (->> b/a-transaction
                   (b/tx-with-time 1 0)
                   (b/tx-with-amount 1)
                   (b/tx-with-merchant "A"))
          tx2 (->> tx1 (b/tx-with-time 3 1))
          validation-state (->> b/initial-validation-state
                                (b/with-transactions [tx1 tx1]))
          expected-violations []
          new-state (l/validate-doubled-transaction validation-state tx2)]
      (is (= expected-violations (:violations new-state))))))

(deftest save-transaction-test
  (testing "should create first transaction"
    (let [state b/initial-state
          tx b/a-transaction
          new-state (l/save-transaction state tx)
          expected-txs [tx]]
      (is (= expected-txs (:transactions new-state)))))

  (testing "should add a transaction"
    (let [tx b/a-transaction
          state (->> b/initial-state (b/with-transactions [tx]))
          new-state (l/save-transaction state tx)
          expected-txs [tx tx]]
      (is (= expected-txs (:transactions new-state))))))

(deftest get-updated-account-test
  (testing "should return the account limit if not exist transactions"
    (let [expected-limit 10
          account (->> b/an-account (b/account-with-limit expected-limit))
          transactions []
          state (->> b/initial-validation-state
                     (b/with-transactions transactions)
                     (b/with-account account))
          new-state (l/get-updated-account state)
          current-limit (-> new-state :account :availableLimit)]
      (is (= expected-limit current-limit))))

  (testing "should return the account with calculated limit (1 tx)"
    (let [account (->> b/an-account (b/account-with-limit 10))
          transactions [(->> b/a-transaction (b/tx-with-amount 5))]
          state (->> b/initial-validation-state
                     (b/with-transactions transactions)
                     (b/with-account account))
          new-state (l/get-updated-account state)
          expected-limit 5
          current-limit (-> new-state :account :availableLimit)]
      (is (= expected-limit current-limit))))

  (testing "should return the account with calculated limit (2 tx)"
    (let [account (->> b/an-account (b/account-with-limit 100))
          transactions [(->> b/a-transaction (b/tx-with-amount 10))
                        (->> b/a-transaction (b/tx-with-amount 20))]
          state (->> b/initial-validation-state
                     (b/with-transactions transactions)
                     (b/with-account account))
          new-state (l/get-updated-account state)
          expected-limit 70
          current-limit (-> new-state :account :availableLimit)]
      (is (= expected-limit current-limit)))))

(deftest has-violations?-test
  (testing "should return false if the validation result not contains violations"
    (let [state (->> b/initial-validation-state (b/with-violations []))]
      (is (false? (l/has-violations? state)))))

  (testing "should return true if the validation result contains violations"
    (let [state (->> b/initial-validation-state
                     (b/with-violation :test-violation))]
      (is (true? (l/has-violations? state))))))

(deftest get-account-json-test
  (testing "should return correct json with no violations"
    (let [state (->> b/initial-validation-state
                     (b/with-availableLimit 10) (b/active))
          parsed-json (l/get-account-json state)
          expected-json "{\"account\":{\"activeCard\":true,\"availableLimit\":10},\"violations\":[]}"]
      (is (= expected-json parsed-json))))

  (testing "should return correct json with violations"
    (let [state (->> b/initial-validation-state
                     (b/with-availableLimit 100)
                     (b/inactive)
                     (b/with-violation :test-violation))
          parsed-json (l/get-account-json state)
          expected-json "{\"account\":{\"activeCard\":false,\"availableLimit\":100},\"violations\":[\"test-violation\"]}"]
      (is (= expected-json parsed-json)))))

(deftest process-transaction-test
  (testing "The transaction amount should not exceed available limit"
    (let [state (->> b/initial-state (b/with-availableLimit 100))
          tx1 (->> b/a-transaction
                   (b/tx-with-amount 20)
                   (b/tx-with-merchant "Burguer King")
                   (b/tx-as-input))
          tx2 (->> b/a-transaction
                   (b/tx-with-amount 90)
                   (b/tx-with-merchant "other merchant")
                   (b/tx-as-input))
          new-state (-> state
                        (l/process-transaction tx1)
                        (b/remove-violations)
                        (l/process-transaction tx2))
          expected {:account {:activeCard true :availableLimit 100}
                    :transactions [{:merchant "Burguer King"
                                    :amount 20
                                    :time  "2019-01-01T10:00:00.000Z"}]
                    :violations [:insufficient-limit]}]
      (is (= new-state expected))))

  (testing "No transaction should be accepted when the card is not active"
    (let [state (->> b/initial-state (b/with-availableLimit 100) (b/inactive))
          tx (->> b/a-transaction
                  (b/tx-with-amount 10)
                  (b/tx-with-merchant "Burguer King") (b/tx-as-input))
          new-state (-> state (l/process-transaction tx))
          expected {:account {:activeCard false :availableLimit 100}
                    :transactions []
                    :violations [:card-not-active]}]
      (is (= new-state expected))))

  (testing "There should not be more than 3 transactions on a 2 minute interval"
    (let [state (->> b/initial-state (b/with-availableLimit 100))
          then b/remove-violations
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
          new-state (-> state
                        (l/process-transaction tx1) (then)
                        (l/process-transaction tx2) (then)
                        (l/process-transaction tx3) (then)
                        (l/process-transaction tx4))
          expected {:account {:activeCard true :availableLimit 100}
                    :transactions [{:merchant "Burguer King 1" :amount 10 :time  "2019-01-01T10:00:00.000Z"}
                                   {:merchant "Burguer King 2" :amount 10 :time  "2019-01-01T10:00:00.000Z"}
                                   {:merchant "Burguer King 3" :amount 10 :time  "2019-01-01T10:00:00.000Z"}]
                    :violations [:high-frequency-small-interval]}]
      (is (= new-state expected))))

  (testing "There should have 2 similar transactions (same amount and merchant) in a 2 minutes interval"
    (let [state (->> b/initial-state (b/with-availableLimit 100))
          tx1 (->> b/a-transaction
                   (b/tx-with-amount 10)
                   (b/tx-with-merchant "Burguer King") (b/tx-as-input))
          tx2 (->> b/a-transaction
                   (b/tx-with-amount 10)
                   (b/tx-with-merchant "Burguer King") (b/tx-as-input))
          new-state (-> state
                        (l/process-transaction tx1) (b/remove-violations)
                        (l/process-transaction tx2))
          expected {:account {:activeCard true :availableLimit 100}
                    :transactions [{:merchant "Burguer King" :amount 10 :time  "2019-01-01T10:00:00.000Z"}
                                   {:merchant "Burguer King" :amount 10 :time  "2019-01-01T10:00:00.000Z"}]
                    :violations []}]
      (is (= expected new-state))))

  (testing "There should not be more than 2 similar transactions (same amount and merchant) in a 2 minutes interval"
    (let [state (->> b/initial-state (b/with-availableLimit 100))
          then b/remove-violations
          tx (->> b/a-transaction
                  (b/tx-with-amount 10)
                  (b/tx-with-merchant "Burguer King") (b/tx-as-input))
          new-state (-> state
                        (l/process-transaction tx) (then)
                        (l/process-transaction tx) (then)
                        (l/process-transaction tx))
          expected {:account {:activeCard true :availableLimit 100}
                    :transactions [{:merchant "Burguer King" :amount 10 :time  "2019-01-01T10:00:00.000Z"}
                                   {:merchant "Burguer King" :amount 10 :time  "2019-01-01T10:00:00.000Z"}]
                    :violations [:doubled-transaction]}]
      (is (= new-state expected))))

  (testing "should have more than one violation"
    (let [state (->> b/initial-state (b/with-availableLimit 100))
          then b/remove-violations
          tx1 (->> b/a-transaction
                   (b/tx-with-amount 40)
                   (b/tx-with-merchant "Burguer King") (b/tx-as-input))
          tx2 (->> b/a-transaction
                   (b/tx-with-amount 40)
                   (b/tx-with-merchant "Burguer King")
                   (b/tx-with-time 1 0) (b/tx-as-input))
          new-state (-> state
                        (l/process-transaction tx1) (then)
                        (l/process-transaction tx1) (then)
                        (l/process-transaction tx2))
          expected {:account {:activeCard true :availableLimit 100}
                    :transactions [{:merchant "Burguer King" :amount 40 :time  "2019-01-01T10:00:00.000Z"}
                                   {:merchant "Burguer King" :amount 40 :time  "2019-01-01T10:00:00.000Z"}]
                    :violations [:insufficient-limit :doubled-transaction]}]
      (is (= new-state expected)))))


