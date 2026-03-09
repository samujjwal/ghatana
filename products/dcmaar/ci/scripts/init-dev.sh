#!/usr/bin/env bash
set -euo pipefail

# Colors for better output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Function to check if a command exists
command_exists() {
  command -v "$1" >/dev/null 2>&1
}

# Function to print section header
section() {
  echo -e "\n${GREEN}==>${NC} ${YELLOW}$1${NC}"
}

# Function to print status
status() {
  echo -e "${GREEN}[✓]${NC} $1"
}

# Function to print warning
warning() {
  echo -e "${YELLOW}[!] $1${NC}"
}

# Function to print error and exit
error() {
  echo -e "${RED}[✗] $1${NC}" >&2
  exit 1
}

# Check if running as root
if [ "$EUID" -eq 0 ]; then
  warning "Running as root is not recommended. Please run as a regular user with sudo privileges."
fi

# Welcome message
echo -e "${GREEN}🚀 Setting up DCMAR development environment...${NC}"
echo -e "This script will check for required tools and set up the project.\n"

# Check for required tools
section "1. Checking required tools"

# Compute repository root (prefer git top-level, fallback to relative path)
REPO_ROOT=""
if command -v git >/dev/null 2>&1; then
  REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || true)"
fi
if [[ -z "${REPO_ROOT}" ]]; then
  REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
fi

# Check for Go
if command_exists go; then
  GO_VERSION=$(go version | awk '{print $3}' | sed 's/go//')
  status "Found Go ${GO_VERSION}"
  
  # Verify Go version (1.25 or higher)
  if [[ "$(printf "%s\n" "1.25" "${GO_VERSION}" | sort -V | head -n1)" != "1.25" ]]; then
    warning "Go version ${GO_VERSION} is installed but version 1.25 or higher is required"
    echo "You can install the latest Go version from: https://golang.org/dl/"
  fi
else
  error "Go is not installed. Please install Go 1.25 or higher from: https://golang.org/dl/"
fi

# Check for Node.js
if command_exists node; then
  NODE_VERSION=$(node --version | sed 's/v//')
  status "Found Node.js ${NODE_VERSION}"
  
  # Verify Node.js version (16 or higher)
  if [[ "$(printf "%s\n" "16.0.0" "${NODE_VERSION}" | sort -V | head -n1)" != "16.0.0" ]]; then
    warning "Node.js version ${NODE_VERSION} is installed but version 16 or higher is recommended"
    echo "You can install the latest LTS version from: https://nodejs.org/"
  fi
else
  error "Node.js is not installed. Please install Node.js 16 or higher from: https://nodejs.org/"
fi

# Check for pnpm
if command_exists pnpm; then
  PNPM_VERSION=$(pnpm --version)
  status "Found pnpm ${PNPM_VERSION}"
else
  warning "pnpm is not installed. Installing pnpm..."
  if command_exists npm; then
    npm install -g pnpm
  elif command_exists curl; then
    curl -fsSL https://get.pnpm.io/install.sh | sh -
  elif command_exists wget; then
    wget -qO- https://get.pnpm.io/install.sh | sh -
  else
    error "Could not install pnpm. Please install it manually: https://pnpm.io/installation"
  fi
fi

# Check for Rust
if command_exists rustc; then
  RUST_VERSION=$(rustc --version | awk '{print $2}')
  status "Found Rust ${RUST_VERSION}"
else
  warning "Rust is not installed. Installing Rust..."
  curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
  source "$HOME/.cargo/env"
fi

# Check for Docker
if command_exists docker; then
  DOCKER_VERSION=$(docker --version | awk '{print $3}' | sed 's/,//')
  status "Found Docker ${DOCKER_VERSION}"
  
  # Check if Docker daemon is running
  if ! docker info >/dev/null 2>&1; then
    error "Docker daemon is not running. Please start Docker and try again."
  fi
else
  error "Docker is not installed. Please install Docker from: https://docs.docker.com/get-docker/"
fi

# Check for Docker Compose
if command_exists docker-compose; then
  DOCKER_COMPOSE_VERSION=$(docker-compose --version | awk '{print $3}' | sed 's/,//')
  status "Found Docker Compose ${DOCKER_COMPOSE_VERSION}"
else
  error "Docker Compose is not installed. Please install it from: https://docs.docker.com/compose/install/"
