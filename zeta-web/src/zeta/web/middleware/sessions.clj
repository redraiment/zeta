(ns zeta.web.middleware.sessions
  "会话读写中间件"
  (:require [zeta
             [id :refer [id]]
             [opt :as zeta]
             [schedule :refer [schedule]]]
            [zeta.web.middleware.cookies :refer [cookies]])
  (:import java.util.UUID))

(def ^:private session-timeout
  "会话超时时间(毫秒)"
  (zeta/of [:web :server :sessions :timeout] (* 30 60 1000)))

(def ^:private session-token
  "cookie名称"
  (zeta/of [:web :server :sessions :token] :zeta))

(def ^:private data
  "sessions数据"
  (ref {}))

(def ^:private ^:dynamic *id*
  "当前session的uuid"
  nil)

(def ^:private expires
  "过期队列"
  (atom []))

(defn- current-timestamp
  "当前时间戳"
  []
  (System/currentTimeMillis))

(defn- remove-if-expired
  "淘汰给定的id
  * 如果不存在，返回nil
  * 如果已淘汰，删除数据并返回true
  * 否则，返回过期的时间戳"
  [id]
  (when-let [expired-at (get-in @data [id :expired-at])]
    (if (< expired-at (current-timestamp))
      (do
        (alter data dissoc id)
        true)
      expired-at)))

(defn- clean-expired-sessions
  "清理掉过期的session"
  []
  (doseq [[expired-at id] @expires
          :when (< expired-at (current-timestamp))
          :let [new-expired-at (remove-if-expired id)]
          :when (integer? new-expired-at)]
    (swap! expires conj [new-expired-at id])))

(schedule {:initial-delay (* 5 60 1000)
           :fixed-delay (* 5 60 1000)}
          clean-expired-sessions)

(defn- access
  "访问当前id对应的session对象，并更新过期时间"
  []
  (when (nil? (remove-if-expired *id*))
    (swap! expires conj [(current-timestamp) *id*]))
  (alter data assoc-in
         [*id* :expired-at]
         (+ (current-timestamp) session-timeout)))

(defn sessions
  "读取、设置、删除Session"
  ([]
   (dosync
    (get-in (access) [*id* :value])))
  ([name]
   (get (sessions) name))
  ([name value]
   (dosync
    (access)
    (if (nil? value)
      (alter data update-in [*id* :value] dissoc name)
      (alter data assoc-in [*id* :value name] value)))))

(defn wrap-sessions
  [handler]
  (bound-fn [request]
    (when-not (cookies session-token)
      (cookies session-token (str (UUID/randomUUID))))
    (binding [*id* (cookies session-token)]
      (handler request))))
