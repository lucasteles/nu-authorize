(ns authorizer.model (:gen-class)
    (:require [schema.core :as s]))

(def Account {:account {:activeCard s/Bool
                        :availableLimit s/Int}})

(def Transaction {:merchant s/Str
                  :amount s/Int
                  :time  s/Str})

(def TransactionInput {:transaction Transaction})

(def Violations [s/Keyword])

(def State (merge
            {(s/optional-key :account) (:account Account)}
            {:transactions [Transaction]}))

(def ValidationState (merge State {:violations Violations}))

(def HandlerResult {:type s/Keyword
                    :violations Violations})

(s/def initial-state :- State  {:transactions []})

