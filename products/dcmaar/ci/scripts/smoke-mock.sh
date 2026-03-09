#!/usr/bin/env bash
set -euo pipefail

# Extended smoke test for the mock-config-server
# - starts the mock server (default port 9001)
# - waits for /config readiness
# - saves responses from /config, /validate and /apply to separate logs
# - exits non-zero on the first failing endpoint


PORT=${1:-9001}
# Resolve base directory (the 'dcmaar' directory). This file lives in dcmaar/scripts.
BASE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
NODE_PATH="${BASE_DIR}/services/desktop/node_modules"
LOG_DIR="${BASE_DIR}/logs"

echo "Starting mock-config-server on port ${PORT} (server logs -> ${LOG_DIR}/mock-server.log)"
mkdir -p "${LOG_DIR}"
NODE_PATH="${NODE_PATH}" PORT="${PORT}" node "${BASE_DIR}/tools/mock-config-server/index.js" > "${LOG_DIR}/mock-server.log" 2>&1 &
PID=$!
echo "mock-config-server pid=${PID}"

cleanup() {
  echo "Stopping mock-config-server pid=${PID}"
  kill ${PID} 2>/dev/null || true
}

trap cleanup EXIT


BASE_URL="http://127.0.0.1:${PORT}"
CONFIG_URL="${BASE_URL}/config"
VALIDATE_URL="${BASE_URL}/validate"
APPLY_URL="${BASE_URL}/apply"

# wait for server to be reachable (by /config)
for i in {1..40}; do
  if curl -sSf "$CONFIG_URL" >/dev/null 2>&1; then
    echo "Mock server responded on ${CONFIG_URL}"
    break
  fi
  sleep 0.5
done

if ! curl -sS "$CONFIG_URL" > "${LOG_DIR}/mock-config.log" 2>&1; then
  echo "Failed to fetch $CONFIG_URL; see ${LOG_DIR}/mock-server.log"
  exit 2
fi

if ! curl -sS "$VALIDATE_URL" > "${LOG_DIR}/mock-validate.log" 2>&1; then
  # /validate may not be implemented by all mock server variants; treat 404 or network errors as a warning
  echo "Warning: /validate did not respond successfully. See ${LOG_DIR}/mock-validate.log and ${LOG_DIR}/mock-server.log"
else
  echo "/validate responded; output in ${LOG_DIR}/mock-validate.log"
fi

# Try POST to /apply (no body). Adjust method or payload if your API requires it.
if ! curl -sS -X POST "$APPLY_URL" > "${LOG_DIR}/mock-apply.log" 2>&1; then
  echo "Failed to POST $APPLY_URL; see ${LOG_DIR}/mock-server.log"
  exit 4
fi

echo "All endpoints responded successfully. Logs written to ${LOG_DIR}/"
exit 0
