(ns zeta.mvn
  "动态加载Maven的jar包"
  (:require [cemerick.pomegranate :refer [add-dependencies]]))

(defmacro dep
  "动态添加一个Maven包
  语法：<groupId>/<artifactId> \"<version>\""
  [group-and-artifact version]
  `(add-dependencies :coordinates '[[~group-and-artifact ~version]]))
