#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PRODUCT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
WARN_ONLY=false

for arg in "$@"; do
  case "$arg" in
    --warn-only) WARN_ONLY=true ;;
    *) echo "Unknown argument: $arg" >&2; exit 2 ;;
  esac
done

CANONICAL_DOCS=(
  "${PRODUCT_DIR}/README.md"
  "${PRODUCT_DIR}/OWNER.md"
  "${PRODUCT_DIR}/REST_API_DOCUMENTATION.md"
  "${PRODUCT_DIR}/api/data-cloud-api.openapi.yaml"
  "${PRODUCT_DIR}/feature-store-ingest/README.md"
  "${PRODUCT_DIR}/docs/ADR-DC-001-MODULE-OWNERSHIP.md"
  "${PRODUCT_DIR}/docs/openapi.yaml"
  "${PRODUCT_DIR}/docs/DATA_CLOUD_E2E_VISION_EXECUTION_PLAN.md"
  "${PRODUCT_DIR}/docs/DATA_CLOUD_REMEDIATION_IMPLEMENTATION_PLAN.md"
)

for file in "${CANONICAL_DOCS[@]}"; do
  if [[ ! -f "$file" ]]; then
    echo "ERROR: Required canonical doc missing: $file" >&2
    exit 2
  fi
done

VIOLATIONS=$(grep -HnE '/api/v1/agents|AgentRegistryHandler|agents event stream' "${CANONICAL_DOCS[@]}" || true)

if [[ -z "$VIOLATIONS" ]]; then
  echo "✓ Canonical Data Cloud docs respect the AEP agentic boundary."
  exit 0
fi

echo ""
echo "⚠️  Data Cloud documentation boundary drift detected"
echo "   Canonical docs must not describe Data Cloud-owned agent runtime routes."
echo "$VIOLATIONS"
echo ""

if [[ "$WARN_ONLY" == "true" ]]; then
  echo "⚠️  Boundary drift found (--warn-only: not failing build)."
  exit 0
fi

echo "❌ Boundary drift found. Remove Data Cloud-owned agent route references from canonical docs."
exit 1