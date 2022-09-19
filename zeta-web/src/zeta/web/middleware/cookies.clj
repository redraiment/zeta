(ns zeta.web.middleware.cookies
  "Cookie读写中间件"
  (:require [ring.middleware.cookies :as ring-cookies]
            [zeta
             [id :refer [id]]
             [opt :as zeta]]))

(def ^:private default-options
  "Cookie的默认选项"
  (zeta/ofs [:web :server :cookies] {}))

(def ^:private ^:dynamic *request-cookies*
  "请求中提交的Cookie数据"
  {})

(def ^:private ^:dynamic *response-cookies*
  "响应中返回的Cookie数据"
  (atom {}))

(defn cookies
  "读取、设置、删除Cookie"
  ([]
   (merge *request-cookies* @*response-cookies*))
  ([name]
   (get-in (cookies) [(id name) :value]))
  ([name value & {:keys [path domain max-age expires secure http-only same-site] :as options}]
   (swap! *response-cookies* assoc
          (id name)
          (let [options (merge default-options options)]
            (if (nil? value)
              (assoc options :value "" :max-age 0)
              (assoc options :value value))))))

(defn wrap-cookies
  ([handler]
   (wrap-cookies handler {}))
  ([handler options]
   (ring-cookies/wrap-cookies
    (bound-fn [{:keys [cookies] :as request}]
      (binding [*request-cookies* cookies
                *response-cookies* (atom {})]
        (assoc ((bound-fn* handler) request)
               :cookies @*response-cookies*)))
    options)))
