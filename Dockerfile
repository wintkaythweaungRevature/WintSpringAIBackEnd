# Stage 1: Build
FROM maven:3.8.4-openjdk-17-slim AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app

# All secrets (SPRING_AI_OPENAI_API_KEY, JWT_SECRET, STRIPE_SECRET_KEY, etc.)
# must be injected at container runtime via environment variables, not baked into the image.

COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]