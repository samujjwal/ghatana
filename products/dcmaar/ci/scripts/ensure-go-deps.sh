#!/usr/bin/env bash
set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'  # No Color

# Required Go version
MIN_GO_VERSION="1.25.0"

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

# Check if Go is installed and meets minimum version
check_go_version() {
  if ! command -v go &> /dev/null; then
    error "Go is not installed. Please install Go $MIN_GO_VERSION or later"
  fi

  local go_version
  go_version=$(go version | awk '{print $3}' | sed 's/go//')
  
  if [ "$(printf "%s\n%s" "$MIN_GO_VERSION" "$go_version" | sort -V | head -n1)" != "$MIN_GO_VERSION" ]; then
    error "Go version $go_version is below minimum required $MIN_GO_VERSION"
  fi
  
  log "Using Go version: $go_version"
}

# Install OpenTelemetry with compatible versions
install_opentelemetry() {
  log "Installing OpenTelemetry dependencies..."
  
  # Clean module cache to avoid conflicts
  go clean -modcache
  
  # Core OpenTelemetry packages (using compatible versions)
  local OTEL_VERSION="1.38.0"
  local OTEL_SDK_VERSION="1.38.0"
  local OTEL_CONTRIB_VERSION="0.38.0"
  
  # Install core packages
  go get "go.opentelemetry.io/otel@v$OTEL_VERSION"
  go get "go.opentelemetry.io/otel/sdk@v$OTEL_SDK_VERSION"
  go get "go.opentelemetry.io/otel/metric@v$OTEL_VERSION"
  go get "go.opentelemetry.io/otel/propagation@v$OTEL_VERSION"
  go get "go.opentelemetry.io/otel/semconv/v1.21.0@latest"
  
  # Install OTLP exporters
  go get "go.opentelemetry.io/otel/exporters/otlp/otlptrace@v$OTEL_VERSION"
  go get "go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc@v$OTEL_VERSION"
  go get "go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp@v$OTEL_VERSION"
  go get "go.opentelemetry.io/otel/exporters/prometheus@v0.41.0"
  
  # Install instrumentation
  go get "go.opentelemetry.io/contrib/instrumentation/google.golang.org/grpc/otelgrpc@v$OTEL_CONTRIB_VERSION"
  go get "go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp@v$OTEL_CONTRIB_VERSION"
  
  # Install SDK metrics
  go get "go.opentelemetry.io/otel/sdk/metric@v1.38.0"
  
  log "Installed OpenTelemetry OTLP exporters (replaced deprecated Jaeger exporter)"
}

# Install other dependencies
install_other_deps() {
  log "Installing other dependencies..."
  
  # Core dependencies
  go get github.com/go-redis/redis/v8@latest
  go get go.uber.org/zap@latest
  go get golang.org/x/time/rate@latest
  
  # Configuration
  go get github.com/spf13/viper@latest
  go get github.com/spf13/pflag@latest
  
  # gRPC and protobuf
  go get google.golang.org/grpc@latest
  go get google.golang.org/protobuf@latest
  go get google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest
  
  # Testing
  go get github.com/stretchr/testify@latest
}

# Setup local module replacements
setup_local_replacements() {
  local proto_dir="$(pwd)/../proto/gen/go"
  
  log "Setting up local module replacements..."
  
  # Verify we're in the correct directory
  if [ ! -f "go.mod" ]; then
    error "Not in the server directory or go.mod not found"
  fi
  
  # Add replace directives for local modules
  if [ -d "$proto_dir" ] && ! grep -q "replace github.com/samujjwal/dcmaar/proto/gen/go" go.mod; then
    log "Adding local proto module replacement..."
    go mod edit -replace=github.com/samujjwal/dcmaar/proto/gen/go="$proto_dir"
  fi
}

# Tidy up dependencies
tidy_deps() {
  log "Tidying up dependencies..."
  go mod tidy -v
  
  # Verify dependencies
  log "Verifying dependencies..."
  go mod verify
  
  # Run go vet for additional checks
  log "Running go vet..."
  go vet ./... || true
}

# Main function
main() {
  log "Ensuring Go module dependencies..."
  
  # Check Go version first
  check_go_version
  
  # Change to server directory
  local script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  local server_dir="$script_dir/../services/server"
  
  if [ ! -d "$server_dir" ]; then
    error "Server directory not found at $server_dir"
  fi
  
  pushd "$server_dir" > /dev/null
  
  # Install dependencies
  install_opentelemetry
  install_other_deps
  
  # Setup local replacements
  setup_local_replacements
  
  # Tidy up
  tidy_deps
  
  popd > /dev/null
  
  log "${GREEN}✓ All Go module dependencies are up to date!${NC}
"
  log "Next steps:"
  echo "1. Review the changes in go.mod and go.sum"
  echo "2. Run 'go build ./...' to verify everything compiles"
  echo "3. Commit the updated dependency files"
}

# Run main function
main "$@"
