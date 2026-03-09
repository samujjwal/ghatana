#!/bin/bash

# Software-Org Development Startup Script
# Starts Java Backend, TypeScript Backend, and Web UI

# Determine script directory and repo root
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
REPO_ROOT="$(dirname "$(dirname "$SCRIPT_DIR")")"

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}Starting Software-Org Development Environment...${NC}"
echo -e "${BLUE}Repo Root: $REPO_ROOT${NC}"

# Function to kill background processes on exit
cleanup() {
    echo -e "\n${RED}Stopping all services...${NC}"
    # Kill all child processes of this script's process group
    pkill -P $$
    exit
}

trap cleanup SIGINT SIGTERM

USE_DOCKER_DB=true
ALT_DB_HOST_PORT=55432

if [ "$SKIP_DOCKER" = "true" ]; then
	echo -e "${BLUE}SKIP_DOCKER=true detected. Using existing PostgreSQL instance for DATABASE_URL.${NC}"
	USE_DOCKER_DB=false
else
	if ! command -v docker >/dev/null 2>&1; then
		echo -e "${BLUE}Docker CLI not found. Using existing PostgreSQL instance for DATABASE_URL (SKIP_DOCKER=true).${NC}"
		USE_DOCKER_DB=false
	elif ! docker info >/dev/null 2>&1; then
		echo -e "${BLUE}Docker daemon unavailable. Using existing PostgreSQL instance for DATABASE_URL (SKIP_DOCKER=true).${NC}"
		USE_DOCKER_DB=false
	else
		PORT_IN_USE=false
		if command -v ss >/dev/null 2>&1; then
			if ss -ltn | grep -q ':5432 '; then
				PORT_IN_USE=true
			fi
		elif command -v lsof >/dev/null 2>&1; then
			if lsof -iTCP:5432 -sTCP:LISTEN >/dev/null 2>&1; then
				PORT_IN_USE=true
			fi
		fi

		if [ "$PORT_IN_USE" = "true" ]; then
			echo -e "${BLUE}Port 5432 already in use. Using Dockerized PostgreSQL on alternative host port ${ALT_DB_HOST_PORT}.${NC}"
			export DB_HOST_PORT="$ALT_DB_HOST_PORT"
		fi
	fi
fi

if [ "$USE_DOCKER_DB" != "true" ]; then
	export SKIP_DOCKER=true
fi

echo -e "${GREEN}[1/4] Setting up PostgreSQL database (Docker + Prisma)...${NC}"
if [ "$USE_DOCKER_DB" = "true" ]; then
	echo -e "${BLUE}Using Dockerized PostgreSQL container (software-org-db).${NC}"
else
	echo -e "${BLUE}Using existing PostgreSQL instance configured via DATABASE_URL.${NC}"
fi
cd "$SCRIPT_DIR/apps/backend"
bash ./scripts/setup-database.sh dev || {
	echo -e "${RED}Database setup failed. See logs above. Aborting.${NC}"
	exit 1
}

# 1. Start Java Backend
echo -e "${GREEN}[2/4] Starting Java Backend (Launcher)...${NC}"
cd "$REPO_ROOT"
./gradlew :products:software-org:apps:launcher:run &
JAVA_PID=$!

# 2. Start TypeScript Backend
echo -e "${GREEN}[3/4] Starting TypeScript Backend (Fastify)...${NC}"
cd "$SCRIPT_DIR/apps/backend"
pnpm run dev &
TS_BACKEND_PID=$!

# 3. Start Web UI
echo -e "${GREEN}[4/4] Starting Web UI (React with HMR)...${NC}"
cd "$SCRIPT_DIR/apps/web"
# Ensure HMR is properly configured for development
export VITE_HMR_HOST=localhost
export VITE_HMR_PROTOCOL=ws
export VITE_HMR_PORT=4000
pnpm run dev &
WEB_PID=$!

echo -e "${BLUE}All services started! Logs will appear below.${NC}"
echo -e "${BLUE}Press Ctrl+C to stop all services.${NC}"
echo ""
echo -e "${GREEN}🚀 Development Environment is ready!${NC}"
echo -e "${GREEN}📱 Web UI: http://localhost:4000 (with HMR enabled)${NC}"
echo -e "${GREEN}🔧 TypeScript Backend: http://localhost:3101${NC}"
echo -e "${GREEN}☕ Java Backend: http://localhost:8080${NC}"
echo -e "${GREEN}🗄️  Database: PostgreSQL on port ${DB_HOST_PORT:-5432}${NC}"
echo ""
echo -e "${BLUE}💡 HMR (Hot Module Replacement) is enabled for the Web UI${NC}"
echo -e "${BLUE}💡 Changes to React components will auto-refresh in the browser${NC}"
echo ""

# Wait for all processes
wait
