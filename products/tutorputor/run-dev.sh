#!/usr/bin/env bash

set -euo pipefail

# Disable Corepack auto-pin prompts
export COREPACK_ENABLE_AUTO_PIN=0

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

LOG_DIR="${SCRIPT_DIR}/.logs"
mkdir -p "${LOG_DIR}"

TS="$(date +%Y%m%d-%H%M%S)"
LOG_FILE="${LOG_DIR}/tutorputor-dev-${TS}.log"

QUIET_CONSOLE="${QUIET_CONSOLE:-true}"
TUTORPUTOR_START_APPS="${TUTORPUTOR_START_APPS:-true}"
TUTORPUTOR_START_SERVICES="${TUTORPUTOR_START_SERVICES:-true}"
TUTORPUTOR_ENABLE_MONITORING="${TUTORPUTOR_ENABLE_MONITORING:-false}"
TUTORPUTOR_ENABLE_KAFKA="${TUTORPUTOR_ENABLE_KAFKA:-false}"
TUTORPUTOR_ENABLE_OBJECT_STORAGE="${TUTORPUTOR_ENABLE_OBJECT_STORAGE:-true}"
TUTORPUTOR_ENABLE_QUEUE="${TUTORPUTOR_ENABLE_QUEUE:-false}"
TUTORPUTOR_ENABLE_SEARCH="${TUTORPUTOR_ENABLE_SEARCH:-false}"

PORT_API_GATEWAY="${PORT_API_GATEWAY:-3200}"
PORT_WEB="${PORT_WEB:-3201}"
PORT_ADMIN="${PORT_ADMIN:-3202}"

log() {
  echo "$@"
}

log_verbose() {
  printf "%s %s\n" "$(date -Iseconds)" "$*" >>"${LOG_FILE}"
}

run_logged() {
  local title="$1"
  shift

  log_verbose "[run] ${title}: $*"

  if [ "${QUIET_CONSOLE}" = "true" ]; then
    ("$@") >>"${LOG_FILE}" 2>&1
  else
    ("$@") 2>&1 | tee -a "${LOG_FILE}"
  fi
}

LAST_BG_PID=""
run_bg_logged() {
  local title="$1"
  shift

  log_verbose "[bg] ${title}: $*"

  ("$@") >>"${LOG_FILE}" 2>&1 &
  LAST_BG_PID=$!
}

pid_is_running() {
  local pid="$1"
  kill -0 "${pid}" >/dev/null 2>&1
}

has_cmd() {
  command -v "$1" >/dev/null 2>&1
}

docker_compose_cmd() {
  if docker compose version >/dev/null 2>&1; then
    echo "docker compose"
  else
    echo "docker-compose"
  fi
}

wait_for_container_healthy() {
  local container_name="$1"
  local max_wait_seconds="$2"
  local waited=0

  while [ $waited -lt $max_wait_seconds ]; do
    if docker ps --filter "name=${container_name}" --filter "health=healthy" 2>/dev/null | grep -q "${container_name}"; then
      return 0
    fi
    sleep 2
    waited=$((waited + 2))
  done

  return 1
}

PIDS=()
cleanup() {
  if [ "${#PIDS[@]:-0}" -gt 0 ]; then
    log "Stopping dev processes"
    for pid in "${PIDS[@]}"; do
      if pid_is_running "${pid}"; then
        kill "${pid}" >/dev/null 2>&1 || true
      fi
    done
  fi
}
trap cleanup EXIT INT TERM

log "Starting TutorPutor dev environment"
log "Logs: ${LOG_FILE}"
log ""
log "Env: TUTORPUTOR_START_APPS=${TUTORPUTOR_START_APPS} TUTORPUTOR_START_SERVICES=${TUTORPUTOR_START_SERVICES}"
log "Env: TUTORPUTOR_ENABLE_OBJECT_STORAGE=${TUTORPUTOR_ENABLE_OBJECT_STORAGE} TUTORPUTOR_ENABLE_QUEUE=${TUTORPUTOR_ENABLE_QUEUE} TUTORPUTOR_ENABLE_SEARCH=${TUTORPUTOR_ENABLE_SEARCH}"
log "Env: TUTORPUTOR_ENABLE_MONITORING=${TUTORPUTOR_ENABLE_MONITORING} TUTORPUTOR_ENABLE_KAFKA=${TUTORPUTOR_ENABLE_KAFKA}"
log ""

