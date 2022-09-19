(ns zeta.starter.repl
  "nREPL服务"
  (:require [reply.main :as reply]
            [clojure.tools.nrepl.server :as nrepl]
            [zeta
             [coercion :refer [as-bool as-int]]
             [logging :as log]
             [opt :as zeta]
             [schedule :refer [at-exit]]]))

(defn start
  "启动REPL"
  []
  (when (as-bool (zeta/of [:repl :enable] true))
    (require 'cider.nrepl)
    (let [port (as-int (zeta/of [:repl :port] 9999))
          handler (ns-resolve 'cider.nrepl 'cider-nrepl-handler)
          interactively? (as-bool (zeta/of [:repl :interactive] true))]
      (log/debug "start zeta repl @ {} interactively? {}" port interactively?)
      (if interactively?
        (future
          (reply/launch-nrepl {:color true
                               :port port
                               :handler handler})
          (shutdown-agents))
        (let [server (nrepl/start-server :bind "0.0.0.0"
                                         :port port
                                         :handler handler)]
          (at-exit #(nrepl/stop-server server)))))))
