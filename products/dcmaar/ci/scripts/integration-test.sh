#!/usr/bin/env bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
echo "Delegating integration test to services/extension/scripts/integration-test.sh"
exec "$ROOT_DIR/services/extension/scripts/integration-test.sh" "$@"