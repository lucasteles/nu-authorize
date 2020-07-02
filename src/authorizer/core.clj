(ns authorizer.core (:gen-class)
  (:require [authorizer.logic :as l]))


(defn read-json-input! [] (l/parse-json-input (read-line)))

(defn main-loop! []
  (let [input (read-json-input!)]
    (cond
      (l/is-account? input) (do (println "uma conta") (recur))
      (l/is-transaction? input) (do (println "uma transacao") (recur)))))

(defn -main
  "Authorize nubank challenge"
  [& args] (main-loop!))
