# Stage 1: Build inside Docker
FROM maven:3.8.4-openjdk-17-slim AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
# ဒီနေရာမှာ အသစ်စက်စက် clean build လုပ်မှာပါ
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
# Stage 1 က ထွက်လာတဲ့ JAR ကိုပဲ app.jar နာမည်နဲ့ ကူးယူမယ်
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]