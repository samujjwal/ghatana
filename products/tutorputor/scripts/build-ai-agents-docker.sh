#!/bin/bash
set -e

echo "🐳 Building TutorPutor AI Agents Docker Image..."

# Navigate to root
cd "$(dirname "$0")/../../.."

# Build the JAR (skip tests for speed in this step, verifying buildability)
echo "📦 Compiling Java Application..."
./gradlew :products:tutorputor:services:tutorputor-ai-agents:assemble --no-daemon -x test

# Build Docker Image
echo "🔨 Building Docker Container..."
docker build -f products/tutorputor/services/tutorputor-ai-agents/Dockerfile \
  -t tutorputor/ai-agents:latest .

echo "✅ Build Process Complete."
echo "➡️  Run via: docker-compose -f docker-compose.tutorputor.yml up -d tutorputor-ai-agents"
