<h1 align="center">Chat2DB</h1>

<div align="center">

智能且多功能的 SQL 客户端和报表工具，适用于各种数据库

[![License](https://img.shields.io/github/license/alibaba/fastjson2?color=4D7A97&logo=apache)](https://www.apache.org/licenses/LICENSE-2.0.html)
[![Java support](https://img.shields.io/badge/Java-17+-green?logo=java&logoColor=white)](https://openjdk.java.net/)
[![GitHub release](https://img.shields.io/github/release/chat2db/Chat2DB)](https://github.com/chat2db/Chat2DB/releases)
[![GitHub Stars](https://img.shields.io/github/stars/chat2db/Chat2DB)](https://github.com/chat2db/Chat2DB/stargazers)
[![GitHub Forks](https://img.shields.io/github/forks/chat2db/Chat2DB)](https://github.com/chat2db/Chat2DB/fork)
[![GitHub Contributors](https://img.shields.io/github/contributors/chat2db/Chat2DB)](https://github.com/chat2db/Chat2DB/graphs/contributors)

</div>

<div align="center">

  Languages： 中文 [English](README.md)

  官网：[Chat2DB](https://sqlgpt.cn/zh)

  <div style="display: flex; align-items: center;">
    如果觉得 Chat2DB 对您有帮助的话，请帮忙<a style="display: flex; align-items: center;margin:0px 6px" target="_blank" href='https://github.com/chat2db/Chat2db'><img src="https://img.shields.io/github/stars/chat2db/Chat2DB.svg?style=flat-square&label=Stars&logo=github" alt="github star"/></a>
    的右上角点个⭐ Star 和 Fork，您的支持是 Chat2DB 变得更好最大的动力
  </div>
</div>



## 案例视频

https://github.com/chat2db/Chat2DB/assets/22975773/b58db908-5768-4a71-aa30-135d202e505f

## 📖 简介

&emsp; &emsp;Chat2DB 是一款有开源免费的多数据库客户端工具，支持 windows、mac 本地安装，也支持服务器端部署，web 网页访问。和传统的数据库客户端软件 Navicat、DBeaver 相比 Chat2DB 集成了 AIGC 的能力，能够将自然语言转换为 SQL，也可以将 SQL 转换为自然语言，可以给出研发人员 SQL 的优化建议，极大的提升人员的效率，是 AI 时代数据库研发人员的利器，未来即使不懂 SQL 的运营业务也可以使用快速查询业务数据、生成报表能力。

## ✨ 特性

- 🌈 AI 智能助手，支持自然语言转 SQL、SQL 转自然语言、SQL 优化建议
- 🔥 智能报表，利用AIGC能力，一句话生成报表。
- 👭 支持个人模式、支持团队协作模式，让研发协同效率更高。
- 🔌 除支持目前主流数据库外，还支持国产数据库如：达梦、Oceanbase、人大金仓。
- ⚙️ 强大的数据管理能力，支持数据表、视图、存储过程、函数、触发器、索引、序列、用户、角色、授权等管理
- 🛡 前端使用 Electron 开发，提供 Windows、Mac、Linux 客户端、网页版本一体化的解决方案
- 🎁 支持环境隔离、线上、日常数据权限分离

## 产品展示

<a><video src="https://chat2db.oss-accelerate.aliyuncs.com/demo/demo2.0.mp4"/></a>

## ⏬ 下载安装


[GitHub下载安装包](https://github.com/chat2db/Chat2DB/releases) 

[官网下载安装包](https://sqlgpt.cn) 

## 🚀 支持的数据库

Chat2DB 支持的数据库连接有:
- MySQL
- PostgreSQL
- H2
- Oracle
- SQLServer
- SQLite
- MariaDB
- ClickHouseare
- DM
- Presto
- DB2
- OceanBase
- Hive
- KingBase

Redis和MongoDB得到部分支持，Hbase、Elasticsearch、openGauss、TiDB、InfluxDB将在未来得到支持。


## 🌰 使用 Demo

### 创建数据源

<a><img src="https://alidocs.oss-cn-zhangjiakou.aliyuncs.com/res/KM7qezzyAVX2Opj8/img/1d7f2d05-9c3b-4308-a693-39aed44a4b39.png?x-oss-process=image/resize,w_1280,m_lfit,limit_1"/></a>

### 数据源管理

<a><img src="https://alidocs.oss-cn-zhangjiakou.aliyuncs.com/res/KM7qezzyAVX2Opj8/img/d5634953-9fe5-4a03-8024-3aa4774b2955.png?x-oss-process=image/resize,w_1280,m_lfit,limit_1"/></a>

### SQL 控制台

<a><img width="1720" alt="2" src="https://github.com/chat2db/Chat2DB/assets/22975773/5d0332ca-8a65-4ed9-95fb-b12fae9209f3"></a>

### AI 智能助手

![image](https://github.com/chat2db/Chat2DB/assets/22975773/2dfc4aaa-c5a3-42c3-bc61-28ebc237a27b)

## 🔥 AI

### 使用Chat2DB AI 上手即用

### 使用自定义大模型
- 使用自定义大模型，接口格式需要和open ai的接口格式保持一致

## 📦 Docker 部署

```bash
  ## 拉取最新客户端,然后运行docker,名字是 `chat2db` , 并且将 `/root/.chat2db` 挂载到 `~/.chat2db-docker`
  docker run --name=chat2db -ti -p 10824:10824 -v ~/.chat2db-docker:/root/.chat2db  chat2db/chat2db:latest
  ## 这里正常会提示`Tomcat started on port(s): 10824 (http) with context path` 就可以结束了

  ## 如果这里提示  `The container name "/chat2db" is already in use by container`, 代表已经存在容器了 运行
  docker start chat2db
  ## 如果想更新chat2db 则需要先rm
  docker rm chat2db
```

## 🎯 运行环境

注意：
如果需要本地调试

- java 运行 <a href="https://adoptopenjdk.net/" target="_blank">Open JDK 17</a>
- Node 运行环境 Node16 <a href="https://nodejs.org/" target="_blank">Node.js</a>.

## 💻 本地调试

- git clone 到本地

```bash
$ git clone git@github.com:chat2db/Chat2DB.git
```

- 前端调试

```bash
# node版本必须为16及以上 
# 一定要用yarn
$ cd Chat2DB/chat2db-client
$ yarn
$ yarn run start:web
```

- 后端调试

```bash
$ cd ../chat2db-server
$ mvn clean install # 需要安装maven 3.8以上版本
$ cd chat2db-server/chat2db-server-start/target/
$ java -jar -Dloader.path=./lib -Dchatgpt.apiKey=xxxxx chat2db-server-start.jar  # 需要安装java 17以上版本，启动应用 chatgpt.apiKey 需要输入ChatGPT的key,如果不输入无法使用AIGC功能
```

- 如果你需要独立部署 
```bash
# chat2db-client
$ npm run build:web:prod 
$ cp -r dist ../chat2db-server/chat2db-server-start/src/main/resources/static/front 
$ cp -r dist/index.html ../chat2db-server/chat2db-server-start/src/main/resources/thymeleaf

# 再打包后端服务
```

## 📑 文档

- <a href="https://doc.sqlgpt.cn/zh/">官方文档</a>
- <a href="https://github.com/chat2db/Chat2DB/issues">Issue</a>

## 常见问题

### 1、无法获取数据源驱动:getJDBCDriver error: null

问题原因：无法联网导致下载数据库驱动包失败。

解决办法：手动下载相关驱动放入到 ~/.chat2db/jdbc-lib 目录下

下载链接 参考：<a href="https://github.com/chat2db/Chat2DB/blob/main/chat2db-server/chat2db-server-start/src/main/resources/application.yml">Application jdbc-jar-downLoad-urls</a>

- https://oss.sqlgpt.cn/lib/mysql-connector-java-8.0.30.jar
- https://oss.sqlgpt.cn/lib/mysql-connector-java-5.1.47.jar
- https://oss.sqlgpt.cn/lib/clickhouse-jdbc-0.3.2-patch8-http.jar
- https://oss.sqlgpt.cn/lib/mariadb-java-client-3.0.8.jar
- https://oss.sqlgpt.cn/lib/mssql-jdbc-11.2.1.jre17.jar
- https://oss.sqlgpt.cn/lib/oceanbase-client-2.4.2.jar
- https://oss.sqlgpt.cn/lib/postgresql-42.5.1.jar
- https://oss.sqlgpt.cn/lib/sqlite-jdbc-3.39.3.0.jar
- https://oss.sqlgpt.cn/lib/ojdbc11.jar

## Stargazers

[![Stargazers repo roster for @chat2db/Chat2DB](https://reporoster.com/stars/chat2db/Chat2DB)](https://github.com/chat2db/Chat2DB/stargazers)

## Forkers

[![Forkers repo roster for @chat2db/Chat2DB](https://reporoster.com/forks/chat2db/Chat2DB)](https://github.com/chat2db/Chat2DB/network/members)

## ☎️ 联系我们

加群前请先 Star 和 Fork，谢谢~关注微信公众号可加入微信、钉钉、QQ 群一起讨论，并可以获取 Chat2DB 最新动态和更新。

<a><img src="https://github.com/chat2db/Chat2DB/assets/22975773/e4239d29-1426-4361-bf57-f1b0b67d1281" width="30%"/></a>

## ❤️ 致谢

感谢所有为 Chat2DB 贡献力量的同学们~

<a href="https://github.com/chat2db/Chat2DB/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=chat2db/Chat2DB" />
</a>

## Star History

<a href="https://star-history.com/#chat2db/chat2db&Date">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=chat2db/chat2db&type=Date&theme=dark" />
    <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=chat2db/chat2db&type=Date" />
    <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=chat2db/chat2db&type=Date" />
  </picture>
</a>
