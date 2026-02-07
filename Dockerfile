# Stage 1: Build the code
FROM maven:3.8.4-openjdk-17 AS build
WORKDIR /app

# 1. Copy pom.xml and download dependencies (cached)
COPY pom.xml .
RUN mvn dependency:go-offline

# 2. Copy the actual source code and BUILD the JAR
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Run the code
FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app

# 3. Copy the FRESHLY BUILT jar from the build stage
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]