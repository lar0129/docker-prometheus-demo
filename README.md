211250127 梁安然



开发一个 Spring Boot 应用，并使用云原生功能

* springboot项目地址：https://github.com/lar0129/prometheus-test-demo

## **1. 功能要求**

​        

1. 实现一个 REST 接口（简单接口即可，比如 json 串 {"msg":"hello"}）

* 目前提供/ping接口与限流功能
* 放到github上方便进行持续集成/持续部署

![image-20230729200322667](https://lar-blog.oss-cn-nanjing.aliyuncs.com/picGo_img/AppData/Roaming/Typora/typora-user-images/image-20230729200322667.png)

​          

2. 接口提供限流功能，当请求达到每秒 100 次的时候，返回 429（Too many requests）

* 代码：
  * 
* 使用Jmeter进行并发测试，结果显示限流成功

![image-20230729200404047](https://lar-blog.oss-cn-nanjing.aliyuncs.com/picGo_img/AppData/Roaming/Typora/typora-user-images/image-20230729200404047.png)

![image-20230729200343091](https://lar-blog.oss-cn-nanjing.aliyuncs.com/picGo_img/AppData/Roaming/Typora/typora-user-images/image-20230729200343091.png)

![image-20230729200307883](https://lar-blog.oss-cn-nanjing.aliyuncs.com/picGo_img/AppData/Roaming/Typora/typora-user-images/image-20230729200307883.png)



3. **加分项**：当后端服务有多个实例的时候（一个 Service 包含若干个 Pod），如何实现**统一限流**

 * 仿照第二次作业，使用Eureka转发实现统一限流？



## **2. DevOps 要求**

1. 为该项目准备 Dockerfile，用于构建镜像

* Dockerfile见项目根目录Dockerfile



2. 为该项目准备 Kubernetes 编排文件，用于在 Kubernetes 集群上创建 Deployment 和 Service

* k8s部署内容见项目中 ./jenkins/scripts/demo.yaml 文件



3. 编写 Jenkins 持续集成流水线，实现**代码构建**/**单元测试**/**镜像构建**功能（需要写至少一个单元测试）

4. 编写 Jenkins 持续部署流水线，实现部署到 Kubernetes 集群的功能，该流水线的触发条件为持续集成流水线执行成功

5. 注意：持续集成流水线和持续部署流水线也可以合二为一。

3/4/5：Jenkinsfile见项目中 ./jenkins/Jenkinsfile文件

* 具体过程：

  * 1.将jenkins连接到k8s-nju13-namespace中

    * 具体过程仿照https://doc.weixin.qq.com/doc/w3_m_YeHOnMvfCxXW?scode=ABoAuwfGAAkIB91cNPAK4AcQYdAN0

    * 图片处需要修改命令

      ![image-20230730182453951](https://lar-blog.oss-cn-nanjing.aliyuncs.com/picGo_img/AppData/Roaming/Typora/typora-user-images/image-20230730182453951.png)

  * 2.部署流水线
    * Jenkinsfile：
    * ![image-20230729201653341](https://lar-blog.oss-cn-nanjing.aliyuncs.com/picGo_img/AppData/Roaming/Typora/typora-user-images/image-20230729201653341.png)
    * ![image-20230730224910852](https://lar-blog.oss-cn-nanjing.aliyuncs.com/picGo_img/AppData/Roaming/Typora/typora-user-images/image-20230730224910852.png)
  
  * 3. 访问端口验证k8s部署是否成功
  
  * 集群网址172.29.4.18，Nodeport配置为30333，故访问172.29.4.18:30333/ping，验证成功
  
    * ![image-20230731185135102](https://lar-blog.oss-cn-nanjing.aliyuncs.com/picGo_img/AppData/Roaming/Typora/typora-user-images/image-20230731185135102.png)

6. 代码提交到仓库自动触发流水线（bonus 5分）

* 使用github webhook / jenkins触发器（待做）

## **3. 扩容场景**

1. 为该 Java 项目提供 Prometheus metrics 接口，可以供 Prometheus 采集监控指标

2. 在 Grafana 中的定制应用的监控大屏（CPU/内存/JVM）

3. 使用压测工具（例如 Jmeter）对接口进压测，在 Grafana 中观察监控数据

4. 通过 Kubernetes 命令进行手工扩容，并再次观察 Grafana 中的监控数据

5. 加分项：使用 Kubernetes HPA 模块根据 CPU 负载做服务的 Auto Scale

# **分数说明**

本次作业占总评 55 分，分数分配如下

\1. **功能要求（****20** **分）**

1.1 实现接口 （5 分）

1.2 实现限流功能（10 分）

1.3 实现接口访问指标（QPS），并暴露给 Prometheus（5分）

1.3 **统一限流（bonus 5 分）**




**2. DevOps 要求（****20** **分）**

2.1 Dockerfile 用于构建镜像（5 分）

2.2 Kubernetes 编排文件（5 分）

2.3 持续集成流水线（5 分）

2.4 持续部署流水线（5 分）

**2.5 代码提交到仓库自动触发流水线（bonus 5分）**



**3. 扩容场景（****15** **分）**

3.1 Prometheus 采集监控指标（5 分）

3.2 Grafana 定制应用监控大屏（5 分）

3.3 压测并观察监控数据（5 分） 

**3.5 Auto Scale（bonus 10 分）**
