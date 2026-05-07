#!/usr/bin/env bash
# =============================================================================
# check-java-architecture.sh — Phase 0 Governance Freeze Enforcement
#
# Detects new product-local agent infrastructure classes that violate
# the governance freeze. See docs/GOVERNANCE_FREEZE_RULES.md.
#
# Exit codes:
#   0 — No violations
#   1 — New violations found
# =============================================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# ---------------------------------------------------------------------------
# Allowed locations — violations in these paths are grandfathered
# ---------------------------------------------------------------------------
ALLOWED_PATHS=(
  "platform/java/agent-"
  "products/data-cloud/planes/action/"
)

# ---------------------------------------------------------------------------
# Known grandfathered exceptions (Phase 0 freeze)
# ---------------------------------------------------------------------------
KNOWN_EXCEPTIONS=(
  "products/yappc/libs/java/yappc-domain/src/main/java/com/ghatana/products/yappc/domain/registry/YAPPCAgentRegistry.java"
  "products/yappc/libs/java/yappc-domain/src/main/java/com/ghatana/products/yappc/domain/registry/YappcAgentCatalog.java"
)

# ---------------------------------------------------------------------------
# Banned class-name patterns (grep extended-regex)
# ---------------------------------------------------------------------------
BANNED_PATTERNS=(
  "class [A-Z][A-Za-z]*AgentRegistry "
  "class [A-Z][A-Za-z]*RegistryHandler "
  "class [A-Z][A-Za-z]*AgentCatalog "
  "class [A-Z][A-Za-z]*CatalogLoader "
)

violations=0

is_allowed() {
  local file="$1"
  for allowed in "${ALLOWED_PATHS[@]}"; do
    if [[ "$file" == *"$allowed"* ]]; then
      return 0
    fi
  done
  return 1
}

is_known_exception() {
  local file="$1"
  for exception in "${KNOWN_EXCEPTIONS[@]}"; do
    if [[ "$file" == *"$exception"* ]]; then
      return 0
    fi
  done
  return 1
}

echo "=== Java Architecture Governance Check ==="
echo "Scanning for banned agent infrastructure patterns..."
echo ""

for pattern in "${BANNED_PATTERNS[@]}"; do
  while IFS= read -r match; do
    [ -z "$match" ] && continue
    file=$(echo "$match" | cut -d: -f1)
    # Make path relative to repo root
    rel_file="${file#"$REPO_ROOT/"}"

    if is_allowed "$rel_file"; then
      continue
    fi
    if is_known_exception "$rel_file"; then
      echo "  [KNOWN] $rel_file (grandfathered — see docs/GOVERNANCE_FREEZE_RULES.md)"
      continue
    fi

    echo "  [VIOLATION] $rel_file"
    echo "    Pattern: $pattern"
    echo "    Line: $(echo "$match" | cut -d: -f2-)"
    violations=$((violations + 1))
  done < <(grep -rn --include="*.java" -E "$pattern" "$REPO_ROOT/products/" 2>/dev/null || true)
done

# ---------------------------------------------------------------------------
# JSON validation for package.json files
# ---------------------------------------------------------------------------
echo ""
echo "Validating package.json files..."

while IFS= read -r pkg; do
  [ -z "$pkg" ] && continue
  if ! python3 -c "import json; json.load(open('$pkg'))" 2>/dev/null; then
    rel_pkg="${pkg#"$REPO_ROOT/"}"
    echo "  [VIOLATION] Invalid JSON: $rel_pkg"
    violations=$((violations + 1))
  fi
done < <(find "$REPO_ROOT/products" "$REPO_ROOT/platform" -name "package.json" -not -path "*/node_modules/*" 2>/dev/null || true)

echo ""
if [ "$violations" -gt 0 ]; then
  echo "FAILED: $violations governance violation(s) found."
  echo "See docs/GOVERNANCE_FREEZE_RULES.md for details."
  exit 1
else
  echo "PASSED: No governance violations detected."
  exit 0
fi
