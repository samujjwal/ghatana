#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TUTORPUTOR_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
E2E_DIR="$TUTORPUTOR_ROOT/tests/e2e"
LOG_DIR="${TUTORPUTOR_E2E_LOG_DIR:-$TUTORPUTOR_ROOT/.tmp/live-e2e}"

POSTGRES_PORT="${TUTORPUTOR_POSTGRES_PORT:-55432}"
REDIS_PORT="${TUTORPUTOR_REDIS_PORT:-56379}"
PLATFORM_PORT="${TUTORPUTOR_PLATFORM_PORT:-7105}"
GATEWAY_PORT="${TUTORPUTOR_GATEWAY_PORT:-3200}"
WEB_PORT="${TUTORPUTOR_WEB_PORT:-3201}"
ADMIN_PORT="${TUTORPUTOR_ADMIN_PORT:-3202}"

JWT_SECRET="${JWT_SECRET:-test-secret-do-not-use-in-prod-1234567890}"
STRIPE_SECRET_KEY="${STRIPE_SECRET_KEY:-stripe_test_placeholder_secret}"
TRUST_PROXY_AUTH_SHARED_SECRET="${TRUST_PROXY_AUTH_SHARED_SECRET:-tutorputor-internal-dev-proxy-secret}"
GRPC_SERVER_ADDRESS="${GRPC_SERVER_ADDRESS:-127.0.0.1:50051}"

BASE_URL="${BASE_URL:-http://127.0.0.1:${WEB_PORT}}"
ADMIN_URL="${ADMIN_URL:-http://127.0.0.1:${ADMIN_PORT}}"
GATEWAY_URL="${GATEWAY_URL:-http://127.0.0.1:${GATEWAY_PORT}}"
PLATFORM_URL="${PLATFORM_URL:-http://127.0.0.1:${PLATFORM_PORT}}"

