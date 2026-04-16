#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PRODUCT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${PRODUCT_ROOT}/../.." && pwd)"
UI_DIR="${PRODUCT_ROOT}/ui"

DATACLOUD_PROFILE="${DATACLOUD_PROFILE:-local}"
DATACLOUD_HTTP_ENABLED="${DATACLOUD_HTTP_ENABLED:-true}"
DATACLOUD_HTTP_PORT="${DATACLOUD_HTTP_PORT:-8082}"
DATACLOUD_UI_PORT="${DATACLOUD_UI_PORT:-5173}"
DATACLOUD_UI_HOST="${DATACLOUD_UI_HOST:-localhost}"
DATACLOUD_TENANT_ID="${DATACLOUD_TENANT_ID:-local-dev}"

UI_ORIGIN="http://${DATACLOUD_UI_HOST}:${DATACLOUD_UI_PORT}"
API_BASE_URL="http://localhost:${DATACLOUD_HTTP_PORT}/api/v1"
WS_BASE_URL="ws://localhost:${DATACLOUD_HTTP_PORT}/ws"

BACKEND_PID=""
UI_PID=""

require_command() {
  local command_name="$1"
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Missing required command: ${command_name}" >&2
    exit 1
  fi
}

is_port_in_use() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1
    return $?
  fi

  if command -v nc >/dev/null 2>&1; then
    nc -z localhost "$port" >/dev/null 2>&1
    return $?
  fi

  return 1
}

next_available_port() {
  local requested_port="$1"
  local port="$requested_port"

  while is_port_in_use "$port"; do
    port=$((port + 1))
  done

  printf '%s\n' "$port"
}

stop_job() {
  local pid="${1:-}"
  if [[ -z "$pid" ]]; then
    return 0
  fi

  pkill -TERM -P "$pid" >/dev/null 2>&1 || true
  kill -TERM "$pid" >/dev/null 2>&1 || true
  wait "$pid" 2>/dev/null || true
}

cleanup() {
  local exit_code="$?"
  trap - EXIT INT TERM
  stop_job "$UI_PID"
  stop_job "$BACKEND_PID"
  exit "$exit_code"
}

wait_for_child_exit() {
  while true; do
    if [[ -n "$BACKEND_PID" ]] && ! kill -0 "$BACKEND_PID" >/dev/null 2>&1; then
      wait "$BACKEND_PID"
      return $?
    fi

    if [[ -n "$UI_PID" ]] && ! kill -0 "$UI_PID" >/dev/null 2>&1; then
      wait "$UI_PID"
      return $?
    fi

    sleep 2
  done
}

require_command pnpm
require_command sed

DATACLOUD_HTTP_PORT="$(next_available_port "$DATACLOUD_HTTP_PORT")"
DATACLOUD_UI_PORT="$(next_available_port "$DATACLOUD_UI_PORT")"

UI_ORIGIN="http://${DATACLOUD_UI_HOST}:${DATACLOUD_UI_PORT}"
API_BASE_URL="http://localhost:${DATACLOUD_HTTP_PORT}/api/v1"
WS_BASE_URL="ws://localhost:${DATACLOUD_HTTP_PORT}/ws"

if [[ ! -x "${REPO_ROOT}/gradlew" ]]; then
  echo "Expected executable Gradle wrapper at ${REPO_ROOT}/gradlew" >&2
  exit 1
fi

if [[ ! -d "$UI_DIR" ]]; then
  echo "Expected UI workspace at ${UI_DIR}" >&2
  exit 1
fi

trap cleanup EXIT INT TERM

echo "Starting Data Cloud local stack"
echo "  profile: ${DATACLOUD_PROFILE}"
echo "  backend: http://localhost:${DATACLOUD_HTTP_PORT}"
echo "  ui:      ${UI_ORIGIN}"
echo "  tenant:  ${DATACLOUD_TENANT_ID}"
echo ""
echo "Press Ctrl+C to stop both processes."
echo ""

(
  cd "$REPO_ROOT" || exit 1
  env \
    DATACLOUD_PROFILE="$DATACLOUD_PROFILE" \
    DATACLOUD_HTTP_ENABLED="$DATACLOUD_HTTP_ENABLED" \
    DATACLOUD_HTTP_PORT="$DATACLOUD_HTTP_PORT" \
    DATACLOUD_CORS_ALLOWED_ORIGINS="$UI_ORIGIN" \
    ./gradlew :products:data-cloud:launcher:runLauncher 2>&1 | sed 's/^/[data-cloud-backend] /'
) &
BACKEND_PID=$!

(
  cd "$REPO_ROOT" || exit 1
  env \
    VITE_API_URL="$API_BASE_URL" \
    VITE_API_BASE_URL="$API_BASE_URL" \
    VITE_WS_URL="$WS_BASE_URL" \
    VITE_USE_MSW=false \
    VITE_USE_MOCK_DATA=false \
    DATACLOUD_TENANT_ID="$DATACLOUD_TENANT_ID" \
    pnpm --dir "$UI_DIR" dev -- --host "$DATACLOUD_UI_HOST" --port "$DATACLOUD_UI_PORT" --strictPort 2>&1 | sed 's/^/[data-cloud-ui] /'
) &
UI_PID=$!

wait_for_child_exit