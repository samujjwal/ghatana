#!/usr/bin/env bash
# Minimal, CI-agnostic integration test runner for the mock-config-server + desktop integration test
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "$0")/../.." && pwd)
DESKTOP_DIR="$ROOT_DIR/services/desktop"
MOCK_SCRIPT="$ROOT_DIR/tools/mock-config-server/index.js"

PORT=${PORT:-9001}

# CLI flags
NO_INSTALL=0
CI_MODE=0
for arg in "$@"; do
  case "$arg" in
    --no-install) NO_INSTALL=1 ;;
    --ci) CI_MODE=1; NO_INSTALL=1 ;;
    *) ;;
  esac
done

if [ "$CI_MODE" -eq 1 ]; then
  echo "Running in CI mode: runner will not attempt installs and expects deps to be provisioned by CI"
fi

echo "Running integration tests (port=$PORT)"

cd "$DESKTOP_DIR"

# ensure previous server stopped
pkill -f tools/mock-config-server || true || true

TS=$(date +%s)
RUN_DIR="$ROOT_DIR/tools/test-runner"
mkdir -p "$RUN_DIR"
LOGFILE="$RUN_DIR/mock-server-log.${TS}.log"
PIDFILE="$RUN_DIR/mock-server-pid.${TS}.pid"

## Prefer installing per-package first to avoid workspace pnpm lockfile issues.
if [ -d "$ROOT_DIR/node_modules/express" ]; then
  # start server from repo root so it resolves repo-level node_modules
  (cd "$ROOT_DIR" && PORT=$PORT node "$MOCK_SCRIPT") > "$LOGFILE" 2>&1 &
else
  if [ -d "$DESKTOP_DIR/node_modules/express" ]; then
    echo "Using existing services/desktop/node_modules for server deps"
    # Ensure Node can resolve modules that are installed under services/desktop/node_modules
    # even though the mock script lives under tools/. Use NODE_PATH to include the per-package node_modules.
    NODE_PATH="$DESKTOP_DIR/node_modules" PORT=$PORT node "$MOCK_SCRIPT" > "$LOGFILE" 2>&1 &
  else
    echo "Attempting per-package install in services/desktop to provision server deps"
    # Try per-package install first (faster and avoids workspace lockfile mismatches)
    if [ "$NO_INSTALL" -eq 1 ]; then
      echo "--no-install passed; skipping install step and aborting because deps not found"
      echo "Please ensure dependencies are installed before running the runner. See tools/install-deps.sh"
      touch "$RUN_DIR/fail-summary.${TS}.txt"
      echo "Missing express in node_modules and --no-install specified." > "$RUN_DIR/fail-summary.${TS}.txt"
      echo "Fail summary: $RUN_DIR/fail-summary.${TS}.txt"
      exit 1
    fi

    if command -v pnpm >/dev/null 2>&1; then
      (cd "$DESKTOP_DIR" && pnpm install --no-frozen-lockfile)
    elif command -v npm >/dev/null 2>&1; then
      (cd "$DESKTOP_DIR" && npm install)
    else
      echo "No pnpm or npm available to install per-package deps. Falling back to workspace installer"
    fi

    if [ -d "$DESKTOP_DIR/node_modules/express" ]; then
      echo "Per-package install succeeded; using services/desktop/node_modules"
      NODE_PATH="$DESKTOP_DIR/node_modules" PORT=$PORT node "$MOCK_SCRIPT" > "$LOGFILE" 2>&1 &
    else
      echo "Per-package install did not provision express; falling back to workspace installer"
      echo "Workspace root missing server deps; running tools/install-deps.sh to install workspace dependencies"
      "$ROOT_DIR/tools/install-deps.sh"
      if [ -d "$ROOT_DIR/node_modules/express" ]; then
        PORT=$PORT node "$MOCK_SCRIPT" > "$LOGFILE" 2>&1 &
      elif [ -d "$DESKTOP_DIR/node_modules/express" ]; then
        echo "Using services/desktop/node_modules for server deps"
        # record that per-package install supplied deps
        echo "PER_PACKAGE_NODE_MODULES=$DESKTOP_DIR/node_modules" > "$RUN_DIR/node_path_fallback.${TS}.txt"
        (cd "$DESKTOP_DIR" && PORT=$PORT node "$MOCK_SCRIPT") > "$LOGFILE" 2>&1 &
      else
        echo "Failed to locate express after install. Aborting."
        echo "See $LOGFILE for server startup attempts"
        touch "$RUN_DIR/fail-summary.${TS}.txt"
        echo "=== mock server log (tail) ===" > "$RUN_DIR/fail-summary.${TS}.txt"
        tail -n 200 "$LOGFILE" >> "$RUN_DIR/fail-summary.${TS}.txt" 2>/dev/null || true
        echo "Fail summary: $RUN_DIR/fail-summary.${TS}.txt"
        exit 1
      fi
    fi
  fi
fi
echo $! > "$PIDFILE"

echo "Started mock server (pid=$(cat $PIDFILE)), logging to $LOGFILE"

echo "Waiting for server readiness..."
n=0
until curl -sSf "http://localhost:${PORT}/config" > /dev/null; do
  n=$((n+1))
  if [ $n -gt 30 ]; then
    echo "mock server failed to start"
    echo "==== $LOGFILE ===="
    cat "$LOGFILE" || true
    kill $(cat "$PIDFILE") || true
    exit 1
  fi
  sleep 1
done

echo "Server ready, running integration tests"
# capture test output to a log so we can include it in the summary
TESTLOG="$RUN_DIR/mock-test-log.${TS}.log"
if npm run test:integration:external:nocov > "$TESTLOG" 2>&1; then
  echo "Integration tests passed"
  echo "Test log: $TESTLOG"
else
  echo "Integration tests failed; producing fail-summary"
  SUMMARY="$RUN_DIR/fail-summary.${TS}.txt"
  echo "=== Mock server log (tail) ===" > "$SUMMARY"
  tail -n 200 "$LOGFILE" >> "$SUMMARY" 2>/dev/null || true
  echo "" >> "$SUMMARY"
  echo "=== Jest test output (tail) ===" >> "$SUMMARY"
  tail -n 500 "$TESTLOG" >> "$SUMMARY" 2>/dev/null || true
  echo "" >> "$SUMMARY"
  echo "Fail summary: $SUMMARY"
  cat "$SUMMARY" || true
  if [ -f "$PIDFILE" ]; then
    PID_TO_KILL=$(cat "$PIDFILE")
    if ps -p "$PID_TO_KILL" > /dev/null 2>&1; then
      kill "$PID_TO_KILL" 2>/dev/null || true
    fi
  fi
  exit 1
fi

rm -f "$TESTLOG"
if [ -f "$PIDFILE" ]; then
  PID_TO_KILL=$(cat "$PIDFILE")
  if ps -p "$PID_TO_KILL" > /dev/null 2>&1; then
    kill "$PID_TO_KILL" 2>/dev/null || true
  fi
  rm -f "$PIDFILE"
fi
echo "Done"
