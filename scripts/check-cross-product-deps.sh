#!/usr/bin/env bash
# =============================================================================
# Cross-Product Dependency Gate (Guardrail 6.3)
#
# Scans all product build files for dependencies on OTHER product modules.
# Cross-product dependencies must go through platform/libs, not directly.
#
# Exit codes:
#   0 — No violations found (or all violations are explicitly approved)
#   1 — Unapproved cross-product dependencies detected
#
# Usage:
#   ./scripts/check-cross-product-deps.sh
#   ./scripts/check-cross-product-deps.sh --strict   # Fail even on approved
#
# CI integration:
#   Add `./scripts/check-cross-product-deps.sh` as a required check.
# =============================================================================
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

STRICT="${1:-}"
VIOLATIONS=()
APPROVED=()

# =============================================================================
# Approved cross-product dependencies (reviewed and accepted by Architecture Board)
# Format: "SOURCE_PRODUCT:TARGET_PROJECT"  e.g. "yappc:products:data-cloud:sdk"
#
# LEGACY entries marked [REMEDIATION REQUIRED] — track for elimination.
# =============================================================================
APPROVED_CROSS_DEPS=(
  # yappc uses data-cloud for event streaming [REMEDIATION REQUIRED: move to shared SPI]
  "yappc:products:data-cloud:platform"
  "yappc:products:data-cloud:sdk"
  # yappc uses AEP for agent processing [REMEDIATION REQUIRED: extract shared interface]
  "yappc:products:aep:platform-bundle"
  "yappc:products:aep:orchestrator"
  # aep uses data-cloud SPI (SPI is the intended integration point, approved)
  "aep:products:data-cloud:spi"
  "aep:products:data-cloud:platform"
  # virtual-org uses aep for orchestration [REMEDIATION REQUIRED: extract shared contracts]
  "virtual-org:products:aep:platform-bundle"
  "virtual-org:products:aep:contracts"
  # software-org uses aep and virtual-org [REMEDIATION REQUIRED]
  "software-org:products:aep:platform-bundle"
  "software-org:products:virtual-org:modules:framework"
  # app-platform uses aep and data-cloud [REMEDIATION REQUIRED: decouple via platform layer]
  "app-platform:products:aep:platform-bundle"
  "app-platform:products:data-cloud:platform"
  "app-platform:products:data-cloud:spi"
  # app-platform integration testing uses finance domains (test-only, accepted)
  "app-platform:products:finance:domains:oms"
  "app-platform:products:finance:domains:risk"
)

is_approved() {
  local source_product="$1"
  local dep_path="$2"
  for approved in "${APPROVED_CROSS_DEPS[@]}"; do
    local ap_source="${approved%%:*}"
    local ap_target="${approved#*:}"
    if [[ "$source_product" == "$ap_source" && "$dep_path" == *"$ap_target"* ]]; then
      return 0
    fi
  done
  return 1
}

echo "Cross-Product Dependency Gate"
echo "============================="
echo ""

# Enumerate all product build files
while IFS= read -r build_file; do
  # Derive which product this build file belongs to
  # e.g. products/yappc/backend/api/build.gradle.kts -> "yappc"
  relative="${build_file#$ROOT/}"
  source_product=$(echo "$relative" | sed 's|^products/||' | cut -d'/' -f1)

  # Find all project(":products:OTHER:...") references
  while IFS= read -r line_with_num; do
    lineno="${line_with_num%%:*}"
    line="${line_with_num#*:}"

    # Extract the referenced project path
    dep_path=$(echo "$line" | sed -n 's/.*project(":products:\([^"]*\)").*/products:\1/p' || true)
    [[ -z "$dep_path" ]] && continue

    # Derive target product from the dep path
    target_product=$(echo "$dep_path" | sed 's|^products:||' | cut -d':' -f1)

    # Skip if same product
    [[ "$source_product" == "$target_product" ]] && continue

    if is_approved "$source_product" "$dep_path"; then
      APPROVED+=("  [APPROVED] $relative:$lineno — $dep_path")
    else
      VIOLATIONS+=("  $relative:$lineno — product '$source_product' depends on '$dep_path'")
    fi
  done < <(grep -n 'project(":products:' "$build_file" 2>/dev/null || true)

done < <(find "$ROOT/products" -name "build.gradle.kts" -not -path "*/build/*" 2>/dev/null)

# =============================================================================
# Report
# =============================================================================

if [[ ${#APPROVED[@]} -gt 0 ]]; then
  echo "Approved cross-product dependencies:"
  printf '%s\n' "${APPROVED[@]}"
  echo ""
fi

if [[ ${#VIOLATIONS[@]} -eq 0 ]]; then
  echo "No unapproved cross-product dependencies found."
  echo ""
  echo "PASS"
  exit 0
else
  echo "UNAPPROVED cross-product dependencies detected:"
  printf '%s\n' "${VIOLATIONS[@]}"
  echo ""
  echo "To fix: Route shared logic through platform/libs or get Architecture Board approval."
  echo "        To approve, add an entry to APPROVED_CROSS_DEPS in this script with rationale."
  echo ""
  echo "FAIL — ${#VIOLATIONS[@]} violation(s) found"
  exit 1
fi
