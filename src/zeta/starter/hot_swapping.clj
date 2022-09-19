(ns zeta.starter.hot-swapping
  "热部署相关服务"
  (:require [zeta
             [file :refer [directory? path-separator
                           watch-accept watch-handler-register]]
             [hot-swapping :refer [watch source-file-changed-hook]]
             [logging :as log]
             [schedule :refer [daemon]]]))

(defn start
  "运行文件监视服务"
  []
  (watch-handler-register source-file-changed-hook)
  ;; watching source path
  (->> path-separator
    (.split (System/getProperty "java.class.path"))
    (filter directory?)
    (apply watch))
  ;; start
  (daemon watch-accept "zeta-hot-swapping")
  (log/debug "start zeta hot-swapping"))
