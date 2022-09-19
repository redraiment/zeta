(ns zeta.web.client
  "HTTP客户端"
  (:refer-clojure :exclude [get alias])
  (:require [clojure.walk :refer [keywordize-keys]]
            [org.httpkit.client :as http]
            [clj-json.core :as json]
            [zeta
             [coercion :refer [as-bool
                               as-long
                               as-double]]
             [cfg :as cfg]
             [logging :as log]
             [opt :as zeta]]))

(def ^:dynamic *options*
  (merge {:method :get
          :timeout 100000
          :as :auto
          :keepalive 300000
          :times 0                     ; 访问次数
          :retry 0                     ; 重试次数
          :interval 0}                 ; 重试间隔，单位毫秒，非正数则不延迟
         (zeta/ofs [:web :client])))

(defn- retryable-request
  "可重试的HTTP请求
  遇到非连接错误时，最多重试:retry-max次"
  ([options]
   (retryable-request options (promise)))
  ([options response]
   (http/request
    options
    (fn [{{:keys [method url times retry interval accept?] :as opts} :opts
          error :error
          :as result}]
      (if (and (< times retry)
               (or (and error (not (instance? java.net.ConnectException error)))
                   (and accept? (not (accept? result)))))
        (do
          (when (pos? interval)
            (Thread/sleep interval))
          (retryable-request (update opts :times inc) response))
        (do
          (when error
            (log/warn "{}[{}/{}] {}({})"
                      url times retry
                      (.. error getClass getName)
                      (.getMessage error)))
          (deliver response result)))))
   response))

(def ^:private coercions
  "返回结果解析函数"
  {:json #(try
            (keywordize-keys (json/parse-string %))
            (catch Throwable _
              (log/warn "{} is not a json" %)))
   :bool as-bool
   :long as-long
   :double as-double
   :auto identity})

(defn- options-split
  "HTTP选项和URL拆分"
  [parameters]
  (let [[pairs urls]
        (split-with (comp keyword? first)
                    (partition-all 2 parameters))]
    [pairs (apply concat urls)]))

(defn- options-merge
  "合并options中重名的key
  * 如果类型不一样或不是集合类型：用后面的value覆盖
  * 否则：用 into 合并"
  [pairs]
  (reduce (fn [options [key value]]
            (update options key
                    (fn [old new]
                      (if (and (= (type old) (type new))
                               (coll? new))
                        (into old new)
                        new))
                    value))
          {}
          pairs))

(defn- request-options-build
  "将request函数的参数转成retryable-request的选项"
  [parameters]
  (let [[pairs urls]
        (options-split parameters)

        {:keys [as content-type form-params] :as options}
        (->> urls
          (apply format)
          (list :url)
          (conj pairs)
          options-merge
          (into *options*))]
    (assoc (if (= content-type :json)
             (-> options
               (dissoc :form-params)
               (assoc :body (json/generate-string form-params))
               (assoc-in [:headers "Content-Type"] "application/json; charset=utf-8"))
             options)
           :as :text                    ; always as plain
           :parse as)))

(defn request
  "通用的http请求
  包装http-kit，添加读写json以及重试"
  [& parameters]
  (let [{:keys [parse default] :as options} (request-options-build parameters)
        response (retryable-request options)]
    (if-let [body (and (nil? (get-in @response [:error]))
                       (:body @response))]
      ((cond
         (= parse :auto)
         (coercions
          (cond
            (re-find #"\bjson\b" (get-in @response [:headers :content-type] "")) :json
            (re-matches #"\d+" body) :long
            (re-matches #"\d+\.\d*" body) :double
            (re-matches #"(?i)true|false" body) :bool
            :else parse))
         (keyword? parse) (coercions parse)
         (fn? parse) parse
         :else identity)
       body)
      default)))

(def get (partial request :method :get))
(def head (partial request :method :head))
(def options (partial request :method :options))
(def post (partial request :method :post))
(def put (partial request :method :put))
(def patch (partial request :method :patch))
(def delete (partial request :method :delete))

(def ^:private -templates
  "API模板"
  (atom (or (cfg/of :api) {})))

(defn alias
  "定义或获取模板"
  [name & prefix]
  (if (empty? prefix)
    (@-templates name)
    (swap! -templates assoc name prefix)))

(defn api
  "执行模板"
  [name & suffix]
  (let [[prefix-options prefix-urls] (options-split (alias name))
        [suffix-options suffix-urls] (options-split suffix)]
    (apply request (concat prefix-options suffix-options prefix-urls suffix-urls))))
