#!/usr/bin/env bash
set -euo pipefail

# Orchestrator for local end-to-end pipeline.
# Usage: ./ops/scripts/e2e-runner.sh [--skip-install] [--skip-build] [--skip-agent]

BASE_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
LOG_DIR="${BASE_DIR}/logs/e2e"
mkdir -p "${LOG_DIR}"

SKIP_INSTALL=0
SKIP_BUILD=0
SKIP_AGENT=0

for arg in "$@"; do
  case "$arg" in
    --skip-install) SKIP_INSTALL=1 ;;
    --skip-build) SKIP_BUILD=1 ;;
    --skip-agent) SKIP_AGENT=1 ;;
    -h|--help) echo "Usage: $(basename "$0") [--skip-install] [--skip-build] [--skip-agent]"; exit 0 ;;
  esac
done

echo "E2E runner starting. Logs -> ${LOG_DIR}"

cleanup() {
  echo "E2E runner cleaning up..."
  if [ -n "${MOCK_PID:-}" ]; then
    echo "Stopping mock server pid=${MOCK_PID}" || true
    kill ${MOCK_PID} 2>/dev/null || true
  fi
  if [ -n "${AGENT_PID:-}" ]; then
    echo "Stopping agent pid=${AGENT_PID}" || true
    kill ${AGENT_PID} 2>/dev/null || true
  fi
}
trap cleanup EXIT

# 1) Prepare: safely move repo node_modules if present (reversible)
if [ -d "${BASE_DIR}/node_modules" ]; then
  echo "Moving existing ${BASE_DIR}/node_modules -> ${BASE_DIR}/node_modules.yappc.bak"
  mv "${BASE_DIR}/node_modules" "${BASE_DIR}/node_modules.yappc.bak"
fi

export NPM_CONFIG_USERCONFIG="${BASE_DIR}/.npmrc"

if [ "$SKIP_INSTALL" -eq 0 ]; then
  echo "Installing JS deps (pnpm)" | tee "${LOG_DIR}/install.log"
  # Try to activate the pnpm version pinned in .npmrc, fall back to latest if tag not found
  PINNED_PNPM=$(awk -F= '/package-manager/ {print $2; exit}' "${BASE_DIR}/.npmrc" 2>/dev/null || echo "")
  if [ -n "${PINNED_PNPM}" ]; then
    echo "Attempting corepack prepare ${PINNED_PNPM} --activate" | tee -a "${LOG_DIR}/install.log"
    if ! corepack prepare "${PINNED_PNPM}" --activate 2>&1 | tee -a "${LOG_DIR}/install.log"; then
      echo "corepack prepare ${PINNED_PNPM} failed; falling back to latest" | tee -a "${LOG_DIR}/install.log"
      corepack prepare pnpm@latest --activate 2>&1 | tee -a "${LOG_DIR}/install.log" || echo "corepack prepare pnpm@latest failed; continuing if pnpm already installed" | tee -a "${LOG_DIR}/install.log"
    fi
  else
    echo "No pinned pnpm version found in .npmrc; preparing pnpm@latest" | tee -a "${LOG_DIR}/install.log"
    corepack prepare pnpm@latest --activate 2>&1 | tee -a "${LOG_DIR}/install.log" || echo "corepack prepare pnpm@latest failed; continuing if pnpm already installed" | tee -a "${LOG_DIR}/install.log"
  fi
  NPM_CONFIG_USERCONFIG="${BASE_DIR}/.npmrc" pnpm install --no-frozen-lockfile -C "${BASE_DIR}" --reporter=append-only 2>&1 | tee -a "${LOG_DIR}/install.log"
else
  echo "--skip-install set; skipping pnpm install" | tee "${LOG_DIR}/install.log"
fi

