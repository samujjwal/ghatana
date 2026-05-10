#!/bin/bash

# Simple Development Script for YAPPC Web App
# This script provides a dev-friendly way to run the app with mock data

echo "🚀 Starting YAPPC Development Environment..."
echo ""

# Kill any existing processes on ports 7003 and 3000
echo "🧹 Cleaning up existing processes..."
lsof -ti:7003 | xargs kill -9 2>/dev/null || true
lsof -ti:3000 | xargs kill -9 2>/dev/null || true

# Start the mock API server
echo "📡 Starting mock API server on port 7003..."
cd /Users/samujjwal/Development/ghatana/products/yappc/frontend
node simple-dev-server.mjs &
API_PID=$!
echo "✅ Mock API server started (PID: $API_PID)"
echo ""

# Wait for API server to be ready
sleep 2

echo "📝 Development Environment Ready!"
echo "=================================="
echo "Mock API Server: http://localhost:7003"
echo ""
echo "Available endpoints:"
echo "  GET  /health"
echo "  GET  /api/workspaces"
echo "  POST /api/workspaces"
echo "  GET  /api/projects"
echo "  POST /api/projects"
echo ""
echo "⚠️  Note: The web app frontend has a tsconfig configuration issue"
echo "     with react-router dev. The mock API server provides backend functionality."
echo ""
echo "To stop the servers, run: kill $API_PID"
echo ""

# Keep the script running
wait $API_PID
