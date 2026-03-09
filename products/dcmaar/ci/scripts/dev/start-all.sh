#!/bin/bash
# Start all client-side components for development

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}Starting DCMaar Client-Side Components${NC}"
echo "========================================"

# Check prerequisites
echo -e "\n${YELLOW}Checking prerequisites...${NC}"

if ! command -v cargo &> /dev/null; then
    echo -e "${RED}Error: cargo not found. Please install Rust.${NC}"
    exit 1
fi

if ! command -v node &> /dev/null; then
    echo -e "${RED}Error: node not found. Please install Node.js.${NC}"
    exit 1
fi

if ! command -v pnpm &> /dev/null; then
    echo -e "${RED}Error: pnpm not found. Please install pnpm.${NC}"
    exit 1
fi

echo -e "${GREEN}✓ All prerequisites found${NC}"

# Get project root
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$PROJECT_ROOT"

# Create log directory
LOG_DIR="$PROJECT_ROOT/logs"
mkdir -p "$LOG_DIR"

# Start Agent
echo -e "\n${YELLOW}Starting Agent...${NC}"
cd "$PROJECT_ROOT/agent-rs"
RUST_LOG=info cargo run > "$LOG_DIR/agent.log" 2>&1 &
AGENT_PID=$!
echo -e "${GREEN}✓ Agent started (PID: $AGENT_PID)${NC}"
echo "$AGENT_PID" > "$LOG_DIR/agent.pid"

# Wait for agent to start
sleep 3

# Check if agent is running
if ! kill -0 $AGENT_PID 2>/dev/null; then
    echo -e "${RED}Error: Agent failed to start. Check $LOG_DIR/agent.log${NC}"
    exit 1
fi

# Start Desktop
echo -e "\n${YELLOW}Starting Desktop...${NC}"
cd "$PROJECT_ROOT/services/desktop"
pnpm tauri dev > "$LOG_DIR/desktop.log" 2>&1 &
DESKTOP_PID=$!
echo -e "${GREEN}✓ Desktop started (PID: $DESKTOP_PID)${NC}"
echo "$DESKTOP_PID" > "$LOG_DIR/desktop.pid"

# Print instructions
echo -e "\n${GREEN}All components started successfully!${NC}"
echo "========================================"
echo ""
echo "Services:"
echo "  - Agent (gRPC):     localhost:50051"
echo "  - Desktop (WebSocket): localhost:12345"
echo "  - Desktop UI:       (opens automatically)"
echo ""
echo "Logs:"
echo "  - Agent:   $LOG_DIR/agent.log"
echo "  - Desktop: $LOG_DIR/desktop.log"
echo ""
echo "To stop all services:"
echo "  ./scripts/dev/stop-all.sh"
echo ""
echo "To load Extension:"
echo "  1. Chrome: chrome://extensions/ → Load unpacked"
echo "  2. Select: $PROJECT_ROOT/services/extension/dist"
echo ""
echo -e "${YELLOW}Press Ctrl+C to stop all services${NC}"

# Wait for interrupt
trap "echo -e '\n${YELLOW}Stopping all services...${NC}'; ./scripts/dev/stop-all.sh; exit 0" INT TERM

# Keep script running
wait
