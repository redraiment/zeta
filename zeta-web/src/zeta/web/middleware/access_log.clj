(ns zeta.web.middleware.access-log
  "记录访问日志"
  (:require [ring.util.response :refer [response
                                        status]]
            [zeta.logging :as log]))

(defn wrap-access-log
  "记录请求日志
  1. 请求的IP和User Agent
  2. 请求的方法和类型
  3. 请求的URL
  4. 输入的参数
  5. 请求的响应时长
  6. 异常的类型
  7. 异常的消息"
  [handler]
  (fn [{:keys [remote-addr headers
               request-method content-type
               uri query-string
               form-params multipart-params body]
        :as request}]
    (let [source (str remote-addr (if-let [user-agent (get headers "user-agent")]
                                    (format "(%s)" user-agent)))
          method (str request-method (if content-type (format "(%s)" content-type)))
          path (if query-string (str uri "?" query-string) uri)
          params (pr-str (merge (if (map? form-params) form-params)
                                (if (map? multipart-params) multipart-params)
                                (if (map? body) body)))]
      (log/debug "{} {} {} {} B" source method path params)
      (let [b (System/currentTimeMillis)
            r (try
                (handler request)
                (catch Throwable e
                  (let [type (.. e getClass getName)
                        message (.getMessage e)]
                    (log/error "{} {} {} {} E {}({})"
                               source method path params
                               type message e)
                    (-> {:success false
                         :type type
                         :message message}
                      response
                      (status 400)))))
            d (System/currentTimeMillis)]
        (log/debug "{} {} {} {} D {}"
                   source method path params
                   (format "%.3f" (/ (- d b) 1000.0)))
        r))))