fi

# Check for buf
if ! command_exists buf; then
  warning "buf is not installed. Installing buf..."
  if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    brew install bufbuild/buf/buf
  elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
    # Linux
    curl -sSL "https://github.com/bufbuild/buf/releases/latest/download/buf-$(uname -s)-$(uname -m)" -o "/usr/local/bin/buf"
    chmod +x "/usr/local/bin/buf"
  else
    error "Unsupported OS. Please install buf manually: https://docs.buf.build/installation"
  fi
fi

# Install project dependencies
section "2. Installing project dependencies"

# Install Go dependencies
status "Installing Go dependencies..."
go mod download

# Install Node.js dependencies
status "Installing Node.js dependencies..."
pnpm install

# Install Rust dependencies
status "Installing Rust dependencies..."
if [ -f "Cargo.toml" ]; then
  cargo fetch
fi

# Set up Git hooks
section "3. Setting up Git hooks"

if [ -d ".git" ]; then
  # Create pre-commit hook
  if [ ! -f ".git/hooks/pre-commit" ]; then
    cat > .git/hooks/pre-commit << 'EOL'
#!/bin/bash
set -e

# Run linters and tests before commit
echo "Running pre-commit checks..."

# Run Go tests
if [ -d "services/server" ]; then
  (cd services/server && go test -v ./...)
fi

# Run Rust tests
if [ -f "Cargo.toml" ]; then
  cargo test --no-fail-fast
fi

# Run TypeScript/JavaScript tests
if [ -f "package.json" ]; then
  pnpm test
fi

# Format code
if command_exists gofmt; then
  gofmt -s -w .
fi

# Add formatted files back to the commit
git add $(git diff --cached --name-only --diff-filter=ACMR)
EOL
    chmod +x .git/hooks/pre-commit
    status "Created pre-commit hook"
  else
    status "pre-commit hook already exists"
  fi
else
  warning "Not a Git repository. Skipping Git hooks setup."
fi

# Generate code from protobuf
section "4. Generating code from protobuf"

if [ -d "proto" ]; then
  status "Generating Go code..."
  if [ -f "buf.gen.go.yaml" ]; then
    buf generate --template buf.gen.go.yaml
  fi
  
  status "Generating Rust code..."
  if [ -f "buf.gen.rust.yaml" ]; then
    buf generate --template buf.gen.rust.yaml
  fi
  
  status "Generating TypeScript code..."
  if [ -f "buf.gen.js.yaml" ]; then
    buf generate --template buf.gen.js.yaml
  fi
  
  status "Generating documentation..."
  if [ -f "buf.gen.doc.yaml" ]; then
    buf generate --template buf.gen.doc.yaml
  fi
else
  warning "No proto directory found. Skipping code generation."
fi

# Build the project
section "5. Building the project"

# Build Go services
if [ -d "services/server" ]; then
  status "Building Go services..."
  (cd services/server && go build -o ../../bin/server .)
fi

# Build Rust components
if [ -f "Cargo.toml" ]; then
  status "Building Rust components..."
  cargo build --release
fi

  # Build web frontend
  if [ -d "frontend" ]; then
    status "Building web frontend..."
    # Run pnpm from repository root so --filter resolves workspace package
    bash "${REPO_ROOT}/scripts/run-pnpm-from-root.sh" --filter "./products/dcmaar/frontend" run build
  fi

# Start development services
section "6. Starting development services"

if [ -f "docker-compose.yml" ]; then
  status "Starting Docker services..."
  docker-compose up -d
  
  # Wait for services to be ready
  echo -e "\n${GREEN}🚀 Development environment is ready!${NC}"
  echo -e "\nNext steps:"
  echo "1. Run 'make test' to run all tests"
  echo "2. Run 'make run' to start the application"
  echo "3. Access the web UI at http://localhost:3000"
  echo "4. View API documentation at http://localhost:8080/docs"
  echo -e "\nHappy coding! 🎉"
else
  warning "No docker-compose.yml found. Skipping service startup."
  echo -e "\n${GREEN}✅ Development environment setup complete!${NC}"
  echo -e "\nNext steps:"
  echo "1. Run 'make test' to run all tests"
  echo "2. Run 'make run' to start the application"
  echo -e "\nHappy coding! 🎉"
fi
