#!/bin/bash
# Stop all client-side components

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Stopping DCMaar Client-Side Components${NC}"
echo "========================================"

# Get project root
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOG_DIR="$PROJECT_ROOT/logs"

# Stop Agent
if [ -f "$LOG_DIR/agent.pid" ]; then
    AGENT_PID=$(cat "$LOG_DIR/agent.pid")
    if kill -0 $AGENT_PID 2>/dev/null; then
        echo -e "${YELLOW}Stopping Agent (PID: $AGENT_PID)...${NC}"
        kill $AGENT_PID
        echo -e "${GREEN}✓ Agent stopped${NC}"
    else
        echo -e "${YELLOW}Agent not running${NC}"
    fi
    rm "$LOG_DIR/agent.pid"
else
    echo -e "${YELLOW}No Agent PID file found${NC}"
fi

# Stop Desktop
if [ -f "$LOG_DIR/desktop.pid" ]; then
    DESKTOP_PID=$(cat "$LOG_DIR/desktop.pid")
    if kill -0 $DESKTOP_PID 2>/dev/null; then
        echo -e "${YELLOW}Stopping Desktop (PID: $DESKTOP_PID)...${NC}"
        kill $DESKTOP_PID
        echo -e "${GREEN}✓ Desktop stopped${NC}"
    else
        echo -e "${YELLOW}Desktop not running${NC}"
    fi
    rm "$LOG_DIR/desktop.pid"
else
    echo -e "${YELLOW}No Desktop PID file found${NC}"
fi

# Kill any remaining processes
echo -e "\n${YELLOW}Cleaning up remaining processes...${NC}"
pkill -f "cargo run" 2>/dev/null || true
pkill -f "tauri dev" 2>/dev/null || true

echo -e "\n${GREEN}All services stopped${NC}"
