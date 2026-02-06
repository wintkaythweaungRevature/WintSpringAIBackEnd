# Build stage
FROM maven:3.8.4-openjdk-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
# ⚠️ အရေးကြီး: target ထဲက jar ကို လက်ရှိ folder (.) ထဲကို app.jar နာမည်နဲ့ ကူးမယ်
COPY --from=build /app/target/*.jar app.jar
# ⚠️ ENTRYPOINT မှာ /app/app.jar လို့ အပြည့်အစုံ ရေးပေးပါ
ENTRYPOINT ["java", "-jar", "/app/app.jar"]