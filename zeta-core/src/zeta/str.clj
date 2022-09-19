(ns zeta.str
  "字符串相关函数")

(def format* (partial apply format))

(defn slice
  "支持负数下标，支持string、vector、seq类型"
  ([o] (slice o 0))
  ([o start] (slice o start (count o)))
  ([o start end]
   (let [length (count o)
         [start end]
         (->> [start end]
           (map #(if (neg? %) (+ length %) %))
           (map (partial min length)))
         sub ((if (string? o) subs subvec)
              (if (seq? o) (vec o) o) start end)]
     (if (seq? o)
       (seq sub)
       sub))))
