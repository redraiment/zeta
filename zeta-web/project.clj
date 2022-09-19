(defproject zeta/zeta-starter-web "3.0.0"
  :description "Zeta web server & client starter"
  :url "https://gitee.com/lambdata/zeta-starter-web"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[zeta "3.0.0"]
                 [http-kit "2.1.19"]
                 [ring/ring-core "1.6.3"]
                 [ring/ring-json "0.4.0"]
                 [javax.servlet/javax.servlet-api "4.0.0"]
                 [hiccup "1.0.5"]
                 [garden "1.3.4"]
                 [clj-json "0.5.3"]]
  :test-paths ["src"]
  :aot :all)
