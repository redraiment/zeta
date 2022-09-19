Zeta
====

Zeta是基于Clojure为个人打造的工作站（Workstation），希望能All in在Zeta中完成实验、预研、开发等日常工作，并通过开放接口、uberjar包等多种形式将成果分享出去。

*注*：对Windows环境支持可能不是特别好。

## 工程结构

* zeta：核心库
* zeta-lib-*：其他类库
* zeta-app-*：应用入口

## 功能列表

Clojure化的日常开发工具。包括：

* cfg, option：统一的配置项入口，包括命令行参数解析、edn配置文件，应用配置
* hot_swapping：加载配置文件和代码
* logging：日志
* coercion：类型转换工具
* coll：集合处理工具
* file：文件系统处理工具
* shell：外部进程交互工具
* json：json读写工具
* mvn：从Maven仓库动态加载类库到当前进程，包含依赖项
* net：网络工具
* time：日期、时间工具
* str：字符串工具
* lang：语言扩展工具
* id：符号、关键字等工具
* scheduler：任务调度工具
* repl：交互式开发
* http客户端
* http服务端
* jdbc数据库：支持所有JDBC驱动的数据库。
* *text*：文本处理工具，例如词法解析、语法解析等。TODO
* *ml*：机器学习库，包括线性回归、逻辑回归、K均值聚类等常见机器学习算法
* *chart*：图表库，生成数据图标

## 名字空间

* zeta：核心库
* zeta.service：后台服务

## 目录结构

以下所有目录或文件都在监控范围内，即发生变化是

*初始文件*

* `~/.zeta.clj`：初始文件
* `~/.config/zeta/init.clj`：初始文件

*类库目录*

* `~/.m2/repository/`：重用Maven的类库目录
* `~/.config/zeta/<site-name>`：插件目录，放置插件源码和配置文件

*工作目录*

* `.`：默认当前目录是工作目录

## 配置方法

## TODO

用命令`zeta`启动。可指定以下参数：

* `--init-file`
* `--no-init-file`：
* `--no-site-path`：不加载`~/.config/zeta/`目录下的代码。
