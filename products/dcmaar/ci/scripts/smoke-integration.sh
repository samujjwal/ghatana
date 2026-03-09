#!/usr/bin/env bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
echo "Delegating smoke integration to services/extension/scripts/smoke-integration.sh"
exec "$ROOT_DIR/services/extension/scripts/smoke-integration.sh" "$@"
