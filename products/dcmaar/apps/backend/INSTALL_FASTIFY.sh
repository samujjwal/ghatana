#!/bin/bash

echo "🚀 Installing Fastify dependencies..."
echo ""

# Navigate to backend directory
cd "$(dirname "$0")"

# Install dependencies
echo "📦 Running pnpm install..."
pnpm install

echo ""
echo "✅ Fastify dependencies installed!"
echo ""
echo "📋 What was installed:"
echo "  ✅ fastify@^5.2.0 (latest)"
echo "  ✅ @fastify/compress (replaces compression)"
echo "  ✅ @fastify/cookie (replaces cookie-parser)"
echo "  ✅ @fastify/cors (replaces cors)"
echo "  ✅ @fastify/helmet (replaces helmet)"
echo "  ✅ @fastify/rate-limit (replaces express-rate-limit)"
echo "  ✅ @fastify/socket.io (Socket.io integration)"
echo "  ✅ @fastify/sensible (helpful utilities)"
echo "  ✅ @sinclair/typebox (type-safe schemas)"
echo ""
echo "❌ What was removed:"
echo "  ❌ express"
echo "  ❌ express-rate-limit"
echo "  ❌ express-validator"
echo "  ❌ compression"
echo "  ❌ cookie-parser"
echo "  ❌ cors"
echo "  ❌ helmet"
echo ""
echo "🎉 Ready to migrate code to Fastify!"
