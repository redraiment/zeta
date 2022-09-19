(ns zeta.db
  (:require [clojure.string :refer [join split] :as cs]
            [zeta
             [cfg :refer [config?] :as cfg]
             [coll :refer [map->hash]]
             [hot-swapping :as hot]
             [id :refer [id]]
             [lang :refer [defn-record third]]
             [logging :as log]
             [time :refer [timestamp->str
                           datetime->str
                           date->str
                           time->str]]])
  (:import com.zaxxer.hikari.HikariDataSource
           [java.io FileOutputStream PrintStream]
           java.util.regex.Matcher
           java.util.zip.GZIPOutputStream))

;;; 数据库连接

(defonce ^{:private true :doc "数据库连接池"}
  -pools
  (atom {}))

(hot/register
 (fn [{:keys [file]}]
   "数据库连接信息文件更新时清空当前连接池"
   (when (and (config? file)
              (cs/ends-with? file "/db.edn"))
     (log/info "refresh database connection pool by {}" file)
     (let [pools @-pools]
       (reset! -pools {})
       (run! (fn [pool]
               (try
                 (.close pool)
                 (catch Throwable _)))
             pools)))))

(defonce ^{:private true :doc "默认的数据库连接池"}
  -adapters
  {:derby "org.apache.derby.jdbc.ClientDataSource"
   :firebird "org.firebirdsql.pool.FBSimpleDataSource"
   :h2 "org.h2.jdbcx.JdbcDataSource"
   :hsqldb "org.hsqldb.jdbc.JDBCDataSource"
   :db2 "com.ibm.db2.jcc.DB2SimpleDataSource"
   :informix "com.informix.jdbcx.IfxDataSource"
   :sqlserver "com.microsoft.sqlserver.jdbc.SQLServerDataSource"
   :mysql "org.mariadb.jdbc.MySQLDataSource"
   :mariadb "org.mariadb.jdbc.MySQLDataSource"
   :oracle "oracle.jdbc.pool.OracleDataSource"
   :orientdb "com.orientechnologies.orient.jdbc.OrientDataSource"
   :pgjdbc-ng "com.impossibl.postgres.jdbc.PGDataSource"
   :postgresql "org.postgresql.ds.PGSimpleDataSource"
   :sap "com.sap.dbtech.jdbc.DriverSapDB"
   :sqlite "org.sqlite.JDBC"
   :sybase "com.sybase.jdbc4.jdbc.SybDataSource"
   :fdbsql "com.foundationdb.sql.jdbc.ds.FDBSimpleDataSource"})

(defn data-source
  "根据连接信息创建HikariCP数据库连接池"
  [{:keys [adapter
           datasource-classname
           driver-class-name
           url
           host
           port
           name
           username
           password
           pool-name
           auto-commit
           read-only
           connection-init-sql
           connection-test-query
           connection-timeout
           validation-timeout
           idle-timeout
           max-lifetime
           maximum-pool-size
           minimum-idle
           leak-detection-threshold
           register-mbeans
           metric-registry]
    :or {auto-commit true
         read-only false
         connection-timeout 30000
         validation-timeout 5000
         idle-timeout 600000
         max-lifetime 1800000
         minimum-idle 1
         maximum-pool-size 10}
    :as options}]
  (let [ds (HikariDataSource.)]
    (doto ds
      (.setAutoCommit auto-commit)
      (.setReadOnly read-only)
      (.setConnectionTimeout connection-timeout)
      (.setValidationTimeout validation-timeout)
      (.setIdleTimeout idle-timeout)
      (.setMaxLifetime max-lifetime)
      (.setMinimumIdle minimum-idle)
      (.setMaximumPoolSize maximum-pool-size))

    (when-let [classname (get -adapters adapter datasource-classname)]
      (.setDataSourceClassName ds classname)
      (if host (.addDataSourceProperty ds "serverName" host))
      (if port (.addDataSourceProperty ds "portNumber" (str port)))
      (if name (.addDataSourceProperty ds "databaseName" name))
      (if username (.addDataSourceProperty ds "user" username))
      (if password (.addDataSourceProperty ds "password" password)))
    (if url (.setJdbcUrl ds url))
    (if driver-class-name (.setDriverClassName ds driver-class-name))
    (if username (.setUsername ds username))
    (if password (.setPassword ds password))
    (if pool-name (.setPoolName ds pool-name))

    (if connection-init-sql
      (.setConnectionInitSql ds connection-init-sql))
    (if connection-test-query
      (.setConnectionTestQuery ds connection-test-query))
    (if leak-detection-threshold
      (.setLeakDetctionThreshold ds leak-detection-threshold))
    (if register-mbeans
      (.setRegisterMbeans ds register-mbeans))
    (if metric-registry
      (.setMetricRegistry ds metric-registry))

    (doseq [[k v] (dissoc options
                          :adapter :datasource-classname :driver-class-name :url
                          :host :port :name :username :password
                          :pool-name :auto-commit :read-only
                          :connection-init-sql :connection-test-query
                          :connection-timeout :validation-timeout
                          :idle-timeout :max-lifetime :maximum-pool-size :minimum-idle
                          :leak-detection-threshold :register-mbeans :metric-registry)]
      (.addDataSourceProperty ds (id k) (id v)))

    ds))

