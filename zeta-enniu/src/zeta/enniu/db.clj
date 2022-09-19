(ns zeta.enniu.db
  (:require [clojure.string :as cs]
            [zeta
             [cfg :as cfg]
             [db :as db]]
            [zeta.enniu.consul :as consul])
  (:import java.util.Properties
           [com.zaxxer.hikari HikariConfig HikariDataSource]))

(defn data-source
  ([key]
   (data-source key consul/*tag*))
  ([key tag]
   (->> (-> "/middleware-config/mysql/%s/%s/config"
          (format tag key)
          consul/kv
          (cs/split #"\r?\n"))
     (reduce (fn [properties line]
               (let [[key value] (cs/split line #"\s*:\s*" 2)]
                 (.put properties key value)
                 properties))
             (Properties.))
     HikariConfig.
     HikariDataSource.)))

(defn pooling!
  "连接恩牛数据库"
  ([alias]
   (let [{:keys [key tag]} (cfg/of :ds alias)]
     (pooling! alias key tag)))
  ([alias key]
   (pooling! alias key consul/*tag*))
  ([alias key tag]
   (db/pooling! alias (data-source key tag))))
