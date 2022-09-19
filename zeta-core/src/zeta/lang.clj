(ns zeta.lang
  "Clojure语言增强"
  (:require [clojure
             [pprint :refer [cl-format]]
             [test :refer [is are]]]
            [zeta
             [coll :refer [leaves walk]]
             [id :refer [ns?
                         id?
                         placeholder
                         interpolate]]]))

(defn type-of
  "返回Clojure类型"
  [o]
  (cond
    (nil? o) :nil
    (symbol? o) :symbol
    (keyword? o) :keyword
    (ns? o) :namespace
    (integer? o) :integer
    (float? o) :float
    (string? o) :string
    (seq? o) :seq
    (vector? o) :vector
    (map? o) :map
    (set? o) :set
    :else (.. o getClass getName)))

;;; control

(defmacro if-seq
  "展开(let [... expr] (if ))
  当 expr 为 nil '() [] {} #{} 时，执行 else；否则执行 then。"
  ([bindings then] `(if-seq ~bindings ~then nil))
  ([[bindings expr] then else]
   `(let [value# ~expr]
      (if (seq value#)
        (let [~bindings value#]
          ~then)
        ~else))))

(defmacro when-seq
  "展开(let [... expr] (when ))
  当 expr 不为 nil '() [] {} #{} 时，执行 then。"
  [bindings then]
  `(if-seq ~bindings (do ~@then)))

(defn first-by
  "找到第一个符合条件的元素并返回"
  {:test #(are [expected f coll] (= expected (first-by f coll))
            1 odd? (range 10)
            0 even? (range 10)
            6 (partial < 5) (range 10))}
  [f coll]
  (first (drop-while (complement f) coll)))

(defn first-of
  "返回第一个非nil结果"
  {:test #(is (= :six (first-of (fn [index]
                                  (when (> index 5)
                                    :six))
                                (range 10))))}
  [f coll]
  (first (drop-while nil? (map f coll))))

(defmacro let-by
  "在同一上下文中修改变量，并保持变量名不变
  (let-by [[user-id order-id] string->int
           amount string->double]
    ;; => user-id (string->int user-id)
    ;; => order-id (string->int order-id)
    ;; => amount (string->double amount)
    ...)"
  [bindings & body]
  (->> bindings
    (partition 2)
    (mapcat (fn [[vs f]]
              (if (or (seq? vs) (vector? vs))
                (mapcat (fn [v] [v (list f v)]) vs)
                [vs (list f vs)])))
    vec
    (conj body)
    (cons 'let)))

(defn pipe
  "管道：(pipe seed f1 f2) => (f2 (f1 seed))"
  {:test #(is (= 4 (pipe 1 inc (partial * 2))))}
  [seed & fns]
  (reduce #(%2 %1) seed fns))

;;; template

(defmacro template-for
  "代码模板，可通过 `$xx$` 占位符批量生成代码
  bindings兼容doseq和for
  *注* 因为代码在编译期展开，因此bindings中不支持引用外部变量
  必须都是常量，但支持表达式
  示例：
      (template-for [[index ordinal] (map-indexed vector '(first second third fourth fifth))]
        (defn my-$ordinal$ [v]
          (nth v $index$ nil)))
  生成 my-first、my-second 等"
  [bindings & code]
  (->> code
    leaves
    (filter id?)
    (map str)
    (mapcat (partial re-seq placeholder))
    (map second)
    (into #{})
    (map (fn [token] [token (symbol token)]))
    (into {})
    (list 'for bindings)
    eval
    (mapcat #(walk (partial interpolate %) code))
    (cons 'do)))

;;; fn

;;; fn - 类型提示

(defn strip-type-hint
  "去掉meta中的:tag"
  [o]
  (vary-meta o dissoc :tag))

(defn typed-bindings-expand
  [[bindings & codes]]
  (if-seq [lets (->> bindings
                  leaves
                  (filter symbol?)
                  (filter (comp :tag meta))
                  (mapcat #(let [e (strip-type-hint %)]
                             [e (list (:tag (meta %)) e)])))]
    (list (walk #(if (symbol? %) (strip-type-hint %) %) bindings)
          `(let [~@lets] ~@codes))
    (list bindings codes)))

(defmacro func
  "fn增强：类型提示语法用于参数转换"
  [& [fn-name :as codes]]
  (let [has-name? (symbol? fn-name)
        codes (if has-name?
                (next codes)
                codes)
        polymorphic? (seq? (first codes))
        codes (map typed-bindings-expand (if polymorphic? codes [codes]))]
    (cond
      (and has-name? polymorphic?) `(fn ~fn-name ~@codes)
      (and has-name? (not polymorphic?)) `(fn ~fn-name ~@(first codes))
      (and (not has-name?) polymorphic?) `(fn ~@codes)
      (and (not has-name?) (not polymorphic?)) `(fn ~@(first codes)))))

(defmacro defun
  "等价于 (def name (func ...))"
  [fn-name & codes]
  `(def ~fn-name (func ~@codes)))

;;; fn - monad安全检查

(defn- wrap-body-when-not-nil
  "对参数+代码块的数据包装参数nil检测"
  [[args & codes :as body]]
  (if-not (empty? args)
    `(~args
      (when (not-any? nil? ~args)
        ~@codes))
    body))

