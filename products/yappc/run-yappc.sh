#!/bin/bash

# =============================================================================
# YAPPC Unified Development Runner
# =============================================================================
# 
# This script provides a single command to run the complete YAPPC development
# environment with configurable embedded/external service modes.
#
# USAGE:
#   ./run-yappc.sh [options]
#
# OPTIONS:
#   --embedded              Run in embedded mode (default): AEP and data-cloud
#                           run as in-process libraries (no external services needed)
#   --external              Run with external AEP and data-cloud services
#   --aep-url URL           External AEP service URL (e.g., http://localhost:8080)
#   --data-cloud-url URL    External data-cloud URL (e.g., http://localhost:7004)
#   --skip-infra            Skip starting infrastructure (postgres, redis, minio)
#   --skip-backend          Skip starting the Java backend
#   --skip-frontend         Skip starting the frontend
#   --skip-api              Skip starting the Node.js API
#   --clean                 Kill existing processes before starting
#   --logs                  Show service logs in foreground (blocking mode)
#   --help                  Show this help message
#
# ENVIRONMENT VARIABLES:
#   AEP_MODE                'library' (embedded) or 'service' (external)
#   DATA_CLOUD_MODE         'library' (embedded) or 'service' (external)
#   AEP_SERVICE_URL         External AEP service URL
#   DATA_CLOUD_SERVICE_URL  External data-cloud service URL
#   DB_USER, DB_PASSWORD    Database credentials
#   SKIP_TESTS              Skip running tests on startup
#
# EXAMPLES:
#   # Run everything in embedded mode (default - no external services needed)
#   ./run-yappc.sh
#
#   # Use external AEP and data-cloud services
#   ./run-yappc.sh --external --aep-url http://localhost:8080 --data-cloud-url http://localhost:7004
#
#   # Run only frontend with external services
#   ./run-yappc.sh --skip-backend --skip-api --external --aep-url http://aep.example.com:8080
#
# =============================================================================

set -e

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"

# Default configuration
API_PORT=7002
WEB_PORT=7001
JAVA_BACKEND_PORT=7003
AEP_PORT=8080
DATA_CLOUD_PORT=7004

# Mode configuration (embedded = library mode by default)
MODE="embedded"
SKIP_INFRA=false
SKIP_BACKEND=false
SKIP_FRONTEND=false
SKIP_API=false
CLEAN=false
LOGS_MODE=false
EXTERNAL_AEP_URL=""
EXTERNAL_DATA_CLOUD_URL=""

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --embedded)
            MODE="embedded"
            shift
            ;;
        --external)
            MODE="external"
            shift
            ;;
        --aep-url)
            EXTERNAL_AEP_URL="$2"
            shift 2
            ;;
        --data-cloud-url)
            EXTERNAL_DATA_CLOUD_URL="$2"
            shift 2
            ;;
        --skip-infra)
            SKIP_INFRA=true
            shift
            ;;
        --skip-backend)
            SKIP_BACKEND=true
            shift
            ;;
        --skip-frontend)
            SKIP_FRONTEND=true
            shift
            ;;
        --skip-api)
            SKIP_API=true
            shift
            ;;
        --clean)
            CLEAN=true
            shift
            ;;
        --logs)
            LOGS_MODE=true
            shift
            ;;
        --help)
            sed -n '/^# ===/,/^# ===/p' "$0" | sed 's/^# //'
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            echo "Run with --help for usage information"
            exit 1
            ;;
    esac
done

# Set environment variables based on mode
if [[ "$MODE" == "embedded" ]]; then
    export AEP_MODE="library"
    export DATA_CLOUD_MODE="library"
    echo -e "${BLUE}Running in EMBEDDED mode (AEP and data-cloud as in-process libraries)${NC}"
