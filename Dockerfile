# ရှေးဟောင်း openjdk အစား ဒါကို သုံးပါ
FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# Maven ကထွက်လာတဲ့ jar ကို ကူးမယ်
COPY target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]