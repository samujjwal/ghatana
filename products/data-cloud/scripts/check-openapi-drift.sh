#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# check-openapi-drift.sh — Data-Cloud OpenAPI contract drift detector
#
# PURPOSE:
#   Detects divergence between the HTTP routes registered in
#   DataCloudHttpServer.java and the paths declared in docs/openapi.yaml.
#   Fails CI when routes are added / removed in code without updating the spec.
#
# USAGE:
#   ./products/data-cloud/scripts/check-openapi-drift.sh [--warn-only]
#
# OPTIONS:
#   --warn-only  Print drift warnings but exit 0 (useful for incremental rollout)
#
# EXIT CODES:
#   0  No drift detected (or --warn-only flag set)
#   1  Drift detected
#   2  Required files not found
#
# ALGORITHM:
#   1. Extract routes from DataCloudHttpServer.java using .with(HttpMethod.X, "path") lines
#   2. Normalise ActiveJ parameters (:param → {param}) to match OpenAPI style
#   3. Extract path keys from docs/openapi.yaml (lines matching '^  /…:')
#   4. Compute symmetric difference; exit 1 on any mismatch
#
# EXCLUSIONS (intentionally omitted from spec by design):
#   - WebSocket routes (/ws) — binary protocol, documented separately
#   - Server-Sent Events (/events/stream, /*/stream) — streaming variants
#     documented inline in their parent path objects
#
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PRODUCT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
SERVER_FILE="${PRODUCT_DIR}/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudHttpServer.java"
OPENAPI_FILE="${PRODUCT_DIR}/docs/openapi.yaml"
WARN_ONLY=false

# ── Parse flags ─────────────────────────────────────────────────────────────
for arg in "$@"; do
  case "$arg" in
    --warn-only) WARN_ONLY=true ;;
    *) echo "Unknown argument: $arg"; exit 2 ;;
  esac
done

# ── Validate inputs ──────────────────────────────────────────────────────────
if [[ ! -f "${SERVER_FILE}" ]]; then
  echo "ERROR: Server file not found: ${SERVER_FILE}" >&2
  exit 2
fi
if [[ ! -f "${OPENAPI_FILE}" ]]; then
  echo "ERROR: OpenAPI spec not found: ${OPENAPI_FILE}" >&2
  exit 2
fi

# ── Step 1: Extract routes from DataCloudHttpServer.java ─────────────────────
# Match lines with .with(HttpMethod.METHOD, "/path", ...)
CODE_ROUTES_RAW=$(grep -E '\.with\(HttpMethod\.[A-Z]+, *"' "${SERVER_FILE}" \
  | sed -E 's/.*\.with\(HttpMethod\.[A-Z]+, *"([^"]+)".*/\1/')

# Normalise ActiveJ path params :param → {param}
CODE_ROUTES=$(echo "${CODE_ROUTES_RAW}" \
  | sed -E 's|:([a-zA-Z_][a-zA-Z0-9_]*)|\{\1\}|g' \
  | sort -u)

# Exclude WebSocket endpoint — binary protocol, documented separately outside OpenAPI
CODE_ROUTES_FILTERED=$(echo "${CODE_ROUTES}" \
  | grep -Ev "^/ws$" \
  | sort -u)

# ── Step 2: Extract paths from openapi.yaml ──────────────────────────────────
SPEC_PATHS=$(grep -E "^  /" "${OPENAPI_FILE}" \
  | sed -E 's/^  (\/[^:]+):.*/\1/' \
  | sort -u)

# ── Step 3: Compute drift ────────────────────────────────────────────────────
CODE_ONLY=$(comm -23 \
  <(echo "${CODE_ROUTES_FILTERED}") \
  <(echo "${SPEC_PATHS}"))

SPEC_ONLY=$(comm -13 \
  <(echo "${CODE_ROUTES_FILTERED}") \
  <(echo "${SPEC_PATHS}"))

# ── Step 4: Report ───────────────────────────────────────────────────────────
DRIFT_FOUND=false

if [[ -n "${CODE_ONLY}" ]]; then
  DRIFT_FOUND=true
  echo ""
  echo "⚠️  ROUTES IN CODE BUT MISSING FROM docs/openapi.yaml"
  echo "   These routes are live but not documented:"
  while IFS= read -r route; do
    echo "   + ${route}"
  done <<< "${CODE_ONLY}"
fi

if [[ -n "${SPEC_ONLY}" ]]; then
  DRIFT_FOUND=true
  echo ""
  echo "⚠️  PATHS IN docs/openapi.yaml BUT NOT IN CODE"
  echo "   These paths are documented but have no registered handler:"
  while IFS= read -r path; do
    echo "   - ${path}"
  done <<< "${SPEC_ONLY}"
fi

if [[ "${DRIFT_FOUND}" == "false" ]]; then
  echo "✅ No OpenAPI drift detected."
  echo "   Code routes: $(echo "${CODE_ROUTES_FILTERED}" | wc -l | tr -d ' ')"
  echo "   Spec paths:  $(echo "${SPEC_PATHS}" | wc -l | tr -d ' ')"
  exit 0
fi

echo ""
if [[ "${WARN_ONLY}" == "true" ]]; then
  echo "⚠️  OpenAPI drift found (--warn-only: not failing build)."
  exit 0
else
  echo "❌ OpenAPI drift found. Update docs/openapi.yaml to match code."
  echo "   See docs/openapi.yaml for the canonical spec."
  exit 1
fi
