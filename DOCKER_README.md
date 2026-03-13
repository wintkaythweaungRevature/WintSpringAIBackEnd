# Docker Run Guide

## Build image
```bash
docker build -t wintspringai .
```

## Run with env vars (fill your keys in terminal)

**Required: OpenAI API key**

```bash
docker run -p 8080:8080 \
  -e SPRING_AI_OPENAI_API_KEY="sk-your-openai-api-key-here" \
  -e JWT_SECRET="your-jwt-secret-min-32-chars" \
  wintspringai
```

**Full production config (PostgreSQL, Stripe, etc.):**

```bash
docker run -p 8080:8080 \
  -e SPRING_AI_OPENAI_API_KEY="sk-your-openai-api-key-here" \
  -e JWT_SECRET="your-jwt-secret-min-32-chars" \
  -e DATABASE_URL="jdbc:postgresql://host:5432/dbname?sslmode=require" \
  -e DATABASE_PASSWORD="your-db-password" \
  -e STRIPE_SECRET_KEY="sk_test_..." \
  -e STRIPE_WEBHOOK_SECRET="whsec_..." \
  wintspringai
```
