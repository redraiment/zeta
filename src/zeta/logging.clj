(ns zeta.logging
  "基于Logback的日志"
  (:require [clojure.string :as cs]
            [zeta
             [coercion :refer [as-int]]
             [coll :refer [hash-seq]]
             [file :refer [absolute-path path]]
             [lang :refer [template-for]]
             [opt :as zeta]])
  (:import org.slf4j.LoggerFactory
           org.slf4j.bridge.SLF4JBridgeHandler
           [ch.qos.logback.classic Level Logger PatternLayout]
           ch.qos.logback.core.ConsoleAppender
           ch.qos.logback.core.encoder.LayoutWrappingEncoder
           [ch.qos.logback.core.rolling RollingFileAppender TimeBasedRollingPolicy])
  (:gen-class :main false
              :name zeta.ZetaLogbackConfigurator
              :extends ch.qos.logback.core.spi.ContextAwareBase
              :implements [ch.qos.logback.classic.spi.Configurator]))

(SLF4JBridgeHandler/removeHandlersForRootLogger)
(SLF4JBridgeHandler/install)

(def ^:private logback-context
  "Logback的上下文对象"
  nil)

(defn level!
  "设置日志Level"
  ([level] (level! Logger/ROOT_LOGGER_NAME level))
  ([package level]
   (when logback-context
     (.. logback-context
         (getLogger (if (cs/blank? package)
                      Logger/ROOT_LOGGER_NAME
                      package))
         (setLevel (Level/toLevel (name level)))))))

;;; initialize

(defn- logback-layout []
  (doto (PatternLayout.)
    (.setPattern "%d{yyyy-MM-dd HH:mm:ss.SSS} %thread %logger %relative %level %message%xEx{full}%n")
    (.setContext logback-context)
    .start))

(defn- logback-encoder [layout]
  (doto (LayoutWrappingEncoder.)
    (.setContext logback-context)
    (.setLayout layout)))

(defn- logback-path [file-name]
  (absolute-path (path (zeta/of [:logging :dir])
                       (if (cs/ends-with? file-name ".log")
                         file-name
                         (str file-name ".log")))))

(defn- logback-appender []
  (let [dir (zeta/of [:logging :dir])
        appender (if (cs/blank? dir)
                   (ConsoleAppender.)
                   (RollingFileAppender.))]

    (when-let [module (and (not (cs/blank? dir))
                           (name (zeta/of :module)))]
      ;; file appender
      (doto appender
        (.setFile (logback-path module))
        (.setRollingPolicy (doto (TimeBasedRollingPolicy.)
                             (.setFileNamePattern (logback-path (str module "-%d{yyyyMMdd}")))
                             (.setMaxHistory (zeta/of [:logging :days] 60))
                             (.setParent appender)
                             (.setContext logback-context)
                             .start))))

    (doto appender
      (.setName "appender")
      (.setContext logback-context)
      (.setEncoder (logback-encoder (logback-layout)))
      .start)))

(defn -configure [this context]
  (alter-var-root #'logback-context (constantly context))
  (let [appender (logback-appender)]
    (doto (.getLogger logback-context Logger/ROOT_LOGGER_NAME)
      .detachAndStopAllAppenders
      (.setLevel Level/INFO)
      (.addAppender appender)))

  (doseq [[packages level] (hash-seq (zeta/ofs [:logging :level]))
          :let [package (cs/join "." (map name packages))]]
    (level! package level)))

;;; exports

(defn logp
  "日志输出函数"
  {:arglists '([package handler template messages throwable?])}
  [package handler template & messages]
  (let [logger (LoggerFactory/getLogger (str (ns-name package)))]
    (if (instance? Throwable (last messages))
      (do
        (handler logger (str template) (to-array (butlast messages)))
        (handler logger "" (last messages)))
      (handler logger (str template) (to-array messages)))))

(template-for [level '[error warn info debug trace]]
  (defmacro $level$ [template & messages]
    `(logp ~*ns* (memfn ~'$level$ ~'template ~'messages) ~template ~@messages)))
