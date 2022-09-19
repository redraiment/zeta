(ns zeta.coll
  "集合操作相关函数"
  (:require [clojure.test :refer [are]]))

(def map->vector
  "map + into []"
  mapv)

(def map->hash
  "map + into {}"
  (comp (partial into {}) map))

(def map->set
  "map + into #{}"
  (comp set map))

(def vconcat
  "vec + conact"
  (comp vec concat))

(defn walk
  "递归版map，返回原始数据结构"
  {:test #(are [expected o] (= expected (walk inc o))
            {1 1 2 {3 #{2 [3]}}}
            {0 0 1 {2 #{1 [2]}}})}
  [f o]
  (cond
    (or (seq? o) (list? o)) (map (partial walk f) o)
    (vector? o) (mapv (partial walk f) o)
    (map? o) (->> o
               (map (fn [[k v]]
                      [(walk f k)
                       (walk f v)]))
               (into {}))
    (set? o) (into #{} (map (partial walk f) o))
    :else (f o)))

(defn leaves
  "平摊所有集合，返回叶子节点"
  {:test #(are [expected o] (= expected (leaves o))
            '(:one 1 :two :three 2 3)
            {:one 1 :two {:three [2 #{3}]}})}
  [o]
  (mapcat (fn [e]
            (if (coll? e)
              (leaves e)
              [e]))
          o))

(defn product
  "笛卡尔积"
  {:test #(are [expected o] (= expected (product o))
            [[1 4 6] [2 4 6] [3 4 6]
             [1 5 6] [2 5 6] [3 5 6]]
            [[1 2 3] [4 5] [6]])}
  [seqs]
  (let [matrix (mapv vec seqs)
        counts (map count seqs)
        powers (reduce
                (fn [v n]
                  (conj v [(if-let [[a b] (last v)]
                             (* a b)
                             1)
                           n]))
                []
                counts)]
    (->> counts
      (reduce * 1N)
      range
      (mapv (fn [index]
              (->> powers
                (map-indexed (fn [i [p n]]
                               (get-in matrix [i (mod (quot index p) n)])))
                (into [])))))))

(defn flatten-all
  "flatten无法抹平hashmap"
  {:test #(are [expected o] (= expected (flatten-all o))
            '(:a :b :c :d :e 1 :f true)
            {:a {:b {:c :d} :e 1} :f true})}
  [o]
  (mapcat (fn [e]
            (if (coll? e)
              (flatten-all e)
              [e]))
          (seq o)))

(defn hash-seq
  "将hash结构转成seq，和(into '() {})的区别是：
  1. 能处理嵌套的hash结构
  2. key是一个vector，表示value在hash中的路径"
  {:test #(are [expected o] (= expected (hash-seq o))
            '([[:a :b :c] :d] [[:a :e] 1] [[:f] true])
            {:a {:b {:c :d} :e 1} :f true})}
  [hash]
  ((fn nested-hash-seq [prefix root]
     (mapcat (fn [[key value]]
               (let [keys (conj prefix key)]
                 (if (map? value)
                   (nested-hash-seq keys value)
                   [[keys value]])))
             root))
   [] hash))

;;; 包装器
;;; 如果不是目标类型的集合，就包装成目标类型

(defn- wrap-coll
  [predicate? wrapper o]
  (if (predicate? o)
    o
    (wrapper o)))

(def wrap-vector
  "Vector包装器"
  (partial wrap-coll vector? vector))

(def wrap-set
  "Set包装器"
  (partial wrap-coll set? hash-set))

(def wrap-list
  "List包装器"
  (partial wrap-coll list? list))
