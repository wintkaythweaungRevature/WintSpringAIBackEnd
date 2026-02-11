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

# GitHub Actions သို့မဟုတ် AWS ကနေ ပို့လိုက်တဲ့ Argument ကို လက်ခံဖို့
ARG SPRING_AI_OPENAI_API_KEY
# လက်ခံရရှိတဲ့ Argument ကို Environment Variable အဖြစ် သတ်မှတ်ပေးခြင်း
ENV SPRING_AI_OPENAI_API_KEY=${SPRING_AI_OPENAI_API_KEY}

COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]