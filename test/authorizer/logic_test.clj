(ns authorizer.logic-test
  (:require
   [clojure.test :refer :all]
   [authorizer.test-builders :as b]
   [authorizer.logic :as l]))

(deftest parse-json-input-test
  (testing "should parse a valid account json"
    (let [valid-json "{ \"account\": { \"activeCard\": true, \"availableLimit\": 100 } }"
          expected-map {:account {:activeCard true, :availableLimit 100}}
          parsed-json (l/parse-json-input valid-json)]
      (is (= parsed-json expected-map))))

  (testing "should parse a valid transaction json"
    (let [valid-json "{ \"transaction\": { \"merchant\": \"Burger King\", \"amount\": 20, \"time\": \"2019-02-13T10:00:00.000Z\" } }"
          expected-map {:transaction {:merchant "Burger King", :amount 20, :time "2019-02-13T10:00:00.000Z"}}
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
    (let [initial-empty-state {}
          account-input b/an-account
          new-state (l/create-account initial-empty-state account-input)]
      (is (= (:account new-state) (:account account-input)))))

  (testing "should not have violations when an account is created"
    (let [initial-empty-state {}
          account-input b/an-account
          new-state (l/create-account initial-empty-state account-input)]
      (is (empty? (:violations new-state)))))

  (testing "should return a violation if an account already exists"
    (let [initial-state b/initial-validation-state
          account-input (->> b/an-account (b/account-with-limit 1))
          new-state (l/create-account initial-state account-input)
          expected-violations [:account-already-initialized]]
      (is (= (:violations new-state) expected-violations))))

  (testing "should add a violation if an account already contains one"
    (let [initial-state (->> b/initial-validation-state (b/with-violation :test-violation))
          account-input b/an-account
          new-state (l/create-account initial-state account-input)
          expected-violations [:test-violation :account-already-initialized]]
      (is (= (:violations new-state) expected-violations))))

  (testing "should not change the current account when have a violation"
    (let [initial-state (->> b/initial-validation-state (b/with-violation :test-violation))
          account-input b/an-account
          new-state (l/create-account initial-state account-input)]
      (is (=  (:account initial-state) (:account new-state))))))

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
    (let [validation-state {:violations []}
          expected-state {:violations [:test-violation]}]
      (is (= expected-state (l/add-violation validation-state :test-violation)))))

  (testing "should add violation to the current state"
    (let [validation-state {:violations [:current-violation]}
          expected-state {:violations [:current-violation :test-violation]}]
      (is (= expected-state (l/add-violation validation-state :test-violation))))))

(deftest validate-limit-test
  (testing "should add insufficient-limit violation in the state if the account dont have limit"
    (let [validation-state (->> b/initial-validation-state (b/with-availableLimit 0))
          transaction (->> b/a-transaction (b/tx-with-amount 1))
          new-state (l/validate-limit validation-state transaction)]
      (println new-state)
      (is (some #(= :insufficient-limit %) (:violations new-state)))))

  (testing "should not add insufficient-limit violation in the state if the account have limit"
    (let [validation-state (->> b/initial-validation-state (b/with-availableLimit 1))
          transaction (->> b/a-transaction (b/tx-with-amount 1))
          new-state (l/validate-limit validation-state transaction)]
      (is (empty?  (:violations new-state))))))

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
          validation-state (->> b/initial-validation-state (b/with-transactions past-transactions))
          expected-violations [:high-frequency-small-interval]
          new-state (l/validate-transaction-frequency validation-state tx)]
      (is (= expected-violations (:violations new-state))))))

(deftest is-doubled?-test
  (testing "should return true when have 2 tx with same amount and merchant in less then 2 minutes"
    (let [tx1 (->> b/a-transaction (b/tx-with-time 1 0) (b/tx-with-amount 1) (b/tx-with-merchant "A"))
          tx2 (->> b/a-transaction (b/tx-with-time 2 0) (b/tx-with-amount 1) (b/tx-with-merchant "A"))]
      (is (l/is-doubled? tx1 tx2))))

  (testing "should return false when have 2 tx with same amount and diferent merchant in less then 2 minutes"
    (let [tx1 (->> b/a-transaction (b/tx-with-time 1 0) (b/tx-with-amount 1) (b/tx-with-merchant "A"))
          tx2 (->> b/a-transaction (b/tx-with-time 2 0) (b/tx-with-amount 1) (b/tx-with-merchant "B"))]
      (is (not (l/is-doubled? tx1 tx2)))))

  (testing "should return false when have 2 tx with same merchant and diferent amount in less then 2 minutes"
    (let [tx1 (->> b/a-transaction (b/tx-with-time 1 0) (b/tx-with-amount 1) (b/tx-with-merchant "A"))
          tx2 (->> b/a-transaction (b/tx-with-time 2 0) (b/tx-with-amount 2) (b/tx-with-merchant "A"))]
      (is (not (l/is-doubled? tx1 tx2)))))

  (testing "should return false when have 2 tx with same merchant and amount in more then 2 minutes"
    (let [tx1 (->> b/a-transaction (b/tx-with-time 1 0) (b/tx-with-amount 1) (b/tx-with-merchant "A"))
          tx2 (->> b/a-transaction (b/tx-with-time 3 1) (b/tx-with-amount 1) (b/tx-with-merchant "A"))]
      (is (not (l/is-doubled? tx1 tx2))))))

(deftest validate-doubled-transaction-test
  (testing "should add violation doubled-transaction"
    (let [tx1 (->> b/a-transaction (b/tx-with-time 1 0) (b/tx-with-amount 1) (b/tx-with-merchant "A"))
          tx2 (->> b/a-transaction (b/tx-with-time 2 0) (b/tx-with-amount 1) (b/tx-with-merchant "A"))
          validation-state (->> b/initial-validation-state (b/with-transactions [tx1]))
          expected-violations [:doubled-transaction]
          new-state (l/validate-doubled-transaction validation-state tx2)]
      (is (= expected-violations (:violations new-state)))))

  (testing "should not add violation doubled-transaction"
    (let [tx1 (->> b/a-transaction (b/tx-with-time 1 0) (b/tx-with-amount 1) (b/tx-with-merchant "A"))
          tx2 (->> b/a-transaction (b/tx-with-time 3 1) (b/tx-with-amount 1) (b/tx-with-merchant "A"))
          validation-state (->> b/initial-validation-state (b/with-transactions [tx1]))
          expected-violations []
          new-state (l/validate-doubled-transaction validation-state tx2)]
      (is (= expected-violations (:violations new-state))))))