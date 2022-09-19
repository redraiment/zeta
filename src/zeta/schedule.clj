(ns zeta.schedule
  "线程调度相关函数"
  (:require [zeta.time :refer [date to-millis]])
  (:import [java.util Timer TimerTask]
           [java.util.concurrent Executors Semaphore TimeUnit]))

(defn sleep
  "休眠当前线程
  * 无参数调用，则永久性停止
  * 单参数调用，默认单位为秒
  * 第二个可选参数取值：:day :hour :minute :second :milli"
  ([]
   (locking Object
     (.wait Object)))
  ([seconds]
   (sleep seconds :second))
  ([duration unit]
   (Thread/sleep (to-millis duration unit))))

(defn daemon
  "新建守护线程"
  ([f]
   (daemon f "zeta-daemon"))
  ([f n]
   (doto (Thread. f n)
     (.setDaemon true)
     (.start))))

(defonce ^{:private true :doc "系统退出时的回调函数"}
  at-exit-handlers
  (atom []))

(. (Runtime/getRuntime) addShutdownHook
   (Thread. (fn []
              (doseq [f @at-exit-handlers] (f))
              (shutdown-agents))))

(defn at-exit
  "系统退出时执行的任务"
  [f]
  (swap! at-exit-handlers conj f))

;;; 线程组

(defn thread-group-root
  "返回最顶部ThreadGroup"
  []
  (loop [group (.getThreadGroup (Thread/currentThread))]
    (if-let [parent (.getParent group)]
      (recur parent)
      group)))

(defn thread-groups
  "罗列ThreadGroup下的ThreadGroup"
  ([group]
   (thread-groups group false))
  ([group recurse]
   (let [a (make-array ThreadGroup (* (.activeGroupCount group) 2))]
     (.enumerate group a recurse)
     (remove nil? a))))

(defn threads
  "罗列ThreadGroup下的Thread"
  ([group]
   (threads group false))
  ([group recurse]
   (let [a (make-array Thread (* (.activeCount group) 2))]
     (.enumerate group a recurse)
     (remove nil? a))))

;;; 时间调度

(defonce ^{:private true :doc "定时调度器"}
  timer
  (Timer. "zeta-schedule-timer" true))

(defn at
  "在指定时间执行"
  {:arglists '([datetime? offset... callback])}
  [& args]
  (when (fn? (last args))
    (let [datetime (butlast args)
          callback (last args)
          task (proxy [TimerTask] []
                 (run [] (callback)))]
      (.schedule timer task (apply date datetime))
      task)))

(defonce ^{:private true :doc "最后提交的单任务"}
  last-task
  (atom nil))

(defn at-once
  "在指定时间执行任务
  队列中永远只保留一个待运行任务
  因此队列中未执行的任务将被取消"
  {:arglists '([datetime? offset... callback])}
  [& args]
  (when-not (nil? @last-task)
    (.cancel @last-task))
  (swap! last-task #(or (apply at args) %)))

(defn scheduler
  "创建新的任务调度器"
  [& {:keys [name size daemon?]
      :or {name "zeta-scheduler"
           size 1
           daemon? true}}]
  (Executors/newScheduledThreadPool
   size
   (proxy [java.util.concurrent.ThreadFactory] []
     (^Thread newThread [^Runnable r]
      (doto (Thread. r name)
        (.setDaemon daemon?))))))

(defonce ^{:private true :doc "任务调度器"}
  system-scheduler
  (scheduler))

(defn schedule
  "周期性任务调度"
  ([options callback]
   (schedule system-scheduler options callback))
  ([scheduler
    {:keys [initial-delay fixed-rate fixed-delay]
     :or {initial-delay 0}}
    callback]
   (cond
     (integer? fixed-rate) (. scheduler scheduleAtFixedRate callback initial-delay fixed-rate TimeUnit/MILLISECONDS)
     (integer? fixed-delay) (. scheduler scheduleWithFixedDelay callback initial-delay fixed-delay TimeUnit/MILLISECONDS)
     :else (throw (IllegalArgumentException. "fixed-rate or fixed-delay should not be nil")))))

;;; 线程池

(def size
  "CPU核数"
  (.availableProcessors (Runtime/getRuntime)))

(defn pool
  "创建大小为size的线程池函数
  如果没有提供参数，等待所有活动线程结束并关闭线程池。
  如果提供回调函数作为参数，提交改任务；如果此时线程池已满，则阻塞等待。"
  ([] (pool size))
  ([size]
   (let [service (Executors/newWorkStealingPool size)
         semaphore (Semaphore. size)]
     (fn
       ([f]
        (.acquire semaphore)
        (. service execute
           (bound-fn []
             (try
               (f)
               (finally
                 (.release semaphore))))))
       ([]
        (.acquire semaphore size)
        (.release semaphore size)
        (.shutdown service))))))

;;; 服务

(at-exit
 (fn []
   (.cancel timer)
   (when-not (nil? @last-task)
     (.cancel @last-task))
   (.shutdown system-scheduler)))
