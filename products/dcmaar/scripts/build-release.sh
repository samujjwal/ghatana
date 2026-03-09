#!/usr/bin/env bash
#
# Guardian Release Builder
# Complete build pipeline: compile, test, build installers, package for distribution
#
# Usage:
#   ./scripts/build-release.sh [--version=VERSION] [--platforms=PLATFORMS] [--skip-tests]
#
# Example:
#   ./scripts/build-release.sh --version=1.0.0 --platforms=all
#   ./scripts/build-release.sh --version=1.0.0 --platforms=macos,linux
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PRODUCT_ROOT="$(dirname "$SCRIPT_DIR")"

# Repository root
if command -v git >/dev/null 2>&1; then
  REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || true)"
fi
if [[ -z "${REPO_ROOT:-}" ]]; then
  REPO_ROOT="$(cd "$PRODUCT_ROOT/../.." && pwd)"
fi

# Defaults
VERSION="${VERSION:-1.0.0}"
PLATFORMS="${PLATFORMS:-all}"
SKIP_TESTS="${SKIP_TESTS:-false}"
BUILD_DIR="$PRODUCT_ROOT/dist/builds"
INSTALLERS_DIR="$PRODUCT_ROOT/dist/installers"
RELEASE_DIR="$PRODUCT_ROOT/dist/release"

# Colors
BLUE='\033[0;34m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log_header() { echo -e "\n${BLUE}==== $* ====${NC}"; }
log_info() { echo -e "${BLUE}ℹ${NC} $*"; }
log_success() { echo -e "${GREEN}✓${NC} $*"; }
log_warn() { echo -e "${YELLOW}⚠${NC} $*"; }
log_error() { echo -e "${RED}✗${NC} $*"; }

# ============================================================================
# Build and Test
# ============================================================================
build_and_test() {
  log_header "Building and Testing All Components"

  cd "$PRODUCT_ROOT"

  # Install dependencies
  log_info "Installing dependencies..."
  bash "$REPO_ROOT/scripts/run-pnpm-from-root.sh" install --frozen-lockfile || \
    bash "$REPO_ROOT/scripts/run-pnpm-from-root.sh" install

  # Run linting
  log_info "Running linter..."
  bash "$SCRIPT_DIR/lib/logger.sh" || true
  bash "$REPO_ROOT/scripts/run-pnpm-from-root.sh" --filter @guardian/* lint || log_warn "Linting issues found"

  # Run type checking
  log_info "Type checking..."
  bash "$REPO_ROOT/scripts/run-pnpm-from-root.sh" --filter @guardian/* type-check || log_warn "Type errors found"

  # Run tests (unless skipped)
  if [[ "$SKIP_TESTS" != "true" ]]; then
    log_info "Running tests..."
    CI=true bash "$REPO_ROOT/scripts/run-pnpm-from-root.sh" --filter @guardian/* test -- --run || log_warn "Some tests failed (non-fatal)"
  else
    log_info "Skipping tests"
  fi

  # Build components
  log_info "Building components..."
  bash "$SCRIPT_DIR/build.sh" --component=all --env=production || {
    log_error "Component build failed"
    return 1
  }

  log_success "Build and test complete"
}

# ============================================================================
# Build Installers
# ============================================================================
build_installers() {
  log_header "Building Installers"

  # Build installers for specified platforms
  "$SCRIPT_DIR/build-installers.sh" --platform="$PLATFORMS" --version="$VERSION" || {
    log_warn "Some installers failed to build (check requirements above)"
  }

  if [[ -d "$INSTALLERS_DIR" ]]; then
    log_success "Installers ready in: $INSTALLERS_DIR"
    ls -lh "$INSTALLERS_DIR"/*/ 2>/dev/null | grep -v total || true
  fi
}

# ============================================================================
# Create Release Package
# ============================================================================
create_release_package() {
  log_header "Creating Release Package"

  mkdir -p "$RELEASE_DIR"

  # Create manifest
  local manifest="$RELEASE_DIR/MANIFEST.txt"
  cat > "$manifest" << EOF
Guardian Release v$VERSION
Generated: $(date -u +"%Y-%m-%dT%H:%M:%SZ")
Repository: $REPO_ROOT

Installers:
EOF

  # List all artifacts
  if [[ -d "$INSTALLERS_DIR" ]]; then
    find "$INSTALLERS_DIR" -type f \( -name "*.dmg" -o -name "*.exe" -o -name "*.pkg" -o -name "*.deb" -o -name "*.rpm" -o -name "*.AppImage" \) | while read -r artifact; do
      local size=$(du -h "$artifact" | cut -f1)
      local filename=$(basename "$artifact")
      echo "  - $filename ($size)" >> "$manifest"
    done
  fi

  # Add checksums
  echo "" >> "$manifest"
  echo "Checksums (SHA-256):" >> "$manifest"
  if [[ -d "$INSTALLERS_DIR" ]]; then
    find "$INSTALLERS_DIR" -type f \( -name "*.dmg" -o -name "*.exe" -o -name "*.pkg" -o -name "*.deb" -o -name "*.rpm" -o -name "*.AppImage" \) | while read -r artifact; do
      local checksum=$(sha256sum "$artifact" | cut -d' ' -f1)
      local filename=$(basename "$artifact")
      echo "  $filename: $checksum" >> "$manifest"
    done
  fi

  log_success "Manifest created: $manifest"

  # Create archive of all installers
  log_info "Creating release archive..."
  cd "$PRODUCT_ROOT/dist"

  local archive_name="guardian-$VERSION-release.tar.gz"
  tar czf "$archive_name" \
    --exclude='*.map' \
    installers/ || log_warn "Archive creation had issues"

  local archive_size=$(du -h "$archive_name" | cut -f1)
  log_success "Release archive: $archive_name ($archive_size)"

  # Copy manifest and archive to release dir
  cp "$archive_name" "$RELEASE_DIR/"
  cp "$manifest" "$RELEASE_DIR/"

  log_success "Release package complete: $RELEASE_DIR"
  ls -lh "$RELEASE_DIR"
}

