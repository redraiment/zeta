(defproject zeta/zeta-starter-ws "3.0.0"
  :description "Zeta workstation starter"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[zeta "3.0.0"]
                 [zeta/zeta-starter-db "3.0.0"]
                 [zeta/zeta-starter-web "3.0.0"]
                 [zeta/zeta-starter-repl "3.0.0"]
                 [zeta/zeta-starter-enniu-ds "3.0.0"]
                 [reply "0.3.7"
                  :exclusions [ring/ring-core
                               org.thnetos/cd-client
                               org.clojure/clojure]]
                 [net.cgrand/parsley "0.9.3"
                  :exclusions [org.clojure/clojure]]]
  :aot :all
  :main ^:skip-aot zeta.main)
