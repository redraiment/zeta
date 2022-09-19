(ns zeta.enniu.consul
  "Consul Agent"
  (:require [clojure.string :as cs]
            [zeta
             [http :as http]
             [opt :as zeta]]))

(def ^:dynamic *tag*
  "默认的consul tag"
  (zeta/property "service.tag" "k8sdev"))

(def url
  "本地consul agent地址"
  (format "http://%s:8500/v1" (zeta/property "consul.agent.ip" "localhost")))

(defn- consul-agent
  "Consul agent api"
  [suffix]
  (http/get (str url suffix)))

(def data-center
  "当前数据中心"
  (delay (get-in (consul-agent "/agent/self") [:Config :Datacenter])))

(def data-centers
  "数据中心列表"
  (delay (consul-agent "/catalog/datacenters")))

(defn services
  "可用服务列表"
  ([name] (services name *tag*))
  ([name tag] (services name tag @data-center))
  ([name tag dc]
   (->> @data-centers
     (remove (partial = dc))
     (cons dc)
     (remove cs/blank?)
     (map #(format "/health/service/%s?passing=true&dc=%s&tag=%s" name % tag))
     (map consul-agent)
     (map (fn [nodes]
            (map (fn [{{node-address :Address} :Node
                       {service-address :Address port :Port} :Service}]
                   (str (if (cs/blank? service-address)
                          node-address
                          service-address)
                        ":" port))
                 nodes)))
     (remove empty?)
     first)))

(defn kv
  "KeyValue配置"
  [key]
  (consul-agent (str "/kv" key "?raw")))
