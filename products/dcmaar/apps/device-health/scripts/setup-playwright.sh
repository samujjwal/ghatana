#!/usr/bin/env bash
# Cross-platform Playwright setup script
# This script handles Playwright browser installation without requiring sudo during pnpm install

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "======================================================"
echo "Playwright Browser Setup"
echo "======================================================"
echo ""

cd "$PROJECT_DIR"

# Check if Playwright is installed
if ! command -v npx &> /dev/null; then
    echo "Error: npx not found. Please install Node.js first."
    exit 1
fi

# Install browsers only (without system dependencies)
echo "Installing Playwright browsers (chromium, firefox, msedge)..."
npx playwright install chromium firefox msedge

echo ""
echo "======================================================"
echo "Browser Installation Complete!"
echo "======================================================"
echo ""
echo "Note: If you encounter errors during E2E tests about missing"
echo "system libraries, run this command once with sudo:"
echo ""
echo "  sudo npx playwright install-deps chromium firefox msedge"
echo ""
echo "This is only needed once per system and is optional on most systems."
echo "======================================================"
