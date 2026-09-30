# --- Etapa de build ---
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Cachear dependencias primero
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B package -DskipTests

# --- Etapa de runtime ---
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S taskflow && adduser -S taskflow -G taskflow
USER taskflow

COPY --from=build /app/target/taskflow-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