(defn pooling!
  "根据连接信息创建连接池，并将连接池更新到连接池集合中"
  ([alias]
   (pooling! alias (merge (cfg/of :db :properties)
                          (cfg/of :db :repositories alias))))
  ([alias options-or-datasource]
   (let [options (if (instance? javax.sql.DataSource options-or-datasource)
                   {:pool options-or-datasource}
                   (assoc options-or-datasource
                          :pool (data-source options-or-datasource)))]
     (->> options
       (swap! -pools assoc alias)
       alias
       :pool))))

(defn pool
  "获得别名对应的数据库连接池，如果连接尚未创建则自动创建"
  [alias]
  (or (get-in @-pools [alias :pool])
      (pooling! alias)))

(def ^:private -alias
  "设定为当前数据库的别名"
  (atom nil))

(defn database
  "获取或设置当前数据库"
  ([] @-alias)
  ([alias] (reset! -alias alias)))

(defn choose-database
  "交互式地选择一个数据库"
  []
  (let [aliases (into #{} (concat (keys (cfg/of :db :repositories))
                                  (keys @-pools)))]
    (loop [alias nil]
      (if (aliases alias)
        (database alias)
        (do
          (println "请选择以下数据库")
          (->> aliases
            sort
            (map-indexed (fn [index alias]
                           (format "%3d: %s" (inc index) alias)))
            (run! println))
          (recur (read)))))))

(defn connect
  "获得指定的连接，如果未指定则返回当前连接"
  [alias]
  (let [alias (or alias (database) (choose-database))]
    (try
      (.getConnection (pool alias))
      (catch Throwable e
        (log/error "db connect {} failed" alias e)
        (throw e)))))

;;; 数据库接口封装

