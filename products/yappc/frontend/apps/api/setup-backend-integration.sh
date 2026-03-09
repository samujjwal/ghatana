#!/bin/bash
#
# Backend Integration Setup Script
# Automates the setup process for Canvas Persistence + WebSocket Collaboration
#

set -e

echo "🚀 Canvas Backend Integration - Setup Script"
echo "=============================================="
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Step 1: Check if we're in the right directory
if [ ! -f "package.json" ]; then
    echo -e "${RED}❌ Error: package.json not found${NC}"
    echo "Please run this script from: products/yappc/frontend/apps/api"
    exit 1
fi

echo -e "${GREEN}✓${NC} Found package.json"

# Step 2: Install dependencies
echo ""
echo "📦 Step 1: Installing dependencies..."
echo "-----------------------------------"
pnpm install

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓${NC} Dependencies installed"
else
    echo -e "${RED}❌ Failed to install dependencies${NC}"
    exit 1
fi

# Step 3: Generate Prisma Client
echo ""
echo "🔧 Step 2: Generating Prisma Client..."
echo "-------------------------------------"
npx prisma generate

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓${NC} Prisma Client generated"
else
    echo -e "${RED}❌ Failed to generate Prisma Client${NC}"
    exit 1
fi

# Step 4: Run database migration/push
echo ""
echo "🗄️  Step 3: Updating database schema..."
echo "--------------------------------------"
echo -e "${YELLOW}Choose migration method:${NC}"
echo "1) prisma migrate dev (creates migration file)"
echo "2) prisma db push (quick schema push, no migration)"
echo ""
read -p "Enter choice (1 or 2): " choice

case $choice in
    1)
        npx prisma migrate dev --name add_canvas_versioning
        ;;
    2)
        npm run db:push
        ;;
    *)
        echo -e "${RED}Invalid choice. Using db:push...${NC}"
        npm run db:push
        ;;
esac

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓${NC} Database schema updated"
else
    echo -e "${RED}❌ Failed to update database schema${NC}"
    echo ""
    echo "Common issues:"
    echo "  - Database not running: Start PostgreSQL"
    echo "  - Connection refused: Check DATABASE_URL in .env.local"
    echo "  - Schema conflicts: Try 'prisma migrate reset' (⚠️  destroys data)"
    exit 1
fi

# Step 5: Build the API
echo ""
echo "🏗️  Step 4: Building API server..."
echo "--------------------------------"
npm run build

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓${NC} API server built successfully"
else
    echo -e "${RED}❌ Build failed${NC}"
    exit 1
fi

# Step 6: Verify build
echo ""
echo "✅ Step 5: Verifying setup..."
echo "----------------------------"

# Check if dist directory exists
if [ -d "dist" ]; then
    echo -e "${GREEN}✓${NC} Build output found (dist/)"
else
    echo -e "${RED}❌ Build output not found${NC}"
    exit 1
fi

# Check if required files exist
if [ -f "dist/index.js" ]; then
    echo -e "${GREEN}✓${NC} Main entry point exists (dist/index.js)"
else
    echo -e "${RED}❌ Main entry point missing${NC}"
    exit 1
fi

# Check if WebSocket service exists
if [ -f "src/services/canvasCollaboration.ts" ]; then
    echo -e "${GREEN}✓${NC} WebSocket service found"
else
    echo -e "${YELLOW}⚠${NC}  WebSocket service not found (expected at src/services/canvasCollaboration.ts)"
fi

# Check if canvas routes updated
if grep -q "CanvasVersion" prisma/schema.prisma; then
    echo -e "${GREEN}✓${NC} CanvasVersion model exists in schema"
else
    echo -e "${RED}❌ CanvasVersion model not found in schema${NC}"
    exit 1
fi

echo ""
echo "=============================================="
echo -e "${GREEN}🎉 Setup Complete!${NC}"
echo "=============================================="
echo ""
echo "Next steps:"
echo ""
echo "1. Start the API server:"
echo "   ${GREEN}npm run dev${NC}"
echo ""
echo "2. The server will be available at:"
echo "   - API: ${GREEN}http://localhost:7003${NC}"
echo "   - GraphQL: ${GREEN}http://localhost:7003/graphql${NC}"
echo "   - WebSocket: ${GREEN}ws://localhost:7003/canvas/:projectId${NC}"
echo ""
echo "3. Start the web app (in another terminal):"
echo "   ${GREEN}cd ../web && pnpm dev${NC}"
echo ""
echo "4. Open the unified canvas:"
echo "   ${GREEN}http://localhost:3000/p/YOUR_PROJECT_ID/unified-canvas${NC}"
echo ""
echo "5. Test collaboration:"
echo "   - Open the same URL in 2 browser windows"
echo "   - Check console for WebSocket connection messages"
echo "   - Move mouse and see cursor updates in other window"
echo ""
echo "6. View database with Prisma Studio:"
echo "   ${GREEN}npm run db:studio${NC}"
echo ""
echo "📚 Full documentation: ../BACKEND_INTEGRATION_COMPLETE.md"
echo ""