PLAYWRIGHT_SPECS=("$@")
if [[ ${#PLAYWRIGHT_SPECS[@]} -eq 0 ]]; then
  PLAYWRIGHT_SPECS=(
    "ContentStudio.spec.ts"
    "LearnerJourney.spec.ts"
    "StudentOnboarding.spec.ts"
    "EducatorWorkflow.spec.ts"
  )
fi

PIDS=()
cleanup() {
  local exit_code=$?
  if [[ ${#PIDS[@]} -gt 0 ]]; then
    echo "Stopping Tutorputor live E2E app processes..."
    kill "${PIDS[@]}" >/dev/null 2>&1 || true
    wait "${PIDS[@]}" 2>/dev/null || true
  fi
  if [[ $exit_code -ne 0 ]]; then
    echo "Live E2E run failed. Logs are available in $LOG_DIR" >&2
  fi
}
trap cleanup EXIT INT TERM

mkdir -p "$LOG_DIR"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

wait_for_http() {
  local label=$1
  local url=$2
  local attempts=${3:-60}
  local delay=${4:-2}

  for ((i = 1; i <= attempts; i++)); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      echo "Verified $label: $url"
      return 0
    fi
    sleep "$delay"
  done

  echo "Timed out waiting for $label at $url" >&2
  return 1
}

wait_for_container_health() {
  local container_name=$1
  local attempts=${2:-60}
  local delay=${3:-2}

  for ((i = 1; i <= attempts; i++)); do
    local status
    status=$(docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_name" 2>/dev/null || true)
    if [[ "$status" == "healthy" || "$status" == "running" ]]; then
      echo "Verified container $container_name: $status"
      return 0
    fi
    sleep "$delay"
  done

  echo "Timed out waiting for container $container_name health" >&2
  return 1
}

start_process() {
  local name=$1
  local cwd=$2
  local log_file=$3
  shift 3

  echo "Starting $name..."
  (
    cd "$cwd"
    env "$@"
  ) >"$log_file" 2>&1 &
  local pid=$!
  PIDS+=("$pid")
}


require_command docker
require_command curl
require_command pnpm

echo "Bringing up Tutorputor backing services on ports ${POSTGRES_PORT} and ${REDIS_PORT}..."
(
  cd "$TUTORPUTOR_ROOT"
  TUTORPUTOR_POSTGRES_PORT="$POSTGRES_PORT" TUTORPUTOR_REDIS_PORT="$REDIS_PORT" docker compose up -d postgres redis
)

echo "Waiting for backing services..."
wait_for_container_health tutorputor-postgres
wait_for_container_health tutorputor-redis

DATABASE_URL="postgresql://postgres:postgres@127.0.0.1:${POSTGRES_PORT}/tutorputor"
REDIS_URL="redis://127.0.0.1:${REDIS_PORT}"

start_process \
  "platform" \
  "$TUTORPUTOR_ROOT/services/tutorputor-platform" \
  "$LOG_DIR/platform.log" \
  PORT="$PLATFORM_PORT" \
  DATABASE_URL="$DATABASE_URL" \
  TUTORPUTOR_DATABASE_URL="$DATABASE_URL" \
  REDIS_URL="$REDIS_URL" \
  JWT_SECRET="$JWT_SECRET" \
  STRIPE_SECRET_KEY="$STRIPE_SECRET_KEY" \
  CONTENT_WORKER_ENABLED=false \
  CONTENT_QUEUE_DISABLED=true \
  GRPC_SERVER_ADDRESS="$GRPC_SERVER_ADDRESS" \
  TRUST_PROXY_AUTH_HEADERS=true \
  TRUST_PROXY_AUTH_SHARED_SECRET="$TRUST_PROXY_AUTH_SHARED_SECRET" \
  pnpm dev

start_process \
  "api-gateway" \
  "$TUTORPUTOR_ROOT/apps/api-gateway" \
  "$LOG_DIR/api-gateway.log" \
  PORT="$GATEWAY_PORT" \
  DATABASE_URL="$DATABASE_URL" \
  TUTORPUTOR_DATABASE_URL="$DATABASE_URL" \
  REDIS_URL="$REDIS_URL" \
  JWT_SECRET="$JWT_SECRET" \
  STRIPE_SECRET_KEY="$STRIPE_SECRET_KEY" \
  CONTENT_WORKER_ENABLED=false \
  CONTENT_QUEUE_DISABLED=true \
  GRPC_SERVER_ADDRESS="$GRPC_SERVER_ADDRESS" \
  TRUST_PROXY_AUTH_HEADERS=true \
  TRUST_PROXY_AUTH_SHARED_SECRET="$TRUST_PROXY_AUTH_SHARED_SECRET" \
  pnpm dev

start_process \
  "tutorputor-web" \
  "$TUTORPUTOR_ROOT/apps/tutorputor-web" \
  "$LOG_DIR/web.log" \
  VITE_API_BASE_URL="http://127.0.0.1:${GATEWAY_PORT}/api" \
  pnpm dev --host 0.0.0.0 --port "$WEB_PORT"

start_process \
  "tutorputor-admin" \
  "$TUTORPUTOR_ROOT/apps/tutorputor-admin" \
  "$LOG_DIR/admin.log" \
  VITE_API_BASE_URL="http://127.0.0.1:${GATEWAY_PORT}" \
  VITE_DEV_AUTH_BYPASS=true \
  VITE_TUTORPUTOR_TENANT_ID=default \
  VITE_TRUST_PROXY_AUTH_SHARED_SECRET="$TRUST_PROXY_AUTH_SHARED_SECRET" \
  pnpm dev --host 0.0.0.0 --port "$ADMIN_PORT"

echo "Waiting for Tutorputor apps to become healthy..."
wait_for_http "platform" "$PLATFORM_URL/health"
wait_for_http "gateway" "$GATEWAY_URL/health"
wait_for_http "learner app" "$BASE_URL/login"
wait_for_http "admin app" "$ADMIN_URL/authoring"

echo "Running Tutorputor live E2E specs: ${PLAYWRIGHT_SPECS[*]}"
(
  cd "$E2E_DIR"
  PLAYWRIGHT_SKIP_WEBSERVER=true \
  BASE_URL="$BASE_URL" \
  ADMIN_URL="$ADMIN_URL" \
  GATEWAY_URL="$GATEWAY_URL" \
  PLATFORM_URL="$PLATFORM_URL" \
  npx playwright test "${PLAYWRIGHT_SPECS[@]}"
)

echo "Tutorputor live E2E run completed successfully. Logs: $LOG_DIR"
