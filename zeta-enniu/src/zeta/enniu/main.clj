(ns zeta.enniu.main
  (:require [clojure.string :refer [blank?]]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.nrepl.server :refer [start-server]]
            [cider.nrepl :refer [cider-nrepl-handler]]
            [zeta
             [coercion :refer [str->int]]
             [db :refer [database]]
             [id :refer [with-ns]]
             [logging :as log]]
            [reply.main :as reply])
  (:gen-class))

(def ^:private command-line-options
  "命令行选项"
  [["-e" "--environments" "指定一到多个目标环境，可指定多次或用逗号分隔"
    :id :environments
    :required "ENVIRONMENTS"
    :assoc-fn (fn [m k v]
                (update m k #(if (blank? %)
                               v
                               (str % "," v))))]
   ["-s" "--server" "启动服务器模式"
    :id :server]
   ["-p" "--port" "服务器模式的端口"
    :id :port
    :required "PORT"
    :default 9999
    :parse-fn str->int
    :validate [#(< 1023 % 0x10000) "端口必须在1024-65536之间"]]
   ["-h" "--help" "显示当前帮助"
    :id :help]])

(defn prompt [_]
  (format "%s=> " (or (database) "")))

(defn -main [& args]
  (let [{{help :help
          environments :environments
          server :server
          port :port} :options
         arguments :arguments
         banner :summary
         errors :errors}
        (parse-opts args command-line-options)]
    (binding [*command-line-args* (next arguments)]
      (if server
        (do
          (start-server :bind "0.0.0.0" :port port :handler cider-nrepl-handler)
          (log/info "workstation start @ {}" port))
        (do
          (cond
            help (println banner)
            errors (doseq [message errors]
                     (log/error "{}" message))
            (seq arguments) (try
                              (with-ns user
                                (load-file (first arguments)))
                              (catch Throwable e
                                (.printStackTrace e)))
            true  (reply/launch-standalone {:custom-prompt prompt
                                            :color true}))
          (shutdown-agents))))))
