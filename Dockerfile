FROM amazoncorretto:21.0.13-alpine AS build

RUN apk add --no-cache bash curl unzip

WORKDIR /workspace

COPY gradlew build.gradle gradle.properties settings.gradle ./
COPY gradle/ gradle/
RUN chmod +x ./gradlew

COPY src/ src/
RUN ./gradlew --no-daemon bootJar -x test

FROM amazoncorretto:21.0.13-alpine

RUN apk add --no-cache curl

WORKDIR /app
COPY --from=build /workspace/build/libs/specmatic-order-api-java-with-oauth.jar /app/app.jar

EXPOSE 8443

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
