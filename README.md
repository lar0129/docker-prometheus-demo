211250127 梁安然



开发一个 Spring Boot 应用，并使用云原生功能

* github项目地址：https://github.com/lar0129/prometheus-test-demo
* gitee项目地址：https://gitee.com/lar0129/prometheus-test-demo
  * Jenkins拉取github仓库经常失败，故gitee为备用


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
                  sh 'curl "http://p2.nju.edu.cn/portal_io/login?username=nju13&password=nju132023"'
                  git branch: 'main', url:
                   'https://github.com/lar0129/prometheus-test-demo.git'                
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
          sh 'curl "http://p2.nju.edu.cn/portal_io/login?username=nju13&password=nju132023"'
          git branch: 'main', url:
      'https://github.com/lar0129/prometheus-test-demo.git'
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
  
          stage('RTF Test'){//集成测试
          echo "8. RTF Test Stage"
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
    * ![image-20230729201653341](https://lar-blog.oss-cn-nanjing.aliyuncs.com/picGo_img/AppData/Roaming/Typora/typora-user-images/image-20230729201653341.png)
    * ![image-20230730224910852](https://lar-blog.oss-cn-nanjing.aliyuncs.com/picGo_img/AppData/Roaming/Typora/typora-user-images/image-20230730224910852.png)
  
  * 3.访问端口验证k8s部署是否成功
  
    * 集群网址172.29.4.18，Nodeport配置为30333，故访问172.29.4.18:30333/ping，验证成功
    * ![image-20230731185135102](https://lar-blog.oss-cn-nanjing.aliyuncs.com/picGo_img/AppData/Roaming/Typora/typora-user-images/image-20230731185135102.png)

6. 代码提交到仓库自动触发流水线（bonus 5分）

* 使用github webhook / jenkins触发器（待做）

## **3. 扩容场景**

1. 为该 Java 项目提供 Prometheus metrics 接口，可以供 Prometheus 采集监控指标

2. 在 Grafana 中的定制应用的监控大屏（CPU/内存/JVM）

3. 使用压测工具（例如 Jmeter）对接口进压测，在 Grafana 中观察监控数据

4. 通过 Kubernetes 命令进行手工扩容，并再次观察 Grafana 中的监控数据

5. 加分项：使用 Kubernetes HPA 模块根据 CPU 负载做服务的 Auto Scale（bonus 5 分）

# **分数说明**

本次作业占总评 55 分，分数分配如下

**1.功能要求（****20** **分）**

1.1 实现接口 （5 分）√

1.2 实现限流功能（10 分）√

1.3 实现接口访问指标（QPS），并暴露给 Prometheus（5分）

1.3 **统一限流（bonus 5 分）**




**2. DevOps 要求（****20** **分）**

2.1 Dockerfile 用于构建镜像（5 分）√

2.2 Kubernetes 编排文件（5 分）√

2.3 持续集成流水线（5 分）√ （单元测试未做）

2.4 持续部署流水线（5 分）√

**2.5 代码提交到仓库自动触发流水线（bonus 5分）**



**3. 扩容场景（****15** **分）**

3.1 Prometheus 采集监控指标（5 分）

3.2 Grafana 定制应用监控大屏（5 分）

3.3 压测并观察监控数据（5 分） 

**3.5 Auto Scale（bonus 10 分）**

# **提交要求**

只需提交一份项目说明文档，必须包含以下内容：

​            \1.     限流功能代码说明和截图

​            \2.     Dockerfile，K8s 编排文件截图及说明

​            \3.     Jenkins 持续集成、持续部署、持续测试配置截图及说明，以及后续验证流水线成功的截图

​            \4.     监控指标采集的配置及说明；Grafana 监控大屏截图

​            \5.     压测工具配置说明，及相应压测监控截图；K8s 手工扩容后，相应压测监控截图

文档内容不限于以上所述，可以任意添加其余说明，使得文档更清晰

一组由一人提交即可，文档内写明组员的信息，姓名和学号



注意：因为只会根据文档评分，文档一定要完整准确清晰地体现所做的工作，请大家对自己负责！

统一提交 pdf 文件，统一文件命名，组号.pdf，如所在组为 1 组，则文件名为：1.pdf
