#!/bin/bash
set -euo pipefail

# Colors
GREEN='\033[0;32m'
NC='\033[0m' # No Color

echo -e "${GREEN}🧹 Cleaning up test data...${NC}"

# Change to the project root directory
cd "$(dirname "$0")/.."

# Remove test data directory
if [ -d "testdata" ]; then
    echo "Removing testdata directory..."
    rm -rf testdata
fi

# Remove generated binaries
if [ -d "bin" ]; then
    echo "Removing generated binaries..."
    rm -f bin/test-data-gen
    rm -f bin/validate-test-data
fi

echo -e "${GREEN}✅ Cleanup complete!${NC}"
