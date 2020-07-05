(ns authorizer.core (:gen-class)
    (:require [authorizer.logic :as l]
              [authorizer.model :as m]
              [schema.core :as s]))

(def UpdateStateFn (s/=> m/State m/ValidationState))

(defn- read-json-input! [] (l/parse-json-input (read-line)))
(s/defn try-run-command! :- m/State
  "run the function wich will return the new app state"
  [f :- UpdateStateFn
   state :- m/State
   input]
  (let [validation (f state input)
        new-state (if (l/has-violations? validation) state validation)]
    (println (l/get-account-json validation))
    (dissoc new-state :violations)))

(s/defn main-loop!
  "interact with user and decide wich action to take"
  [state :- m/State]
  (let [input (read-json-input!)]
    (cond
      (l/is-account? input)
      (recur (try-run-command! l/create-account state input))

      (l/is-transaction? input)
      (recur (try-run-command! l/process-transaction state input)))))

(defn -main
  "Authorize nubank challenge"
  [] (main-loop! m/initial-state))
