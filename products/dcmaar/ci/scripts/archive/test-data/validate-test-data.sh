#!/bin/bash
set -euo pipefail

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}🔍 Validating test data...${NC}"

# Change to the project root directory
cd "$(dirname "$0")/.."

# Ensure the testdata directory exists
if [ ! -d "testdata" ]; then
    echo -e "${RED}❌ Test data directory not found. Please generate test data first.${NC}"
    exit 1
fi

# Build the test data validator
echo -e "${GREEN}🔨 Building test data validator...${NC}"
go build -o bin/validate-test-data scripts/validate-test-data.go

# Run the test data validator
echo -e "${GREEN}🔍 Running validation...${NC}"
if ! ./bin/validate-test-data; then
    echo -e "${RED}❌ Test data validation failed${NC}"
    exit 1
fi

echo -e "${GREEN}✅ All test data is valid!${NC}"
