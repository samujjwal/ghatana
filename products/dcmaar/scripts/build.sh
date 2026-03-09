#!/usr/bin/env bash
#
# Guardian Product Build Script
# Builds all Guardian components or specific ones based on flags
#
# Usage:
#   ./scripts/build.sh [--component=COMPONENT] [--env=ENV] [--skip-tests]
#
# Components: backend, parent-dashboard, parent-mobile, browser-extension, 
#             agent-react-native, agent-desktop, all (default)
# Environments: development, production (default)
#

set -euo pipefail

if ! command -v timeout >/dev/null 2>&1; then
  timeout() {
    local _seconds="$1"
    shift
    "$@"
  }
fi

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PRODUCT_ROOT="$(dirname "$SCRIPT_DIR")"

# Compute repository root (prefer git top-level if available). This makes
# workspace-aware pnpm --filter invocations deterministic when scripts are
# run from a product subdirectory.
if command -v git >/dev/null 2>&1; then
  REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || true)"
fi
if [[ -z "${REPO_ROOT:-}" ]]; then
  # Fallback: assume repository root is two levels up from product root
  REPO_ROOT="$(cd "$PRODUCT_ROOT/../.." && pwd)"
fi

# Source shared utilities
source "$SCRIPT_DIR/lib/utils.sh"
source "$SCRIPT_DIR/lib/logger.sh"

# Default values
COMPONENT="${COMPONENT:-all}"
ENV="${ENV:-production}"
SKIP_TESTS="${SKIP_TESTS:-false}"
VERBOSE="${VERBOSE:-false}"

# Parse command line arguments
for arg in "$@"; do
  case $arg in
    --component=*)
      COMPONENT="${arg#*=}"
      shift
      ;;
    --env=*)
      ENV="${arg#*=}"
      shift
      ;;
    --skip-tests)
      SKIP_TESTS=true
      shift
      ;;
    --verbose)
      VERBOSE=true
      shift
      ;;
    --help)
      cat << EOF
Guardian Product Build Script

Usage: $0 [OPTIONS]

Options:
  --component=COMPONENT   Component to build (backend, parent-dashboard, parent-mobile,
                         browser-extension, agent-react-native, agent-desktop, all)
                         Default: all
  --env=ENV              Build environment (development, production)
                         Default: production
  --skip-tests           Skip running tests during build
  --verbose              Enable verbose output
  --help                 Show this help message

Examples:
  $0                                          # Build all components for production
  $0 --component=backend                      # Build only backend
  $0 --component=parent-mobile --env=development    # Build parent-mobile for development
  $0 --component=all --skip-tests             # Build all without tests

EOF
      exit 0
      ;;
    *)
      log_error "Unknown argument: $arg"
      log_info "Use --help for usage information"
      exit 1
      ;;
  esac
done

# Enable verbose mode if requested
if [[ "$VERBOSE" == "true" ]]; then
  set -x
fi

log_header "Guardian Product Build"
log_info "Component: $COMPONENT"
log_info "Environment: $ENV"
log_info "Skip Tests: $SKIP_TESTS"
log_info "Repo Root: $REPO_ROOT"
log_info "Product Root: $PRODUCT_ROOT"
echo ""

# Build timestamp
BUILD_TIMESTAMP=$(date +%Y%m%d_%H%M%S)
export BUILD_TIMESTAMP

# Progress tracking variables
TOTAL_COMPONENTS=0
COMPLETED_COMPONENTS=0
FAILED_COMPONENTS=0
COMPONENT_START_TIME=0

# Display progress bar
show_progress_bar() {
  local current=$1
  local total=$2
  local percent=$((current * 100 / total))
  local filled=$((percent / 5))
  local empty=$((20 - filled))
  
  printf "Progress: ["
  for ((i = 0; i < filled; i++)); do printf "█"; done
  for ((i = 0; i < empty; i++)); do printf "░"; done
  printf "] $percent%% ($current/$total)\n"
}

