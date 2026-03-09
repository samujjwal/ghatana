#!/usr/bin/env bash
set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'  # No Color

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

# Install specific version of OpenTelemetry that matches our code
install_opentelemetry() {
  log "Installing OpenTelemetry dependencies..."
  
  # First, remove any existing versions to avoid conflicts
  go clean -modcache
  
  # Install specific versions that match our code
  # Using older versions that have the required packages
  go get go.opentelemetry.io/otel@v1.0.0
  go get go.opentelemetry.io/otel/exporters/jaeger@v1.0.0
  go get go.opentelemetry.io/otel/exporters/otlp/otlptrace@v1.0.0
  go get go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc@v1.0.0
  go get go.opentelemetry.io/otel/exporters/prometheus@v0.32.1
  
  # Install SDK components
  go get go.opentelemetry.io/otel/sdk@v1.0.0
  go get go.opentelemetry.io/otel/sdk/metric@v0.23.0
  go get go.opentelemetry.io/otel/sdk/trace@v1.0.0
  
  # Install additional required packages
  go get go.opentelemetry.io/otel/metric@v0.23.0
  go get go.opentelemetry.io/otel/propagation@v1.0.0
  go get go.opentelemetry.io/otel/semconv/v1.4.0
  
  # Install experimental packages that might be needed
  go get go.opentelemetry.io/otel/exporters/otlp/internal@v0.20.0
  go get go.opentelemetry.io/otel/exporters/otlp/otlptrace/internal@v1.0.0
}

# Install other dependencies
install_other_deps() {
  log "Installing other dependencies..."
  
  # Core dependencies
  go get github.com/go-redis/redis/v8@latest
  go get go.uber.org/zap@latest
  go get golang.org/x/time/rate@latest
  
  # Utility libraries
  go get github.com/spf13/viper@latest
  go get github.com/pkg/errors@latest
  
  # Required for protobuf
  go get google.golang.org/protobuf/cmd/protoc-gen-go@latest
  go get google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest
}

# Setup local module replacements
setup_local_replacements() {
  local proto_dir="$(pwd)/../proto/gen/go"
  
  log "Setting up local module replacements..."
  
  # We should already be in the server directory at this point
  if [ ! -f "go.mod" ]; then
    error "Not in the server directory or go.mod not found"
  fi
  
  # Add replace directives for local modules
  if ! grep -q "replace github.com/samujjwal/dcmaar/proto/gen/go" go.mod; then
    log "Adding local proto module replacement..."
    go mod edit -replace=github.com/samujjwal/dcmaar/proto/gen/go="$proto_dir"
  fi
  
  # Add replace for the server module itself if needed
  local module_name=$(go list -m)
  if [ "$module_name" != "github.com/samujjwal/dcmaar/server" ]; then
    warn "Unexpected module name: $module_name"
    if ! grep -q "replace github.com/samujjwal/dcmaar/services/server" go.mod; then
      log "Adding local server module replacement..."
      go mod edit -replace=github.com/samujjwal/dcmaar/services/server=.
    fi
  fi
  
  # Tidy up
  log "Running go mod tidy..."
  go mod tidy -v
  
  # Verify all dependencies
  log "Verifying dependencies..."
  if ! go mod verify; then
    warn "Dependency verification failed, trying to fix..."
    go clean -modcache
    go mod tidy -v
  fi
}

# Main function
main() {
  log "Setting up Go dependencies..."
  
  # Check if we're in the project root
  if [ ! -d "services" ]; then
    error "Could not find 'services' directory. Please run this script from the project root."
  fi
  
  # Change to server directory
  cd "services/server" || error "Failed to change to server directory"
  
  # Initialize go.mod if it doesn't exist
  if [ ! -f "go.mod" ]; then
    log "Initializing new Go module..."
    go mod init github.com/samujjwal/dcmaar/server
  fi
  
  # Install dependencies
  install_opentelemetry
  install_other_deps
  setup_local_replacements
  
  log "${GREEN}✓ Dependencies installed successfully!${NC}"
  log "You can now build the server with: ./scripts/build.sh server"
}

# Run main function
main "$@"