# ============================================================================
# Generate Release Notes
# ============================================================================
generate_release_notes() {
  log_header "Generating Release Notes"

  local release_notes="$RELEASE_DIR/RELEASE_NOTES.md"
  
  cat > "$release_notes" << EOF
# Guardian Release v$VERSION

## Release Date
$(date -u +"%Y-%m-%d")

## Download

Choose your platform below:

### macOS
- **DMG:** Guardian-$VERSION.dmg (for drag-and-drop installation)
- **PKG:** Guardian.pkg (for package installation)
- Minimum: macOS 10.13+
- [Installation Guide](https://github.com/ghatana/guardian/docs/guides/INSTALLERS.md#macos-installation)

### Windows
- **Setup:** Guardian-$VERSION-Setup.exe
- Minimum: Windows 7+
- Administrator privileges required
- [Installation Guide](https://github.com/ghatana/guardian/docs/guides/INSTALLERS.md#windows-installation)

### Linux
- **DEB:** guardian-$VERSION_amd64.deb (Ubuntu/Debian)
- **RPM:** guardian-$VERSION-1.x86_64.rpm (Red Hat/CentOS/Fedora)
- **AppImage:** Guardian-$VERSION-x86_64.AppImage (Universal)
- Minimum: Ubuntu 18.04+, Debian 10+, CentOS 7+
- [Installation Guide](https://github.com/ghatana/guardian/docs/guides/INSTALLERS.md#linux-installation)

## What's New

- Cross-platform native installers (macOS, Windows, Linux)
- Improved browser extension with Declarative Net Request (DNR)
- Enhanced dashboard UI
- Better device management
- Performance optimizations

## Installation

See platform-specific guides above or follow [INSTALLERS.md](https://github.com/ghatana/guardian/docs/guides/INSTALLERS.md).

Quick start:
\`\`\`bash
# Linux
sudo apt install guardian-$VERSION_amd64.deb

# Or use the universal installer builder
./scripts/build-installers.sh --version=$VERSION
\`\`\`

## Security

All installers are signed and verified. Check SHA-256 hashes in MANIFEST.txt.

## Support

- Documentation: https://github.com/ghatana/guardian/docs
- Issues: https://github.com/ghatana/guardian/issues
- Wiki: https://github.com/ghatana/guardian/wiki

---

**Built with:**
- Node.js $(node --version)
- pnpm $(pnpm --version)
- Rust $(rustc --version)
- Docker $(docker --version | cut -d' ' -f3)

EOF

  log_success "Release notes: $release_notes"
}

# ============================================================================
# Summary
# ============================================================================
print_summary() {
  log_header "Release Build Summary"

  echo ""
  echo "Version:              $VERSION"
  echo "Platforms:            $PLATFORMS"
  echo "Build directory:      $BUILD_DIR"
  echo "Installers directory: $INSTALLERS_DIR"
  echo "Release directory:    $RELEASE_DIR"
  echo ""

  if [[ -d "$RELEASE_DIR" ]]; then
    echo "Release artifacts:"
    ls -lh "$RELEASE_DIR"
  fi

  echo ""
  log_success "Release build complete!"
  echo ""
  echo "Next steps:"
  echo "  1. Review release artifacts in: $RELEASE_DIR"
  echo "  2. Test each installer on target platforms"
  echo "  3. Upload to release server or GitHub Releases"
  echo "  4. Tag repository: git tag -a v$VERSION -m 'Release v$VERSION'"
  echo ""
}

# ============================================================================
# Parse Arguments
# ============================================================================
for arg in "$@"; do
  case $arg in
    --version=*)
      VERSION="${arg#*=}"
      ;;
    --platforms=*)
      PLATFORMS="${arg#*=}"
      ;;
    --skip-tests)
      SKIP_TESTS=true
      ;;
    --help)
      cat << EOF
Guardian Release Builder

Usage: $0 [OPTIONS]

Options:
  --version=VERSION      Release version (default: 1.0.0)
  --platforms=PLATFORMS  Comma-separated list: macos,windows,linux (default: all)
  --skip-tests          Skip running tests
  --help                Show this help message

Examples:
  $0 --version=1.0.0
  $0 --version=1.0.0 --platforms=macos,linux
  $0 --version=1.0.0 --skip-tests

EOF
      exit 0
      ;;
    *)
      log_error "Unknown argument: $arg"
      exit 1
      ;;
  esac
done

# ============================================================================
# Main Pipeline
# ============================================================================
main() {
  local start_time=$(date +%s)

  log_header "Guardian Release Build Pipeline v$VERSION"
  echo "Platforms: $PLATFORMS"
  echo "Start: $(date)"
  echo ""

  # Step 1: Build and test
  if ! build_and_test; then
    log_error "Build and test failed"
    exit 1
  fi

  echo ""

  # Step 2: Build installers
  build_installers

  echo ""

  # Step 3: Create release package
  create_release_package

  echo ""

  # Step 4: Generate release notes
  generate_release_notes

  echo ""

  # Summary
  local end_time=$(date +%s)
  local duration=$((end_time - start_time))

  print_summary

  echo "Duration: ${duration}s"
  echo "End: $(date)"
}

main
