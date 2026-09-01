FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/target/investment-api-*.jar /app/investment-api.jar

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/investment-api.jar"]
