#!/usr/bin/env bash
set -euo pipefail

OUTPUT_DIR="docs/operations"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
OUTPUT_PATH="$OUTPUT_DIR/social-validation-$TIMESTAMP.log"

echo "Collecting social validation evidence"
corepack pnpm vitest run src/modules/engagement/social/__tests__/routes.test.ts 2>&1 | tee "$OUTPUT_PATH"

echo "Evidence log written to $OUTPUT_PATH"
