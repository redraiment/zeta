(ns zeta.net
  "网络相关工具函数"
  (:require [zeta.lang :refer [defmemoize]])
  (:import java.net.NetworkInterface)
  (:gen-class))

(defmemoize ips
  "返回本机所有IP地址"
  []
  (mapcat (fn [interface]
            (map (memfn getHostAddress)
                 (enumeration-seq (.getInetAddresses interface))))
          (enumeration-seq (NetworkInterface/getNetworkInterfaces))))

(defmemoize ipv4
  "返回所有IPv4地址"
  []
  (filter (partial re-matches #"\d+(?:\.\d+){3}") (ips)))

(defmemoize remote-ipv4
  "返回所有非本地IPv4地址"
  []
  (remove (partial = "127.0.0.1") (ipv4)))