(defn- wrap-define-when-not-nil
  "对代码定义包装参数nil检测"
  [f codes]
  (if (some vector? codes)
    (let [[prefix suffix] (split-with (complement vector?) codes)]
      `(~f ~@prefix ~@(wrap-body-when-not-nil suffix)))
    (let [[prefix suffixes] (split-with (complement list?) codes)]
      `(~f ~@prefix ~@(map wrap-body-when-not-nil suffixes)))))

(defmacro monad
  "当`fn`有任何一个参数为nil时返回nil，否则执行相应代码"
  [& codes]
  (wrap-define-when-not-nil 'fn codes))

(defmacro defmonad
  "当`defn`有任何一个参数为nil时返回nil，否则执行相应代码"
  [& codes]
  (wrap-define-when-not-nil 'defn codes))

(defmacro defmonad-
  "当`defn`有任何一个参数为nil时返回nil，否则执行相应代码"
  [& codes]
  (wrap-define-when-not-nil 'defn- codes))

;;; fn - 其他简写

(defmacro ^{:doc "defn + memoize"
            :arglists '([name doc-string? [params*] exprs*]
                        [name doc-string? ([params*] exprs*) +])}
  defmemoize
  [name & [doc-string & codes :as body]]
  (if (string? doc-string)
    `(def ~name ~doc-string (memoize (fn ~@codes)))
    `(def ~name (memoize (fn ~@body)))))

(defmacro defn-record
  "定义继承了IFn接口的record对象
  即可作为函数被调用"
  [name fields callable & specs]
  (letfn [(arglist [n]
            (map (comp symbol (partial str "arg") inc) (range n)))]
    `(defrecord ~name ~fields
       clojure.lang.IFn
       (~'applyTo ~@callable)
       ~@(map (fn [times]
                `(~'invoke [~'this ~@(arglist times)]
                  (.applyTo ~'this (list ~@(arglist times)))))
           (range 21))
       (~'invoke [~'this ~@(arglist 20) ~'args]
        (.applyTo ~'this (concat (list ~@(arglist 20)) ~'args)))
       ~@specs)))

;;; 补齐序数词 third, fourth, ..., ninety-ninth

(template-for [[index cardinal ordinal]
               (map (fn [index]
                      [(dec index) index
                       (symbol (cl-format false "~:r" index))])
                    (drop 3 (range 100)))]
  (defn $ordinal$
    "返回序列中第$cardinal$个值
  如果序列为nil或第$cardinal$个值不存在，返回nil"
    {:test (fn []
             (is (= $index$ ($ordinal$ (range 100))))
             (is (nil? ($ordinal$ []))))}
    [v]
    (nth v $index$ nil)))