# AI Service Configuration Check
log "Checking AI Service Configuration..."
if [ -z "${OPENAI_API_KEY:-}" ]; then
  log "⚠ WARNING: OPENAI_API_KEY is not set"
  log "  The AI service will use web search fallback instead of OpenAI"
  log "  For full AI capabilities, set: export OPENAI_API_KEY=sk-your-api-key"
  log ""
else
  log "✓ OPENAI_API_KEY is configured"
fi

# Web Search Configuration Check
WEB_SEARCH_API="${WEB_SEARCH_API:-duckduckgo}"
log "✓ Web Search fallback enabled (provider: ${WEB_SEARCH_API})"

# Ollama Configuration Check (optional local LLM)
USE_OLLAMA="${USE_OLLAMA:-true}"
OLLAMA_BASE_URL="${OLLAMA_BASE_URL:-http://localhost:11434}"
OLLAMA_MODEL="${OLLAMA_MODEL:-llama3.2:latest}"

if [ "${USE_OLLAMA}" = "true" ]; then
  log "Ollama configuration:"
  log "  Base URL: ${OLLAMA_BASE_URL}"
  log "  Model: ${OLLAMA_MODEL}"
  log "  To use Ollama, ensure it's running: ollama serve"
  log "  Or run in Docker: docker run -d --name ollama -p 11434:11434 ollama/ollama"
  log ""
else
  log "ℹ Ollama disabled (set USE_OLLAMA=true to enable local LLM)"
fi

log ""

if ! has_cmd docker; then
  log "ERROR: docker is not installed"
  exit 1
fi

if ! docker ps >/dev/null 2>&1; then
  log "ERROR: docker is not running or you don't have permissions"
  exit 1
fi

DOCKER_COMPOSE_CMD="$(docker_compose_cmd)"

SERVICES=(postgres redis)

if [ "${TUTORPUTOR_ENABLE_OBJECT_STORAGE}" = "true" ]; then
  SERVICES+=(minio)
fi

if [ "${TUTORPUTOR_ENABLE_QUEUE}" = "true" ]; then
  SERVICES+=(rabbitmq)
fi

if [ "${TUTORPUTOR_ENABLE_SEARCH}" = "true" ]; then
  SERVICES+=(elasticsearch)
fi

if [ "${TUTORPUTOR_ENABLE_MONITORING}" = "true" ]; then
  SERVICES+=(prometheus grafana jaeger)
fi

if [ "${TUTORPUTOR_ENABLE_KAFKA}" = "true" ]; then
  SERVICES+=(zookeeper kafka kafka-ui)
fi

log "Checking shared containers (${SERVICES[*]})"

# Function to check if a container is running
is_container_running() {
  local container_name="$1"
  docker ps 2>/dev/null | grep -q "$container_name"
}

# Check which services need to be started
SERVICES_TO_START=()

for service in "${SERVICES[@]}"; do
  container_name="ghatana-${service}"
  if is_container_running "$container_name"; then
    log "✓ $service is already running - reusing it"
  else
    log "$service not running, will start it"
    SERVICES_TO_START+=("$service")
  fi
done

# Start only the services that aren't running
if [ "${#SERVICES_TO_START[@]:-0}" -gt 0 ]; then
  log "Starting missing services: ${SERVICES_TO_START[*]}"
  (
    cd "${ROOT_DIR}"
    run_logged "docker compose up (shared)" ${DOCKER_COMPOSE_CMD} up -d "${SERVICES_TO_START[@]}"
  )
  
  # Wait for services to be healthy
  for service in "${SERVICES_TO_START[@]}"; do
    container_name="ghatana-${service}"
    case "$service" in
      postgres|redis)
        if ! wait_for_container_healthy "$container_name" 30; then
          log "WARN: $container_name did not become healthy within 30s (continuing)"
        fi
        ;;
      minio)
        if ! wait_for_container_healthy "$container_name" 60; then
          log "WARN: $container_name did not become healthy within 60s (continuing)"
        fi
        ;;
      rabbitmq)
        if ! wait_for_container_healthy "$container_name" 60; then
          log "WARN: $container_name did not become healthy within 60s (continuing)"
        fi
        ;;
      elasticsearch)
        if ! wait_for_container_healthy "$container_name" 90; then
          log "WARN: $container_name did not become healthy within 90s (continuing)"
        fi
        ;;
      *)
        if ! wait_for_container_healthy "$container_name" 30; then
          log "WARN: $container_name did not become healthy within 30s (continuing)"
        fi
        ;;
    esac
  done
