(defproject zeta/zeta-starter-51 "3.0.0"
  :description "面向51的工作站"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[zeta "3.0.0"]
                 [zeta/zeta-starter-db "3.0.0"]
                 [zeta/zeta-starter-mvn "3.0.0"]
                 [zeta/zeta-starter-web "3.0.0"]
                 ;; REPL
                 [org.clojure/tools.nrepl "0.2.13"]
                 [cider/cider-nrepl "0.15.0"]
                 [reply "0.3.7"
                  :exclusions [ring/ring-core
                               org.thnetos/cd-client
                               org.clojure/clojure]]
                 [net.cgrand/parsley "0.9.3"
                  :exclusions [org.clojure/clojure]]
                 ;; database
                 [io.forward/yaml "1.0.6"]
                 [mysql/mysql-connector-java "5.1.43"]
                 [org.postgresql/postgresql "42.1.4"]
                 [com.u51/codec "1.1.2"]]
  :aot :all
  :main ^:skip-aot zeta.enniu.main
  :uberjar-name "workstation-standalone.jar")
