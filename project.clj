(defproject zeta "5.0.0"
  :description "个人Clojure开发库"
  :url "https://gitee.com/redraiment/zeta"
  :license {:name "BSD 2-Clause \"Simplified\" License"
            :url "https://en.wikipedia.org/wiki/BSD_licenses#2-clause_license_(%22Simplified_BSD_License%22_or_%22FreeBSD_License%22)"
            :year 2019
            :key "bsd-2-clause"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [org.clojure/tools.namespace "1.1.0"
                  :exclusions [org.clojure/clojure]]
                 ;; 日志
                 [org.slf4j/slf4j-api "1.7.32"]
                 [org.slf4j/jul-to-slf4j "1.7.32"]
                 [org.slf4j/log4j-over-slf4j "1.7.32"]
                 [ch.qos.logback/logback-classic "1.2.4"]
                 ;; change directory
                 [net.java.dev.jna/jna "5.8.0"]
                 [com.github.jnr/jnr-posix "3.1.7"]
                 ;; maven
                 ;; 新版本为了适配Java 9，在Java 8下无法正常运行
                 [com.cemerick/pomegranate "0.3.1"
                  :exclusions [org.tcrawley/dynapath]]
                 [org.tcrawley/dynapath "0.2.4"]
                 ;; nREPL
                 [reply "0.4.4"
                  :exclusions [org.clojure/clojure
                               net.cgrand/parsley
                               cheshire
                               nrepl]]
                 [cider/cider-nrepl "0.26.0"
                  :exclusions [nrepl]]
                 [net.cgrand/parsley "0.9.3"
                  :exclusions [org.clojure/clojure]]
                 [nrepl "0.8.3"]
                 ;; JSON
                 [cheshire "5.10.0"
                  :exclusions [com.fasterxml.jackson.core/jackson-databind
                               com.fasterxml.jackson.core/jackson-core]]]
  :test-paths ["src"]
  :target-path "target/%s"
  :aot :all
  :main ^:skip-aop zeta.core
  :omit-source true)
