#!/bin/bash
# Build and push React frontend Docker image
# Run from your React project root (e.g. SpringAI or spring-ai-react)

set -e

echo "Building React app..."
npm run build

echo "Building Docker image..."
docker build -t wintkaythweaugn/spring-ai-react:latest .

echo "Pushing to Docker Hub..."
docker push wintkaythweaugn/spring-ai-react:latest

echo "Done. Deploy on EC2: docker pull wintkaythweaugn/spring-ai-react:latest"
