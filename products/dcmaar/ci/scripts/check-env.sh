#!/bin/bash
set -euo pipefail

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Logging functions
info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1"
    exit 1
}

check_command() {
    if ! command -v $1 &> /dev/null; then
        error "$1 is not installed. $2"
    else
        info "✓ $1 is installed"
    fi
}

check_version() {
    local cmd=$1
    local min_version=$2
    local version_cmd=$3
    local version_regex=$4
    
    if command -v $cmd &> /dev/null; then
        local version=$($version_cmd 2>&1 | grep -Eo $version_regex | head -1)
        if [ "$(printf '%s\n' "$min_version" "$version" | sort -V | head -n1)" = "$min_version" ]; then
            info "✓ $cmd version $version >= $min_version"
        else
            error "$cmd version $version is less than required $min_version"
        fi
    fi
}

info "🔍 Checking development environment..."

# Check Node.js and pnpm
check_command node "Please install Node.js 18+ from https://nodejs.org/"
check_command pnpm "Please install pnpm 8+ using 'npm install -g pnpm'"
check_version node 18.0.0 "node --version" "v([0-9]+\.[0-9]+\.[0-9]+)"
check_version pnpm 8.0.0 "pnpm --version" "([0-9]+\.[0-9]+\.[0-9]+)"

# Check Go
check_command go "Please install Go 1.25+ from https://golang.org/doc/install"
check_version go 1.25.0 "go version" "go([0-9]+\.[0-9]+(\.[0-9]+)?)"

# Check Rust
check_command rustup "Please install Rust using https://rustup.rs/"
check_command cargo "Please install Rust using https://rustup.rs/"
check_version rustc 1.70.0 "rustc --version" "rustc ([0-9]+\.[0-9]+\.[0-9]+)"

# Check Docker (optional but recommended)
if command -v docker &> /dev/null; then
    info "✓ Docker is installed"
    if docker info &> /dev/null; then
        info "  ✓ Docker daemon is running"
    else
        warn "  Docker daemon is not running. Some commands may fail."
    fi
else
    warn "Docker is not installed. Some features may not work."
fi

# Check project structure
info "\n📁 Checking project structure..."
required_dirs=(
    "services/server"
    "services/extension"
    "services/desktop"
    "libs/agent-common"
    "services/dashboards"
    "proto"
    "scripts"
)

for dir in "${required_dirs[@]}"; do
    if [ -d "$dir" ]; then
        info "✓ Directory exists: $dir"
    else
        error "Missing directory: $dir"
    fi
done

# Check for .env file
if [ -f ".env" ]; then
    info "✓ .env file exists"
else
    warn ".env file is missing. Copy .env.example to .env and update the values."
fi

info "\n🎉 Environment check complete!"

# Check for recommended tools
info "\n🔧 Recommended tools:"
recommended_tools=(
    "docker-compose"
    "git"
    "make"
    "jq"
    "yq"
)

for tool in "${recommended_tools[@]}"; do
    if command -v $tool &> /dev/null; then
        info "✓ $tool is installed"
    else
        warn "$tool is not installed. Consider installing it for a better development experience."
    fi
done

echo -e "\n${GREEN}✅ Your environment is ready for DCMaar development!${NC}"
echo -e "Run ${YELLOW}make build${NC} to build the project or ${YELLOW}make test${NC} to run tests."
