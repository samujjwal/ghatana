#!/bin/bash
# Documentation Index Validation Script
#
# Validates that:
# 1. All canonical docs in docs/ are linked from DOCUMENTATION_INDEX.md
# 2. Archive docs are excluded from validation
# 3. No new scattered docs are added without being tracked
#
# Usage: ./scripts/validate-docs-index.sh [--ci]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCS_DIR="$(dirname "$SCRIPT_DIR")/docs"
INDEX_FILE="$DOCS_DIR/DOCUMENTATION_INDEX.md"
ARCHIVE_DIR="$DOCS_DIR/archive"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

CI_MODE=false
if [[ "$1" == "--ci" ]]; then
  CI_MODE=true
fi

log_error() {
  echo -e "${RED}ERROR: $1${NC}"
  if [ "$CI_MODE" = true ]; then
    exit 1
  fi
}

log_warn() {
  echo -e "${YELLOW}WARNING: $1${NC}"
}

log_success() {
  echo -e "${GREEN}✓ $1${NC}"
}

# Check if DOCUMENTATION_INDEX.md exists
if [ ! -f "$INDEX_FILE" ]; then
  log_error "DOCUMENTATION_INDEX.md not found at $INDEX_FILE"
fi

# Find all markdown files in docs/ (excluding archive, node_modules, and hidden files)
canonical_docs=$(find "$DOCS_DIR" -name "*.md" -not -path "$ARCHIVE_DIR/*" -not -path "*/node_modules/*" -not -path "*/.*" 2>/dev/null || true)

# Extract linked docs from DOCUMENTATION_INDEX.md
# Matches both [filename](path) and [text](path) patterns
linked_docs=$(grep -oE '\[.*?\]\([^)]+\.md\)' "$INDEX_FILE" | sed 's/.*](//' | sed 's/)$//' || true)

# Convert to absolute paths for comparison
declare -a missing_links
declare -a unlinked_docs

for doc in $canonical_docs; do
  # Get relative path from docs/ directory
  rel_path="${doc#$DOCS_DIR/}"
  
  # Check if this doc is linked in the index (as relative path from docs/)
  is_linked=false
  for linked in $linked_docs; do
    # Handle both absolute and relative links
    linked_basename=$(basename "$linked")
    doc_basename=$(basename "$rel_path")
    
    if [[ "$linked" == *"$rel_path"* ]] || [[ "$linked_basename" == "$doc_basename" ]]; then
      is_linked=true
      break
    fi
  done
  
  # Skip the index file itself
  if [[ "$rel_path" == "DOCUMENTATION_INDEX.md" ]]; then
    continue
  fi
  
  if [ "$is_linked" = false ]; then
    unlinked_docs+=("$rel_path")
  fi
done

# Check for docs in archive that shouldn't be linked
archive_docs=$(find "$ARCHIVE_DIR" -name "*.md" 2>/dev/null || true)
if [ -n "$archive_docs" ]; then
  for archive_doc in $archive_docs; do
    archive_rel="${archive_doc#$DOCS_DIR/}"
    # Check if archive docs are incorrectly linked in index
    if echo "$linked_docs" | grep -q "$archive_rel"; then
      log_warn "Archive doc is linked in index (should be excluded): $archive_rel"
    fi
  done
fi

# Report results
if [ ${#unlinked_docs[@]} -gt 0 ]; then
  log_error "Found ${#unlinked_docs[@]} canonical docs not linked from DOCUMENTATION_INDEX.md:"
  for doc in "${unlinked_docs[@]}"; do
    echo "  - $doc"
  done
  echo ""
  echo "Please add these docs to the appropriate section in DOCUMENTATION_INDEX.md"
  echo "or move them to docs/archive/ if they are no longer relevant."
  exit 1
fi

log_success "All canonical docs are linked from DOCUMENTATION_INDEX.md"
log_success "Archive docs are properly excluded"

# Check for scattered docs (docs outside products/yappc/docs/)
# This is informational only - doesn't fail the build
SCATTERED_DOCS=(
  "products/yappc/ARTIFACT_COMPILER_IMPLEMENTATION_PLAN.md"
  "products/yappc/DOMAIN_MODEL_REGISTRY.md"
  "products/yappc/frontend/YAPPC_PACKAGE_CLASSIFICATION.md"
)

found_scattered=0
for scattered in "${SCATTERED_DOCS[@]}"; do
  full_path="$SCRIPT_DIR/$scattered"
  if [ -f "$full_path" ]; then
    if [ "$found_scattered" -eq 0 ]; then
      echo ""
      log_warn "Found scattered docs outside docs/ (tracked in DOCUMENTATION_INDEX.md):"
      found_scattered=1
    fi
    echo "  - $scattered"
  fi
done

if [ "$found_scattered" -eq 1 ]; then
  echo ""
  echo "These scattered docs are tracked in DOCUMENTATION_INDEX.md under 'Scattered Docs — To Be Consolidated'."
  echo "No action required, but consider moving them to docs/ in a future cleanup."
fi

log_success "Documentation index validation passed"
