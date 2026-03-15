#!/bin/bash
# Deploy spring-ai-backend to EC2
# Set env vars in .env or export before running. DO NOT commit .env with real secrets.

set -e

echo "Pulling latest image..."
docker pull wintkaythweaugn/spring-ai-backend:latest

echo "Stopping and removing old container..."
docker stop spring-ai-backend 2>/dev/null || true
docker rm spring-ai-backend 2>/dev/null || true

echo "Starting new container..."
docker run -d -p 8080:8080 --name spring-ai-backend --restart always \
  -e SPRING_AI_OPENAI_API_KEY="${SPRING_AI_OPENAI_API_KEY}" \
  -e DB_PASSWORD="${DB_PASSWORD}" \
  -e STRIPE_SECRET_KEY="${STRIPE_SECRET_KEY}" \
  -e STRIPE_WEBHOOK_SECRET="${STRIPE_WEBHOOK_SECRET}" \
  -e STRIPE_PRICE_MEMBER="${STRIPE_PRICE_MEMBER}" \
  -e YOUTUBE_CLIENT_ID="${YOUTUBE_CLIENT_ID}" \
  -e YOUTUBE_CLIENT_SECRET="${YOUTUBE_CLIENT_SECRET}" \
  -e INSTAGRAM_APP_ID="${INSTAGRAM_APP_ID}" \
  -e INSTAGRAM_APP_SECRET="${INSTAGRAM_APP_SECRET}" \
  -e TIKTOK_CLIENT_KEY="${TIKTOK_CLIENT_KEY}" \
  -e TIKTOK_CLIENT_SECRET="${TIKTOK_CLIENT_SECRET}" \
  -e LINKEDIN_CLIENT_ID="${LINKEDIN_CLIENT_ID}" \
  -e LINKEDIN_CLIENT_SECRET="${LINKEDIN_CLIENT_SECRET}" \
  -e X_CLIENT_ID="${X_CLIENT_ID}" \
  -e X_CLIENT_SECRET="${X_CLIENT_SECRET}" \
  wintkaythweaugn/spring-ai-backend:latest

echo "Backend deployed. Check: docker logs -f spring-ai-backend"
