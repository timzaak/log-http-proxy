FROM eclipse-temurin:21-jdk-alpine
WORKDIR /server

CMD ["/bin/bash", "bin/log-http-proxy", "-Dconfig.file=/server/config/application.conf"]


RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

RUN apk update && apk add --no-cache bash

COPY ./src/main/resources/application.conf /server/config/

COPY ./target/universal/stage/ /server/