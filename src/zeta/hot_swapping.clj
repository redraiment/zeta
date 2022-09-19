(ns zeta.hot-swapping
  "热部署相关函数"
  (:require [clojure.string :as cs]
            [zeta
             [id :refer [path->ns]]
             [file :refer [file? absolute-path relative-path] :as file]
             [logging :as log]]))

(defonce ^{:private true :doc "监听的源码路径"}
  prefixes
  (atom #{}))

(defn watch
  "监听源码路径"
  [& paths]
  (swap! prefixes into (map #(str (absolute-path %) file/separator) paths))
  (apply file/watch paths))

(defonce ^{:private true :doc "源文件变化时处理函数"}
  handlers
  (atom []))

(defn register
  "订阅"
  [f]
  (swap! handlers conj f))

(defn source-file-changed-hook
  "源文件修改的回调函数
  只处理文件的新增和修改事件"
  [{:keys [action file]}]
  (when-let [path (and (file? file)     ; create & modify only
                       (some #(when (cs/starts-with? file %)
                                (relative-path % file))
                             @prefixes))]
    (let [ns (path->ns path)]
      (log/debug "{} {}" action file)
      (when (cs/ends-with? file ".clj")
        (require [ns :reload true]))
      (doseq [handler @handlers]
        (handler {:file path :ns ns})))))
