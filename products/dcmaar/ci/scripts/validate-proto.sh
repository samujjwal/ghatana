#!/usr/bin/env bash
set -euo pipefail

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROTO_DIR="${ROOT_DIR}/proto"
BUF_CFG="${PROTO_DIR}/buf.yaml"

buf_cmd() {
  if command -v npx >/dev/null 2>&1; then
    npx --yes @bufbuild/buf "$@"
  else
    buf "$@"
  fi
}

echo -e "${GREEN}🔍 Using config: ${BUF_CFG}${NC}"
cd "${PROTO_DIR}"

# Lint proto files
echo -e "${GREEN}🔍 Linting proto files...${NC}"
buf_cmd mod update
buf_cmd lint

# Check for breaking changes against local main
echo -e "${GREEN}🔍 Checking for breaking changes...${NC}"
if git rev-parse --verify main >/dev/null 2>&1; then
  buf_cmd breaking --against '.git#branch=main'
else
  echo -e "${YELLOW}⚠️  Main branch not found, skipping breaking change detection${NC}"
fi

# Regenerate code and ensure no diffs
echo -e "${GREEN}🔄 Regenerating code (make proto)...${NC}"
make proto
if ! git diff --quiet; then
  echo -e "${RED}❌ Generated code is not up to date. Run 'make proto' and commit changes.${NC}"
  git status
  exit 1
fi

echo -e "${GREEN}✅ Proto validation passed${NC}"
