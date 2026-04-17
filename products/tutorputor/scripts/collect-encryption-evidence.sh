#!/usr/bin/env bash
set -euo pipefail

ENVIRONMENT="${1:-local}"
OUTPUT_DIR="docs/operations"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
OUTPUT_PATH="$OUTPUT_DIR/encryption-$ENVIRONMENT-$TIMESTAMP.log"

echo "Collecting encryption evidence for '$ENVIRONMENT'"
./scripts/verify-object-storage-encryption.sh 2>&1 | tee "$OUTPUT_PATH"

echo "Evidence log written to $OUTPUT_PATH"
