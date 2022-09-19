(ns zeta.json
  "JSON相关工具函数"
  (:require [clojure.string :as cs]
            [cheshire
             [core :as json]
             [generate :refer [add-encoder
                               encode-long
                               remove-encoder]]]
            [zeta.file :refer [file?]])
  (:import com.fasterxml.jackson.databind.ObjectMapper
           [net.thisptr.jackson.jq JsonQuery Scope])
  (:gen-class))

(defn decode
  "解析目标字符串

  1. 字符串不能为空
  2. 字符串是存在的本地文件路径或远程地址，则解析从地址中读取的内容
  3. 否则，解析字符串内容"
  [s]
  (when-not (cs/blank? s)
    (json/decode
     (if (or (re-find #"^\w+://" s)
             (file? s))
       (slurp s)
       s)
     true)))

(defn encode
  "将对象序列化成JSON字符串"
  [o]
  (json/encode o))

(let [encoder #(encode-long (.getTime %1) %2)]
  (doseq [type [java.util.Date
                java.sql.Timestamp
                java.sql.Date
                java.sql.Time]]
    (add-encoder type encoder)))

(defonce ^:private object-mapper
  (ObjectMapper.))

(defn- to-json-query
  [^String path]
  (JsonQuery/compile path))

(defn- to-json-node
  [^String s]
  (.readTree object-mapper s))

(defn- jq-apply
  [^String path ^String s]
  (. (to-json-query path) apply
     (Scope/newEmptyScope)
     (to-json-node s)))

;;; 单结果

(defn- jq-str
  [^String path s]
  (->> s
    (jq-apply path)
    first
    str))

(defn- jq-coll
  [^String path s]
  (->> s
    encode
    (jq-str path)
    decode))

(defn jq
  "jq命令，返回单结果"
  [^String path o]
  (cond
    (coll? o) (jq-coll path o)
    (string? o) (jq-str path o)
    :else (jq-str path (str o))))

;;; 多结果

(defn- jqs-str
  [^String path s]
  (str \[
       (->> s
         (jq-apply path)
         (map str)
         (cs/join ","))
       \]))

(defn- jqs-coll
  [^String path s]
  (->> s
    encode
    (jqs-str path)
    decode))

(defn jqs
  "jq命令，返回多结果集"
  [^String path o]
  (cond
    (coll? o) (jqs-coll path o)
    (string? o) (jqs-str path o)
    :else (jqs-str path (str o))))