else
    export AEP_MODE="service"
    export DATA_CLOUD_MODE="service"
    echo -e "${BLUE}Running in EXTERNAL mode (connecting to external services)${NC}"
    
    if [[ -n "$EXTERNAL_AEP_URL" ]]; then
        export AEP_SERVICE_URL="$EXTERNAL_AEP_URL"
    fi
    if [[ -n "$EXTERNAL_DATA_CLOUD_URL" ]]; then
        export DATA_CLOUD_SERVICE_URL="$EXTERNAL_DATA_CLOUD_URL"
    fi
fi

# Increase file descriptor limit for macOS/watchman
ulimit -n 65536 2>/dev/null || true

# =============================================================================
# Helper Functions
# =============================================================================

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if a port is in use
is_port_in_use() {
    local port=$1
    if command -v lsof >/dev/null 2>&1; then
        lsof -ti :$port 2>/dev/null | grep -q .
        return $?
    elif command -v netstat >/dev/null 2>&1; then
        netstat -tuln 2>/dev/null | grep -q ":$port "
        return $?
    else
        return 1
    fi
}

# Kill process on a port
kill_port() {
    local port=$1
    if command -v lsof >/dev/null 2>&1; then
        local pid=$(lsof -ti :$port 2>/dev/null)
        if [[ -n "$pid" ]]; then
            kill -9 $pid 2>/dev/null || true
            sleep 0.5
        fi
    fi
}

# Wait for a service to be ready
wait_for_service() {
    local name=$1
    local url=$2
    local timeout=${3:-60}
    local elapsed=0
    
    log_info "Waiting for $name to be ready (max ${timeout}s)..."
    while [[ $elapsed -lt $timeout ]]; do
        if curl -s "$url" >/dev/null 2>&1; then
            log_success "$name is ready at $url"
            return 0
        fi
        echo -ne "${BLUE}.${NC}"
        sleep 2
        elapsed=$((elapsed + 2))
    done
    
    log_warn "$name did not become ready within ${timeout}s"
    return 1
}

# Cleanup function
cleanup() {
    echo ""
    log_info "Shutting down all services..."
    
    # Kill all child processes
    pkill -P $$ 2>/dev/null || true
    
    # Kill specific PIDs if we tracked them
    [[ -n "$UNIFIED_PID" ]] && kill -9 $UNIFIED_PID 2>/dev/null || true
    [[ -n "$API_PID" ]] && kill -9 $API_PID 2>/dev/null || true
    [[ -n "$WEB_PID" ]] && kill -9 $WEB_PID 2>/dev/null || true
    [[ -n "$CANVAS_AI_PID" ]] && kill -9 $CANVAS_AI_PID 2>/dev/null || true
    [[ -n "$DATA_CLOUD_PID" ]] && kill -9 $DATA_CLOUD_PID 2>/dev/null || true
    
    log_success "Cleanup complete"
    exit 0
}

trap cleanup SIGINT SIGTERM EXIT

# =============================================================================
# Banner
# =============================================================================

echo ""
echo -e "${BLUE}╔══════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║                   YAPPC Development Runner                       ║${NC}"
echo -e "${BLUE}║              AI-Native Software Development Platform             ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${BLUE}Mode:${NC} $MODE"
echo -e "${BLUE}Script Dir:${NC} $SCRIPT_DIR"
echo -e "${BLUE}Repo Root:${NC} $REPO_ROOT"
echo ""

# =============================================================================
# Clean up existing processes
# =============================================================================

if [[ "$CLEAN" == true ]]; then
    log_info "Cleaning up existing processes..."
    for port in $WEB_PORT $API_PORT $JAVA_BACKEND_PORT $AEP_PORT $DATA_CLOUD_PORT 50051; do
        kill_port $port
    done
    log_success "Cleanup complete"
fi

# =============================================================================
# Check prerequisites
# =============================================================================

log_info "Checking prerequisites..."

# Check Node.js
if ! command -v node >/dev/null 2>&1; then
    log_error "Node.js is not installed"
    exit 1
fi

# Check pnpm
if ! command -v pnpm >/dev/null 2>&1; then
    log_error "pnpm is not installed. Install with: npm install -g pnpm"
    exit 1
fi