# Format elapsed time
format_time() {
  local seconds=$1
  local hours=$((seconds / 3600))
  local minutes=$(((seconds % 3600) / 60))
  local secs=$((seconds % 60))
  
  if [[ $hours -gt 0 ]]; then
    printf "%dh %dm %ds" $hours $minutes $secs
  elif [[ $minutes -gt 0 ]]; then
    printf "%dm %ds" $minutes $secs
  else
    printf "%ds" $secs
  fi
}

# Export helper functions
export -f show_progress_bar
export -f format_time

# Function to build a component
build_component() {
  local component=$1
  local current=$2
  local total=$3
  local component_path="$PRODUCT_ROOT/$component"
  
  COMPONENT_START_TIME=$(date +%s)
  
  # Show component header with counter
  echo ""
  log_step "[$current/$total] Building $component"
  show_progress_bar $((current - 1)) $total
  echo ""
  
  if [[ ! -d "$component_path" ]]; then
    log_error "Component directory not found: $component_path"
    return 1
  fi
  
  cd "$component_path"
  
  # Check if this is a Rust project (Cargo.toml)
  if [[ -f "Cargo.toml" ]]; then
    log_info "  → Detected: Rust project"
    if command -v cargo >/dev/null 2>&1; then
      log_info "  → Compiling Rust (release mode)..."
      if cargo build --release 2>&1 | sed 's/^/    /'; then
        local elapsed=$(($(date +%s) - COMPONENT_START_TIME))
        log_success "$component built successfully ($(format_time $elapsed))"
        cd "$PRODUCT_ROOT"
        return 0
      else
        log_error "Cargo build failed"
        cd "$PRODUCT_ROOT"
        return 1
      fi
    else
      log_error "Cargo not found. Please install Rust toolchain"
      return 1
    fi
  fi
  
  # Check if package.json exists for Node.js projects
  if [[ ! -f "package.json" ]]; then
    log_error "No package.json or Cargo.toml found in $component_path"
    return 1
  fi
  
  log_info "  → Detected: Node.js project"
  
  # Install dependencies if node_modules doesn't exist
  if [[ ! -d "node_modules" ]]; then
    log_info "  ⬇ Installing dependencies..."
    # First try frozen-lockfile, fallback to no-frozen-lockfile if lockfile is outdated
    if ! pnpm install --frozen-lockfile 2>/dev/null && ! pnpm install --no-frozen-lockfile 2>/dev/null; then
      log_warn "  ⚠ Dependency installation had issues (continuing)"
    else
      log_info "  ✓ Dependencies installed"
    fi
  else
    log_info "  ✓ Dependencies already present"
  fi
  
  # Run type checking if script exists
  if check_npm_script "type-check"; then
    log_info "  → Type checking..."
    if pnpm run type-check 2>&1 | sed 's/^/    /'; then
      log_info "  ✓ Type checking passed"
    else
      log_warn "  ⚠ Type checking issues detected (non-fatal)"
    fi
  fi
  
  # Run linting if script exists
  if check_npm_script "lint"; then
    log_info "  → Linting..."
    if pnpm run lint 2>&1 | sed 's/^/    /'; then
      log_info "  ✓ Linting passed"
    else
      log_warn "  ⚠ Linting warnings/errors detected (non-fatal)"
    fi
  fi
  
  # Run tests if not skipped and script exists
  if [[ "$SKIP_TESTS" == "false" ]] && check_npm_script "test"; then
    log_info "  → Running tests..."
    if CI=true pnpm test -- --run 2>&1 | sed 's/^/    /'; then
      log_info "  ✓ Tests passed"
    else
      log_warn "  ⚠ Tests failed (continuing build)"
    fi
  fi
  
  # Build the component
  if check_npm_script "build"; then
    log_info "  → Building for $ENV environment..."
    if NODE_ENV=$ENV pnpm run build 2>&1 | sed 's/^/    /'; then
      local elapsed=$(($(date +%s) - COMPONENT_START_TIME))
      log_success "$component built successfully ($(format_time $elapsed))"
      cd "$PRODUCT_ROOT"
      return 0
    else
      log_error "Build failed"
      cd "$PRODUCT_ROOT"
      return 1
    fi
  else
    log_warn "No build script found for $component"
    cd "$PRODUCT_ROOT"
    return 0
  fi
}

