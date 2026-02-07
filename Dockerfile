FROM maven:3.8.4-openjdk-17 AS build
WORKDIR /app
# ၁။ pom.xml ကို အရင်ကူးမယ်
COPY pom.xml .
# ၂။ Dependency တွေ အရင်ဆွဲမယ်
RUN mvn dependency:go-offline
# ၃။ Source code တစ်ခုလုံးကို အခုမှ ကူးမယ်
COPY src ./src
# ၄။ အရင် build တွေကို အကုန်ဖျက်ပြီး အသစ်ပြန် build မယ်
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]