# Architecting a Distributed File System with Microservices

基于微服务架构的分布式文件系统项目

**郑聪尉** [151220169@smail.nju.edu.cn](151220169@smail.nju.edu.cn)

## 作业要求 & 框架示例

[不会写代码的架构师不是好程序猿](https://blog.aosabook.cc/)

## 完成情况

### 功能要求都已实现

- 基于Spring Boot，在Spring Cloud微服务平台上运行一个NameNode实例和多个DataNode实例
- NameNode提供REST风格接口与用户交互，实现用户文件的上传、下载、删除；DataNode不与用户直接交互
- NameNode将用户上传文件文件拆为固定大小的存储块，分散存储在各个DataNode上，每个块保存若干副本，块大小和副本数可通过系统参数配置 

### 非功能要求没有实现

- DataNode服务可弹性扩展，每次启动一个DataNode服务NameNode可发现并将其纳入整个系统
- NameNode负责检查各DataNode健康状态，需模拟某个DataNode下线时NameNode自动在其他DataNode上复制（迁移）该下线服务原本保存的数据块
- NameNode在管理数据块存储和迁移过程中应实现一定策略尽量保持各DataNode的负载均衡
- 提供一个namenode上的前端页面

## 项目详情

### 开发环境

- IntelliJ IDEA 2018.1.5
- Java8
- Maven 4.0.0

### 设计要点

* 根据课上所学知识，创建Cloud Discovery项目时，将NameNode配置为Eureka Server，将DataNode配置为Eureka Client，多个DataNode的端口通过启动时的参数设置
* NameNode提供Post，Get，Delete接口与用户进行交互，分别对应用户文件的上传、查看和删除
* NameNode中的Controller负责处理用户请求、文件切块、文件信息管理等，相当于一个微型文件管理系统
* DataNode只与NameNode交互，负责文件块的读写删等操作，其中实现方法利用到了[storage](https://github.com/spring-guides/gs-uploading-files/tree/master/complete/src/main/java/hello)

### 实操演示

#### 相关配置

* zdfs-namenode只需开启一个进程即可
* zdfs-datanode则需要多个，因此zdfs-datanode中的server.port不要事先配置，通过终端传入即可

#### 运行程序

* zdfs-namenode在IDEA中运行即可

  可设置器端口号为8761

* zdfs-datanode通过终端启动多个进程

  ```
  $ mvn spring-boot:run -D server.port=8762
  $ mvn spring-boot:run -D server.port=8763
  $ mvn spring-boot:run -D server.port=8764
  ```

![zdfs-1](https://github.com/challvy/zdfs/raw/master/README_RES/zdfs-1.png)

#### POST命令

![zdfs-2](https://github.com/challvy/zdfs/raw/master/README_RES/zdfs-2.png)

![zdfs-3](https://github.com/challvy/zdfs/raw/master/README_RES/zdfs-3.png)

#### DELETE命令

![zdfs-4](https://github.com/challvy/zdfs/raw/master/README_RES/zdfs-4.png)

![zdfs-5](https://github.com/challvy/zdfs/raw/master/README_RES/zdfs-5.png)

#### GET命令

当数据上传完成后，可以直接通过浏览器获取文件，在浏览器输入即可获得对应文件名的文件

```
localhost:8761/filename
```

也可以在Postman查看当前已上传的文件，以及对应的文件分块数量

![zdfs-6](https://github.com/challvy/zdfs/raw/master/README_RES/zdfs-2.png)

![zdfs-8](https://github.com/challvy/zdfs/raw/master/README_RES/zdfs-2.png)

#### DataNode状态检测

框架提供好了接口，但失效问题以及数据迁移没有实现

![zdfs-7](https://github.com/challvy/zdfs/raw/master/README_RES/zdfs-2.png)

#### 数据库

用户上传的文件块

## 不足与改进

项目名全称为Zheng Distribute File System，功能性不足开篇也已经提到，当然也有自身设计性不足，如NameNode模块中的ZdfsNamenodeController实在太过杂乱，耦合度太大太大。项目后期会再实现改进的。

# End