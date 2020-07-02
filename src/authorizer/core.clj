(ns authorizer.core (:gen-class)
    (:require [authorizer.logic :as l]))

(defn read-json-input! [] (l/parse-json-input (read-line)))

(defn try-run-command! [f state input]
  (let [validation (f state input)
        new-state (if (l/has-violations? validation) state validation)]
    (println (l/get-show-data validation))
    (dissoc new-state :violations)))

(defn main-loop! [state]
  (let [input (read-json-input!)]
    (cond
      (l/is-account? input)
      (recur (try-run-command! l/create-account state input))

      (l/is-transaction? input)
      (recur (try-run-command! l/process-transaction state input)))))

(defn -main
  "Authorize nubank challenge"
  [& args] (main-loop! {}))
