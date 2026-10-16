FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src src
RUN mvn -B verify
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/cloudguard-1.0.0.jar app.jar
USER 10001
EXPOSE 8080 9091
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","app.jar"]
