FROM amazoncorretto:25.0.2-alpine

RUN apk add --no-cache git curl bash jq && \
    rm -rf /var/cache/apk/*

SHELL ["/bin/bash", "-c"]

WORKDIR /app

EXPOSE 8080

COPY ./build/libs/specmatic-order-api-java-with-oauth.jar /app/app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
