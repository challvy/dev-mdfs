# Architecting a Distributed File System with Microservices

基于微服务架构的分布式文件系统项目

## 作业要求 & 框架示例

[不会写代码的架构师不是好程序猿](https://blog.aosabook.cc/)

## 完成情况

### **功能要求**都已实现

- 基于Spring Boot，在Spring Cloud微服务平台上运行一个NameNode实例和多个DataNode实例
- NameNode提供REST风格接口与用户交互，实现用户文件的上传、下载、删除；DataNode不与用户直接交互
- NameNode将用户上传文件文件拆为固定大小的存储块，分散存储在各个DataNode上，每个块保存若干副本，块大小和副本数可通过系统参数配置 

###**非功能要求**没有实现

- DataNode服务可弹性扩展，每次启动一个DataNode服务NameNode可发现并将其纳入整个系统
- NameNode负责检查各DataNode健康状态，需模拟某个DataNode下线时NameNode自动在其他DataNode上复制（迁移）该下线服务原本保存的数据块
- NameNode在管理数据块存储和迁移过程中应实现一定策略尽量保持各DataNode的负载均衡
- 提供一个namenode上的前端页面

## 项目详情

### 设计要点

* 根据课上所学知识，创建Cloud Discovery项目时，将NameNode配置为Eureka Server，将DataNode配置为Eureka Client，多个DataNode的端口通过启动时的参数设置
* NameNode提供Post，Get，Delete接口与用户进行交互，分别对应用户文件的上传、查看和删除
* NameNode中的Controller负责处理用户请求、文件切块、文件信息管理等，相当于一个微型文件管理系统
* DataNode只与NameNode交互，负责文件块的读写删等操作

### 使用说明



## End