else
  log "✓ All required shared services are already running"
fi

if ! has_cmd pnpm; then
  log "ERROR: pnpm is not installed"
  exit 1
fi

# Seed database
if [ "${TUTORPUTOR_SKIP_SEED:-false}" = "true" ]; then
  log "Skipping database seed (TUTORPUTOR_SKIP_SEED=true)"
else
  log "Seeding database..."
  (
    cd "${SCRIPT_DIR}/services/tutorputor-db"

    TUTORPUTOR_SEED_PROFILE="${TUTORPUTOR_SEED_PROFILE:-demo}"
    log "  - Running seed (prisma:seed) profile=${TUTORPUTOR_SEED_PROFILE}..."
    if TUTORPUTOR_SEED_PROFILE="${TUTORPUTOR_SEED_PROFILE}" pnpm prisma:seed >>"${LOG_FILE}" 2>&1; then
       log "    ✓ Seed completed"
    else
       log "    ✗ Seed failed (check logs)"
    fi
  )
fi

if [ "${TUTORPUTOR_START_APPS}" = "true" ]; then
  log "Building workspace libs..."
  (
    cd "${SCRIPT_DIR}"
    if pnpm -C services/tutorputor-db build >>"${LOG_FILE}" 2>&1; then
      log "  ✓ Built @tutorputor/db"
    else
      log "  ✗ Failed to build @tutorputor/db (check logs)"
    fi

    if pnpm -C contracts build >>"${LOG_FILE}" 2>&1; then
      log "  ✓ Built @tutorputor/contracts"
    else
      log "  ✗ Failed to build @tutorputor/contracts (check logs)"
    fi

    if pnpm -C libs/learning-kernel build >>"${LOG_FILE}" 2>&1; then
      log "  ✓ Built @tutorputor/learning-kernel"
    else
      log "  ✗ Failed to build @tutorputor/learning-kernel (check logs)"
    fi

    # Build all Ghatana shared libraries using workspace commands
    log "  Building Ghatana shared libraries..."
    cd "${ROOT_DIR}"
    if pnpm build:deps >>"${LOG_FILE}" 2>&1; then
      log "  ✓ Built Ghatana dependencies (@ghatana/tokens, @ghatana/ui, @ghatana/theme)"
    else
      log "  ✗ Failed to build Ghatana dependencies (check logs)"
    fi

    # Build additional libraries needed for admin UI
    if pnpm --filter "@ghatana/utils" build >>"${LOG_FILE}" 2>&1; then
      log "  ✓ Built @ghatana/utils"
    else
      log "  ✗ Failed to build @ghatana/utils (check logs)"
    fi

    if pnpm --filter "@ghatana/charts" build >>"${LOG_FILE}" 2>&1; then
      log "  ✓ Built @ghatana/charts"
    else
      log "  ✗ Failed to build @ghatana/charts (check logs)"
    fi
  )

  log "Starting API Gateway"
  run_bg_logged "tutorputor api-gateway" bash -lc "cd \"${SCRIPT_DIR}/apps/api-gateway\" && PORT=${PORT_API_GATEWAY} pnpm dev"
  PIDS+=("${LAST_BG_PID}")

  log "Starting Web"
  run_bg_logged "tutorputor web" bash -lc "cd \"${SCRIPT_DIR}/apps/tutorputor-web\" && PORT=${PORT_WEB} pnpm dev"
  PIDS+=("${LAST_BG_PID}")

  log "Starting Admin"
  run_bg_logged "tutorputor admin" bash -lc "cd \"${SCRIPT_DIR}/apps/tutorputor-admin\" && PORT=${PORT_ADMIN} pnpm dev"
  PIDS+=("${LAST_BG_PID}")

  log "URLs:"
  log "- API Gateway: http://localhost:${PORT_API_GATEWAY}/health"
  log "- Web:        http://localhost:${PORT_WEB}"
  log "- Admin:      http://localhost:${PORT_ADMIN}"
