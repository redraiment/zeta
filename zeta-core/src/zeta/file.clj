(ns zeta.file
  "文件系统相关函数
  1. 文件属性判断
  2. 文件内容操作
  3. 文件夹操作
  4. 文件路径操作
  5. 文件系统变化监控"
  (:refer-clojure :exclude [find])
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as cs]
            [zeta
             [coll :refer [wrap-vector]]
             [schedule :refer [at-exit]]])
  (:import [java.io File PushbackReader]
           [java.nio.file FileSystems Files Path Paths
            FileVisitor FileVisitOption FileVisitResult
            LinkOption CopyOption StandardCopyOption
            StandardWatchEventKinds]
           java.nio.file.attribute.FileAttribute
           java.util.LinkedList
           [jnr.posix POSIXFactory POSIXHandler]))

;;; default options

(def ^:private link-options
  "文件链接选项：为空默认处理文件链接"
  (make-array LinkOption 0))

(def ^:private file-attributes
  "文件属性"
  (make-array java.nio.file.attribute.FileAttribute 0))

(def ^:private file-visit-options
  "文件访问选项：为空默认不处理链接"
  (make-array FileVisitOption 0))

(def ^:private copy-options
  "文件复制选项"
  (into-array CopyOption
              [StandardCopyOption/COPY_ATTRIBUTES
               StandardCopyOption/REPLACE_EXISTING]))

(def ^:private move-options
  "文件移动选项"
  (into-array CopyOption
              [StandardCopyOption/ATOMIC_MOVE
               StandardCopyOption/REPLACE_EXISTING]))

(def ^:private watch-events
  "监视事件"
  (into-array java.nio.file.WatchEvent$Kind
              [StandardWatchEventKinds/ENTRY_CREATE
               StandardWatchEventKinds/ENTRY_MODIFY
               StandardWatchEventKinds/ENTRY_DELETE]))

;;; cd命令

(def ^:private posix-handler
  (POSIXFactory/getPOSIX
   (proxy [POSIXHandler] []
     (error [error extra]
       (throw (RuntimeException. (str "posix handler error " error extra))))
     (unimplementedError [method-name]
       (throw (UnsupportedOperationException. (str "posix handler unimplement error " method-name))))
     (warn [warn-id message & data]
       (.println System/err  (str "posix hander warning " warn-id message data)))

     (isVerbose []
       false)
     (getCurrentWorkingDirectory []
       (System/getProperty "user.dir"))
     (getEnv []
       (map str (System/getenv)))
     (getInputStream []
       System/in)
     (getOutputStream []
       System/out)
     (getErrorStream []
       System/err)
     (getPID []
       (rand-int 65536)))
   true))

(defn cd
  "改变当前工作目录"
  [path]
  (.chdir posix-handler path)
  (System/setProperty "user.dir" path))

;;; 文件操作

(defonce ^{:doc "目录分隔符"} separator
  File/separator)

(defonce ^{:doc "路径分隔符"} path-separator
  File/pathSeparator)

(defn join
  "连接路径"
  [& paths]
  (if paths
    (cs/join separator (remove cs/blank? paths))
    ""))

(defn path
  "创建java.nio.file.Path"
  [first & more]
  (if (instance? Path first)
    first
    (Paths/get first (into-array String more))))

(defn file
  "创建java.io.File"
  [first & more]
  (.toFile (apply path first more)))

(defn to-file
  "转成java.io.File"
  [o]
  (cond
    (instance? File o) o
    (string? o) (File. o)
    (instance? Path o) (.toFile o)))

(defn absolute-path
  "转成绝对路径"
  [path]
  (.getCanonicalPath (to-file path)))

(defn relative-path
  "转成相对路径"
  ([target]
   (relative-path "" target))
  ([source target]
   (let [absolute-source (path (absolute-path source))
         absolute-target (path (absolute-path target))
         relative (str (.relativize absolute-source absolute-target))]
     (if (cs/blank? relative)
       ""
       relative))))

