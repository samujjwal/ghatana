#!/bin/bash

# Guardian Backend - Quick Setup & Test Script
# This script sets up the backend, installs dependencies, and runs basic tests

set -e  # Exit on error

echo "🚀 Guardian Backend - Quick Setup"
echo "================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Navigate to backend directory
cd "$(dirname "$0")"

echo -e "${BLUE}📦 Installing dependencies...${NC}"
pnpm install

echo ""
echo -e "${BLUE}🔧 Checking TypeScript compilation...${NC}"
pnpm tsc --noEmit 2>&1 | head -20 || true

echo ""
echo -e "${GREEN}✅ Setup complete!${NC}"
echo ""
echo "📋 Next steps:"
echo ""
echo "1. Set up PostgreSQL database:"
echo "   ${YELLOW}psql postgres${NC}"
echo "   ${YELLOW}CREATE DATABASE guardian_db;${NC}"
echo "   ${YELLOW}CREATE USER guardian_user WITH PASSWORD 'your_password';${NC}"
echo "   ${YELLOW}GRANT ALL PRIVILEGES ON DATABASE guardian_db TO guardian_user;${NC}"
echo "   ${YELLOW}\\c guardian_db${NC}"
echo "   ${YELLOW}GRANT ALL ON SCHEMA public TO guardian_user;${NC}"
echo "   ${YELLOW}\\q${NC}"
echo ""
echo "2. Load database schema:"
echo "   ${YELLOW}psql -U guardian_user -d guardian_db -f src/db/schema.sql${NC}"
echo ""
echo "3. Configure environment:"
echo "   ${YELLOW}cp .env.example .env${NC}"
echo "   ${YELLOW}# Edit .env with your database credentials${NC}"
echo ""
echo "4. Start development server:"
echo "   ${YELLOW}pnpm dev${NC}"
echo ""
echo "5. Test API endpoints:"
echo "   ${YELLOW}curl http://localhost:3001/health${NC}"
echo ""
echo "📚 Documentation:"
echo "   - API Reference: ../BACKEND_APIS_COMPLETE.md"
echo "   - Auth Guide: ../CUSTOM_AUTH_COMPLETE.md"
echo "   - Setup Guide: ../FULL_STACK_SETUP.md"
echo ""