fi

if [ "${TUTORPUTOR_START_SERVICES}" = "true" ]; then
  log "Starting backend services..."
  for svc in tutorputor-ai-proxy; do
    if [ -d "${SCRIPT_DIR}/services/${svc}" ]; then
      # Only start if package.json has a "dev" script
      if grep -q '"dev":' "${SCRIPT_DIR}/services/${svc}/package.json"; then
        run_bg_logged "tutorputor service ${svc}" bash -lc "cd \"${SCRIPT_DIR}/services/${svc}\" && pnpm dev"
        PIDS+=("${LAST_BG_PID}")
        log "- ${svc}: Started"
        
        # Special handling for AI Proxy service
        if [ "${svc}" = "tutorputor-ai-proxy" ]; then
          # Export Ollama environment variables for the AI proxy
          export USE_OLLAMA="${USE_OLLAMA:-true}"
          export OLLAMA_BASE_URL="${OLLAMA_BASE_URL:-http://localhost:11434}"
          export OLLAMA_MODEL="${OLLAMA_MODEL:-llama3.2:latest}"
          
          AI_PROXY_PORT="${AI_PROXY_PORT:-3300}"
          log "  Waiting for AI Proxy service to be ready (port ${AI_PROXY_PORT})..."
          
          # Wait for AI Proxy to be healthy with timeout
          max_attempts=30
          attempt=0
          while [ $attempt -lt $max_attempts ]; do
            if curl -s "http://localhost:${AI_PROXY_PORT}/health" >/dev/null 2>&1 || \
               curl -s "http://localhost:${AI_PROXY_PORT}/" >/dev/null 2>&1; then
              log "  ✓ AI Proxy service is ready"
              
              # Check if OPENAI_API_KEY is set
              if [ -z "${OPENAI_API_KEY:-}" ]; then
                log "  ⚠ WARNING: OPENAI_API_KEY is not set"
                log "    AI service will use web search fallback (see WEB_SEARCH_DEPLOYMENT_READY.md)"
                log "    To enable full AI capabilities, set OPENAI_API_KEY environment variable"
              else
                log "  ✓ OPENAI_API_KEY is configured"
              fi
              break
            fi
            
            attempt=$((attempt + 1))
            if [ $((attempt % 5)) -eq 0 ]; then
              log "  Waiting... (attempt $attempt/$max_attempts)"
            fi
            sleep 1
          done
          
          if [ $attempt -ge $max_attempts ]; then
            log "  ✗ WARNING: AI Proxy service did not respond within timeout"
            log "    Continuing anyway, but check logs at: ${LOG_FILE}"
          fi
        fi
      else
        log_verbose "Skipping ${svc} (no dev script)"
      fi
    fi
  done
fi

log "Ready"
log "- To view logs: tail -f ${LOG_FILE}"
log "- Stop: Ctrl+C"
log ""
log "Services Status:"
log "- API Gateway: http://localhost:${PORT_API_GATEWAY}/health"
log "- Web:        http://localhost:${PORT_WEB}"
log "- Admin:      http://localhost:${PORT_ADMIN}"
if [ "${TUTORPUTOR_START_SERVICES}" = "true" ]; then
  AI_PROXY_PORT="${AI_PROXY_PORT:-3300}"
  log "- AI Proxy:   http://localhost:${AI_PROXY_PORT}/health"
fi
log ""
log "AI Service Status:"
if [ -z "${OPENAI_API_KEY:-}" ]; then
  log "⚠ Using web search fallback (OPENAI_API_KEY not set)"
  log "  See: WEB_SEARCH_DEPLOYMENT_READY.md for details"
else
  log "✓ OpenAI API Key configured"
fi
log ""

wait
