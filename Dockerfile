# Build stage
FROM maven:3.8.4-openjdk-17 AS build
COPY . .
RUN mvn clean package -DskipTests

# Run stage (openjdk နေရာမှာ eclipse-temurin ကို အစားထိုးလိုက်ပါ)
FROM eclipse-temurin:17-jdk-alpine
COPY --from=build /target/*.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]