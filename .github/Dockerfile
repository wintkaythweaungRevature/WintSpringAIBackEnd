# Step 1: Use OpenJDK 17
FROM openjdk:17-jdk-slim

# Step 2: ပတ်ဝန်းကျင် ပြောင်းလဲမှု (Optional)
WORKDIR /app

# Step 3: Maven က ထွက်လာတဲ့ .jar ဖိုင်ကို app.jar နာမည်နဲ့ ကူးထည့်မယ်
# မင်း project နာမည်က SpringAIDemo ဖြစ်လို့ target ထဲမှာ .jar ဖိုင် ရှိနေပါလိမ့်မယ်
COPY target/*.jar app.jar

# Step 4: Application ကို run မယ်
ENTRYPOINT ["java", "-jar", "app.jar"]