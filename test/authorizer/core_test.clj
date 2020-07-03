(ns authorizer.core-test
  (:require [clojure.test :refer :all]
            [clojure.string :as string]
            [authorizer.core :as app]))

(defn join-as-lines [in] (string/join "\n" in))
(defn interact [args] (string/split-lines (with-out-str (with-in-str (join-as-lines args) (app/-main)))))

(deftest main-test
  (testing "The transaction amount should not exceed available limit"
    (let [input ["{ \"account\": { \"activeCard\": true, \"availableLimit\": 100 } }"
                 "{ \"transaction\": { \"merchant\": \"Burger King\", \"amount\": 20, \"time\": \"2019-02-13T10:00:00.000Z\" } }"
                 "{ \"transaction\": { \"merchant\": \"Habbib's\", \"amount\": 90, \"time\": \"2019-02-13T11:00:00.000Z\" } }"]
          output ["{\"account\":{\"activeCard\":true,\"availableLimit\":100},\"violations\":[]}"
                  "{\"account\":{\"activeCard\":true,\"availableLimit\":80},\"violations\":[]}"
                  "{\"account\":{\"activeCard\":true,\"availableLimit\":80},\"violations\":[\"insufficient-limit\"]}"]
          result (interact input)]
      (is (= output result))))

  (testing "once created, the account should not be updated or recreated"
    (let [input ["{ \"account\": { \"activeCard\": true, \"availableLimit\": 100 } }"
                 "{ \"account\": { \"activeCard\": true, \"availableLimit\": 350 } }"]
          output ["{\"account\":{\"activeCard\":true,\"availableLimit\":100},\"violations\":[]}"
                  "{\"account\":{\"activeCard\":true,\"availableLimit\":100},\"violations\":[\"account-already-initialized\"]}"]
          result (interact input)]
      (is (= output result))))

  (testing "No transaction should be accepted when the card is not active"
    (let [input ["{ \"account\": { \"activeCard\": false, \"availableLimit\": 100 } }"
                 "{ \"transaction\": { \"merchant\": \"Burger King\", \"amount\": 10, \"time\": \"2019-02-13T10:00:00.000Z\" } }"]
          output ["{\"account\":{\"activeCard\":false,\"availableLimit\":100},\"violations\":[]}"
                  "{\"account\":{\"activeCard\":false,\"availableLimit\":100},\"violations\":[\"card-not-active\"]}"]
          result (interact input)]
      (is (= output result))))

  (testing "There should not be more than 3 transactions on a 2 minute interval"
    (let [input ["{ \"account\": { \"activeCard\": true, \"availableLimit\": 100 } }"
                 "{ \"transaction\": { \"merchant\": \"Burger King 1\", \"amount\": 10, \"time\": \"2019-02-13T10:00:00.000Z\" } }"
                 "{ \"transaction\": { \"merchant\": \"Burger King 2\", \"amount\": 10, \"time\": \"2019-02-13T10:01:00.000Z\" } }"
                 "{ \"transaction\": { \"merchant\": \"Burger King 3\", \"amount\": 10, \"time\": \"2019-02-13T10:01:10.000Z\" } }"
                 "{ \"transaction\": { \"merchant\": \"Burger King 4\", \"amount\": 10, \"time\": \"2019-02-13T10:01:30.000Z\" } }"]
          output ["{\"account\":{\"activeCard\":true,\"availableLimit\":100},\"violations\":[]}"
                  "{\"account\":{\"activeCard\":true,\"availableLimit\":90},\"violations\":[]}"
                  "{\"account\":{\"activeCard\":true,\"availableLimit\":80},\"violations\":[]}"
                  "{\"account\":{\"activeCard\":true,\"availableLimit\":70},\"violations\":[]}"
                  "{\"account\":{\"activeCard\":true,\"availableLimit\":70},\"violations\":[\"high-frequency-small-interval\"]}"]
          result (interact input)]
      (is (= output result))))
      
  (testing "There should not be more than 2 similar transactions (same amount and merchant) in a 2 minutes interval"
    (let [input ["{ \"account\": { \"activeCard\": true, \"availableLimit\": 100 } }"
                 "{ \"transaction\": { \"merchant\": \"Burger King\", \"amount\": 10, \"time\": \"2019-02-13T10:00:00.000Z\" } }"
                 "{ \"transaction\": { \"merchant\": \"Burger King\", \"amount\": 10, \"time\": \"2019-02-13T10:01:30.000Z\" } }"]
          output ["{\"account\":{\"activeCard\":true,\"availableLimit\":100},\"violations\":[]}"
                  "{\"account\":{\"activeCard\":true,\"availableLimit\":90},\"violations\":[]}"
                  "{\"account\":{\"activeCard\":true,\"availableLimit\":90},\"violations\":[\"doubled-transaction\"]}"]
          result (interact input)]
      (is (= output result))))
      
      
  (testing "should list more than one violation"
    (let [input ["{ \"account\": { \"activeCard\": true, \"availableLimit\": 100 } }"
                 "{ \"transaction\": { \"merchant\": \"Burger King\", \"amount\": 60, \"time\": \"2019-02-13T10:00:00.000Z\" } }"
                 "{ \"transaction\": { \"merchant\": \"Burger King\", \"amount\": 60, \"time\": \"2019-02-13T10:01:30.000Z\" } }"]
          output ["{\"account\":{\"activeCard\":true,\"availableLimit\":100},\"violations\":[]}"
                  "{\"account\":{\"activeCard\":true,\"availableLimit\":40},\"violations\":[]}"
                  "{\"account\":{\"activeCard\":true,\"availableLimit\":40},\"violations\":[\"insufficient-limit\",\"doubled-transaction\"]}"]
          result (interact input)]
      (is (= output result)))))
      
      
