#!/bin/bash
set -euo pipefail

# Colors for output
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
    echo -e "${RED}[ERROR]${NC} $1" >&2
    exit 1
}

# Check prerequisites
check_prerequisites() {
    info "Checking prerequisites..."
    
    # Check Node.js and pnpm
    if ! command -v node &> /dev/null; then
        error "Node.js is not installed. Please install Node.js 18+ and try again."
    fi
    
    if ! command -v pnpm &> /dev/null; then
        error "pnpm is not installed. Please install pnpm and try again."
    fi
    
    # Check Go
    if ! command -v go &> /dev/null; then
        error "Go is not installed. Please install Go 1.25+ and try again."
    fi
    
    # Check Rust
    if ! command -v cargo &> /dev/null; then
        error "Rust is not installed. Please install Rust and try again."
    fi
    
    info "All prerequisites are installed."
}

# Build Rust components
build_rust() {
    info "Building Rust components..."
    cd "$(dirname "$0")/.." || error "Failed to change to project root"
    
    if ! cargo build --workspace; then
        error "Failed to build Rust components in debug mode"
    fi
    
    if ! cargo build --release --workspace; then
        error "Failed to build Rust components in release mode"
    fi
    
    info "Rust components built successfully"
}

# Build Go components
build_go() {
    info "Building Go components..."
    cd "$(dirname "$0")/../services/server" || error "Failed to change to server directory"
    
    if ! go build -o ../../bin/server ./cmd/server; then
        error "Failed to build Go server"
    fi
    
    info "Go components built successfully"
}

# Build browser extension
build_extension() {
    info "Building browser extension..."
    cd "$(dirname "$0")/../services/extension" || error "Failed to change to extension directory"
    
    if ! pnpm install; then
        error "Failed to install extension dependencies"
    fi
    
    if ! pnpm run build; then
        error "Failed to build extension"
    fi
    
    info "Browser extension built successfully"
}

# Build desktop application
build_desktop() {
    info "Building desktop application..."
    cd "$(dirname "$0")/../services/desktop" || error "Failed to change to desktop directory"
    
    if ! pnpm install; then
        error "Failed to install desktop app dependencies"
    fi
    
    if ! pnpm run build; then
        error "Failed to build desktop app frontend"
    fi
    
    cd src-tauri || error "Failed to change to Tauri directory"
    if ! cargo build; then
        error "Failed to build Tauri application"
    fi
    
    info "Desktop application built successfully"
}

# Build dashboards
build_dashboards() {
    info "Building dashboards..."
    cd "$(dirname "$0")/../services/dashboards" || error "Failed to change to dashboards directory"
    
    if ! pnpm install; then
        error "Failed to install dashboard dependencies"
    fi
    
    if ! pnpm run build; then
        error "Failed to build dashboards"
    fi
    
    info "Dashboards built successfully"
}

# Main function
main() {
    info "🚀 Starting DCMaar build process..."
    
    check_prerequisites
    
    # Create bin directory if it doesn't exist
    mkdir -p "$(dirname "$0")/../bin"
    
    # Build components
    build_rust
    build_go
    build_extension
    build_desktop
    build_dashboards
    
    info "🎉 All components built successfully!"
    info "📦 Build artifacts are available in their respective directories."
}

# Run the main function
main "$@"
