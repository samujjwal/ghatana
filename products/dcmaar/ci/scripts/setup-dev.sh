#!/usr/bin/env bash
set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Minimum versions
MIN_RUST_VERSION="1.70.0"
MIN_GO_VERSION="1.21.0"
MIN_NODE_VERSION="18.0.0"
MIN_PNPM_VERSION="8.0.0"
MIN_DOCKER_VERSION="20.10.0"
MIN_KUBECTL_VERSION="1.25.0"

# Helper functions
log() {
  echo -e "${GREEN}[+]${NC} $1"
}

warn() {
  echo -e "${YELLOW}[!] $1${NC}"
}

error() {
  echo -e "${RED}[x] Error: $1${NC}" >&2
  exit 1
}

# Check if command exists
command_exists() {
  command -v "$1" >/dev/null 2>&1
}

# Compare version numbers
version_compare() {
  if [ "$1" = "$(echo -e "$1\n$2" | sort -V | head -n1)" ]; then
    if [ "$1" = "$2" ]; then
      echo "equal"
    else
      echo "less"
    fi
  else
    echo "greater"
  fi
}

# Check Rust installation
check_rust() {
  log "Checking Rust installation..."
  if ! command_exists rustc || ! command_exists cargo; then
    warn "Rust not found. Installing Rust..."
    curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
    source "$HOME/.cargo/env"
  fi
  
  local rust_version
  rust_version=$(rustc --version | awk '{print $2}')
  
  if [ "$(version_compare "$rust_version" "$MIN_RUST_VERSION")" = "less" ]; then
    warn "Rust version $rust_version is below minimum required $MIN_RUST_VERSION"
    log "Updating Rust..."
    rustup update
  fi
  
  # Install required Rust targets
  log "Installing required Rust targets..."
  
  # Always add wasm32-wasip1 as it's the newer target
  rustup target add wasm32-wasip1
  
  # Only add wasm32-wasi if not on Apple Silicon
  if [[ "$(uname -m)" != "arm64" || "$(uname -s)" != "Darwin" ]]; then
    rustup target add wasm32-wasi
  fi
  
  log "Rust is installed and up to date: $(rustc --version)"
  log "Installed targets: wasm32-wasip1$( [[ "$(uname -m)" != "arm64" || "$(uname -s)" != "Darwin" ]] && echo ", wasm32-wasi" )"
}

# Check Go installation
check_go() {
  log "Checking Go installation..."
  if ! command_exists go; then
    error "Go is not installed. Please install Go $MIN_GO_VERSION or later from https://golang.org/dl/"
  fi
  
  local go_version
  go_version=$(go version | awk '{print $3}' | sed 's/go//')
  
  if [ "$(version_compare "$go_version" "$MIN_GO_VERSION")" = "less" ]; then
    error "Go version $go_version is below minimum required $MIN_GO_VERSION"
  fi
  
  log "Installing Go tools..."
  go install google.golang.org/protobuf/cmd/protoc-gen-go@latest
  go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest
  go install github.com/golangci/golangci-lint/cmd/golangci-lint@latest
  
  log "Go is installed and up to date: $(go version)"
}

