(ns zeta.starter.web
  "Web服务启动器"
  (:require [org.httpkit.server :as http]
            [zeta
             [core :refer [namespaces-of]]
             [logging :as log]
             [opt :as zeta]
             [schedule :refer [at-exit]]]
            [zeta.web.server :refer [ns-accept? routes]]))

(defn start
  "启动web服务器"
  []
  (let [{:keys [enable port] :as options}
        (merge {:enable true :port 3000}
               (zeta/ofs [:web :server :options]))]
    (when enable
      (log/info "start web @ {}" port)
      (when-let [server (http/run-server routes options)]
        (at-exit (fn []
                   (server :timeout 100)
                   (log/info "stop web"))))
      (doseq [package (namespaces-of ns-accept?)]
        (require package)))))
