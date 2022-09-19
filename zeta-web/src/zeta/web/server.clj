(ns zeta.web.server
  "Web服务"
  (:require [clojure.string :as cs]
            [cheshire.core :as json]
            [garden.core :as garden]
            [hiccup.core :as hiccup]
            [ring.util
             [mime-type :refer [ext-mime-type]]
             [response :refer [content-type
                               not-found
                               redirect
                               resource-response
                               response]]]
            [ring.middleware
             [content-type :refer [wrap-content-type]]
             [head :refer [wrap-head]]
             [json :refer [wrap-json-body]]
             [keyword-params :refer [wrap-keyword-params]]
             [multipart-params :refer [wrap-multipart-params]]
             [nested-params :refer [wrap-nested-params]]
             [not-modified :refer [wrap-not-modified]]
             [params :refer [wrap-params]]
             [resource :refer [wrap-resource]]]
            [zeta
             [coercion :refer [to-double]]
             [coll :refer [flatten-all walk]]
             [id :refer [id]]
             [opt :as zeta]]
            [zeta.web.middleware
             [access-log :refer [wrap-access-log]]
             [cookies :refer [wrap-cookies]]
             [default-charset :refer [wrap-default-charset]]
             [headers :refer [wrap-headers]]
             [sessions :refer [wrap-sessions]]])
  (:import clojure.lang.ExceptionInfo
           java.util.ArrayList
           java.util.Collections
           java.util.regex.Pattern))

;;; 响应函数

(defn- json-response
  "利用cheshire将结果返回成json"
  [o]
  (-> o
    json/generate-string
    response
    (content-type "application/json; charset=utf-8")))

(defn- html-response
  "利用hiccup将结果返回成html"
  [o]
  (-> "<!DOCTYPE html>\n"
    (str (hiccup/html o))
    response
    (content-type "text/html; charset=utf-8")))

(defn- css-response
  "利用garden将结果返回成css"
  [o]
  (-> o
    garden/css
    response
    (content-type "text/css; charset=utf-8")))

(defn- js-response
  "将字符串返回成JavaScript脚本"
  [s]
  (->> s
    response
    (content-type "application/javascript; charset=utf-8")))

;;; 常量

(def ^:private static-resource-path
  "静态资源路径"
  (zeta/of [:web :server :static-resource-path] "statics"))

(def ^:private default-mime-type
  "默认资源类型"
  (zeta/of [:web :server :default-mime-type] "text/html"))

(def ^:private ext-mime-types
  "扩展名对应的类型和处理函数"
  (->> [:web :server :ext-mime-types]
    zeta/of
    (merge {:json {:accepts #{"application/json"}
                   :handler json-response}
            :html {:accepts #{"text/html"}
                   :handler html-response}
            :css {:accepts #{"text/css"}
                  :handler css-response}
            :js {:accepts #{"application/javascript" "text/javascript"}
                 :handler js-response}})
    (map (fn [[ext {:keys [accepts handler]}]]
           [ext {:accepts (into #{} (mapcat (fn [accept]
                                              [accept
                                               (-> accept
                                                 (cs/split #"/" 2)
                                                 first
                                                 (str "/*"))
                                               "*/*"])
                                            accepts))
                 :handler handler}]))
    (into {})))

;;; namespace

