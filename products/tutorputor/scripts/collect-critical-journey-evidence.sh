#!/usr/bin/env bash
set -euo pipefail

ENVIRONMENT="${1:-local}"
OUTPUT_DIR="docs/operations"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
OUTPUT_PATH="$OUTPUT_DIR/critical-journey-$ENVIRONMENT-$TIMESTAMP.log"

echo "Collecting critical-journey evidence for environment '$ENVIRONMENT'"
corepack pnpm playwright test tests/e2e --reporter=line 2>&1 | tee "$OUTPUT_PATH"

echo "Evidence log written to $OUTPUT_PATH"