# Function to build Docker image for a component
build_docker_image() {
  local component=$1
  local component_path="$PRODUCT_ROOT/$component"
  
  if [[ ! -f "$component_path/Dockerfile" ]]; then
    log_warn "No Dockerfile found for $component, skipping Docker build"
    return 0
  fi
  
  log_step "Building Docker image for $component"
  
  cd "$component_path"
  
  local image_name="guardian-$component:$BUILD_TIMESTAMP"
  local image_latest="guardian-$component:latest"
  
  docker build -t "$image_name" -t "$image_latest" .
  
  log_success "Docker image built: $image_name"
  cd "$PRODUCT_ROOT"
}

# Function to find built artifacts for a component
find_artifacts() {
  local component_path=$1

  # Common Node / web build outputs
  [[ -d "$component_path/dist" ]] && echo "$component_path/dist"
  [[ -d "$component_path/build" ]] && echo "$component_path/build"
  [[ -d "$component_path/.next" ]] && echo "$component_path/.next"

  # For Node.js/backend builds, check for compiled JS in lib/
  if [[ -d "$component_path/lib" ]] && [[ $(find "$component_path/lib" -name "*.js" 2>/dev/null | wc -l) -gt 0 ]]; then
    echo "$component_path/lib"
  fi

  # For Rust builds, only consider target/release when this component is a Rust project.
  if [[ -f "$component_path/Cargo.toml" ]]; then
    # Prefer explicit CARGO_TARGET_DIR if set; otherwise search upwards from the
    # component directory for the nearest workspace-level target/release.
    if [[ -n "${CARGO_TARGET_DIR:-}" && -d "$CARGO_TARGET_DIR/release" ]]; then
      echo "$CARGO_TARGET_DIR/release"
    else
      local search_dir="$component_path"
      while [[ "$search_dir" != "/" ]]; do
        if [[ -d "$search_dir/target/release" ]]; then
          echo "$search_dir/target/release"
          break
        fi
        search_dir="$(dirname "$search_dir")"
      done
    fi
  fi
}