(defmacro ^:private defile
  "定义文件相关操作"
  [name comment & body]
  `(defn ~name ~comment [~'first & ~'more]
     (let [~'p (apply path ~'first ~'more)]
       ~@body)))

(defile exists?
  "文件存在"
  (Files/exists p link-options))

(defile file?
  "是文件"
  (Files/isRegularFile p link-options))

(defile directory?
  "是目录"
  (Files/isDirectory p link-options))

(defile link?
  "是链接"
  (Files/isSymbolicLink p))

(defile hidden?
  "是隐藏文件"
  (Files/isHidden p))

(defile readable?
  "可读"
  (Files/isReadable p))

(defile writable?
  "可写"
  (Files/isWritable p))

(defile executable?
  "可执行"
  (Files/isExecutable p))

(defn is?
  "判断文件的后缀名"
  [ext-or-exts path]
  (->> ext-or-exts
    wrap-vector
    (map (partial str "."))
    (some (partial cs/ends-with? path))))

(defn mode
  "设置文件模式并返回"
  [file-name & {:keys [readable?
                       writable?
                       executable?
                       owner-only?]}]
  (let [file (to-file file-name)]
    (when-not (nil? readable?)
      (.setReadable file readable? (boolean owner-only?)))
    (when-not (nil? writable?)
      (.setWritable file writable? (if (nil? owner-only?) true owner-only?)))
    (when-not (nil? executable?)
      (.setExecutable file executable? (if (nil? owner-only?) true owner-only?)))
    {:readable? (.canRead file)
     :writable? (.canWrite file)
     :executable? (.canExecute file)}))

(defile touch
  "创建文件"
  (Files/createFile p file-attributes))

(defn link
  "创建链接"
  [source target]
  (Files/createSymbolicLink (path target) (path source) file-attributes))

(defile mkdir
  "创建目录"
  (Files/createDirectories p file-attributes))

(defn glob?
  "是否包含glob通配符
  若包含了符号?、*、[、]、{、}中的任意一个，则返回true；否则返回false"
  [^String s]
  (boolean (some #{\? \* \[ \] \{ \}} s)))

(defn glob
  "将通配符展开成匹配的文件路径列表"
  [^String s & {:keys [follow-link? show-hide? depth]
                :or {follow-link? true
                     depth Integer/MAX_VALUE}}]
  (if (glob? s)
    ;; 包含通配符
    (let [[starts globs]
          (split-with (complement glob?)
                      (cs/split (or s "") (re-pattern separator)))

          prefix (if (= starts (list "")) ; root path
                   separator
                   (apply join starts))
          root (-> prefix path .toAbsolutePath)
          ;; 是否需要忽略隐藏文件/文件夹
          ignore-hidden? #(and (not show-hide?)
                               (not= root %)
                               (hidden? %))
          pattern (->> globs
                    (cs/join separator)
                    (str "glob:")
                    (getPathMatcher)
                    (.. FileSystems getDefault))
          collector (LinkedList.)
          collect-matches #(let [suffix (->> %
                                          .toAbsolutePath
                                          (.relativize root))]
                             (when (.matches pattern suffix)
                               (.add collector (join prefix (str suffix)))))]
      (Files/walkFileTree
       root
       (if follow-link?
         #{FileVisitOption/FOLLOW_LINKS}
         #{})
       depth
       (reify FileVisitor
         (preVisitDirectory [this dir attrs]
           (if (ignore-hidden? dir)
             FileVisitResult/SKIP_SUBTREE
             (do
               (collect-matches dir)
               FileVisitResult/CONTINUE)))
         (visitFile [this file attrs]
           (when-not (ignore-hidden? file)
             (collect-matches file))
           FileVisitResult/CONTINUE)
         (visitFileFailed [this file e]
           FileVisitResult/CONTINUE)
         (postVisitDirectory [this dir e]
           FileVisitResult/CONTINUE)))
      (into [] collector))
    ;; 不包含通配符
    (if (exists? s)
      [s])))

#_(defn ls
  "返回给定目录下的文件列表
  如果给定的参数是String，则作为glob处理；
  如果给定的参数是Pattern，则作为regex处理。"
  [& starts]
  (->> ["."]
    (or starts)
    (map relative-path)
    (map (fn [start]
           (if (cs/ends-with? start "/**")
             [(if (= start "/**") "/" (subs start 0 (- (count start) 3))) true]
             [start false])))
    (mapcat (fn [[start recursive?]]
              (cond
                (directory? start) (->> (if recursive?
                                          (Files/walk (path start) file-visit-options)
                                          (Files/list (path start)))
                                     .iterator
                                     iterator-seq
                                     (map str))
                (file? start) [start]
                :else [])))))

(defn find
  "在ls的基础上再加上过滤条件"
  [condition & starts]
  (filter condition (apply ls starts)))

(defn- path-merge
  [f dirs]
  (if (>= (count dirs) 2)
    (let [paths (map path dirs)
          target (last paths)]
      (cond
        (== (count dirs) 2) (f (first paths) target)
        (directory? target) (doseq [source (butlast paths)]
                              (f source target))
        :else (throw (IllegalArgumentException. "copy target must be a directory"))))
    (throw (IllegalArgumentException. "source... target"))))

(defn mv
  "移动文件"
  [& paths]
  (path-merge (fn [source target]
                (Files/move source target move-options))
              paths))

(defn cp
  "复制文件"
  [& paths]
  (path-merge (fn [source target]
                (Files/copy source target copy-options))
              paths))

(defn rm
  "删除文件"
  [& paths]
  (->> paths
    (apply ls)
    reverse
    (map path)
    (run! #(Files/delete %))))

;;; 文件内容

(defn reader
  "增强clojure.java.io/reader
  可读取 resource:// 开头的资源文件"
  [source]
  (if (and (string? source) (cs/starts-with? source "resource://"))
    (if-let [resource (io/resource (subs source 11))]
      (io/reader resource))
    (io/reader source)))

(defn lines
  "返回行"
  [source]
  (line-seq (reader source)))

(defn contents
  "返回内容"
  [source]
  (slurp (reader source)))

(defn edn-of
  "从reader中读取edn数据"
  [source]
  (when-let [input (reader source)]
    (edn/read (PushbackReader. input))))

;;; 文件监视

(defonce ^{:private true :doc "监视服务器"}
  watch-server
  (.newWatchService (FileSystems/getDefault)))

(defn watch
  "监视路径"
  [& paths]
  (let [dirs (->> paths
               (into #{})
               (filter directory?)
               (map absolute-path))]
    (->> dirs
      (map #(str % "/**"))
      (apply ls)
      (concat paths)
      (filter directory?)
      (into #{})
      (map path)
      (run! #(.register % watch-server watch-events)))))

(defonce ^{:private true :doc "文件变更处理器"}
  watch-handlers
  (atom []))

(defn watch-handler-register
  "注册处理器"
  [f]
  (swap! watch-handlers conj f))

(defn watch-accept
  "接收到文件变化时广播事件"
  []
  (while true
    (let [key (.take watch-server)]
      (doseq [event (.pollEvents key)
              :let [{:keys [action file] :as e}
                    {:action ({StandardWatchEventKinds/ENTRY_CREATE :create
                               StandardWatchEventKinds/ENTRY_DELETE :delete
                               StandardWatchEventKinds/ENTRY_MODIFY :modify}
                              (.kind event))
                     :file (absolute-path (.resolve (.watchable key)
                                                    (.context event)))
                     :count (.count event)}]
              :when (not (or (nil? action)
                             (and (= action :modify)
                                  (directory? file))))]
        (doseq [f @watch-handlers]
          (f e))
        (when (and (= action :create) (directory? file))
          (watch file)))
      (when-not (.reset key)
        (.cancel key)))))
