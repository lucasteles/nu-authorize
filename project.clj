(defproject authorizer "0.1.0-SNAPSHOT"
  :description "Nubank authorizer code challenge"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/data.json "1.0.0"]
                 [clj-time "0.15.2"]
                 [prismatic/schema "1.1.12"]]
  :main ^:skip-aot authorizer.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
