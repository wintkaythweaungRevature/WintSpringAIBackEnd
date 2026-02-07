# Stage 1: Build the application
FROM maven:3.8.4-openjdk-17 AS build
WORKDIR /app

# Dependency တွေ အရင်ဆွဲမယ်
COPY pom.xml .
RUN mvn dependency:go-offline

# Source code တွေကို ကူးပြီး Class ဖိုင်တွေအဖြစ် Compile လုပ်မယ်
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app

# ရှေ့အဆင့်က ထွက်လာတဲ့ Jar ဖိုင်ကို ကူးမယ်
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]