(defn sql-stringify
  "Clojure对象序列化（转义）成SQL值，额外支持序列类型的参数（将其转换成为用逗号分隔的）"
  [value]
  (cond
    (nil? value) "null"
    (instance? java.util.Date value)
    (str "'"
         ((cond
            (instance? java.sql.Timestamp value) timestamp->str
            (instance? java.sql.Date value) date->str
            (instance? java.sql.Time value) time->str
            :else datetime->str)
          value)
         "'")
    (instance? String value) (str "'" (cs/replace value #"'" "''") "'")
    (instance? java.math.BigDecimal value) (.toPlainString value)
    (or (seq? value) (vector? value) (set? value)) (join "," (map sql-stringify value))
    :else value))

(defn sql-format
  "扩充SQL占位符：支持 `%` 字符串格式化，但不会消耗 `''` 字符串中个 `%`。
  返回格式化后的SQL和消耗掉的参数。"
  [template & parameters]
  (let [fragments (-> template
                    (cs/replace #"'[^\\']*(?:\\.[^\\']*)*'" "\000$0\000")
                    (split #"\000+"))
        statement (->> fragments
                    (map (fn [fragment]
                           (if (cs/starts-with? fragment "'")
                             "\000"
                             fragment)))
                    (apply str))
        striped-statement (cs/replace statement #"%%" "")
        used-indexes (into #{} (concat (->> striped-statement
                                         (re-seq #"(?<=%)\d+(?=\$)")
                                         (map (fn [index]
                                                (dec (Long/parseLong index)))))
                                       (->> striped-statement
                                         (re-seq #"%\d*[a-zA-Z]")
                                         count
                                         range)))]
    (cons (reduce
           (fn [sql fragment]
             (.replaceFirst sql "\000" (Matcher/quoteReplacement fragment)))
           (apply format statement
                  (map (fn [parameter]
                         (if (coll? parameter)
                           (sql-stringify parameter)
                           parameter))
                       parameters))
           (filter (fn [fragment] (cs/starts-with? fragment "'")) fragments))
          (->> parameters
            (map-indexed vector)
            (remove (comp used-indexes first))
            (map second)))))

(defn fill
  "填充语句中的 `?` 占位符"
  [prepared-statement & parameters]
  (reduce
   (fn [statement [index value]]
     (.setObject statement index value)
     statement)
   prepared-statement
   (map-indexed (fn [i o] [(inc i) o]) parameters)))

(defn prepare
  "在指定的数据库连接上准备语句，可接受格式化参数"
  [connection sql & parameters]
  (apply fill (.prepareStatement connection sql) parameters))

;;; SQL语法扩充

(def extensions
  "语法扩充插件"
  (atom []))

(defmacro def-extension
  "定义语法扩充插件"
  [bindings & body]
  `(swap! extensions conj (bound-fn ~bindings ~@body)))

(defn sql-extension
  "扩充SQL语法"
  [sql]
  (reduce (fn [sql f]
            (f sql))
          sql
          @extensions))

(defn- template-prepare
  "内部通常的SQL模板格式化及预处理函数"
  [connection template parameters]
  (let [[sql & args] (apply sql-format template parameters)]
    (apply prepare connection (sql-extension sql) args)))

;; 插件

(def plugins
  "查询结果修正插件"
  (atom {}))

(defmacro def-plugin
  "注册插件"
  [prefix bindings & body]
  `(swap! plugins assoc ~(name prefix) (bound-fn ~bindings ~@body)))

(def-plugin timestamp [value]
  (if (instance? java.util.Date value)
    (.getTime value)
    value))

(defn- plugins-to [column value]
  (loop [tags (split (name column) #"_")
         wrapped value]
    (if-let [plugin (and (second tags) (@plugins (first tags)))]
      (recur (next tags) (plugin wrapped))
      [(join "_" tags) wrapped])))

;; 查询

(defmacro ^:private defn-prepared [name bindings doc-string? & body]
  `(defn ~name
     ~(if (and (string? doc-string?) (seq body)) doc-string? "")
     [& args#]
     (let [~bindings
           (if (or (nil? (first args#))
                   (keyword? (first args#)))
             args#
             (conj args# nil))]
       ~@(if (and (string? doc-string?) (seq body))
           body
           (cons doc-string? body)))))

(defn- mapcat-records
  "获取所有的查询结果集并逐个调用回调函数"
  [callback statement]
  (if-let [first-rs (.getResultSet statement)]
    (loop [records (doall (callback first-rs))]
      (if-let [rs (and (.getMoreResults statement)
                       (.getResultSet statement))]
        (recur (doall (concat records (callback rs))))
        records))
    (list)))

(defn- execute!
  "统一的内部执行SQL函数，以执行成功的`PreparedStatement`作为参数调用回调函数"
  [callback alias template parameters]
  (with-open [connection (connect alias)
              prepared-statement (template-prepare connection template parameters)]
    (.execute prepared-statement)
    (callback prepared-statement)))

(defn resultset->seq
  "类似resultset-seq，但不返回lazy，并且列名不最小化"
  [^java.sql.ResultSet rs]
  (let [meta (.getMetaData rs)
        columns (->> meta
                  .getColumnCount
                  inc
                  (range 1)
                  (map (fn [index]
                         [(keyword (.getColumnLabel meta index))
                          (.getColumnLabel meta index)])))]
    (loop [records []]
      (if (.next rs)
        (recur (conj records
                     (map->hash (fn [[column name]]
                                  [column (.getObject rs name)])
                                columns)))
        records))))

(defn-prepared all
  [alias template & parameters]
  "执行查询，返回所有结果集的记录，支持多语句查询
  对每一行每一列执行插件处理"
  (map (fn [record]
         (map->hash (fn [[column value]]
                      (let [[column value] (plugins-to column value)]
                        [(keyword column) value]))
                    record))
       (execute! (partial mapcat-records resultset->seq) alias template parameters)))

(defn values
  "执行查询，返回所有第一列的值组成的列表"
  [& args]
  (map (comp second first) (apply all args)))

(defn hashing
  "执行查询，所有第一列的值做为key、第二列的值作为value组成的hash-map"
  [& args]
  (map->hash (fn [record]
               [(second (first record))
                (second (second record))])
             (apply all args)))

(defn one
  "执行查询，返回第一行记录"
  [& args]
  (first (apply all args)))

(defn value
  "执行查询，返回第一行第一列"
  [& args]
  (second (first (apply one args))))

(defn say
  "格式化输出（主要针对时间对象），返回总行数"
  [& args]
  (let [records (apply all args)]
    (run! (fn [record]
            (->> record
              (sort-by (fn [[key]]
                         (let [column (name key)]
                           (if (cs/starts-with? column "highlight_")
                             (subs column 10)
                             column))))
              (map (fn [[column value]]
                     (format "\033[36m%s\033[0m \033[%dm%s\033[0m"
                             (let [key (name column)]
                               (if (cs/starts-with? key "highlight_")
                                 (str "\033[31m\033[43m" (subs key 10))
                                 key))
                             (cond
                               (nil? value) 7
                               (integer? value) 34
                               (number? value) 35
                               (string? value) 4
                               (instance? java.util.Date value) 32
                               :else 39)
                             (if (instance? java.util.Date value)
                               (str value)
                               value))))
              (join ", ")
              println))
          records)
    (count records)))

;; 更新

(defn-prepared execute
  [alias template & parameters]
  "执行更新，返回影响的行数"
  (execute! (memfn getUpdateCount) alias template parameters))

(def ^:dynamic *batch-commit-size*
  "每N次自动提交"
  1000)

(defn- -add-batch [pool statement parameters]
  (apply fill statement parameters)
  (.addBatch statement)
  (when (zero? (mod (inc pool) *batch-commit-size*))
    (.executeBatch statement))
  (inc pool))

(defn-record Batch [statement pool]
  ([{:keys [statement pool]} parameters]
   (send-off pool -add-batch statement parameters))
  java.lang.AutoCloseable
  (close [{:keys [statement pool]}]
    (when statement
      (with-open [connection (.getConnection statement)]
        (await pool)
        (.executeBatch statement)
        (.close statement)))))

(defn-prepared batching [alias template & parameters]
  "创建可自动关闭的批量操作对象
只有一种多线程环境会失败：
```
(with-open [transaction (batching \"insert into users (name) values (?)\")]
  (doseq [name [\"1\" \"2\" \"3\"]]
    (future
      (Thread/sleep 1000)
      (transaction name))))
```
在 with-open 环境中，新开启的线程还未执行 addBatch，但 statement 已经被 close。
因此，多线程环境中，建议确保所有相关线程都结束后再手工 close。"
  (Batch. (template-prepare (connect alias) template parameters) (agent 0)))

(defn-prepared batch [alias sql values]
  "批量操作"
  (with-open [transaction (batching alias sql)]
    (doseq [parameters values]
      (apply transaction parameters))))

;;; 格式转换

(def ^:private -coercions
  "输出导出格式转换函数"
  (letfn [(insert-process [values]
            (->> values
              (group-by second)
              (map (fn [[table records]]
                     (str "insert into " table " ("
                          (join ", " (map third records))
                          ") values ("
                          (join ", " (map (comp sql-stringify last) records))
                          ")")))))
          (matrix-processer [f delimiter]
            (fn [values]
              (->> values
                (map f)
                (join delimiter)
                vector)))]
    {;; insert sql 语句
     :insert {:process insert-process
              :output (fn [out record]
                        (.println out (str record ";")))}
     ;; clojure可执行的 (execute "insert") 语句
     :execute {:process insert-process
               :output (fn [out record]
                         (. out println
                            (format "(execute \"%s\")"
                                    (cs/replace record #"\"" "\\\""))))}
     ;; 逗号分隔的csv文件
     :csv {:header (fn [columns]
                     (join "," (map third columns)))
           :process (matrix-processer (comp str last) ",")
           :output (fn [out record]
                     (.println out record))}
     ;; Excel特定的csv文件
     :excel {:header (fn [columns]
                       (str "=\"" (join "\";=\"" (map third columns)) "\""))
             :process (matrix-processer #(str "=\"" (last %) "\"") ";")
             :output (fn [out record]
                       (.write out (.getBytes (str record "\n") "GBK")))}
     ;; PostgreSQL
     :pgsql {:process (matrix-processer #(str "\"" (cs/replace (str (last %)) #"\"" "\"\"") "\"") ",")
             :output (fn [out record]
                       (.println out record))}}))

(defn- record-iterator [{:keys [preprocess header process output]}
                        by to full?]
  (fn [^java.sql.ResultSet rs]
    (let [meta (.getMetaData rs)
          defines (->> meta
                    .getColumnCount
                    inc
                    (range 1)
                    (remove #(and (not full?) (.isAutoIncrement meta %)))
                    (map #(vector % (.getTableName meta %) (.getColumnLabel meta %))))
          titles (if header (header defines))
          invoke #(process (by (mapv (fn [[index table column :as record]]
                                       (let [[column value]
                                             (plugins-to column (.getObject rs index))]
                                         [index table column value]))
                                     defines)))]
      (if to
        (do
          (if titles
            (output to titles))
          (while (.next rs)
            (run! (partial output to) (invoke))))
        (loop [records (if titles (list titles))]
          (if (.next rs)
            (recur (doall (concat records (invoke))))
            records))))))

(defn-prepared export [alias & [options :as args]]
  (let [{:keys [to as by full? append?] :or {as :insert}} (if (map? options) options)
        output (cond-> to
                 (string? to) (FileOutputStream. (boolean append?))
                 (re-find #"\.gz(?:ip)?$" (or to "")) GZIPOutputStream.
                 (string? to) PrintStream.)
        [template & parameters] (if (map? options) (next args) args)]
    (try
      (execute! (partial mapcat-records
                         (record-iterator (-coercions as)
                                          (if (fn? by) by identity)
                                          output
                                          (if (nil? full?)
                                            (not= as :csv)
                                            (boolean full?))))
                alias template parameters)
      (finally
        (if output
          (.close output))))))

;;; 元信息

(defn- with-meta-data
  "处理元信息的高阶函数"
  [alias getter processor]
  (with-open [connection (connect alias)]
    (->> (.getMetaData connection)
      getter
      resultset->seq
      (map processor))))

(defn-prepared databases
  [alias]
  "罗列数据库"
  (with-meta-data alias (memfn getCatalogs) :TABLE_CAT))

(defn-prepared schemas
  [alias & [catalog schema]]
  "罗列数据库模式"
  (with-meta-data alias
    #(.getSchemas % catalog schema)
    (fn [{:keys [table_schem table_catalog]}]
      {:catalog table_catalog
       :schema table_schem})))

(defn-prepared tables
  [alias & [catalog schema table & types]]
  "罗列数据库表"
  (with-meta-data alias
    #(.getTables % catalog schema table (into-array String (or types ["TABLE" "VIEW"])))
    identity))

(defn-prepared columns
  [alias & [catalog schema table column]]
  "罗列数据库表字段"
  (with-meta-data alias
    #(.getColumns % catalog schema table column)
    (fn [{:keys [BUFFER_LENGTH CHAR_OCTET_LENGTH COLUMN_DEF COLUMN_NAME COLUMN_SIZE DATA_TYPE DECIMAL_DIGITS IS_AUTOINCREMENT IS_NULLABLE NULLABLE NUM_PREC_RADIX ORDINAL_POSITION REMARKS SCOPE_CATLOG SCOPE_SCHEMA SCOPE_TABLE SOURCE_DATA_TYPE SQL_DATA_TYPE SQL_DATETIME_SUB TABLE_CAT TABLE_NAME TABLE_SCHEM TYPE_NAME]}]
      {:catalog TABLE_CAT
       :schema TABLE_SCHEM
       :table TABLE_NAME
       :name COLUMN_NAME
       :comment REMARKS
       :type_name TYPE_NAME
       :data_type DATA_TYPE
       :size COLUMN_SIZE
       :default COLUMN_DEF
       :nullable? (= IS_NULLABLE "YES")
       :autoincrment? (= IS_AUTOINCREMENT "YES")
       :decimal_digits DECIMAL_DIGITS
       :ordinal_position ORDINAL_POSITION
       :num_prec_radix NUM_PREC_RADIX})))

;;; 快照

;; (defn-prepared save
;;   [alias table values & {:keys [force?] :or {force? false}}]
;;   "自适应字段的upsert
;; 为兼容所有数据库，使用try-catch，在插入失败时执行更新"
;;   )
