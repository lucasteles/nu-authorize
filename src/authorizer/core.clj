(ns authorizer.core (:gen-class)
    (:require [authorizer.logic :as l]
              [authorizer.model :as m]
              [schema.core :as s]
              [authorizer.components.system :as system]))


(defn -main
  "Authorize nubank challenge"
  [] (system/build-and-start))
