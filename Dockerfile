FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B package

FROM eclipse-temurin:17-jre
RUN useradd -r -u 1001 -m app
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER app
EXPOSE 8089
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
