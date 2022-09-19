(ns user
  (:require [clojure.string :refer [blank? join split] :as cs]
            [clojure.java.io :as io]
            [zeta
             [coercion :refer :all]
             [coll :refer :all]
             [id :refer :all]
             [lang :refer :all]
             [schedule :refer :all]

             [cfg :as cfg]
             [file :as file]
             [json :as json]
             [logging :as log]
             [mvn :as mvn]
             [net :as net]
             [opt :as zeta]
             [shell :as sh]
             [str :as zs]
             [time :as time]
             
             #_[db :as db :refer [database execute
                                  say all one value
                                  values hashing
                                  batch batching export
                                  databases tables columns]]
             #_[http :as http]]))
