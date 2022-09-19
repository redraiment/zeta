(ns zeta.cfg
  "应用配置
  有别于zeta.opt，cfg用于应用程序的配置"
  (:require [clojure.string :as cs]
            [zeta
             [file :refer [edn-of]]
             [id :refer [ns->path id]]
             [hot-swapping :as hot]
             [opt :as zeta]
             [str :refer [slice]]]))

(defonce ^:dynamic *tag*
  (zeta/of [:cfg :tag]))

(defonce ^{:private true :doc "配置文件目录前缀"}
  -prefix
  (str (ns->path (zeta/of [:cfg :ns] (zeta/package "config")))
       "/" (id *tag*) "/"))

(defn config?
  "判断给定的文件名是否当前环境的配置文件"
  [file-name]
  (and (cs/starts-with? file-name -prefix)
       (cs/ends-with? file-name ".edn")))

(def ^:private caches
  "缓存的资源数据"
  (atom {}))

(defn- refresh
  "刷新缓存"
  [key]
  ((swap! caches assoc key
          (edn-of (str "resource://" -prefix key ".edn")))
   key))

(defn of
  "配置项由应用程序自己控制，因此不需要默认值"
  [k & ks]
  (let [key (id k)]
    (if-let [config (or (@caches key) (refresh key))]
      (if (seq ks)
        (get-in config ks)
        config))))

(hot/register
 (fn [{:keys [file]}]
   (when (config? file)
     (refresh (slice file (count -prefix) -4)))))
