(ns zeta.id
  "标识(identifier)相关函数
  包括：symbol、keyword、namespace"
  (:require [clojure
             [string :as cs]
             [test :refer [are]]]))

(defn ns?
  "是否为Clojure命名空间对象"
  [o]
  (instance? clojure.lang.Namespace o))

(defn id?
  "字符串或符号或关键字或命名空间"
  [o]
  (or (string? o)
      (symbol? o)
      (keyword? o)
      (ns? o)))

(def symbolf
  "symbol + format"
  (comp symbol format))

(def keywordf
  "keyword + format"
  (comp keyword format))

(def nsf
  "namespace + format"
  (comp the-ns symbolf))

(defn id
  "提取tag的字符串名字"
  [o]
  (cond
    (keyword? o) (name o)
    (ns? o) (str (ns-name o))
    :else (str o)))

(defn path->ns
  "文件路径转命名空间Symbol"
  [path]
  (-> path
    (cs/replace #"\.[^.]+$" "")
    (cs/replace #"/" ".")
    (cs/replace #"_" "-")
    symbol))

(defn ns->path
  [n]
  (-> n
    id
    (cs/replace #"-" "_")
    (cs/replace #"[.\\/]+" "/")
    (cs/replace #"/$" "")))

(def placeholder #"\$([-_0-9a-zA-Z]+)\$")

(defn interpolate
  "类似字符串插值
  用 smap 中的映射替换目标中 /$[-_0-9a-zA-Z]+$/ 形式的占位符"
  {:test #(are [expected id smap] (= expected (interpolate smap id))
            "Hello joe" "Hello $name$" {"name" "joe"}
            'foo-baz 'foo-$name$ {"name" "baz"}
            :foo-baz :foo-$name$ {"name" "baz"})}
  [smap o]
  (let [name (id o)]
    (cond
      (and (symbol? o) (re-matches placeholder name)) (smap (cs/replace name #"\$" ""))
      (id? o) ((cond
                 (symbol? o) symbol
                 (keyword? o) keyword
                 (ns? o) nsf
                 :else identity)
               (cs/replace name placeholder (comp str smap second)))
      :else o)))

(defmacro with-ns
  "临时切换命名空间"
  [ns & body]
  `(binding [*ns* (the-ns '~ns)]
     ~@body))
