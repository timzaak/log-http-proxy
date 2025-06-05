FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

RUN apk add --no-cache tzdata &&  cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime &&     echo "Asia/Shanghai" > /etc/timezone

RUN apk add --no-cache bash

COPY target/universal/stage /app/

EXPOSE 443

CMD ["/bin/bash", "/app/bin/app"]
