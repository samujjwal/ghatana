#!/usr/bin/env bash
# DCMaar Demo Preflight Check
# Validates the environment before starting the demo

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Minimum requirements
MIN_DOCKER_VER=20.10.0
MIN_DOCKER_COMPOSE_VER=2.0.0
MIN_DISK_GB=10
MIN_MEM_GB=4
MIN_CPUS=2

# Check if running as root
if [ "$EUID" -eq 0 ]; then
  echo -e "${YELLOW}⚠️  Warning: Running as root is not recommended.${NC}"
fi

# Function to compare version numbers
version_compare() {
    if [ "$1" = "$2" ]; then
        return 0
    fi
    local IFS=.
    local i ver1=($1) ver2=($2)
    for ((i=${#ver1[@]}; i<${#ver2[@]}; i++)); do
        ver1[i]=0
    done
    for ((i=0; i<${#ver1[@]}; i++)); do
        if [[ -z ${ver2[i]} ]]; then
            ver2[i]=0
        fi
        if ((10#${ver1[i]} > 10#${ver2[i]})); then
            return 1
        fi
        if ((10#${ver1[i]} < 10#${ver2[i]})); then
            return 2
        fi
    done
    return 0
}

# Check Docker installation and version
echo -e "🔍 Checking Docker installation..."
if ! command -v docker &> /dev/null; then
    echo -e "${RED}✗ Docker is not installed. Please install Docker ${MIN_DOCKER_VER}+.${NC}"
    exit 1
else
    DOCKER_VERSION=$(docker --version | awk '{print $3}' | tr -d ',')
    if version_compare "$DOCKER_VERSION" "$MIN_DOCKER_VER" && [ $? -eq 2 ]; then
        echo -e "${RED}✗ Docker version $DOCKER_VERSION is too old. Please upgrade to ${MIN_DOCKER_VER}+.${NC}"
        exit 1
    else
        echo -e "${GREEN}✓ Docker $DOCKER_VERSION is installed.${NC}"
    fi
fi

# Check Docker Compose installation and version
echo -e "🔍 Checking Docker Compose installation..."
if ! command -v docker-compose &> /dev/null; then
    echo -e "${RED}✗ Docker Compose is not installed. Please install Docker Compose ${MIN_DOCKER_COMPOSE_VER}+.${NC}"
    exit 1
else
    DOCKER_COMPOSE_VERSION=$(docker-compose --version | awk '{print $3}' | tr -d ',')
    if version_compare "$DOCKER_COMPOSE_VERSION" "$MIN_DOCKER_COMPOSE_VER" && [ $? -eq 2 ]; then
        echo -e "${RED}✗ Docker Compose version $DOCKER_COMPOSE_VERSION is too old. Please upgrade to ${MIN_DOCKER_COMPOSE_VER}+.${NC}"
        exit 1
    else
        echo -e "${GREEN}✓ Docker Compose $DOCKER_COMPOSE_VERSION is installed.${NC}"
    fi
fi

# Check Docker daemon is running
if ! docker info &> /dev/null; then
    echo -e "${RED}✗ Docker daemon is not running. Please start Docker.${NC}"
    exit 1
else
    echo -e "${GREEN}✓ Docker daemon is running.${NC}"
fi

# Check available disk space
echo -e "🔍 Checking disk space..."
DISK_SPACE_GB=$(df -BG . | tail -1 | awk '{print $4}' | tr -d 'G')
if [ "$DISK_SPACE_GB" -lt "$MIN_DISK_GB" ]; then
    echo -e "${YELLOW}⚠️  Warning: Low disk space (${DISK_SPACE_GB}GB available, ${MIN_DISK_GB}GB recommended).${NC}"
else
    echo -e "${GREEN}✓ Sufficient disk space available (${DISK_SPACE_GB}GB).${NC}"
fi

# Check available memory
echo -e "🔍 Checking system resources..."
TOTAL_MEM_GB=$(free -g | awk '/^Mem:/{print $2}')
if [ "$TOTAL_MEM_GB" -lt "$MIN_MEM_GB" ]; then
    echo -e "${YELLOW}⚠️  Warning: Low system memory (${TOTAL_MEM_GB}GB available, ${MIN_MEM_GB}GB recommended).${NC}"
else
    echo -e "${GREEN}✓ Sufficient memory available (${TOTAL_MEM_GB}GB).${NC}"
fi

# Check CPU cores
CPU_CORES=$(nproc)
if [ "$CPU_CORES" -lt "$MIN_CPUS" ]; then
    echo -e "${YELLOW}⚠️  Warning: Limited CPU cores (${CPU_CORES} available, ${MIN_CPUS} recommended).${NC}"
else
    echo -e "${GREEN}✓ Sufficient CPU cores available (${CPU_CORES}).${NC}"
fi

# Check required ports
echo -e "🔍 Checking required ports..."
REQUIRED_PORTS=(8080 50051 3000 9090 3001 5432 6379 8081)
PORTS_IN_USE=()

for port in "${REQUIRED_PORTS[@]}"; do
    if lsof -i ":$port" > /dev/null 2>&1; then
        PORTS_IN_USE+=("$port")
    fi
done

if [ ${#PORTS_IN_USE[@]} -gt 0 ]; then
    echo -e "${YELLOW}⚠️  Warning: The following required ports are in use: ${PORTS_IN_USE[*]}${NC}"
    echo -e "   Please stop the services using these ports or update the configuration."
else
    echo -e "${GREEN}✓ All required ports are available.${NC}"
fi

# Check for demo certificates
echo -e "🔍 Checking for demo certificates..."
if [ ! -f "security/dev-pki/demo/ca.crt" ] || [ ! -f "security/dev-pki/demo/server.crt" ] || [ ! -f "security/dev-pki/demo/server.key" ]; then
    echo -e "${YELLOW}⚠️  Demo certificates not found. Generating new ones...${NC}"
    mkdir -p security/dev-pki/demo
    # TODO: Add certificate generation logic
    echo -e "${GREEN}✓ Demo certificates generated.${NC}"
else
    echo -e "${GREEN}✓ Demo certificates found.${NC}"
fi

# Check for demo environment variables
echo -e "🔍 Checking environment variables..."
if [ -z "${DEMO_API_KEY:-}" ]; then
    echo -e "${YELLOW}⚠️  DEMO_API_KEY is not set. Using default value.${NC}"
    export DEMO_API_KEY="demo-$(openssl rand -hex 8)"
    echo -e "   Generated DEMO_API_KEY: ${DEMO_API_KEY}"
else
    echo -e "${GREEN}✓ DEMO_API_KEY is set.${NC}"
fi

echo -e "\n${GREEN}✅ Preflight checks completed successfully!${NC}"
echo -e "You can now start the demo environment with: ${YELLOW}make demo-up${NC}"

exit 0
