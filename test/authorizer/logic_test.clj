(ns authorizer.logic-test
  (:require
   [clojure.test :refer :all]
   [authorizer.logic :as l]))

(defn create-active-account [limit] {:account {:activeCard true
                                               :availableLimit limit}})

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
          account-input (create-active-account 0)
          new-state (l/create-account initial-empty-state account-input)]
      (is (= (:state new-state) account-input))))

  (testing "should not have violations when an account is created"
    (let [initial-empty-state {}
          account-input (create-active-account 0)
          new-state (l/create-account initial-empty-state account-input)]
      (is (empty? (:violations new-state)))))

  (testing "should return a violation if an account already exists"
    (let [initial-state {:state (create-active-account 0)}
          account-input (create-active-account 1)
          new-state (l/create-account initial-state account-input)
          expected-violations [:account-already-initialized]]
      (is (= (:violations new-state) expected-violations))))

  (testing "should add a violation if an account already contains one"
    (let [initial-state {:state (create-active-account 0) :violations [:test-violation]}
          account-input {:account "new account"}
          new-state (l/create-account initial-state account-input)
          expected-violations [:test-violation :account-already-initialized]]
      (is (= (:violations new-state) expected-violations))))

  (testing "should not change the current account when have a violation"
    (let [initial-state {:state (create-active-account 0) :violations [:test-violation]}
          account-input {:account "new account"}
          new-state (l/create-account initial-state account-input)]
      (is (=  (:state initial-state) (:state new-state))))))

(deftest has-no-limit?-test
  (testing "should return true if the account dont have limit"
    (let [account (create-active-account 0)
          amount 1]
      (is (l/has-no-limit? account amount))))

  (testing "should return false if the account have the exaclty limit"
    (let [account (create-active-account 1)
          amount 0]
      (is (not (l/has-no-limit? account amount)))))

  (testing "should return false if the account have more then the necessary limit"
    (let [account (create-active-account 3)
          amount 1]
      (is (not (l/has-no-limit? account amount))))))

(deftest add-violation-test
  (testing "should add violation to empty state"
    (let [current-state {:violations []}
          expected-state {:violations [:test-violation]}]
      (is (= expected-state (l/add-violation current-state :test-violation)))))

  (testing "should add violation to the current state"
    (let [current-state {:violations [:current-violation]}
          expected-state {:violations [:current-violation :test-violation]}]
      (is (= expected-state (l/add-violation current-state :test-violation))))))

(deftest validate-limit-test
  (testing "should add insufficient-limit violation in the state if the account dont have limit"
    (let [current-state {:state (create-active-account 0)  :violations []}
          transaction {:transaction {:merchant "", :amount 1, :time "2019-02-13T10:00:00.000Z"}}
          new-state (l/validate-limit current-state transaction)]
             (is (some #(= :insufficient-limit %) (:violations new-state) )))))
