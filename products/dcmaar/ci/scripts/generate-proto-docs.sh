#!/bin/bash
set -euo pipefail

# Colors
GREEN='\033[0;32m'
NC='\033[0m' # No Color

PROTO_DIR="$(pwd)/proto"
DOCS_DIR="$(pwd)/docs/proto"

# Create docs directory if it doesn't exist
mkdir -p "$DOCS_DIR"

echo -e "${GREEN}🚀 Generating Protocol Buffers documentation...${NC}"

# Install buf if not already installed
if ! command -v buf &> /dev/null; then
    echo "Installing buf..."
    brew install bufbuild/buf/buf
fi

# Generate documentation
buf generate \
    --template buf.gen.doc.yaml \
    --path "$PROTO_DIR" \
    --output "$DOCS_DIR"

echo -e "${GREEN}✅ Documentation generated at $DOCS_DIR${NC}"
echo -e "${GREEN}📄 Open $DOCS_DIR/index.html in your browser to view the documentation${NC}"
