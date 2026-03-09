#!/usr/bin/env bash
#
# Guardian Product Deployment Script
# Deploys Guardian components using Docker Compose
#
# Usage:
#   ./scripts/deploy.sh [--env=ENV] [--component=COMPONENT] [--action=ACTION]
#
# Environments: development, staging, production
# Components: all, dashboard, backend, full-stack (dashboard+backend)
# Actions: up, down, restart, logs, status
#

set -euo pipefail

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PRODUCT_ROOT="$(dirname "$SCRIPT_DIR")"

# Source shared utilities
source "$SCRIPT_DIR/lib/utils.sh"
source "$SCRIPT_DIR/lib/logger.sh"

# Default values
ENV="${ENV:-development}"
COMPONENT="${COMPONENT:-all}"
ACTION="${ACTION:-up}"
DETACH="${DETACH:-true}"
BUILD="${BUILD:-false}"

# Parse command line arguments
for arg in "$@"; do
  case $arg in
    --env=*)
      ENV="${arg#*=}"
      shift
      ;;
    --component=*)
      COMPONENT="${arg#*=}"
      shift
      ;;
    --action=*)
      ACTION="${arg#*=}"
      shift
      ;;
    --no-detach)
      DETACH=false
      shift
      ;;
    --build)
      BUILD=true
      shift
      ;;
    --help)
      cat << EOF
Guardian Product Deployment Script

Usage: $0 [OPTIONS]

Options:
  --env=ENV              Deployment environment (development, staging, production)
                        Default: development
  --component=COMPONENT  Component to deploy (all, dashboard, backend, full-stack)
                        Default: all
  --action=ACTION        Action to perform (up, down, restart, logs, status, ps)
                        Default: up
  --no-detach           Run in foreground (don't detach)
  --build               Build images before deploying
  --help                Show this help message

Examples:
  $0                                          # Deploy all components (development)
  $0 --env=production --build                 # Deploy all for production with build
  $0 --component=backend --action=restart     # Restart backend service
  $0 --action=logs --component=dashboard      # View dashboard logs
  $0 --action=down                            # Stop all services

Available actions:
  up       - Start services
  down     - Stop and remove services
  restart  - Restart services
  logs     - View service logs
  status   - Show service status
  ps       - List running containers
  exec     - Execute command in container

EOF
      exit 0
      ;;
    *)
      log_error "Unknown argument: $arg"
      log_info "Use --help for usage information"
      exit 1
      ;;
  esac
done

# Verify Docker is available
if ! command_exists docker; then
  log_error "Docker is not installed or not in PATH"
  exit 1
fi

if ! command_exists docker-compose && ! docker compose version &>/dev/null; then
  log_error "Docker Compose is not installed"
  exit 1
fi

# Use 'docker compose' or 'docker-compose' based on availability
DOCKER_COMPOSE="docker compose"
if ! docker compose version &>/dev/null; then
  DOCKER_COMPOSE="docker-compose"
fi

log_header "Guardian Product Deployment"
log_info "Environment: $ENV"
log_info "Component: $COMPONENT"
log_info "Action: $ACTION"
echo ""

# Function to get compose file for environment
get_compose_files() {
  local files=()
  
  case "$ENV" in
    development)
      if [[ -f "$PRODUCT_ROOT/docker-compose.dev.yml" ]]; then
        files+=("-f" "docker-compose.dev.yml")
      else
        files+=("-f" "docker-compose.yml")
      fi
      ;;
    staging)
      files+=("-f" "docker-compose.yml")
      if [[ -f "$PRODUCT_ROOT/docker-compose.staging.yml" ]]; then
        files+=("-f" "docker-compose.staging.yml")
      fi
      ;;
    production)
      files+=("-f" "docker-compose.yml")
      if [[ -f "$PRODUCT_ROOT/docker-compose.prod.yml" ]]; then
        files+=("-f" "docker-compose.prod.yml")
      fi
      ;;
    *)
      log_error "Unknown environment: $ENV"
      exit 1
      ;;
  esac
  
  echo "${files[@]}"
}

# Function to get services based on component
get_services() {
  case "$COMPONENT" in
    all)
      echo ""  # Empty means all services
      ;;
    dashboard)
      echo "dashboard"
      ;;
    backend)
      echo "backend db redis"
      ;;
    full-stack)
      echo "dashboard backend db redis"
      ;;
    *)
      log_error "Unknown component: $COMPONENT"
      exit 1
      ;;
  esac
}

# Main deployment logic
main() {
  cd "$PRODUCT_ROOT"
  
  # Check if .env file exists
  if [[ ! -f ".env" ]] && [[ -f ".env.example" ]]; then
    log_warn ".env file not found, copying from .env.example"
    cp .env.example .env
    log_info "Please update .env with your configuration"
  fi
  
  # Get compose files and services
  local compose_files=($(get_compose_files))
  local services=$(get_services)
  
  # Build command
  local cmd=("$DOCKER_COMPOSE")
  cmd+=("${compose_files[@]}")
  
  # Execute action
  case "$ACTION" in
    up)
      log_step "Starting services..."
      
      if [[ "$BUILD" == "true" ]]; then
        log_info "Building images..."
        "${cmd[@]}" build $services
      fi
      
      if [[ "$DETACH" == "true" ]]; then
        "${cmd[@]}" up -d $services
        log_success "Services started in background"
        echo ""
        "${cmd[@]}" ps
      else
        "${cmd[@]}" up $services
      fi
      ;;
      
    down)
      log_step "Stopping services..."
      "${cmd[@]}" down
      log_success "Services stopped"
      ;;
      
    restart)
      log_step "Restarting services..."
      "${cmd[@]}" restart $services
      log_success "Services restarted"
      ;;
      
    logs)
      if [[ -z "$services" ]]; then
        "${cmd[@]}" logs -f
      else
        "${cmd[@]}" logs -f $services
      fi
      ;;
      
    status)
      log_step "Service Status"
      "${cmd[@]}" ps
      echo ""
      log_step "Docker Networks"
      docker network ls | grep guardian || log_info "No Guardian networks found"
      echo ""
      log_step "Docker Volumes"
      docker volume ls | grep guardian || log_info "No Guardian volumes found"
      ;;
      
    ps)
      "${cmd[@]}" ps
      ;;
      
    exec)
      log_error "Use: docker compose ${compose_files[*]} exec <service> <command>"
      exit 1
      ;;
      
    *)
      log_error "Unknown action: $ACTION"
      log_info "Valid actions: up, down, restart, logs, status, ps"
      exit 1
      ;;
  esac
  
  log_success "Deployment $ACTION completed"
}

# Run main function
main
