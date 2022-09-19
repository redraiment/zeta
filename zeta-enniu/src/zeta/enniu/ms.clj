(ns zeta.enniu.ms
  "微服务"
  (:refer-clojure :exclude [get])
  (:require [zeta.http :as http]
            [zeta.enniu.consul :as consul])
  (:import java.net.URI))

(defn request
  "通用的http请求
  包装http-kit，添加读写json以及重试"
  [& parameters]
  (let [[pairs [[template] :as urls]]
        (split-with (comp keyword? first)
                    (partition-all 2 parameters))

        url (->> urls
              flatten
              next
              (apply format template))
        {:keys [scheme authority path query fragment]}
        (-> url URI/create bean)

        service (re-find #"^[^:@]+" authority)
        tag (re-find #"(?<=:)[^:@]+" authority)
        dc (re-find #"(?<=@)[^:@]+" authority)

        url (if (= scheme "consul")
              (str "http://"
                   (rand-nth (consul/services service
                                              (or tag consul/*tag*)
                                              (or dc @consul/data-center)))
                   path
                   (if query (str "?" query))
                   (if fragment (str "#" fragment)))
              url)]
    (apply http/request (flatten [pairs url]))))

(def get (partial request :method :get))
(def head (partial request :method :head))
(def options (partial request :method :options))
(def post (partial request :method :post))
(def put (partial request :method :put))
(def patch (partial request :method :patch))
(def delete (partial request :method :delete))
