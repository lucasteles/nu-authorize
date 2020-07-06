(ns authorizer.core (:gen-class)
    (:require [authorizer.logic :as l]
              [authorizer.model :as m]
              [schema.core :as s]))

(s/defn main-loop!
  "interact with user and process the new state"
  [state :- m/State]
  (when-let
   [validation (l/action-handler
                state
                (read-line) )]
    (println (l/get-account-json validation))
    (recur (dissoc validation :violations))))

(defn -main
  "Authorize nubank challenge"
  [] (main-loop! m/initial-state))
