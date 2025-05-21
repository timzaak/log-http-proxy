FROM eclipse-temurin:21-jdk-alpine
WORKDIR /server

CMD ["/bin/bash", "/server/bin/app"]


RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

RUN apk update && apk add --no-cache bash

VOLUME /server/config

COPY ./app /server/