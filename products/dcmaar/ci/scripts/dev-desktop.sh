#!/usr/bin/env bash
set -euo pipefail

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"

# Simple helper to start the mock-config-server and then run the desktop dev server.
# Usage: ./scripts/dev-desktop.sh [PORT]

PORT=${1:-9001}
LOGS_DIR="${PROJECT_ROOT}/logs"
MOCK_SERVER="${PROJECT_ROOT}/dcmaar/ops/tools/mock-config-server/index.js"

mkdir -p "${LOGS_DIR}"
echo "Starting mock-config-server on port ${PORT} (logs -> ${LOGS_DIR}/mock-server.log)"
(cd "${PROJECT_ROOT}" && \
 NODE_PATH="${PROJECT_ROOT}/dcmaar/services/desktop/node_modules" \
 PORT=${PORT} \
 node "${MOCK_SERVER}" > "${LOGS_DIR}/mock-server.log" 2>&1) &
MOCK_PID=$!
echo "mock-config-server pid=${MOCK_PID}"

function cleanup() {
  echo "Stopping mock-config-server pid=${MOCK_PID}"
  kill ${MOCK_PID} 2>/dev/null || true
}

trap cleanup EXIT

echo "Starting desktop dev server (vite)"
# Use helper to run pnpm from repo root so filters resolve correctly
bash "${SCRIPT_DIR}/../../../../scripts/run-pnpm-from-root.sh" --filter "./products/dcmaar/services/desktop" run dev
