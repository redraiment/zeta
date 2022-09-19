(ns zeta.web.middleware.default-charset
  "自动为文本型的响应添加默认字符编码
  代码Copy自从ring，但补充了文本类型"
  (:require [ring.util.response :as response]))

(defn- text-based-content-type? [content-type]
  (or (re-find #"text/" content-type)
      (re-find #"application/(?:json|xml|xhtml\+xml|.*script)" content-type)))

(defn- contains-charset? [content-type]
  (re-find #";\s*charset=[^;]*" content-type))

(defn default-charset-response
  "为文本型的response添加默认的字符编码"
  [response charset]
  (if response
    (if-let [content-type (response/get-header response "Content-Type")]
      (if (and (text-based-content-type? content-type)
               (not (contains-charset? content-type)))
        (response/charset response charset)
        response)
      response)))

(defn wrap-default-charset
  "当文本型的响应没有指定字符编码时，采用默认的字符编码
  默认utf-8"
  ([handler]
   (wrap-default-charset handler "utf-8"))
  ([handler charset]
   (fn [request]
     (default-charset-response (handler request) charset))))
