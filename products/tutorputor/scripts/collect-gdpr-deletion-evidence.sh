#!/usr/bin/env bash
set -euo pipefail

ENVIRONMENT="${1:-local}"
OUTPUT_DIR="docs/operations"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
OUTPUT_PATH="$OUTPUT_DIR/gdpr-delete-$ENVIRONMENT-$TIMESTAMP.log"

echo "Collecting GDPR deletion evidence for environment '$ENVIRONMENT'"
./scripts/verify-gdpr-delete-flow.sh 2>&1 | tee "$OUTPUT_PATH"

echo "Evidence log written to $OUTPUT_PATH"
