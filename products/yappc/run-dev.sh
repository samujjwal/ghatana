#!/bin/bash

# Increase file descriptor limit for macOS/watchman
ulimit -n 65536

# YAPPC Development Startup Script
# Starts Agentic Event Processor (AEP), YAPPC Backend, and Web UI

# Determine script directory and repo root
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
REPO_ROOT="$(dirname "$(dirname "$SCRIPT_DIR")")"

# Ports Configuration
API_PORT=7002
WEB_PORT=7001
JAVA_BACKEND_PORT=7003

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}Starting YAPPC Development Environment...${NC}"
echo -e "${BLUE}Script Dir: $SCRIPT_DIR${NC}"
echo -e "${BLUE}Repo Root: $REPO_ROOT${NC}"

# Function to kill background processes on exit
cleanup() {
    echo -e "\n${RED}Stopping all services...${NC}"
    # Kill all child processes of this script's process group
    pkill -P $$
    exit
}

trap cleanup SIGINT SIGTERM

# Cleanup existing processes on critical ports
echo -e "${BLUE}Cleaning up existing processes on critical ports...${NC}"
for port in 8086 8080 7001 7002 7003; do
    # Find PID using lsof (macOS/Linux)
    if command -v lsof >/dev/null 2>&1; then
        pid=$(lsof -ti :$port 2>/dev/null)
        if [ ! -z "$pid" ]; then
            echo -e "${BLUE}Killing process $pid on port $port${NC}"
            kill -9 $pid 2>/dev/null || true
            sleep 0.5
        fi
    fi
done

# Verify ports are actually free
echo -e "${BLUE}Verifying ports are free...${NC}"
sleep 3
for port in 8086 8080 7001 7003; do
    if command -v lsof >/dev/null 2>&1; then
        pid=$(lsof -ti :$port 2>/dev/null)
        if [ ! -z "$pid" ]; then
            echo -e "${RED}Warning: Process still on port $port (PID: $pid), forcefully killing...${NC}"
            kill -9 $pid 2>/dev/null || sudo kill -9 $pid 2>/dev/null || true
            sleep 1
        fi
    fi
done
echo -e "${GREEN}✓ Ports cleaned up${NC}"

# Check for required directories
if [ ! -d "$SCRIPT_DIR/frontend" ]; then
    echo -e "${RED}Error: frontend directory not found at $SCRIPT_DIR/frontend${NC}"
    exit 1
fi

if [ ! -d "$SCRIPT_DIR/backend" ]; then
    echo -e "${RED}Error: backend directory not found at $SCRIPT_DIR/backend${NC}"
    exit 1
fi

# Check for shared Ghatana services
echo -e "${GREEN}[1/5] Checking shared Ghatana services...${NC}"

# Function to check if a service is running
is_service_running() {
    local service_name=$1
    docker ps 2>/dev/null | grep -q "$service_name"
}

# Check which services need to be started
SERVICES_TO_START=()

if is_service_running "ghatana-postgres"; then
    echo -e "${GREEN}✓ PostgreSQL is already running - reusing it${NC}"
else
    echo -e "${BLUE}PostgreSQL not running, will start it${NC}"
    SERVICES_TO_START+=("postgres")
fi

if is_service_running "ghatana-redis"; then
    echo -e "${GREEN}✓ Redis is already running - reusing it${NC}"
else
    echo -e "${BLUE}Redis not running, will start it${NC}"
    SERVICES_TO_START+=("redis")
fi

if is_service_running "ghatana-minio"; then
    echo -e "${GREEN}✓ MinIO is already running - reusing it${NC}"
else
    echo -e "${BLUE}MinIO not running, will start it${NC}"
    SERVICES_TO_START+=("minio")
fi

