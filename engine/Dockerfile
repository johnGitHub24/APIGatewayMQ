FROM gradle:8.5-jdk21-alpine AS build
WORKDIR /build
COPY . .
RUN gradle :engine:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/engine/build/libs/trading-engine-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
