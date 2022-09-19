(ns zeta.web.middleware.headers
  "用副作用函数设置Response中headers"
  (:require [zeta.id :refer [id]]))

(def ^:private ^:dynamic *headers*
  "响应中返回的Headers数据"
  (atom {}))

(defn headers
  "读取、设置、删除Cookie"
  ([] @headers)
  ([name]
   ((headers) (id name)))
  ([name value]
   (let [key (id name)]
     (if (nil? value)
       (swap! *headers* dissoc key)
       (swap! *headers* assoc key value)))))

(defn wrap-headers
  "合并handler返回的headers与调用本插件设置的headers"
  [handler]
  (bound-fn [request]
    (update (handler request) :headers merge @*headers*)))
