(ns authorizer.core-test
  (:require [clojure.test :refer :all]
            [clojure.string :as string]
            [authorizer.core :as app]))

(def json "{\"account\":{\"activeCard\":true,\"availableLimit\":10}}")

(defn join-as-lines [in] (string/join "\n" in))
(defn interact [args] (string/split-lines (with-out-str (with-in-str (join-as-lines args) (app/-main)))))

(deftest main-test
  (testing "should process the base testcase"
    (let [input ["{ \"account\": { \"activeCard\": true, \"availableLimit\": 100 } }"
                 "{ \"transaction\": { \"merchant\": \"Burger King\", \"amount\": 20, \"time\": \"2019-02-13T10:00:00.000Z\" } }"
                 "{ \"transaction\": { \"merchant\": \"Habbib's\", \"amount\": 90, \"time\": \"2019-02-13T11:00:00.000Z\" } }"]
          output ["{\"account\":{\"activeCard\":true,\"availableLimit\":100},\"violations\":[]}"
                  "{\"account\":{\"activeCard\":true,\"availableLimit\":80},\"violations\":[]}"
                  "{\"account\":{\"activeCard\":true,\"availableLimit\":80},\"violations\":[\"insufficient-limit\"]}"]
          result (interact input)]
      (is (= output result)))))