if [ "$SKIP_BUILD" -eq 0 ]; then
  echo "Building Rust agent (release)" | tee "${LOG_DIR}/build-rust.log"
  if [ -f "${BASE_DIR}/apps/agent/Cargo.toml" ]; then
    (cd "${BASE_DIR}/apps/agent" && cargo build --release) 2>&1 | tee -a "${LOG_DIR}/build-rust.log" || echo "cargo build had failures; check ${LOG_DIR}/build-rust.log"
  else
    echo "No Rust agent found at apps/agent; skipping" | tee -a "${LOG_DIR}/build-rust.log"
  fi

  echo "Building desktop and extension" | tee "${LOG_DIR}/build-js.log"
  if [ -f "${BASE_DIR}/apps/desktop/package.json" ]; then
    pnpm -C "${BASE_DIR}/apps/desktop" build 2>&1 | tee -a "${LOG_DIR}/build-js.log" || echo "desktop build failed; check ${LOG_DIR}/build-js.log"
  fi
  if [ -f "${BASE_DIR}/apps/extension/package.json" ]; then
    pnpm -C "${BASE_DIR}/apps/extension" build 2>&1 | tee -a "${LOG_DIR}/build-js.log" || echo "extension build failed; check ${LOG_DIR}/build-js.log"
  fi
else
  echo "--skip-build set; skipping builds" | tee "${LOG_DIR}/build-js.log"
fi

# 2) Start mock-config-server (node) in background
echo "Starting mock-config-server (node)" | tee "${LOG_DIR}/mock-start.log"
NODE_PATH="${BASE_DIR}/apps/desktop/node_modules" PORT=9001 node "${BASE_DIR}/ops/tools/mock-config-server/index.js" > "${LOG_DIR}/mock-server.log" 2>&1 &
MOCK_PID=$!
echo "mock pid=${MOCK_PID}" | tee -a "${LOG_DIR}/mock-start.log"

# Optionally start agent (best-effort)
if [ "$SKIP_AGENT" -eq 0 ] && [ -f "${BASE_DIR}/apps/agent/Cargo.toml" ]; then
  echo "Attempting to start the agent (cargo run --release)" | tee "${LOG_DIR}/agent-start.log"
  (cd "${BASE_DIR}/apps/agent" && RUST_BACKTRACE=1 cargo run --release) > "${LOG_DIR}/agent.log" 2>&1 &
  AGENT_PID=$!
  echo "agent pid=${AGENT_PID}" | tee -a "${LOG_DIR}/agent-start.log"
else
  echo "Agent start skipped (SKIP_AGENT set or no agent)" | tee "${LOG_DIR}/agent-start.log"
fi

# 3) Wait for /config
BASE_URL="http://127.0.0.1:9001"
CONFIG_URL="${BASE_URL}/config"
for i in {1..60}; do
  if curl -sSf "$CONFIG_URL" >/dev/null 2>&1; then
    echo "Mock server responded on ${CONFIG_URL}" | tee -a "${LOG_DIR}/mock-start.log"
    break
  fi
  sleep 0.5
done

if ! curl -sS "$CONFIG_URL" > "${LOG_DIR}/mock-config.log" 2>&1; then
  echo "Failed to fetch $CONFIG_URL; see ${LOG_DIR}/mock-server.log" | tee -a "${LOG_DIR}/mock-start.log"
  exit 2
fi

# 4) Run existing smoke test
echo "Running smoke test" | tee "${LOG_DIR}/smoke.log"
"${BASE_DIR}/ops/scripts/smoke-mock.sh" 9001 2>&1 | tee -a "${LOG_DIR}/smoke.log" || echo "smoke test returned non-zero status; examine logs"

# 5) If Playwright tests present, run them (best-effort)
if [ -d "${BASE_DIR}/apps/desktop/test" ] || [ -d "${BASE_DIR}/apps/desktop/e2e" ]; then
  echo "Running Playwright tests (if configured)" | tee "${LOG_DIR}/playwright.log"
  if command -v pnpm >/dev/null 2>&1; then
    pnpm -C "${BASE_DIR}/apps/desktop" exec -- playwright test --reporter=list 2>&1 | tee -a "${LOG_DIR}/playwright.log" || echo "Playwright tests failed or not configured"
  else
    echo "pnpm not available to run Playwright tests" | tee -a "${LOG_DIR}/playwright.log"
  fi
else
  echo "No Playwright tests detected; skipping" | tee "${LOG_DIR}/playwright.log"
fi

echo "E2E runner completed. Logs available in ${LOG_DIR}"
exit 0
