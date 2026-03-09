#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
echo "Delegating production build to services/extension/scripts/build-production.sh"
node "$ROOT_DIR/services/extension/scripts/build-production.sh" "$@"