# Start only the services that aren't running
if [ ${#SERVICES_TO_START[@]} -gt 0 ]; then
    echo -e "${BLUE}Starting missing services: ${SERVICES_TO_START[*]}${NC}"
    cd "$REPO_ROOT"
    docker compose up -d "${SERVICES_TO_START[@]}" 2>/dev/null || true
    
    # Wait for services
    echo -e "${BLUE}Waiting for services to be healthy...${NC}"
    sleep 5
else
    echo -e "${GREEN}✓ All required shared services are already running${NC}"
fi

# Start Unified Launcher (AEP + YAPPC Backend in single JVM)
echo -e "${GREEN}[2/5] Starting Unified Launcher (AEP + YAPPC Backend)...${NC}"
echo -e "${BLUE}Memory optimized: Both services run in a single JVM${NC}"

# Kill any existing launcher instances
pkill -f "launcher-unified" 2>/dev/null || true
sleep 1

cd "$REPO_ROOT"

# Start in background with output redirection
./gradlew :launcher-unified:run :products:yappc:backend:api:classes --console=plain > /tmp/unified-launcher.log 2>&1 &
UNIFIED_PID=$!
echo -e "${BLUE}Unified Launcher started with PID: $UNIFIED_PID${NC}"
echo -e "${BLUE}  • AEP API:    http://localhost:8080${NC}"
echo -e "${BLUE}  • YAPPC API:  http://localhost:8086${NC}"
echo -e "${BLUE}  • AEP UI:     http://localhost:7001 (dev mode)${NC}"

# Wait for services to be ready with health checks
echo -e "${BLUE}Waiting for Unified Launcher to initialize (max 60 seconds)...${NC}"
STARTUP_TIMEOUT=60
STARTUP_ELAPSED=0
STARTUP_SUCCESS=false

while [ $STARTUP_ELAPSED -lt $STARTUP_TIMEOUT ]; do
    # Check if process is still running
    if ! kill -0 $UNIFIED_PID 2>/dev/null; then
        # Process died, check logs
        if tail -5 /tmp/unified-launcher.log | grep -i "error\|exception" > /dev/null 2>&1; then
            echo -e "${RED}Error: Unified Launcher failed to start${NC}"
            echo -e "${RED}Check /tmp/unified-launcher.log for details${NC}"
            tail -20 /tmp/unified-launcher.log
            exit 1
        fi
    fi
    
    # Check for successful startup messages
    if grep -q "io.activej.http.HttpServer.*listening\|HttpServer.*0.0.0.0:8086\|HttpServer.*0.0.0.0:8080" /tmp/unified-launcher.log 2>/dev/null; then
        echo -e "${GREEN}✓ Unified Launcher is ready${NC}"
        STARTUP_SUCCESS=true
        break
    fi
    
    echo -ne "${BLUE}.${NC}"
    sleep 2
    STARTUP_ELAPSED=$((STARTUP_ELAPSED + 2))
done

if [ "$STARTUP_SUCCESS" = false ]; then
    echo -e "\n${BLUE}Launcher startup still in progress, continuing with other services...${NC}"
fi

# Start Canvas AI Service (Java gRPC Backend)
echo -e "${GREEN}[3/6] Starting Canvas AI Service (gRPC Backend on port 50051)...${NC}"
cd "$SCRIPT_DIR/canvas-ai-service"

# Build and install distribution if needed
if [ ! -d "build/install/canvas-ai-service" ]; then
    echo -e "${BLUE}Building Canvas AI Service distribution...${NC}"
    cd "$REPO_ROOT"
    ./gradlew :products:yappc:canvas-ai-service:installDist --console=plain > /tmp/canvas-ai-build.log 2>&1
    if [ $? -ne 0 ]; then
        echo -e "${RED}Error: Canvas AI Service build failed${NC}"
        echo -e "${RED}Check /tmp/canvas-ai-build.log for details${NC}"
        tail -20 /tmp/canvas-ai-build.log
        exit 1
    fi
    echo -e "${GREEN}✓ Canvas AI Service built successfully${NC}"
    cd "$SCRIPT_DIR/canvas-ai-service"
fi

# Kill any existing canvas-ai-service instances
pkill -f "canvas-ai-service" 2>/dev/null || true
sleep 1

# Start Canvas AI Service in background
build/install/canvas-ai-service/bin/canvas-ai-service > /tmp/canvas-ai-service.log 2>&1 &
CANVAS_AI_PID=$!
echo -e "${BLUE}Canvas AI Service started with PID: $CANVAS_AI_PID${NC}"
echo -e "${BLUE}  • gRPC API:   localhost:50051${NC}"

# Wait for Canvas AI Service to be ready (check for "started successfully" message)
echo -e "${BLUE}Waiting for Canvas AI Service to initialize (max 30 seconds)...${NC}"
CANVAS_AI_TIMEOUT=30
CANVAS_AI_ELAPSED=0
CANVAS_AI_SUCCESS=false

while [ $CANVAS_AI_ELAPSED -lt $CANVAS_AI_TIMEOUT ]; do
    # Check if process is still running
    if ! kill -0 $CANVAS_AI_PID 2>/dev/null; then
        echo -e "${RED}Error: Canvas AI Service failed to start${NC}"
        echo -e "${RED}Check /tmp/canvas-ai-service.log for details${NC}"
        tail -20 /tmp/canvas-ai-service.log
        exit 1
    fi
    
    # Check for successful startup message
    if grep -q "Canvas AI Server started successfully\|started successfully on port 50051" /tmp/canvas-ai-service.log 2>/dev/null; then
        echo -e "${GREEN}✓ Canvas AI Service is ready${NC}"
        CANVAS_AI_SUCCESS=true
        break
    fi
    
    echo -ne "${BLUE}.${NC}"
    sleep 2
    CANVAS_AI_ELAPSED=$((CANVAS_AI_ELAPSED + 2))
done

if [ "$CANVAS_AI_SUCCESS" = false ]; then
    echo -e "\n${BLUE}Canvas AI Service startup still in progress, continuing with other services...${NC}"
fi

# Start GraphQL API (Node.js)
echo -e "${GREEN}[4/6] Starting Node.js API Gateway on port $API_PORT...${NC}"
cd "$SCRIPT_DIR/app-creator/apps/api"

# Set Database URL for GraphQL API BEFORE loading any configs
export DATABASE_URL="postgresql://${DB_USER:-ghatana}:${DB_PASSWORD:-ghatana123}@localhost:${DB_PORT:-5432}/${DB_NAME:-yappc_dev}?schema=public"
export PORT=$API_PORT

# Create or update .env.local if it doesn't exist
if [ ! -f ".env.local" ]; then
    echo -e "${BLUE}Creating .env.local for GraphQL API...${NC}"
    cat > .env.local << EOF
DATABASE_URL=$DATABASE_URL
PORT=$API_PORT
EOF
fi

# Install dependencies if needed (fast check)
if [ ! -d "node_modules" ]; then
    echo -e "${BLUE}Installing GraphQL API dependencies...${NC}"
    pnpm install
fi

# Generate Prisma Client
if [ -d "prisma" ]; then
    echo -e "${BLUE}Setting up database...${NC}"
    
    # Load environment variables
    if [ -f ".env.local" ]; then
        set -a
        source .env.local
        set +a
    fi
    
    # Always generate Prisma client
    echo -e "${BLUE}Generating Prisma Client...${NC}"
    pnpm prisma generate || { echo -e "${RED}✗ Prisma generate failed${NC}"; exit 1; }
    
    # Always run db push for development (uses current schema)
    echo -e "${BLUE}Syncing database schema...${NC}"
    export DATABASE_URL
    pnpm prisma db push || { echo -e "${RED}✗ Database sync failed${NC}"; exit 1; }
    echo -e "${GREEN}✓ Database sync complete${NC}"
    
    # Wait for PostgreSQL to fully commit the schema changes (fix for race condition)
    echo -e "${BLUE}Waiting for schema to stabilize...${NC}"
    sleep 2
    
    # Verify database connection
    echo -e "${BLUE}Verifying database connection...${NC}"
    pnpm prisma db execute --stdin <<< "SELECT 1 as test;" || { echo -e "${RED}✗ Database verification failed${NC}"; exit 1; }
    
    # Seed database - MUST complete before API starts
    echo -e "${BLUE}Seeding database with test data...${NC}"
    pnpm prisma db seed || { echo -e "${RED}✗ Database seeding failed${NC}"; exit 1; }
    echo -e "${GREEN}✓ Seeding complete${NC}"
    
    echo -e "${GREEN}✓ Database fully ready${NC}"
    
    # Wait a moment for connection pools to refresh
    sleep 2
fi

# Start API server AFTER database is fully ready
echo -e "${BLUE}Starting GraphQL API server...${NC}"
pnpm run dev > /tmp/graphql-api.log 2>&1 &
GRAPHQL_PID=$!
echo -e "${BLUE}GraphQL API started with PID: $GRAPHQL_PID${NC}"

# Wait briefly for GraphQL to start and check for immediate errors
sleep 2
if ! kill -0 $GRAPHQL_PID 2>/dev/null; then
    echo -e "${RED}Error: GraphQL API failed to start${NC}"
    echo -e "${RED}Check /tmp/graphql-api.log for details${NC}"
    tail -30 /tmp/graphql-api.log
    exit 1
fi


# Start Web UI (App Creator)
echo -e "${GREEN}[5/6] Starting Web UI (App Creator) on port $WEB_PORT...${NC}"
cd "$SCRIPT_DIR/app-creator/apps/web"

# Check if package.json exists
if [ ! -f "package.json" ]; then
    echo -e "${RED}Error: package.json not found in app-creator/apps/web${NC}"
    exit 1
fi

# Set Vite Configuration
export VITE_HMR_HOST=localhost
export VITE_HMR_PROTOCOL=ws
export VITE_HMR_PORT=$WEB_PORT
# Point to real GraphQL API for Lifecycle Hub features (REST + GraphQL)
export VITE_API_BASE_URL=http://localhost:$API_PORT/api
export VITE_GRAPHQL_ENDPOINT=http://localhost:$API_PORT/graphql
export PORT=$WEB_PORT
export VITE_PORT=$WEB_PORT

# Start the dev server - the PORT env var will be picked up by vite.config.ts
pnpm run dev &
WEB_PID=$!
echo -e "${BLUE}Web UI started with PID: $WEB_PID${NC}"

echo ""
echo -e "${GREEN}All services started! Logs will appear below.${NC}"
echo -e "${BLUE}Press Ctrl+C to stop all services.${NC}"
echo ""
echo -e "${GREEN}🚀 Development Environment is ready!${NC}"
echo -e "${GREEN}📱 Web UI (App Creator):     http://localhost:$WEB_PORT${NC}"
echo -e "${GREEN}⚛️  GraphQL API:            http://localhost:$API_PORT/graphql${NC}"
echo -e "${GREEN}🔌 REST API (Workspace):    http://localhost:$API_PORT/api${NC}"
echo -e "${GREEN}☕ AEP API:                 http://localhost:8080${NC}"
echo -e "${GREEN}🔌 YAPPC Backend API:       http://localhost:8086${NC}"
echo -e "${GREEN}🤖 Canvas AI Service:       localhost:50051 (gRPC)${NC}"
echo -e "${GREEN}🎨 AEP UI (dev):            http://localhost:7001${NC}"
echo -e "${GREEN}🗄️  Database & Cache:       PostgreSQL, Redis, MinIO (seeded)${NC}"
echo ""
echo -e "${BLUE}💡 HMR (Hot Module Replacement) is enabled for the Web UI${NC}"
echo -e "${BLUE}💡 Changes to components will auto-refresh in the browser${NC}"
echo -e "${BLUE}💡 Memory optimized: AEP + YAPPC share a single JVM${NC}"
echo ""

# Wait for all processes
wait