# Check Node.js and pnpm
check_node() {
  log "Checking Node.js installation..."
  if ! command_exists node; then
    warn "Node.js not found. Installing via nvm..."
    if ! command_exists nvm; then
      curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.3/install.sh | bash
      export NVM_DIR="$HOME/.nvm"
      [ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh"
    fi
    nvm install --lts
  fi
  
  local node_version
  node_version=$(node --version | sed 's/v//')
  
  if [ "$(version_compare "$node_version" "$MIN_NODE_VERSION")" = "less" ]; then
    warn "Node.js version $node_version is below minimum required $MIN_NODE_VERSION"
    log "Updating Node.js..."
    nvm install --lts
  fi
  
  log "Checking pnpm installation..."
  if ! command_exists pnpm; then
    log "Installing pnpm..."
    npm install -g pnpm
  fi
  
  local pnpm_version
  pnpm_version=$(pnpm --version)
  
  if [ "$(version_compare "$pnpm_version" "$MIN_PNPM_VERSION")" = "less" ]; then
    log "Updating pnpm..."
    pnpm add -g pnpm
  fi
  
  log "Node.js $(node --version) and pnpm $(pnpm --version) are installed and up to date"
}

# Check Docker installation
check_docker() {
  log "Checking Docker installation..."
  if ! command_exists docker; then
    error "Docker is not installed. Please install Docker from https://www.docker.com/get-started"
  fi
  
  if ! docker info >/dev/null 2>&1; then
    error "Docker daemon is not running. Please start Docker and try again."
  fi
  
  local docker_version
  docker_version=$(docker --version | awk '{print $3}' | sed 's/,//')
  
  if [ "$(version_compare "$docker_version" "$MIN_DOCKER_VERSION")" = "less" ]; then
    warn "Docker version $docker_version is below minimum recommended $MIN_DOCKER_VERSION"
    warn "Consider updating Docker for better compatibility"
  else
    log "Docker is installed and running: $(docker --version)"
  fi
}

# Check kubectl installation
check_kubectl() {
  log "Checking kubectl installation..."
  if ! command_exists kubectl; then
    warn "kubectl not found. Attempting to install via Homebrew..."
    if command_exists brew; then
      brew install kubectl
    else
      warn "Homebrew not found. Please install kubectl manually: https://kubernetes.io/docs/tasks/tools/"
      return
    fi
  fi
  
  log "kubectl is installed: $(kubectl version --client --short 2>/dev/null || echo "kubectl found but version check failed")"
}

# Install protoc
install_protoc() {
  log "Checking protoc installation..."
  if ! command_exists protoc; then
    warn "protoc not found. Installing..."
    
    if [[ "$OSTYPE" == "darwin"* ]]; then
      # macOS
      if command_exists brew; then
        brew install protobuf
      else
        error "Homebrew not found. Please install protoc manually: https://grpc.io/docs/protoc-installation/"
      fi
    elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
      # Linux
      if command_exists apt-get; then
        sudo apt-get update && sudo apt-get install -y protobuf-compiler
      elif command_exists yum; then
        sudo yum install -y protobuf-compiler
      else
        error "Unsupported package manager. Please install protoc manually: https://grpc.io/docs/protoc-installation/"
      fi
    else
      error "Unsupported OS. Please install protoc manually: https://grpc.io/docs/protoc-installation/"
    fi
  fi
  
  log "protoc is installed: $(protoc --version 2>/dev/null || echo "protoc found but version check failed")"
}

# Install project dependencies
install_project_deps() {
  log "Installing project dependencies..."
  
  # Install Rust dependencies
  if [ -f "Cargo.toml" ]; then
    log "Installing Rust dependencies..."
    cargo fetch
  fi
  
  # Install Node.js dependencies
  if [ -f "package.json" ]; then
    log "Installing Node.js dependencies..."
    pnpm install
  fi
  
  # Install Go dependencies
  if [ -f "go.mod" ]; then
    log "Installing Go dependencies..."
    ./scripts/ensure-go-deps.sh
  fi
  
  # Set up git hooks if .githooks directory exists
  if [ -d ".githooks" ]; then
    log "Setting up git hooks..."
    git config core.hooksPath .githooks
    chmod +x .githooks/*
  fi
}

# Main function
main() {
  log "Starting DCMAR development environment setup..."
  
  # Check and install system dependencies
  check_rust
  check_go
  check_node
  check_docker
  check_kubectl
  install_protoc
  
  # Install project dependencies
  install_project_deps
  
  log "${GREEN}✓ Development environment setup complete!${NC}"
  log "\nNext steps:"
  echo "  1. Run 'make build' to build all components"
  echo "  2. Run 'make test' to run tests"
  echo "  3. Run 'make docker' to build Docker images"
  echo "  4. Run 'make deploy' to deploy to local Kubernetes"
  echo "\nFor more information, see BUILD.md"
}

# Run main function
main "$@"
