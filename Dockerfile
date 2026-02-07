# Stage 1: Build the application
FROM maven:3.8.4-openjdk-17 AS build
WORKDIR /app

# ၁။ Dependency တွေ အရင်ဆွဲမယ် (Cache မိအောင်လို့ပါ)
COPY pom.xml .
RUN mvn dependency:go-offline

# ၂။ Source code အကုန်ကူးပြီး အသစ်စက်စက် Build မယ်
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app

# ၃။ ရှေ့အဆင့်ကထွက်လာတဲ့ JAR ကိုပဲ ယူမယ်
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]