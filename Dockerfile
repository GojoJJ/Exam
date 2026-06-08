# Stage 1: Build the application
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Use the port environment variable injected by Render (falls back to 8081 in properties if not set)
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
