#!/usr/bin/env bash
set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'  # No Color

# Default values
VERBOSE=false
CLEAN=false
TEST=false
COMPONENT="all"

# Valid components (Go server is deprecated and no longer built via this script)
VALID_COMPONENTS=("all" "agent" "agent-rs" "desktop" "dashboards" "extension" "plugin-sdk")

# Show help
show_help() {
  echo -e "${BLUE}DCMAR Build System${NC}"
  echo "Usage: $0 [options] [component]"
  echo
  echo "Options:"
  echo "  -h, --help        Show this help message and exit"
  echo "  -v, --verbose     Enable verbose output"
  echo "  -c, --clean       Clean before building"
  echo "  -t, --test        Run tests after building"
  echo "  --component NAME  Build specific component (default: all)"
  echo
  echo "Components:"
  for comp in "${VALID_COMPONENTS[@]}"; do
    echo "  $comp"
  done | column -t -s $'\t'
  echo
  echo "Examples:"
  echo "  $0                     # Show help"
  echo "  $0 --component agent   # Build only the Go agent"
  echo "  $0 --component agent-rs# Build only the Rust agent"
  echo "  $0 --component desktop # Build the desktop app"
  echo "  $0 --component extension # Build the browser extension"
  exit 0
}

# Check if a component is valid
is_valid_component() {
  local comp=$1
  for valid in "${VALID_COMPONENTS[@]}"; do
    if [ "$comp" = "$valid" ]; then
      return 0
    fi
  done
  return 1
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    -h|--help)
      show_help
      ;;
    -v|--verbose)
      VERBOSE=true
      shift
      ;;
    -c|--clean)
      CLEAN=true
      shift
      ;;
    -t|--test)
      TEST=true
      shift
      ;;
    --component)
      if [ -z "$2" ] || [[ $2 == -* ]]; then
        error "Missing component name for --component"
      fi
      COMPONENT="$2"
      if ! is_valid_component "$COMPONENT"; then
        error "Invalid component: $COMPONENT"
      fi
      shift 2
      ;;
    *)
      # Handle bare component names
      if [[ "$1" != -* ]]; then
        COMPONENT="$1"
        if ! is_valid_component "$COMPONENT"; then
          error "Invalid component: $1"
        fi
        shift
      else
        error "Unknown option: $1"
      fi
      ;;
  esac
done

# Helper functions
log() {
  echo -e "${GREEN}[+]${NC} $1"
}

warn() {
  echo -e "${YELLOW}[!] $1${NC}"
}

error() {
  echo -e "${RED}[x] Error: $1${NC}" >&2
  show_help
  exit 1
}

# Show help if no arguments provided
if [ $# -eq 0 ] && [ "$COMPONENT" = "all" ]; then
  show_help
fi

# Build a component
build_component() {
  local comp=$1
  log "Building $comp..."
  
  case $comp in
    server|agent)
      if [ "$VERBOSE" = true ]; then
        make -C "services/$comp" build V=1
      else
        make -C "services/$comp" build
      fi
      ;;
    agent-rs|plugin-sdk)
      if [ "$VERBOSE" = true ]; then
        make -C "services/$comp" build V=1
      else
        make -C "services/$comp" build
      fi
      ;;
    desktop|dashboards|extension)
      if [ "$VERBOSE" = true ]; then
        make -C "services/$comp" build V=1
      else
        make -C "services/$comp" build
      fi
      ;;
    *)
      error "Unknown component: $comp"
      ;;
  esac
}

# Clean a component
clean_component() {
  local comp=$1
  log "Cleaning $comp..."
  
  case $comp in
    server|agent|agent-rs|plugin-sdk|desktop|dashboards|extension)
      make -C "services/$comp" clean
      ;;
    *)
      error "Unknown component: $comp"
      ;;
  esac
}

# Test a component
test_component() {
  local comp=$1
  log "Testing $comp..."
  
  case $comp in
    server|agent|agent-rs|plugin-sdk|desktop|dashboards|extension)
      local test_cmd="make -C services/$comp test"
      if [ "$VERBOSE" = true ]; then
        eval "$test_cmd V=1"
      else
        eval "$test_cmd"
      fi
      ;;
    *)
      error "Unknown component: $comp"
      ;;
  esac
}

# Main build function
main() {
  # Clean if requested
  if [ "$CLEAN" = true ]; then
    log "Cleaning build..."
    if [ "$COMPONENT" = "all" ]; then
      make clean
    else
      clean_component "$COMPONENT"
    fi
  fi

  # Build specific component or all
  if [ "$COMPONENT" != "all" ]; then
    build_component "$COMPONENT"
    
    # Run tests if requested
    if [ "$TEST" = true ]; then
      test_component "$COMPONENT"
    fi
  else
    # Build all components
    log "Building all components..."
    if [ "$VERBOSE" = true ]; then
      make build V=1
    else
      make build
    fi
    
    # Run tests if requested
    if [ "$TEST" = true ]; then
      log "Running all tests..."
      if [ "$VERBOSE" = true ]; then
        make test V=1
      else
        make test
      fi
    fi
  fi
  
  log "Build completed successfully!"
}

# Run main function
main "$@"
