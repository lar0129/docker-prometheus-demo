[TOC]

开发一个 Spring Boot 应用，并使用云原生功能

* gitee项目地址：https://gitee.com/lar0129/prometheus-test-demo
* github项目地址：https://github.com/lar0129/prometheus-test-demo
  * Jenkins拉取github仓库经常失败，故gitee为主项目，github为备份



小组成员

* 211250165 刘尧力
* 211250127 梁安然
* 211250135 赵简

# 过程截图


## **1. 功能要求**

​        

1. 实现一个 REST 接口（简单接口即可，比如 json 串 {"msg":"hello"}）

* 目前提供/ping接口与限流功能
* 放到github上方便进行持续集成/持续部署

![image-20230729200322667](https://lar-blog.oss-cn-nanjing.aliyuncs.com/picGo_img/AppData/Roaming/Typora/typora-user-images/image-20230729200322667.png)

​          

2. 接口提供限流功能，当请求达到每秒 100 次的时候，返回 429（Too many requests）

* 代码：
  * ```java
    private final RateLimiter rateLimiter = RateLimiter.create(100.0);
    
        @GetMapping("/ping")
        public ResponseEntity<String> hello() {
            if (rateLimiter.tryAcquire()) {
                return ResponseEntity.ok("{\"msg\":\"Hello, this is NJU13\"}");
            } else {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Too many requests");
            }
        }
    ```
  
* 使用Jmeter进行并发测试，结果显示限流成功

![image-20230729200404047](https://lar-blog.oss-cn-nanjing.aliyuncs.com/picGo_img/AppData/Roaming/Typora/typora-user-images/image-20230729200404047.png)

![image-20230729200343091](https://lar-blog.oss-cn-nanjing.aliyuncs.com/picGo_img/AppData/Roaming/Typora/typora-user-images/image-20230729200343091.png)

![image-20230729200307883](https://lar-blog.oss-cn-nanjing.aliyuncs.com/picGo_img/AppData/Roaming/Typora/typora-user-images/image-20230729200307883.png)



3. **加分项**：当后端服务有多个实例的时候（一个 Service 包含若干个 Pod），如何实现**统一限流**（bonus 5分）

 * 仿照第二次作业，使用Eureka转发实现统一限流？（待做）



## **2. DevOps 要求**

1. 为该项目准备 Dockerfile，用于构建镜像

* Dockerfile见项目根目录Dockerfile

* ```dockerfile
  # Dockerfile
  FROM openjdk:17
  
  RUN ln -sf /usr/share/zoneinfo/Asia/shanghai /etc/localtime
  RUN echo 'Asia/shanghai' >/etc/timezone
  
  WORKDIR /app
  ADD target/demo-0.0.1-SNAPSHOT.jar .
  
  ENTRYPOINT ["java", "-jar", "demo-0.0.1-SNAPSHOT.jar"]
  
  ```

  

2. 为该项目准备 Kubernetes 编排文件，用于在 Kubernetes 集群上创建 Deployment 和 Service

* k8s部署内容见项目中 ./jenkins/scripts/demo.yaml 文件

* ```yaml
  # demo.yaml
  apiVersion: apps/v1
  kind: Deployment #对象类型
  metadata:
    labels:
      app: lar-demo
    name: lar-demo
    namespace: nju13
  spec:
    replicas: 1 #运行容器的副本数
    selector:
      matchLabels:
        app: lar-demo
    template:
      metadata:
        annotations:
          prometheus.io/path: /actuator/prometheus
          prometheus.io/port: "8080"
          prometheus.io/scheme: http
          prometheus.io/scrape: "true"
        labels:
          app: lar-demo
      spec:
        containers: #docker容器的配置
        - image: harbor.edu.cn/nju13/lar-demo:{VERSION} #pull镜像的地址,本地测试时注销
  #      - image: demo:latest  # win下本地测试用
          name: lar-demo
  #      imagePullPolicy: Always # 本地测试用
        imagePullSecrets: # 本地测试时注销
         - name: docker-harbor-nju13 # 本地测试时注销
  ---
  apiVersion: v1
  kind: Service
  metadata:
    name: lar-demo
    namespace: nju13
    labels:
      app: lar-demo
  spec:
    type: NodePort
    selector:
      app: lar-demo
    ports:
    - name: tcp
      nodePort: 30332
      protocol: TCP
      port: 8080
      targetPort: 8080
  
  ```

  

3. 编写 Jenkins 持续集成流水线，实现**代码构建**/**单元测试**/**镜像构建**功能（需要写至少一个单元测试）

4. 编写 Jenkins 持续部署流水线，实现部署到 Kubernetes 集群的功能，该流水线的触发条件为持续集成流水线执行成功

5. 注意：持续集成流水线和持续部署流水线也可以合二为一。

3/4/5：Jenkinsfile见项目中 ./jenkins/Jenkinsfile文件

* ```groovy
  pipeline {
      agent none
  
      environment {
          REGISTRY = "harbor.edu.cn/nju13"
      }
  
      stages {
          stage('Clone Code') {//第一步：克隆代码，为了build image
              agent {
                  label 'master'
              }
              steps {
                  echo "1.Git Clone Code"
                  sh 'curl "http://p2.nju.edu.cn/portal_io/login?username=211250127&password=cllxj8756"'
                  git branch: 'main', url:
  //                 'https://github.com/lar0129/prometheus-test-demo.git'
                  "https://gitee.com/lar0129/prometheus-test-demo.git"
              }
          }
          stage('Maven Build') {//第二步：maven打包
              agent {
                  docker {
                      image 'maven:latest'
                      args '-v /root/.m2:/root/.m2'
                  }
              }
              steps {
                  echo "2.Maven Build Stage"
                  sh 'mvn -B clean package -Dmaven.test.skip=true'
              }
          }
          stage('Image Build') {//第三步：构建镜像
              agent {
                  label 'master'
              }
              steps {
              echo "3.Image Build Stage"
              sh 'docker build -f Dockerfile --build-arg jar_name=target/demo-0.0.1-SNAPSHOT.jar -t lar-demo:${BUILD_ID} . '
              // library使用宏定义，可以在全局设置中设置
              sh 'docker tag  lar-demo:${BUILD_ID}  ${REGISTRY}/lar-demo:${BUILD_ID}'
              }
          }
          stage('Push') {
              agent {
                  label 'master'
              }
              steps {
              echo "4.Push Docker Image Stage"
              sh "docker login --username=nju13 harbor.edu.cn -p nju132023"
              sh "docker push ${REGISTRY}/lar-demo:${BUILD_ID}"
              }
          }
      }
  }
  
  
  node('slave') {
      container('jnlp-kubectl') {
  
          stage('Clone YAML') {
          echo "5. Git Clone YAML To Slave"
          sh 'curl "http://p2.nju.edu.cn/portal_io/login?username=211250127&password=cllxj8756"'
          git branch: 'main', url:
  //    'https://github.com/lar0129/prometheus-test-demo.git'
          "https://gitee.com/lar0129/prometheus-test-demo.git"
          }
          
          stage('YAML') {
          echo "6. Change YAML File Stage"
          // ./jenkins/scripts/是自己创建的？
          sh 'sed -i "s#{VERSION}#${BUILD_ID}#g" ./jenkins/scripts/demo.yaml'
          }
      
          stage('Deploy') {
          echo "7. Deploy To K8s Stage"
          sh 'kubectl apply -f ./jenkins/scripts/demo.yaml -n nju13'
          // sh 'kubectl apply -f ./jenkins/scripts/demo-monitor.yaml'
          }
  
          stage('Monitor') {
              echo "8. Deploy Prometheus Monitor"
               sh 'kubectl apply -f ./jenkins/scripts/demo-monitor.yaml'
          }
  
          stage('RTF Test'){//集成测试
          echo "9. RTF Test Stage"
          // sh 'kubectl apply -f ./jenkins/scripts/rtf.yaml -n nju13'
          }
      }
  }
  
  
  ```
  
* 具体过程：

  * 1.将jenkins连接到k8s-nju13-namespace中，完成鉴权

    * 具体过程仿照https://doc.weixin.qq.com/doc/w3_m_YeHOnMvfCxXW?scode=ABoAuwfGAAkIB91cNPAK4AcQYdAN0

    * 图片处需要修改命令

      ![image-20230730182453951](https://lar-blog.oss-cn-nanjing.aliyuncs.com/picGo_img/AppData/Roaming/Typora/typora-user-images/image-20230730182453951.png)

  * 2.部署流水线
    
    * Jenkins流程：
    
    * |          |         Stage          |         step         |
      | :------: | :--------------------: | :------------------: |
      | 持续集成 |    1.Git Clone Code    | 拉取spring boot代码  |
      |          |     2.Maven Build      |    maven构建jar包    |
      |          |     3.Image Build      |       构建镜像       |
      |          | 4.Push Image To Harbor | push镜像至docker仓库 |
      | 持续部署 |    5.Git Clone Yaml    | 拉取部署所需yaml文件 |
      |          |   6.Change YAML File   |   改变Yaml环境变量   |
      |          |  7.Deploy the Web App  | 部署spring boot应用  |
      |          |  8.Deploy the Monitor  |  部署Prometheus监控  |
      | 集成测试 |         9.Test         |         测试         |
    
    * 部署成功截图
    
    * ![image-20230730224910852](https://lar-blog.oss-cn-nanjing.aliyuncs.com/picGo_img/AppData/Roaming/Typora/typora-user-images/image-20230730224910852.png)
    
  * 3.访问端口验证k8s部署是否成功
  
    * 集群网址172.29.4.18，Nodeport配置为30333，故访问172.29.4.18:30333/ping，验证成功
    * ![image-20230731185135102](https://lar-blog.oss-cn-nanjing.aliyuncs.com/picGo_img/AppData/Roaming/Typora/typora-user-images/image-20230731185135102.png)

6. 代码提交到仓库自动触发流水线（bonus 5分）

* 使用github webhook / jenkins触发器（待做）

## **3. 扩容场景**

1.为该 Java 项目提供 Prometheus metrics 接口，可以供 Prometheus 采集监控指标

* 在springboot项目中配置Prometheus metrics 接口

  * ![image-20230801112225930](https://lar-blog.oss-cn-nanjing.aliyuncs.com/picGo_img/AppData/Roaming/Typora/typora-user-images/image-20230801112225930.png)

* 部署k8s-monitor对象

  * yaml代码位于./jenkins/script/demo-monitor.yaml

  * ```yaml
    apiVersion: monitoring.coreos.com/v1
    kind: ServiceMonitor
    metadata:
      labels:
        k8s-app: lar-demo
      name: lar-demo
      namespace: monitoring
    spec:
      endpoints:
      - interval: 30s
        port: tcp
        path: /actuator/prometheus
        scheme: 'http'
      selector:
        matchLabels:
          app: lar-demo
      namespaceSelector:
        matchNames:
        - nju13
    ```

  * 放入流水线中部署

  * ![image-20230801112409002](https://lar-blog.oss-cn-nanjing.aliyuncs.com/picGo_img/AppData/Roaming/Typora/typora-user-images/image-20230801112409002.png)

* 在Prometheus的UI界面验证部署是否成功

  * ![image-20230801112050506](https://lar-blog.oss-cn-nanjing.aliyuncs.com/picGo_img/AppData/Roaming/Typora/typora-user-images/image-20230801112050506.png)

2.在 Grafana 中的定制应用的监控大屏（CPU/内存/JVM）

* 网站系统的安全隐患：Grafana中默认的管理员账户admin密码admin没改，随随便便就能进去操作
* 在Grafana 中的定制监控应用lar-demo的各种性能
  * 容器CPU使用情况
  * 容器内存使用情况
  * 网络请求情况
  * ![image-20230801150417996](https://lar-blog.oss-cn-nanjing.aliyuncs.com/picGo_img/AppData/Roaming/Typora/typora-user-images/image-20230801150417996.png)

3.使用压测工具（例如 Jmeter）对接口进压测，在 Grafana 中观察监控数据

* 使用Jmeter，向172.29.4.18:30332/ping  用100个进程同时发送 100个GET请求
* 配置和结果如下
  * ![image-20230801150552940](https://lar-blog.oss-cn-nanjing.aliyuncs.com/picGo_img/AppData/Roaming/Typora/typora-user-images/image-20230801150552940.png)
  * ![image-20230801150604028](https://lar-blog.oss-cn-nanjing.aliyuncs.com/picGo_img/AppData/Roaming/Typora/typora-user-images/image-20230801150604028.png)
  * ![image-20230801150640926](https://lar-blog.oss-cn-nanjing.aliyuncs.com/picGo_img/AppData/Roaming/Typora/typora-user-images/image-20230801150640926.png)
    * 部分请求错误，证明限流成功
* Grafana 监控应用lar-demo结果如下，明显可看出变化
  * CPU使用情况
    * ![image-20230801150824336](https://lar-blog.oss-cn-nanjing.aliyuncs.com/picGo_img/AppData/Roaming/Typora/typora-user-images/image-20230801150824336.png)
    * 从25升至50
  * Memory使用情况
    * ![image-20230801150928604](https://lar-blog.oss-cn-nanjing.aliyuncs.com/picGo_img/AppData/Roaming/Typora/typora-user-images/image-20230801150928604.png)
    * 从2.7G升至3G
  * 网络使用情况
    * ![image-20230801151009171](https://lar-blog.oss-cn-nanjing.aliyuncs.com/picGo_img/AppData/Roaming/Typora/typora-user-images/image-20230801151009171.png)

4.通过 Kubernetes 命令进行手工扩容，并再次观察 Grafana 中的监控数据

* 命令行扩容发现权限不足，故只能更改yaml文件重新构建流水线
  * ![image-20230801152235388](https://lar-blog.oss-cn-nanjing.aliyuncs.com/picGo_img/AppData/Roaming/Typora/typora-user-images/image-20230801152235388.png)
  * ![image-20230801152328292](https://lar-blog.oss-cn-nanjing.aliyuncs.com/picGo_img/AppData/Roaming/Typora/typora-user-images/image-20230801152328292.png)
* 扩容后观察Grafana可见，Cpu和Memory的监控面板新增两个Container的曲线
  * ![image-20230801152753480](https://lar-blog.oss-cn-nanjing.aliyuncs.com/picGo_img/AppData/Roaming/Typora/typora-user-images/image-20230801152753480.png)

5.加分项：使用 Kubernetes HPA 模块根据 CPU 负载做服务的 Auto Scale（bonus 5 分）

- 创建HPA并查看

- 创建demo-hpa.yaml如下配置hpa自动扩容的参数

  - ```yaml
    apiVersion: apps/v1
    kind: Deployment
    metadata:
      labels:
        app: lar-demo-hpa
      name: lar-demo-hpa
      namespace: nju13
    spec:
      replicas: 1
      selector:
        matchLabels:
          app: lar-demo-hpa
      template:
        metadata:
          labels:
            app: lar-demo-hpa
        spec:
          containers:
            - name: lar-demo-hpa
              image: harbor.edu.cn/nju13/lar-demo:70
              ports:
                - containerPort: 8080
              resources: #基于内存自动扩容
                limits:
                  cpu: 200m
                requests:
                  cpu: 80m  
          imagePullSecrets: # 本地测试时注销
            - name: docker-harbor-nju13 # 本地测试时注销
    
    ---
    apiVersion: v1
    kind: Service
    metadata:
      name: lar-demo-hpa
      namespace: nju13
    spec:
      ports:
        - port: 8080
          protocol: TCP
      selector:
        app: lar-demo-hpa
      type: ClusterIP
    
    ```

    

- 流水线中执行yaml文件创建deploy

  ![image-20230815224934507](https://lar-blog.oss-cn-nanjing.aliyuncs.com/picGo_img/AppData/Roaming/Typora/typora-user-images/image-20230815224934507.png)

- 设置HPA：最小1个pod，最多10个pods，cpu负载超过50%扩容

  ![image-20230815224952591](https://lar-blog.oss-cn-nanjing.aliyuncs.com/picGo_img/AppData/Roaming/Typora/typora-user-images/image-20230815224952591.png)

- 查看HPA

  ![image-20230815225026390](https://lar-blog.oss-cn-nanjing.aliyuncs.com/picGo_img/AppData/Roaming/Typora/typora-user-images/image-20230815225026390.png)

  demo-hpa成功监测。且目前replicas数量为1

- 压测验证HPA

  - ![image-20230815230319182](https://lar-blog.oss-cn-nanjing.aliyuncs.com/picGo_img/AppData/Roaming/Typora/typora-user-images/image-20230815230319182.png)
  - 146个请求后到达极限

- 压力测试后，观测到HPA自动扩容成功

  - ![image-20230815230426622](https://lar-blog.oss-cn-nanjing.aliyuncs.com/picGo_img/AppData/Roaming/Typora/typora-user-images/image-20230815230426622.png)
  - ![image-20230815230456357](https://lar-blog.oss-cn-nanjing.aliyuncs.com/picGo_img/AppData/Roaming/Typora/typora-user-images/image-20230815230456357.png)


# **分数完成度**



**1.功能要求（20分）**

1.1 实现接口 （5 分）√

1.2 实现限流功能（10 分）√

1.3 实现接口访问指标（QPS），并暴露给 Prometheus（5分）√

1.3 **统一限流（bonus 5 分）**




**2. DevOps 要求（20分）**

2.1 Dockerfile 用于构建镜像（5 分）√

2.2 Kubernetes 编排文件（5 分）√

2.3 持续集成流水线（5 分）√ （单元测试未做）

2.4 持续部署流水线（5 分）√

**2.5 代码提交到仓库自动触发流水线（bonus 5分）**



**3. 扩容场景（15分）**

3.1 Prometheus 采集监控指标（5 分）√

3.2 Grafana 定制应用监控大屏（5 分）√

3.3 压测并观察监控数据（5 分） √

**3.5 Auto Scale（bonus 10 分）**√