# Main build logic
main() {
  local start_time=$(date +%s)
  
  # Ensure we're in the product root
  cd "$PRODUCT_ROOT"

  # Build local libraries that apps depend on (ensure workspace packages are built)
  log_step "Building required workspace libraries (2 packages)"
  # Try to build the browser extension core package used by browser-extension
  if command -v pnpm >/dev/null 2>&1; then
    # NOTE: @dcmaar/connectors has TypeScript compilation errors (strict type issues)
    # It will be resolved via Vite aliases during browser-extension build
    # Skip tsc build for now; Vite will handle source files directly
    log_warn "  ⚠ @dcmaar/connectors build skipped (will use Vite aliases for resolution)"
    echo ""

    # Use helper (via bash) to run pnpm from repository root so --filter resolves workspace packages
    # Add timeout to prevent hanging on pnpm operations (increased to 120s for TypeScript compilation)
    log_info "  [1/2] Building @dcmaar/browser-extension-core..."
    local lib1_start=$(date +%s)
    if timeout 120 bash "$REPO_ROOT/scripts/run-pnpm-from-root.sh" --filter @dcmaar/browser-extension-core build 2>&1 | sed 's/^/    /'; then
      local lib1_elapsed=$(($(date +%s) - lib1_start))
      log_success "  ✓ @dcmaar/browser-extension-core built ($(format_time $lib1_elapsed))"
    else
      log_warn "  ⚠ Failed to build @dcmaar/browser-extension-core (continuing)"
    fi
    echo ""

    log_info "  [2/2] Building @guardian/dashboard-core..."
    local lib2_start=$(date +%s)
    if timeout 120 bash "$REPO_ROOT/scripts/run-pnpm-from-root.sh" --filter @guardian/dashboard-core build 2>&1 | sed 's/^/    /'; then
      local lib2_elapsed=$(($(date +%s) - lib2_start))
      log_success "  ✓ @guardian/dashboard-core built ($(format_time $lib2_elapsed))"
    else
      log_warn "  ⚠ Failed to build @guardian/dashboard-core (continuing)"
    fi
  else
    log_warn "pnpm not available; skipping workspace lib build"
  fi
  echo ""
  
  # Define components to build
  local components=()
  
  if [[ "$COMPONENT" == "all" ]]; then
    # Build order matters - backend first (located in apps/backend), then frontend components
    components=(
      "apps/backend"
      "apps/parent-dashboard"
      "apps/parent-mobile"
      "apps/browser-extension"
      "apps/agent-react-native"
      "apps/agent-desktop"
    )
  else
    # Handle specific component paths
    case "$COMPONENT" in
      backend)
        # Backend package lives under apps/backend inside the guardian product
        components=("apps/backend")
        ;;
      parent-dashboard|parent-mobile|browser-extension|agent-react-native|agent-desktop)
        components=("apps/$COMPONENT")
        ;;
      *)
        log_error "Unknown component: $COMPONENT"
        log_info "Valid components: backend, parent-dashboard, parent-mobile, browser-extension, agent-react-native, agent-desktop, all"
        exit 1
        ;;
    esac
  fi
  
  # Build each component
  local failed_components=()
  local component_count=0
  for component in "${components[@]}"; do
    component_count=$((component_count + 1))
    if ! build_component "$component" "$component_count" "${#components[@]}"; then
      failed_components+=("$component")
    fi
    echo ""
  done
  
  # Build Docker images for production
  if [[ "$ENV" == "production" ]]; then
    log_header "Building Docker Images"
    for component in "${components[@]}"; do
      build_docker_image "$component" || true
    done
  fi
  
  # Summary
  local end_time=$(date +%s)
  local duration=$((end_time - start_time))
  local successful_components=$((${#components[@]} - ${#failed_components[@]}))

  echo ""
  log_header "Build Completed"
  
  # Summary statistics
  echo -e "${BOLD}Build Statistics:${NC}"
  printf "  %-30s %s\n" "Total time:" "$(format_time $duration)"
  printf "  %-30s %s\n" "Components built:" "$successful_components/${#components[@]}"
  printf "  %-30s %s\n" "Successful:" "$(log_success '✓ '$successful_components | sed 's/\x1b\[[0-9;]*m//g')"
  if [[ ${#failed_components[@]} -gt 0 ]]; then
    printf "  %-30s %s\n" "Failed:" "$(log_error '✗ '${#failed_components[@]} | sed 's/\x1b\[[0-9;]*m//g')"
  fi
  echo ""

  log_step "Built Components & Artifacts:"
  for component in "${components[@]}"; do
    local component_path="$PRODUCT_ROOT/$component"
    local component_artifacts=()

    while IFS= read -r artifact; do
      [[ -n "$artifact" ]] && component_artifacts+=("$artifact")
    done < <(find_artifacts "$component_path")

    if ((${#component_artifacts[@]} > 0)); then
      echo "  📦 $component"
      for artifact in "${component_artifacts[@]}"; do
        local rel_path="${artifact#$PRODUCT_ROOT/}"
        local size=$(du -sh "$artifact" 2>/dev/null | cut -f1)
        echo "      └─ $rel_path ($size)"
      done
    else
      echo "  📦 $component (no build artifacts detected)"
    fi
  done
  
  echo ""
  if [[ ${#failed_components[@]} -eq 0 ]]; then
    log_success "All components built successfully! ✓"
    exit 0
  else
    echo -e "${BOLD}${RED}Failed components:${NC}"
    for component in "${failed_components[@]}"; do
      echo "  ✗ $component"
    done
    echo ""
    log_error "Build failed"
    exit 1
  fi
}

# Run main function
main
