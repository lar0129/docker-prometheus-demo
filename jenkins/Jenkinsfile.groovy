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
        git branch: 'main', url:
//    'https://github.com/lar0129/prometheus-test-demo.git'
        "https://gitee.com/lar0129/prometheus-test-demo.git"
        }
        
        stage('YAML') {
        echo "6. Change YAML File Stage"
        // ./jenkins/scripts/是自己创建的？
        sh 'sed -i "s#{VERSION}#${BUILD_ID}#g" ./jenkins/scripts/demo.yaml'
        }

        stage('HPA') {
            echo "10. Test HPA"
            sh 'kubectl apply -f ./jenkins/scripts/demo-hpa.yaml'
            sh 'kubectl autoscale deployment demo-native -n nju13 --cpu-percent=50 --min=1 --max=10'
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


