#!/bin/bash
# YAPPC Development Environment Verification Script
# Checks all prerequisites before starting development

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

ERRORS=0
WARNINGS=0

echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║     YAPPC Development Environment Verification            ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Function to print status
print_status() {
    local status=$1
    local message=$2
    local details=$3
    
    if [ "$status" = "ok" ]; then
        echo -e "${GREEN}✓${NC} $message ${details}"
    elif [ "$status" = "warn" ]; then
        echo -e "${YELLOW}⚠${NC} $message ${details}"
        ((WARNINGS++))
    elif [ "$status" = "error" ]; then
        echo -e "${RED}✗${NC} $message ${details}"
        ((ERRORS++))
    else
        echo -e "  $message ${details}"
    fi
}

echo -e "${BLUE}[1/9] Checking Java...${NC}"
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -ge 21 ]; then
        JAVA_FULL=$(java -version 2>&1 | head -n 1)
        print_status "ok" "Java installed" "($JAVA_FULL)"
    else
        print_status "error" "Java 21+ required" "(found Java $JAVA_VERSION)"
    fi
else
    print_status "error" "Java not found" "(install OpenJDK 21 or Temurin 21)"
fi

echo ""
echo -e "${BLUE}[2/9] Checking Node.js...${NC}"
if command -v node &> /dev/null; then
    NODE_VERSION=$(node -v | cut -d'v' -f2 | cut -d'.' -f1)
    if [ "$NODE_VERSION" -ge 20 ]; then
        NODE_FULL=$(node -v)
        print_status "ok" "Node.js installed" "($NODE_FULL)"
    else
        print_status "error" "Node.js 20+ required" "(found Node $NODE_VERSION)"
    fi
else
    print_status "error" "Node.js not found" "(install Node.js 20+ from nodejs.org)"
fi

echo ""
echo -e "${BLUE}[3/9] Checking pnpm...${NC}"
if command -v pnpm &> /dev/null; then
    PNPM_VERSION=$(pnpm -v)
    print_status "ok" "pnpm installed" "(v$PNPM_VERSION)"
else
    print_status "error" "pnpm not found" "(install: npm install -g pnpm)"
fi

echo ""
echo -e "${BLUE}[4/9] Checking Docker...${NC}"
if command -v docker &> /dev/null; then
    DOCKER_VERSION=$(docker --version | cut -d' ' -f3 | cut -d',' -f1)
    print_status "ok" "Docker installed" "(v$DOCKER_VERSION)"
    
    # Check if Docker daemon is running
    if docker info &> /dev/null; then
        print_status "ok" "Docker daemon running"
    else
        print_status "warn" "Docker daemon not running" "(start Docker Desktop)"
    fi
else
    print_status "error" "Docker not found" "(install Docker Desktop)"
fi

echo ""
echo -e "${BLUE}[5/9] Checking Docker Compose...${NC}"
if docker compose version &> /dev/null; then
    COMPOSE_VERSION=$(docker compose version | cut -d' ' -f4 | cut -d'v' -f2)
    print_status "ok" "Docker Compose installed" "(v$COMPOSE_VERSION)"
elif command -v docker-compose &> /dev/null; then
    COMPOSE_VERSION=$(docker-compose --version | cut -d' ' -f4 | cut -d',' -f1)
    print_status "ok" "Docker Compose installed" "(v$COMPOSE_VERSION - consider upgrading to 'docker compose')"
else
    print_status "error" "Docker Compose not found"
fi

echo ""
echo -e "${BLUE}[6/9] Checking Gradle...${NC}"
if [ -f "./gradlew" ]; then
    print_status "ok" "Gradle wrapper found" "(./gradlew)"
else
    print_status "error" "Gradle wrapper not found" "(ensure you're in the yappc directory)"
fi

echo ""
echo -e "${BLUE}[7/9] Checking required ports...${NC}"
check_port() {
    local port=$1
    local service=$2
    if lsof -i:$port &> /dev/null; then
        local process=$(lsof -i:$port | tail -n 1 | awk '{print $1}')
        print_status "warn" "Port $port in use" "(by $process - required for $service)"
    else
        print_status "ok" "Port $port available" "($service)"
    fi
}

check_port 3000 "Frontend"
check_port 8000 "Backend API"
check_port 8080 "Domain Service"
check_port 8081 "AI Requirements API"
check_port 8082 "Lifecycle API"
check_port 5432 "PostgreSQL"
check_port 6379 "Redis"

echo ""
echo -e "${BLUE}[8/9] Checking disk space...${NC}"
if command -v df &> /dev/null; then
    AVAILABLE=$(df -h . | tail -n 1 | awk '{print $4}')
    AVAILABLE_GB=$(df -k . | tail -n 1 | awk '{print $4}')
    AVAILABLE_GB=$((AVAILABLE_GB / 1024 / 1024))
    
    if [ "$AVAILABLE_GB" -ge 10 ]; then
        print_status "ok" "Disk space sufficient" "($AVAILABLE available)"
    elif [ "$AVAILABLE_GB" -ge 5 ]; then
        print_status "warn" "Low disk space" "($AVAILABLE available, 10GB+ recommended)"
    else
        print_status "error" "Insufficient disk space" "($AVAILABLE available, 10GB+ required)"
    fi
fi

echo ""
echo -e "${BLUE}[9/9] Checking memory...${NC}"
if command -v sysctl &> /dev/null; then
    TOTAL_MEM_BYTES=$(sysctl -n hw.memsize)
    TOTAL_MEM_GB=$((TOTAL_MEM_BYTES / 1024 / 1024 / 1024))
    
    if [ "$TOTAL_MEM_GB" -ge 8 ]; then
        print_status "ok" "Memory sufficient" "(${TOTAL_MEM_GB}GB total)"
    else
        print_status "warn" "Low memory" "(${TOTAL_MEM_GB}GB total, 8GB+ recommended)"
    fi
elif command -v free &> /dev/null; then
    TOTAL_MEM=$(free -h | grep Mem | awk '{print $2}')
    print_status "ok" "Memory available" "($TOTAL_MEM total)"
fi

echo ""
echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
echo ""

# Summary
if [ $ERRORS -eq 0 ] && [ $WARNINGS -eq 0 ]; then
    echo -e "${GREEN}✓ Environment verification passed!${NC}"
    echo ""
    echo "You're ready to start development. Run:"
    echo -e "  ${BLUE}./start-infra.sh${NC}           # Start infrastructure (Postgres, Redis)"
    echo -e "  ${BLUE}./gradlew :products:yappc:domain:run${NC}  # Start Java backend"
    echo -e "  ${BLUE}cd frontend && pnpm dev:web${NC}        # Start frontend"
    echo ""
elif [ $ERRORS -eq 0 ]; then
    echo -e "${YELLOW}⚠ Environment verification passed with $WARNINGS warning(s)${NC}"
    echo ""
    echo "You can start development, but consider addressing the warnings above."
    echo ""
else
    echo -e "${RED}✗ Environment verification failed with $ERRORS error(s) and $WARNINGS warning(s)${NC}"
    echo ""
    echo "Please fix the errors above before starting development."
    echo ""
    echo "Quick fixes:"
    echo "  • Install Java 21:  ${BLUE}brew install openjdk@21${NC}"
    echo "  • Install Node 20:  ${BLUE}brew install node@20${NC}"
    echo "  • Install pnpm:     ${BLUE}npm install -g pnpm${NC}"
    echo "  • Install Docker:   ${BLUE}https://www.docker.com/products/docker-desktop${NC}"
    echo ""
    exit 1
fi

exit 0