(def ^:private web-ns
  "web项目的名字空间"
  (delay
   (when-let [prefix (zeta/of [:web :server :ns] (zeta/package "web"))]
     (-> prefix
       id
       (cs/replace #"[.\\/]+" ".")
       (cs/replace #"^\.+|\.+$" "")
       (str ".")))))

(defn ns-accept?
  "判断给定的名字空间是否是web的命名空间"
  [ns]
  (and @web-ns (cs/starts-with? (id ns) @web-ns)))

(defn- ns->path
  "名字空间转成Web服务的URL路径"
  [ns]
  (when (ns-accept? ns)
    (->> (-> ns
           id
           (subs (count @web-ns))
           (cs/split #"\.+"))
      (remove cs/blank?)
      (map #(Pattern/quote %))
      (cs/join "/(\\d+)/")
      (str "/"))))

;;; define controller

;; 控制器 格式 {[:method "content-type"] [[#"path-pattern" #(fn)]]}
;; :method 是 :get, :post, :put, :patch 和 :delete 等
;; "content-type" 是字符串形式的mime-type
;; #"path-pattern" 是正则表达式形式的路径
;; #(fn) 是处理函数
(defonce ^:private controllers (atom {}))

(defn spec
  "根据命名空间和RESTful动词，生成对应的URI和请求方法"
  [package rest-or-spec]
  (if (vector? rest-or-spec)
    ;; 已经符合spec
    (let [[method pattern ext] rest-or-spec]
      [method
       (if (string? pattern)
         (re-pattern (Pattern/quote pattern))
         pattern)
       ext])
    ;; 根据空间名和动词动态计算
    (when-let [resources-path (ns->path package)]
      (let [[action ext] (cs/split (id rest-or-spec) #"\." 2)
            suffix (if ext (str "(?:\\." ext ")?") "")
            resources-pattern (re-pattern (str resources-path suffix))
            resource-pattern (re-pattern (str resources-path "/(\\d+)" suffix))]
        (conj (case action
                "index" [:get resources-pattern]
                "show" [:get resource-pattern]
                "create" [:post resources-pattern]
                "update" [:patch resource-pattern]
                "upsert" [:put resource-pattern]
                "delete" [:delete resource-pattern])
              (keyword ext))))))

(defn- strip-tags
  "去除解构对象中的:tag元信息"
  [bindings]
  (walk #(if (symbol? %) (vary-meta % dissoc :tag) %) bindings))

(defn- wrap-tags
  "使用对象的:tag元信息对应的函数包装对象内容"
  [bindings]
  (->> bindings
    flatten-all
    (filter #(and (symbol? %) (symbol? (:tag (meta %)))))
    (mapcat (fn [e] [e (list (:tag (meta e)) e)]))))

(defn request-mapping!
  "将请求映射到控制器"
  [[method pattern ext] f]
  (let [{:keys [accepts handler]} (ext-mime-types (or ext :json))]
    (doseq [content-type accepts]
      (swap! controllers update
             [method content-type]
             (fnil conj [])
             [pattern (comp handler f)]))))

(defmacro defcontroller
  "自定义控制器
  * rest-or-spec
    * rest: (index|show|create|update|upsert|delete)(?:\\.(json|html|js|css))
    * spec: [<method> <pattern> [ext]]
      * method: :(get|post|patch|put|delete)
      * pattern: 正则表达式或字符串形式的正则表达式
      * ext: 可选，资源扩展名，:(json|html|js|css) 等
  * bindings: request对象的解构参数，可通过 ^<fn> 来将字符串参数转换成其他类型
  * body: 方法体"
  [rest-or-spec bindings & body]
  `(when-let [mapping#
              (spec ~*ns* ~(if (symbol? rest-or-spec)
                             `(quote ~rest-or-spec)
                             rest-or-spec))]
     (request-mapping! mapping# (bound-fn ~(strip-tags bindings)
                                  (let [~@(wrap-tags bindings)]
                                    ~@body)))))

;;; routes

(defn- sort-accepts [accepts]
  (let [content-types
        (->> (-> accepts
               (or "")
               (cs/replace #"\s+" "")
               (cs/split #",+"))
          (map #(let [[_ content-type quality]
                      (re-find #"([^;]+)(?:;q=([.\d]+))?" %)]
                  [(if quality (to-double quality) 1) content-type]))
          ArrayList.)]
    (Collections/sort content-types #(compare (first %2) (first %1)))
    (map second content-types)))

;;; response

(defn response-of
  "提前返回结果"
  [response]
  (throw (ex-info nil response)))

(defn static-of
  "返回静态资源"
  [path]
  (response-of
   (-> path
     (resource-response {:root static-resource-path})
     (content-type (or (ext-mime-type path) default-mime-type)))))

(defn redirect-to
  "页面跳转"
  [path]
  (response-of (redirect path)))

(defn- RESTful [{:keys [request-method headers uri] :as request}]
  (if-let [[handler & path-params]
           (some (fn [content-type]
                   (some (fn [[pattern handler]]
                           (when-let [matched (re-matches pattern uri)]
                             (if (vector? matched)
                               (assoc matched 0 handler)
                               [handler])))
                         (@controllers [request-method content-type])))
                 (if-let [content-type (ext-mime-type uri)]
                   [content-type]
                   (sort-accepts (headers "accept"))))]
    (try
      (handler (assoc request :path-params path-params))
      (catch ExceptionInfo e
        (ex-data e)))
    (not-found (str uri " Not Found"))))

(defonce routes
  (-> RESTful
    wrap-headers
    wrap-sessions
    wrap-cookies
    wrap-access-log
    (wrap-json-body {:keywords? true})
    wrap-multipart-params
    wrap-keyword-params
    wrap-nested-params
    wrap-params
    wrap-content-type
    wrap-head
    (wrap-resource static-resource-path)
    wrap-default-charset
    wrap-not-modified))
