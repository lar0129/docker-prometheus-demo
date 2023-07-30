# Dockerfile
FROM openjdk:17

RUN ln -sf /usr/share/zoneinfo/Asia/shanghai /etc/localtime
RUN echo 'Asia/shanghai' >/etc/timezone

WORKDIR /app
ADD target/demo-0.0.1-SNAPSHOT.jar .

ENTRYPOINT ["java", "-jar", "demo-0.0.1-SNAPSHOT.jar"]
