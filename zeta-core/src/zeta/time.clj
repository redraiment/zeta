(ns zeta.time
  "时间相关函数"
  (:require [zeta.lang :refer [defmonad first-of]])
  (:import [java.sql Timestamp Date Time]
           java.text.SimpleDateFormat
           java.util.Calendar
           java.util.concurrent.TimeUnit))

;;; formatting

(defmonad ^java.text.SimpleDateFormat format-of
  [^String pattern]
  (SimpleDateFormat. pattern))

(defmonad ^String datetime-to
  [^String pattern ^java.util.Date d]
  (.format (format-of pattern) d))

(defmonad ^java.util.Date datetime-of
  [^String pattern ^String d]
  (.parse (SimpleDateFormat. pattern) d))

(defmonad ^String pattern-guess
  "猜测给定字符串最符合的格式
  如果都无法匹配，返回默认日期时间格式"
  ([^String s]
   (pattern-guess s "yyyy-MM-dd HH:mm:ss"))
  ([^String s ^String default-pattern]
   (or (first-of (fn [[pattern template]]
                   (when-let [[_ & matched] (re-find pattern s)]
                     (apply format template matched)))
                 [[#"(?:\d\d){1,2}([-/])\d{1,2}\1\d{1,2}([ T])\d{1,2}(?::\d{1,2}){2}\.\d{1,3}" "yyyy%1$sMM%1$sdd'%2$s'HH:mm:ss.SSS"]
                  [#"(?:\d\d){1,2}([-/])\d{1,2}\1\d{1,2}([ T])\d{1,2}(?::\d{1,2}){2}" "yyyy%1$sMM%1$sdd%2$sHH:mm:ss"]
                  [#"(?:\d\d){1,2}([-/])\d{1,2}\1\d{1,2}" "yyyy%1$sMM%1$sdd"]
                  [#"\d{1,2}(?::\d{1,2}){2}" "HH:mm:ss"]])
       default-pattern)))

(defmonad ^java.util.Date str->datetime
  "String -> java.util.Date"
  ([^String s]
   (str->datetime (pattern-guess s) s))
  ([^String pattern ^String s]
   (datetime-of pattern s)))

(defn ->datetime
  "anything -> java.util.Date"
  [o]
  (cond
    (integer? o) (java.util.Date. o)
    (string? o) (str->datetime o)
    (instance? java.util.Date o) o
    (instance? java.util.Calendar o) (.getTime o)))

(defmonad ^Long str->datetime->long
  "String -> java.util.Date -> long"
  ([^String s]
   (str->datetime->long (pattern-guess s) s))
  ([^String pattern ^String s]
   (.getTime (str->datetime pattern s))))

(defmonad ^java.sql.Timestamp str->timestamp
  "String -> java.sql.Timestamp"
  ([^String s]
   (str->timestamp (pattern-guess s "yyyy-MM-dd HH:mm:ss.SSS") s))
  ([^String pattern ^String s]
   (Timestamp. (str->datetime->long pattern s))))

(defmonad ^java.sql.Date str->date
  "String -> java.sql.Date"
  ([^String s]
   (str->date (pattern-guess s "yyyy-MM-dd") s))
  ([^String pattern ^String s]
   (Date. (str->datetime->long pattern s))))

(defmonad ^java.sql.Time str->time
  "String -> java.sql.Time"
  ([^String s]
   (str->time (pattern-guess s "HH:mm:ss") s))
  ([^String pattern ^String s]
   (Time. (str->datetime->long pattern s))))

(defn ^String timestamp->str
  "java.sql.Timestamp -> String"
  [^java.util.Date d]
  (datetime-to "yyyy-MM-dd HH:mm:ss.SSS" d))

(defn ^String datetime->str
  "java.util.Date -> String"
  [^java.util.Date d]
  (datetime-to "yyyy-MM-dd HH:mm:ss" d))

(defn ^String date->str
  "java.sql.Date -> String"
  [^java.util.Date d]
  (datetime-to "yyyy-MM-dd" d))

(defn ^String time->str
  "java.sql.Time -> String"
  [^java.util.Date d]
  (datetime-to "HH:mm:ss" d))

(defmonad ^java.util.Date long->datetime
  "long -> java.util.Date"
  [^Long l]
  (java.util.Date. l))

(defmonad ^java.sql.Timestamp long->timestamp
  "long -> java.util.Date"
  [^Long l]
  (java.sql.Timestamp. l))

(defmonad ^java.sql.Date long->date
  "long -> java.util.Date"
  [^Long l]
  (java.sql.Date. l))

(defmonad ^java.sql.Time long->time
  "long -> java.util.Date"
  [^Long l]
  (java.sql.Time. l))

(defmonad ^String long->datetime->str
  "long -> java.util.Date -> String"
  ([^Long l] (long->datetime->str "yyyy-MM-dd HH:mm:ss" l))
  ([^String pattern ^Long l]
   (datetime-to pattern (long->datetime l))))

;;; time machine

(def ^:dynamic *now*
  "`java.util.Date`实例
  默认为空，即实时获取当前时间。
  回归测试时可指定某个日期代替当前时间。"
  nil)

(defmacro with
  "局部绑定 *now*
  接受字符串形式的日期（如格式YYYY-MM-DD）
  或`java.util.Date`的实例"
  [now & body]
  `(binding [*now* (->datetime ~now)]
     ~@body))

;;; utilities

(defn to-millis
  "将给定的单位换算成毫秒"
  ([unit] (to-millis 1 unit))
  ([duration unit & more]
   (reduce
    (fn [millis [duration unit]]
      (+ (. (case unit
              :day TimeUnit/DAYS
              :hour TimeUnit/HOURS
              :minute TimeUnit/MINUTES
              :second TimeUnit/SECONDS
              :milli TimeUnit/MILLISECONDS
              TimeUnit/NANOSECONDS)
            toMillis duration)
         millis))
    0
    (cons [duration unit]
          (partition 2 more)))))

(defmonad leap?
  "是否为闰年"
  [year]
  (or (and (zero? (mod year 4))
           (pos? (mod year 100)))
      (zero? (mod year 400))))

(defn ^java.util.Calendar offset!
  "在给定日历的基础上做日期调整
  当前函数会修改传入的参数"
  [^java.util.Calendar c & offsets]
  (doseq [[amount unit] (partition 2 offsets)
          :let [field (case unit
                        :year Calendar/YEAR
                        :month Calendar/MONTH
                        :day Calendar/DAY_OF_MONTH
                        :hour Calendar/HOUR_OF_DAY
                        :minute Calendar/MINUTE
                        :second Calendar/SECOND
                        :milli Calendar/MILLISECOND
                        unit)]]
    (.add c field amount))
  c)

(defn ^java.util.Calendar offset
  "在给定日历的基础上做日期调整"
  [^java.util.Calendar c & offsets]
  (apply offset! (.clone c) offsets))

(defn ^java.util.Calendar calendar
  "返回基于 *now* 的 Calendar 对象。
  如果 *now* 为空，则返回当前时间的Calendar对象；
  否则，生成指定时间的Calendar对象。"
  [& [datetime & offsets :as args]]
  (let [now (Calendar/getInstance)
        datetime-provided? (odd? (count args))]
    (when-let [at (if datetime-provided?
                    (->datetime datetime)
                    *now*)]
      (.setTime now at))
    (apply offset! now (if datetime-provided? offsets args))))

(def ^java.util.Date date
  "返回基于 *now* 的 Date 对象"
  (comp (memfn getTime) (partial calendar)))

(defmonad year
  "获得给定日期的年"
  ([] (year *now*))
  ([date] (.get (calendar date) Calendar/YEAR)))

(defmonad month
  "获得给定日期的月"
  ([] (month *now*))
  ([date] (inc (.get (calendar date) Calendar/MONTH))))

(defmonad day
  "获得给定日期的天"
  ([] (day *now*))
  ([date] (.get (calendar date) Calendar/DAY_OF_MONTH)))

;;; 与当前时间比较

(defmonad before?
  "判断给定的时间是否在 *now* 之前"
  [o]
  (.before (date) (->datetime o)))

(defmonad not-before?
  "判断给定的时间是否不在 *now* 之前"
  [o]
  (not (before? o)))

(defmonad after?
  "判断给定的时间是否在 *now* 之后"
  [o]
  (.after (date) (->datetime o)))

(defmonad not-after?
  "判断给定的时间是否不在 *now* 之后"
  [o]
  (not (after? 0)))

;;; 区间

(defmonad diff
  "两个给定日期的距离"
  ([f t] (diff f t :second))
  ([f t unit]
   (let [from (calendar f)
         to (calendar t)]
     (case unit
       :year (- (.get to Calendar/YEAR) (.get from Calendar/YEAR))
       :month (+ (* 12 (- (.get to Calendar/YEAR) (.get from Calendar/YEAR)))
                 (- (.get to Calendar/MONTH) (.get from Calendar/MONTH)))
       (quot (- (.. to getTime getTime)
                (.. from getTime getTime))
             (to-millis unit))))))

(defmonad period
  "返回判断时间是否在区间内的函数"
  ([begin] (period begin *now*))
  ([begin end]
   (let [[begin end]
         (map #(if (vector? %)
                 (apply date %)
                 (date %))
              [begin end])]
     #(let [now (->datetime %)]
        (not (or (.after begin now)
                 (.after now end)))))))
