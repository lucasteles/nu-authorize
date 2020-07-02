(ns authorizer.test-builders)

(def an-account  {:account {:activeCard true :availableLimit 0}})
(defn account-with-limit [limit account] (assoc-in account [:account :availableLimit] limit))
(defn account-active [account] (assoc-in account [:account :activeCard] true))
(defn account-inactive [account] (assoc-in account [:account :activeCard] false))

(def a-transaction
  {:transaction {:merchant "Merchant name"
                 :amount 0
                 :time  "2019-01-01T10:00:00.000Z"}})

(defn tx-with-amount [amount transaction] (assoc-in transaction [:transaction :amount] amount))
(defn tx-with-merchant [name transaction] (assoc-in transaction [:transaction :merchant] name))
(defn tx-with-time [minutes seconds transaction] (assoc-in transaction
                                                        [:transaction :time] (format "2019-01-01T10:%02d:%02d.000Z" minutes seconds)))
(def initial-validation-state
   (merge an-account { :transactions [] :violations []}))

(defn with-availableLimit [limit current-state]
  (assoc-in current-state [:account :availableLimit] limit))

(defn with-activeCard [active current-state]
  (assoc-in current-state [:account :activeCard] active))

(defn with-transactions [transactions current-state]
  (assoc-in current-state [:transactions] transactions))

(defn add-transaction [transaction current-state]
  (update-in current-state [:transactions] conj transaction))

(defn with-violation [violation current-state]
  (update current-state :violations conj violation))

(defn active [current-state] (assoc-in current-state [:account :activeCard] true))
(defn inactive [current-state] (assoc-in current-state [:account :activeCard] false))