#!/usr/bin/env bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
echo "Delegating validate-production to services/extension/scripts/validate-production.sh"
exec "$ROOT_DIR/services/extension/scripts/validate-production.sh" "$@"