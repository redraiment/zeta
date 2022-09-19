(ns zeta.core
  "Zeta框架入口"
  (:require [clojure.string :as cs]
            [clojure.tools.namespace.find :as nf]
            [zeta.file :as file]
            zeta.opt)
  (:gen-class))

(defn- source-files
  "源代码目录"
  []
  (->> file/path-separator
    (.split (System/getProperty "java.class.path"))
    (filter file/exists?)
    (map file/to-file)))

(defn namespaces-of
  "根据条件过滤出符合条件的命名空间符号"
  ([] (namespaces-of (constantly true)))
  ([prefix-or-fn]
   (let [f (if (fn? prefix-or-fn)
             prefix-or-fn
             #(cs/starts-with? (str %) prefix-or-fn))]
     (->> (source-files)
       nf/find-namespaces
       (filter f)
       (into #{})))))

(defn start
  "开启所有zeta启动器"
  [package]
  (alter-var-root #'zeta.opt/root
                  (->> #"\."
                    (cs/split package)
                    butlast
                    (cs/join ".")
                    constantly))
  (doseq [package (namespaces-of "zeta.starter.")]
    (require package)
    (when-let [starter (ns-resolve package 'start)]
      (@starter))))

(defmacro run
  "运行Zeta"
  []
  `(start ~(-> *ns* ns-name str)))

(defn -main [& args]
  (run))
