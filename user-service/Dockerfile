FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /workspace
ENV MAVEN_OPTS="-Dhttp.keepAlive=false -Dmaven.wagon.http.pool=false -Dmaven.wagon.http.retryHandler.count=5 -Dmaven.wagon.http.retryHandler.requestSentEnabled=true -Daether.connector.http.retryHandler.count=5 -Daether.connector.http.retryHandler.requestSentEnabled=true -Daether.connector.connectionTimeout=60000 -Daether.connector.requestTimeout=60000"
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /workspace/target/user-service-0.1.0.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
