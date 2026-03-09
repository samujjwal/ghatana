#!/bin/bash
set -euo pipefail

# Colors
GREEN='\033[0;32m'
NC='\033[0m' # No Color

echo -e "${GREEN}🚀 Generating test data...${NC}"

# Change to the project root directory
cd "$(dirname "$0")/.."

# Ensure the testdata directory exists
mkdir -p testdata

# Build the test data generator
echo -e "${GREEN}🔨 Building test data generator...${NC}"
go build -o bin/test-data-gen scripts/generate-test-data.go

# Run the test data generator
echo -e "${GREEN}📊 Generating test data...${NC}"
./bin/test-data-gen

echo -e "${GREEN}✅ Test data generated successfully!${NC}"
echo -e "📂 Test data location: $(pwd)/testdata/"

# List the generated files
ls -la testdata/