# Check Docker if needed
if [[ "$SKIP_INFRA" == false ]]; then
    if ! command -v docker >/dev/null 2>&1; then
        log_warn "Docker is not installed. Infrastructure services (postgres, redis) may not start."
    fi
fi

log_success "Prerequisites check passed"

# =============================================================================
# Start Infrastructure Services
# =============================================================================

if [[ "$SKIP_INFRA" == false ]]; then
    log_info "[1/6] Starting infrastructure services..."
    
    # Check which services are already running
    SERVICES_TO_START=()
    
    if docker ps 2>/dev/null | grep -q "ghatana-postgres"; then
        log_success "PostgreSQL is already running"
    else
        SERVICES_TO_START+=("postgres")
    fi
    
    if docker ps 2>/dev/null | grep -q "ghatana-redis"; then
        log_success "Redis is already running"
    else
        SERVICES_TO_START+=("redis")
    fi
    
    if docker ps 2>/dev/null | grep -q "ghatana-minio"; then
        log_success "MinIO is already running"
    else
        SERVICES_TO_START+=("minio")
    fi
    
    # Start missing services
    if [[ ${#SERVICES_TO_START[@]} -gt 0 ]]; then
        log_info "Starting services: ${SERVICES_TO_START[*]}"
        cd "$REPO_ROOT"
        
        # Check if there's a docker-compose at repo root or use shared-services
        if [[ -f "$REPO_ROOT/docker-compose.yml" ]]; then
            docker compose up -d "${SERVICES_TO_START[@]}" 2>/dev/null || {
                log_warn "Docker compose failed, trying shared-services..."
                cd "$REPO_ROOT/shared-services" && docker compose up -d postgres redis 2>/dev/null || true
            }
        elif [[ -f "$REPO_ROOT/shared-services/docker-compose.yml" ]]; then
            cd "$REPO_ROOT/shared-services" && docker compose up -d postgres redis 2>/dev/null || true
        fi
        
        log_info "Waiting for infrastructure to be healthy..."
        sleep 5
    else
        log_success "All infrastructure services are already running"
    fi
fi

# =============================================================================
# Start Java Backend (Unified Launcher with AEP + YAPPC)
# =============================================================================

if [[ "$SKIP_BACKEND" == false ]]; then
    log_info "[2/6] Starting Java Backend (AEP + YAPPC in unified JVM)..."
    
    # Kill any existing instances
    pkill -f "launcher-unified" 2>/dev/null || true
    sleep 1
    
    cd "$REPO_ROOT"
    
    # Check if launcher-unified exists
    if [[ -d "$REPO_ROOT/launcher-unified" ]]; then
        # Start in background
        ./gradlew :launcher-unified:run :products:yappc:backend:api:classes --console=plain > /tmp/unified-launcher.log 2>&1 &
        UNIFIED_PID=$!
        
        log_info "Unified Launcher started (PID: $UNIFIED_PID)"
        log_info "  • AEP mode:   $MODE"
        log_info "  • YAPPC API:  http://localhost:$JAVA_BACKEND_PORT"
        
        # Wait for startup
        STARTUP_TIMEOUT=60
        STARTUP_ELAPSED=0
        while [[ $STARTUP_ELAPSED -lt $STARTUP_TIMEOUT ]]; do
            if ! kill -0 $UNIFIED_PID 2>/dev/null; then
                if grep -i "error\|exception" /tmp/unified-launcher.log 2>/dev/null | tail -5 >/dev/null; then
                    log_error "Unified Launcher failed to start"
                    tail -20 /tmp/unified-launcher.log
                    exit 1
                fi
            fi
            
            if grep -q "HttpServer.*listening\|0.0.0.0:$JAVA_BACKEND_PORT" /tmp/unified-launcher.log 2>/dev/null; then
                log_success "Java Backend is ready"
                break
            fi
            
            echo -ne "${BLUE}.${NC}"
            sleep 2
            STARTUP_ELAPSED=$((STARTUP_ELAPSED + 2))
        done
        
        if [[ $STARTUP_ELAPSED -ge $STARTUP_TIMEOUT ]]; then
            log_warn "Java Backend startup timeout, continuing..."
        fi
    else
        log_warn "launcher-unified not found, skipping Java backend"
    fi
else
    log_info "[2/6] Skipping Java Backend (--skip-backend)"
fi

# =============================================================================
# Start Canvas AI Service (if available)
# =============================================================================

log_info "[3/6] Starting Canvas AI Service..."

if [[ -d "$SCRIPT_DIR/canvas-ai-service" ]]; then
    cd "$SCRIPT_DIR/canvas-ai-service"
    
    # Build if needed
    if [[ ! -d "build/install/canvas-ai-service" ]]; then
        log_info "Building Canvas AI Service..."
        cd "$REPO_ROOT"
        ./gradlew :products:yappc:canvas-ai-service:installDist --console=plain > /tmp/canvas-ai-build.log 2>&1
        if [[ $? -ne 0 ]]; then
            log_warn "Canvas AI Service build failed, continuing without it"
        fi
        cd "$SCRIPT_DIR/canvas-ai-service"
    fi
    
    # Kill existing instances
    pkill -f "canvas-ai-service" 2>/dev/null || true
    sleep 1
    
    # Start if built
    if [[ -f "build/install/canvas-ai-service/bin/canvas-ai-service" ]]; then
        build/install/canvas-ai-service/bin/canvas-ai-service > /tmp/canvas-ai-service.log 2>&1 &
        CANVAS_AI_PID=$!
        log_info "Canvas AI Service started (PID: $CANVAS_AI_PID) on port 50051"
        
        # Wait for startup
        CANVAS_AI_TIMEOUT=30
        CANVAS_AI_ELAPSED=0
        while [[ $CANVAS_AI_ELAPSED -lt $CANVAS_AI_TIMEOUT ]]; do
            if ! kill -0 $CANVAS_AI_PID 2>/dev/null; then
                log_warn "Canvas AI Service exited unexpectedly"
                break
            fi
            
            if grep -q "started successfully\|port 50051" /tmp/canvas-ai-service.log 2>/dev/null; then
                log_success "Canvas AI Service is ready"
                break
            fi
            
            echo -ne "${BLUE}.${NC}"
            sleep 2
            CANVAS_AI_ELAPSED=$((CANVAS_AI_ELAPSED + 2))
        done
    else
        log_warn "Canvas AI Service not built, skipping"
    fi
else
    log_warn "Canvas AI Service not found, skipping"
fi

# =============================================================================
# Start Node.js API
# =============================================================================

if [[ "$SKIP_API" == false ]]; then
    log_info "[4/6] Starting Node.js API Gateway on port $API_PORT..."
    
    cd "$SCRIPT_DIR/frontend/apps/api"
    
    # Set environment variables
    export DATABASE_URL="postgresql://${DB_USER:-ghatana}:${DB_PASSWORD:-ghatana123}@localhost:${DB_PORT:-5432}/${DB_NAME:-yappc_dev}?schema=public"
    export PORT=$API_PORT
    export AEP_MODE=${AEP_MODE:-library}
    export DATA_CLOUD_MODE=${DATA_CLOUD_MODE:-library}
    
    # Create/update .env
    cat > .env << EOF
DATABASE_URL=$DATABASE_URL
PORT=$API_PORT
AEP_MODE=$AEP_MODE
DATA_CLOUD_MODE=$DATA_CLOUD_MODE
JWT_ACCESS_SECRET=${JWT_ACCESS_SECRET:-dev-jwt-access-secret-change-in-production}
JWT_REFRESH_SECRET=${JWT_REFRESH_SECRET:-dev-jwt-refresh-secret-change-in-production}
EOF
    
    if [[ -n "$AEP_SERVICE_URL" ]]; then
        echo "AEP_SERVICE_URL=$AEP_SERVICE_URL" >> .env
    fi
    if [[ -n "$DATA_CLOUD_SERVICE_URL" ]]; then
        echo "DATA_CLOUD_SERVICE_URL=$DATA_CLOUD_SERVICE_URL" >> .env
    fi
    
    # Install dependencies if needed
    if [[ ! -d "node_modules" ]]; then
        log_info "Installing API dependencies..."
        pnpm install
    fi
    
    # Setup database
    if [[ -d "prisma" ]]; then
        log_info "Setting up database..."
        
        # Generate Prisma client
        log_info "Generating Prisma Client..."
        pnpm prisma generate || {
            log_error "Prisma generate failed"
            exit 1
        }
        
        # Push schema
        log_info "Syncing database schema..."
        pnpm prisma db push || {
            log_error "Database sync failed"
            exit 1
        }
        
        sleep 2
        
        # Seed if needed
        log_info "Seeding database..."
        pnpm prisma db seed || log_warn "Database seeding had issues, continuing..."
        
        log_success "Database setup complete"
    fi
    
    # Start API server
    log_info "Starting API server..."
    pnpm run dev > /tmp/yappc-api.log 2>&1 &
    API_PID=$!
    
    log_info "API Server started (PID: $API_PID)"
    
    # Wait briefly and check
    sleep 3
    if ! kill -0 $API_PID 2>/dev/null; then
        log_error "API Server failed to start"
        tail -30 /tmp/yappc-api.log
        exit 1
    fi
    
    log_success "API Server is running"
else
    log_info "[4/6] Skipping Node.js API (--skip-api)"
fi

# =============================================================================
# Start Frontend
# =============================================================================

if [[ "$SKIP_FRONTEND" == false ]]; then
    log_info "[5/6] Starting Frontend Web UI on port $WEB_PORT..."
    
    # Find the web app directory
    WEB_DIR=""
    if [[ -d "$SCRIPT_DIR/frontend/apps/web" ]]; then
        WEB_DIR="$SCRIPT_DIR/frontend/apps/web"
    elif [[ -d "$SCRIPT_DIR/frontend/web" ]]; then
        WEB_DIR="$SCRIPT_DIR/frontend/web"
    else
        # Try to find any web directory
        WEB_DIR=$(find "$SCRIPT_DIR/frontend" -name "web" -type d 2>/dev/null | head -1)
    fi
    
    if [[ -z "$WEB_DIR" ]] || [[ ! -d "$WEB_DIR" ]]; then
        log_warn "Frontend web directory not found, skipping"
    else
        cd "$WEB_DIR"
        
        # Check for package.json
        if [[ ! -f "package.json" ]]; then
            # Maybe it's in a parent directory
            if [[ -f "$SCRIPT_DIR/frontend/package.json" ]]; then
                cd "$SCRIPT_DIR/frontend"
            fi
        fi
        
        # Set environment
        export VITE_HMR_HOST=localhost
        export VITE_HMR_PROTOCOL=ws
        export VITE_HMR_PORT=$WEB_PORT
        export VITE_API_BASE_URL=http://localhost:$API_PORT/api
        export VITE_GRAPHQL_ENDPOINT=http://localhost:$API_PORT/graphql
        export PORT=$WEB_PORT
        export VITE_PORT=$WEB_PORT
        export VITE_AEP_MODE=${AEP_MODE:-library}
        export VITE_DATA_CLOUD_MODE=${DATA_CLOUD_MODE:-library}
        
        # Pass external URLs to frontend if in external mode
        if [[ "$MODE" == "external" ]]; then
            [[ -n "$EXTERNAL_AEP_URL" ]] && export VITE_AEP_SERVICE_URL="$EXTERNAL_AEP_URL"
            [[ -n "$EXTERNAL_DATA_CLOUD_URL" ]] && export VITE_DATA_CLOUD_SERVICE_URL="$EXTERNAL_DATA_CLOUD_URL"
        fi
        
        # Install dependencies if needed
        if [[ ! -d "node_modules" ]]; then
            log_info "Installing frontend dependencies..."
            pnpm install
        fi
        
        # Start dev server
        log_info "Starting frontend dev server..."
        pnpm dev > /tmp/yappc-web.log 2>&1 &
        WEB_PID=$!
        
        log_info "Frontend started (PID: $WEB_PID)"
        
        sleep 3
        if ! kill -0 $WEB_PID 2>/dev/null; then
            log_warn "Frontend may have failed to start, check /tmp/yappc-web.log"
        fi
        
        log_success "Frontend is running"
    fi
else
    log_info "[5/6] Skipping Frontend (--skip-frontend)"
fi

# =============================================================================
# Health Checks
# =============================================================================

log_info "[6/6] Running health checks..."

HEALTHY=true

# Check API
if [[ "$SKIP_API" == false ]]; then
    if curl -s http://localhost:$API_PORT/health >/dev/null 2>&1; then
        log_success "API Gateway is healthy"
    else
        log_warn "API Gateway health check failed"
        HEALTHY=false
    fi
fi

# Check Java Backend
if [[ "$SKIP_BACKEND" == false ]]; then
    if curl -s "http://localhost:$JAVA_BACKEND_PORT/health" >/dev/null 2>&1; then
        log_success "Java Backend is healthy"
    else
        log_warn "Java Backend health check failed"
    fi
fi

# =============================================================================
# Summary
# =============================================================================

echo ""
echo -e "${GREEN}╔══════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║              YAPPC Development Environment Ready!                ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════════════════════════╝${NC}"
echo ""

if [[ "$SKIP_FRONTEND" == false ]]; then
    echo -e "${GREEN}🌐 Web UI:${NC}              http://localhost:$WEB_PORT"
fi
if [[ "$SKIP_API" == false ]]; then
    echo -e "${GREEN}⚡ API Gateway:${NC}         http://localhost:$API_PORT"
    echo -e "${GREEN}📊 GraphQL:${NC}             http://localhost:$API_PORT/graphql"
fi
if [[ "$SKIP_BACKEND" == false ]]; then
    echo -e "${GREEN}☕ Java Backend:${NC}        http://localhost:$JAVA_BACKEND_PORT"
    if [[ "$MODE" == "external" ]]; then
        echo -e "${GREEN}🔗 AEP API:${NC}            ${EXTERNAL_AEP_URL:-http://localhost:$AEP_PORT}"
    else
        echo -e "${GREEN}🔗 AEP Runtime:${NC}        embedded in Java backend"
    fi
fi
echo -e "${GREEN}🤖 Canvas AI:${NC}          localhost:50051 (gRPC)"

# Mode indicator
echo ""
if [[ "$MODE" == "embedded" ]]; then
    echo -e "${BLUE}Mode:${NC} EMBEDDED (AEP and data-cloud running as in-process libraries)"
    echo -e "${BLUE}     No external services required!${NC}"
else
    echo -e "${YELLOW}Mode:${NC} EXTERNAL"
    [[ -n "$EXTERNAL_AEP_URL" ]] && echo -e "${YELLOW}     AEP:${NC} $EXTERNAL_AEP_URL"
    [[ -n "$EXTERNAL_DATA_CLOUD_URL" ]] && echo -e "${YELLOW}     Data-Cloud:${NC} $EXTERNAL_DATA_CLOUD_URL"
fi

echo ""
echo -e "${BLUE}Logs:${NC}"
echo "  API:     tail -f /tmp/yappc-api.log"
echo "  Web:     tail -f /tmp/yappc-web.log"
echo "  Backend: tail -f /tmp/unified-launcher.log"
echo "  Canvas:  tail -f /tmp/canvas-ai-service.log"

echo ""
echo -e "${YELLOW}Press Ctrl+C to stop all services${NC}"
echo ""

# If logs mode, show combined logs
if [[ "$LOGS_MODE" == true ]]; then
    log_info "Showing combined logs (Ctrl+C to exit)..."
    tail -f /tmp/yappc-*.log /tmp/unified-launcher.log /tmp/canvas-ai-service.log 2>/dev/null || true
else
    # Keep script running
    wait
fi
