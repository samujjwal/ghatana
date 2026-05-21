#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# check-openapi-drift.sh — Data-Cloud OpenAPI contract drift detector
#
# PURPOSE:
#   Detects divergence between the HTTP routes registered in
#   DataCloudRouterBuilder.java and the paths declared in owned OpenAPI contracts.
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
#   1. Extract routes from DataCloudRouterBuilder.java using .with(HttpMethod.X, "path") lines
#   2. Normalise ActiveJ parameters (:param → {param}) to match OpenAPI style
#   3. Extract path keys from contracts/openapi/data-cloud.yaml (lines matching '^  /…:')
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
SERVER_FILE="${PRODUCT_DIR}/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudRouterBuilder.java"
DATA_OPENAPI_FILE="${PRODUCT_DIR}/contracts/openapi/data-cloud.yaml"
ACTION_OPENAPI_FILE="${PRODUCT_DIR}/contracts/openapi/action-plane.yaml"
COMPATIBILITY_REGISTRY_FILE="${PRODUCT_DIR}/contracts/openapi/route-compatibility-registry.yaml"
ACTION_GENERIC_RESPONSE_WAIVERS_FILE="${PRODUCT_DIR}/contracts/openapi/action-plane-generic-response-waivers.txt"
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
if [[ ! -f "${DATA_OPENAPI_FILE}" ]]; then
  echo "ERROR: OpenAPI spec not found: ${DATA_OPENAPI_FILE}" >&2
  exit 2
fi
if [[ ! -f "${ACTION_OPENAPI_FILE}" ]]; then
  echo "ERROR: OpenAPI spec not found: ${ACTION_OPENAPI_FILE}" >&2
  exit 2
fi
if [[ ! -f "${COMPATIBILITY_REGISTRY_FILE}" ]]; then
  echo "ERROR: Route compatibility registry not found: ${COMPATIBILITY_REGISTRY_FILE}" >&2
  exit 2
fi

extract_spec_operations() {
  local spec_file="$1"
  awk '
    /^  \// {
      path=$1
      sub(/:$/, "", path)
      next
    }
    path != "" && /^    (get|post|put|delete|patch):$/ {
      method=toupper(substr($1, 1, length($1)-1))
      print method " " path
    }
  ' "${spec_file}" | sort -u
}

extract_missing_action_metadata_ops() {
  local spec_file="$1"
  awk '
    function flush_op() {
      if (op == "") return
      if (!seenSensitivity || !seenRequiredAccess || !seenRequiresPolicy || !seenRequiresBlockingAudit || !seenLegacyStatus) {
        print op
      }
    }
    /^  \// {
      flush_op()
      path=$1
      sub(/:$/, "", path)
      op=""
      seenSensitivity=0
      seenRequiredAccess=0
      seenRequiresPolicy=0
      seenRequiresBlockingAudit=0
      seenLegacyStatus=0
      next
    }
    path != "" && /^    (get|post|put|delete|patch):$/ {
      flush_op()
      method=toupper(substr($1, 1, length($1)-1))
      op=method " " path
      seenSensitivity=0
      seenRequiredAccess=0
      seenRequiresPolicy=0
      seenRequiresBlockingAudit=0
      seenLegacyStatus=0
      next
    }
    op != "" && /^      x-ghatana-sensitivity:/ { seenSensitivity=1 }
    op != "" && /^      x-ghatana-required-access:/ { seenRequiredAccess=1 }
    op != "" && /^      x-ghatana-requires-policy:/ { seenRequiresPolicy=1 }
    op != "" && /^      x-ghatana-requires-blocking-audit:/ { seenRequiresBlockingAudit=1 }
    op != "" && /^      x-ghatana-legacy-status:/ { seenLegacyStatus=1 }
    END { flush_op() }
  ' "${spec_file}" | sort -u
}

extract_action_generic_response_ops() {
  local spec_file="$1"
  awk '
    function flush_op() {
      if (op != "" && sawGenericResponse) {
        print op
      }
    }
    /^  \// {
      flush_op()
      path=$1
      sub(/:$/, "", path)
      op=""
      sawGenericResponse=0
      next
    }
    path != "" && /^    (get|post|put|delete|patch):$/ {
      flush_op()
      method=toupper(substr($1, 1, length($1)-1))
      op=method " " path
      sawGenericResponse=0
      next
    }
    op != "" && /additionalProperties:[[:space:]]*true/ { sawGenericResponse=1 }
    END { flush_op() }
  ' "${spec_file}" | sort -u
}

extract_action_missing_idempotency_ops() {
  local spec_file="$1"
  awk '
    function flush_op() {
      if (op == "") return
      if ((method == "POST" || method == "PUT" || method == "PATCH" || method == "DELETE") && !hasIdempotency) {
        print op
      }
    }
    /^  \/api\/v1\/action\// {
      flush_op()
      path=$1
      sub(/:$/, "", path)
      op=""
      method=""
      hasIdempotency=0
      next
    }
    path != "" && /^    (get|post|put|delete|patch):$/ {
      flush_op()
      method=toupper(substr($1, 1, length($1)-1))
      op=method " " path
      hasIdempotency=0
      next
    }
    op != "" && /#\/components\/parameters\/IdempotencyKeyHeader/ { hasIdempotency=1 }
    END { flush_op() }
  ' "${spec_file}" | sort -u
}

extract_action_missing_envelope_response_ops() {
  local spec_file="$1"
  awk '
    function flush_op() {
      if (op == "") return
      if (!hasEnvelopeResponse) {
        print op
      }
    }
    /^  \/api\/v1\/action\// {
      flush_op()
      path=$1
      sub(/:$/, "", path)
      op=""
      hasEnvelopeResponse=0
      inResponses=0
      inResponse200=0
      next
    }
    path != "" && /^    (get|post|put|delete|patch):$/ {
      flush_op()
      method=toupper(substr($1, 1, length($1)-1))
      op=method " " path
      hasEnvelopeResponse=0
      inResponses=0
      inResponse200=0
      next
    }
    op != "" && /^      responses:/ {
      inResponses=1
      inResponse200=0
      next
    }
    op != "" && inResponses && /^        '\''200'\'':/ {
      inResponse200=1
      next
    }
    op != "" && inResponses && /^        '\''[0-9]{3}'\'':/ {
      inResponse200=0
      next
    }
    op != "" && inResponses && /^      [a-zA-Z0-9_-]+:/ {
      inResponses=0
      inResponse200=0
      next
    }
    op != "" && inResponse200 && /#\/components\/schemas\/ActionPlaneEnvelope/ {
      hasEnvelopeResponse=1
    }
    END { flush_op() }
  ' "${spec_file}" | sort -u
}

extract_action_mutating_request_without_envelope_ops() {
  local spec_file="$1"
  awk '
    function flush_op() {
      if (op == "") return
      if ((method == "POST" || method == "PUT" || method == "PATCH") && sawRequestBody && !hasEnvelopeRequest) {
        print op
      }
    }
    /^  \/api\/v1\/action\// {
      flush_op()
      path=$1
      sub(/:$/, "", path)
      op=""
      method=""
      sawRequestBody=0
      hasEnvelopeRequest=0
      inRequestBody=0
      next
    }
    path != "" && /^    (get|post|put|delete|patch):$/ {
      flush_op()
      method=toupper(substr($1, 1, length($1)-1))
      op=method " " path
      sawRequestBody=0
      hasEnvelopeRequest=0
      inRequestBody=0
      next
    }
    op != "" && /^      requestBody:/ {
      sawRequestBody=1
      inRequestBody=1
      next
    }
    op != "" && inRequestBody && /^      [a-zA-Z0-9_-]+:/ {
      inRequestBody=0
      next
    }
    op != "" && inRequestBody && /#\/components\/schemas\/ActionPlaneEnvelope/ {
      hasEnvelopeRequest=1
    }
    END { flush_op() }
  ' "${spec_file}" | sort -u
}

# ── Step 1: Extract routes from DataCloudRouterBuilder.java ──────────────────
# Match lines with .with(HttpMethod.METHOD, "/path", ...)
CODE_OPERATIONS_RAW=$(grep -E '\.with\(HttpMethod\.[A-Z]+, *"' "${SERVER_FILE}" \
  | sed -E 's/.*\.with\(HttpMethod\.([A-Z]+), *"([^"]+)".*/\1 \2/')

# Normalise ActiveJ path params :param → {param}
CODE_OPERATIONS=$(echo "${CODE_OPERATIONS_RAW}" \
  | sed -E 's|:([a-zA-Z_][a-zA-Z0-9_]*)|\{\1\}|g' \
  | sort -u)

CODE_ROUTES=$(echo "${CODE_OPERATIONS}" | cut -d' ' -f2- | sort -u)

# Exclude WebSocket endpoint — binary protocol, documented separately outside OpenAPI
CODE_OPERATIONS_FILTERED=$(echo "${CODE_OPERATIONS}" \
  | grep -Ev "^[A-Z]+ /ws$" \
  | sort -u)

CODE_ROUTES_FILTERED=$(echo "${CODE_OPERATIONS_FILTERED}" | cut -d' ' -f2- | sort -u)

ACTION_CODE_OPS=$(echo "${CODE_OPERATIONS_FILTERED}" | grep -E "^[A-Z]+ /api/v1/action/" || true)
NON_ACTION_CODE_OPS=$(echo "${CODE_OPERATIONS_FILTERED}" | grep -Ev "^[A-Z]+ /api/v1/action/" || true)

ACTION_CODE_ROUTES=$(echo "${ACTION_CODE_OPS}" | cut -d' ' -f2- | sort -u)
NON_ACTION_CODE_ROUTES=$(echo "${NON_ACTION_CODE_OPS}" | cut -d' ' -f2- | sort -u)

COMPATIBILITY_ROUTES=$(grep -E '^[[:space:]]*- path: "' "${COMPATIBILITY_REGISTRY_FILE}" \
  | sed -E 's/.*path: "([^"]+)".*/\1/' \
  | sed -E 's|:([a-zA-Z_][a-zA-Z0-9_]*)|\{\1\}|g' \
  | sort -u)

DATA_CODE_ROUTES=$(comm -23 \
  <(echo "${NON_ACTION_CODE_ROUTES}" | sort -u) \
  <(echo "${COMPATIBILITY_ROUTES}" | sort -u))

DATA_CODE_OPS=$(awk '
  NR==FNR { compat[$1]=1; next }
  {
    path = $0
    sub(/^[A-Z]+ /, "", path)
    if (!(path in compat)) {
      print $0
    }
  }
' <(echo "${COMPATIBILITY_ROUTES}") <(echo "${NON_ACTION_CODE_OPS}") | sort -u)

COMPATIBILITY_CODE_ROUTES=$(comm -12 \
  <(echo "${NON_ACTION_CODE_ROUTES}" | sort -u) \
  <(echo "${COMPATIBILITY_ROUTES}" | sort -u))

UNREGISTERED_NON_ACTION_COMPATIBILITY=$(comm -23 \
  <(echo "${NON_ACTION_CODE_ROUTES}" \
    | grep -E '^/api/v1/(agents|autonomy|executions|memory|pipelines|plugins)(/|$)' \
    | sort -u || true) \
  <(echo "${COMPATIBILITY_ROUTES}" | sort -u))

# ── Step 2: Extract operations from OpenAPI ownership contracts ──────────────
DATA_SPEC_OPS=$(extract_spec_operations "${DATA_OPENAPI_FILE}")
ACTION_SPEC_OPS=$(extract_spec_operations "${ACTION_OPENAPI_FILE}")

DATA_SPEC_PATHS=$(echo "${DATA_SPEC_OPS}" | cut -d' ' -f2- | sort -u)
ACTION_SPEC_PATHS=$(echo "${ACTION_SPEC_OPS}" | cut -d' ' -f2- | sort -u)

# ── Step 3: Compute drift ────────────────────────────────────────────────────
DATA_CODE_ONLY=$(comm -23 \
  <(echo "${DATA_CODE_OPS}" | sort -u) \
  <(echo "${DATA_SPEC_OPS}" | sort -u))

DATA_SPEC_ONLY=$(comm -13 \
  <(echo "${DATA_CODE_OPS}" | sort -u) \
  <(echo "${DATA_SPEC_OPS}" | sort -u))

ACTION_CODE_ONLY=$(comm -23 \
  <(echo "${ACTION_CODE_OPS}" | sort -u) \
  <(echo "${ACTION_SPEC_OPS}" | sort -u))

ACTION_SPEC_ONLY=$(comm -13 \
  <(echo "${ACTION_CODE_OPS}" | sort -u) \
  <(echo "${ACTION_SPEC_OPS}" | sort -u))

ACTION_MISSING_METADATA=$(extract_missing_action_metadata_ops "${ACTION_OPENAPI_FILE}")
ACTION_MISSING_IDEMPOTENCY=$(extract_action_missing_idempotency_ops "${ACTION_OPENAPI_FILE}")
ACTION_MISSING_ENVELOPE_RESPONSE=$(extract_action_missing_envelope_response_ops "${ACTION_OPENAPI_FILE}")
ACTION_MUTATING_REQUEST_WITHOUT_ENVELOPE=$(extract_action_mutating_request_without_envelope_ops "${ACTION_OPENAPI_FILE}")

ACTION_GENERIC_RESPONSE_OPS=$(extract_action_generic_response_ops "${ACTION_OPENAPI_FILE}")
if [[ -f "${ACTION_GENERIC_RESPONSE_WAIVERS_FILE}" ]]; then
  ACTION_GENERIC_RESPONSE_WAIVERS=$(grep -Ev '^[[:space:]]*#|^[[:space:]]*$' "${ACTION_GENERIC_RESPONSE_WAIVERS_FILE}" | sort -u || true)
else
  ACTION_GENERIC_RESPONSE_WAIVERS=""
fi

ACTION_UNWAIVED_GENERIC_RESPONSE_OPS=$(comm -23 \
  <(echo "${ACTION_GENERIC_RESPONSE_OPS}" | sort -u) \
  <(echo "${ACTION_GENERIC_RESPONSE_WAIVERS}" | sort -u))

ACTION_STALE_GENERIC_RESPONSE_WAIVERS=$(comm -23 \
  <(echo "${ACTION_GENERIC_RESPONSE_WAIVERS}" | sort -u) \
  <(echo "${ACTION_GENERIC_RESPONSE_OPS}" | sort -u))

COMPATIBILITY_SPEC_IN_DATA=$(comm -12 \
  <(echo "${DATA_SPEC_PATHS}") \
  <(echo "${COMPATIBILITY_ROUTES}"))

# ── Step 4: Report ───────────────────────────────────────────────────────────
DRIFT_FOUND=false

if [[ -n "${DATA_CODE_ONLY}" ]]; then
  DRIFT_FOUND=true
  echo ""
  echo "⚠️  ROUTES IN CODE BUT MISSING FROM contracts/openapi/data-cloud.yaml"
  echo "   These non-Action Data Cloud routes are live but not documented:"
  while IFS= read -r route; do
    echo "   + ${route}"
  done <<< "${DATA_CODE_ONLY}"
fi

if [[ -n "${DATA_SPEC_ONLY}" ]]; then
  DRIFT_FOUND=true
  echo ""
  echo "⚠️  PATHS IN contracts/openapi/data-cloud.yaml BUT NOT IN CODE"
   echo "   These paths are documented but have no registered handler:"
  while IFS= read -r path; do
    echo "   - ${path}"
  done <<< "${DATA_SPEC_ONLY}"
fi

if [[ -n "${ACTION_CODE_ONLY}" ]]; then
  DRIFT_FOUND=true
  echo ""
  echo "⚠️  ACTION ROUTES IN CODE BUT MISSING FROM contracts/openapi/action-plane.yaml"
  echo "   These canonical Action Plane routes are live but not documented:"
  while IFS= read -r route; do
    echo "   + ${route}"
  done <<< "${ACTION_CODE_ONLY}"
fi

if [[ -n "${ACTION_SPEC_ONLY}" ]]; then
  DRIFT_FOUND=true
  echo ""
  echo "⚠️  PATHS IN contracts/openapi/action-plane.yaml BUT NOT IN CODE"
  echo "   These Action Plane paths are documented but have no registered handler:"
  while IFS= read -r path; do
    echo "   - ${path}"
  done <<< "${ACTION_SPEC_ONLY}"
fi

if [[ -n "${ACTION_MISSING_METADATA}" ]]; then
  DRIFT_FOUND=true
  echo ""
  echo "⚠️  ACTION OPERATIONS MISSING REQUIRED x-ghatana METADATA"
  echo "   Every Action operation must define sensitivity/access/policy/audit/legacy metadata:"
  while IFS= read -r operation; do
    echo "   - ${operation}"
  done <<< "${ACTION_MISSING_METADATA}"
fi

if [[ -n "${ACTION_MISSING_IDEMPOTENCY}" ]]; then
  DRIFT_FOUND=true
  echo ""
  echo "⚠️  MUTATING ACTION OPERATIONS MISSING IDEMPOTENCY HEADER"
  echo "   Every POST/PUT/PATCH/DELETE Action operation must include IdempotencyKeyHeader:" 
  while IFS= read -r operation; do
    echo "   - ${operation}"
  done <<< "${ACTION_MISSING_IDEMPOTENCY}"
fi

if [[ -n "${ACTION_MISSING_ENVELOPE_RESPONSE}" ]]; then
  DRIFT_FOUND=true
  echo ""
  echo "⚠️  ACTION OPERATIONS MISSING CANONICAL ENVELOPE RESPONSE"
  echo "   Every Action operation must return ActionPlaneEnvelope for 200 responses:" 
  while IFS= read -r operation; do
    echo "   - ${operation}"
  done <<< "${ACTION_MISSING_ENVELOPE_RESPONSE}"
fi

if [[ -n "${ACTION_MUTATING_REQUEST_WITHOUT_ENVELOPE}" ]]; then
  DRIFT_FOUND=true
  echo ""
  echo "⚠️  MUTATING ACTION REQUEST BODIES MUST USE CANONICAL ENVELOPE"
  echo "   POST/PUT/PATCH operations with requestBody must use ActionPlaneEnvelope:" 
  while IFS= read -r operation; do
    echo "   - ${operation}"
  done <<< "${ACTION_MUTATING_REQUEST_WITHOUT_ENVELOPE}"
fi

if [[ -n "${ACTION_UNWAIVED_GENERIC_RESPONSE_OPS}" ]]; then
  DRIFT_FOUND=true
  echo ""
  echo "⚠️  ACTION OPERATIONS USE GENERIC RESPONSE SCHEMA WITHOUT EXPLICIT WAIVER"
  echo "   Add explicit waivers in ${ACTION_GENERIC_RESPONSE_WAIVERS_FILE} or replace with typed schema:"
  while IFS= read -r operation; do
    echo "   - ${operation}"
  done <<< "${ACTION_UNWAIVED_GENERIC_RESPONSE_OPS}"
fi

if [[ -n "${ACTION_STALE_GENERIC_RESPONSE_WAIVERS}" ]]; then
  DRIFT_FOUND=true
  echo ""
  echo "⚠️  STALE ACTION GENERIC RESPONSE WAIVERS DETECTED"
  echo "   These waiver entries no longer match any generic response operation and must be removed:" 
  while IFS= read -r operation; do
    echo "   - ${operation}"
  done <<< "${ACTION_STALE_GENERIC_RESPONSE_WAIVERS}"
fi

if [[ -n "${UNREGISTERED_NON_ACTION_COMPATIBILITY}" ]]; then
  DRIFT_FOUND=true
  echo ""
  echo "⚠️  LEGACY ACTION-STYLE ROUTES IN CODE BUT MISSING FROM route-compatibility-registry.yaml"
  echo "   These non-Action routes look like deprecated Action aliases and must be explicitly registered:"
  while IFS= read -r route; do
    echo "   + ${route}"
  done <<< "${UNREGISTERED_NON_ACTION_COMPATIBILITY}"
fi

if [[ -n "${COMPATIBILITY_SPEC_IN_DATA}" ]]; then
  DRIFT_FOUND=true
  echo ""
  echo "⚠️  COMPATIBILITY ROUTES PRESENT IN contracts/openapi/data-cloud.yaml"
  echo "   Legacy Action aliases belong in route-compatibility-registry.yaml/aep.yaml, not the canonical Data contract:"
  while IFS= read -r route; do
    echo "   - ${route}"
  done <<< "${COMPATIBILITY_SPEC_IN_DATA}"
fi

if [[ "${DRIFT_FOUND}" == "false" ]]; then
  echo "✅ No OpenAPI drift detected."
  echo "   Data operations: $(echo "${DATA_CODE_OPS}" | wc -l | tr -d ' ')"
  echo "   Data routes:     $(echo "${DATA_CODE_ROUTES}" | wc -l | tr -d ' ')"
  echo "   Data spec:     $(echo "${DATA_SPEC_PATHS}" | wc -l | tr -d ' ')"
  echo "   Action operations: $(echo "${ACTION_CODE_OPS}" | wc -l | tr -d ' ')"
  echo "   Action routes:     $(echo "${ACTION_CODE_ROUTES}" | wc -l | tr -d ' ')"
  echo "   Action spec:       $(echo "${ACTION_SPEC_PATHS}" | wc -l | tr -d ' ')"
  echo "   Registry routes:   $(echo "${COMPATIBILITY_ROUTES}" | wc -l | tr -d ' ')"
  echo "   Compatibility aliases: $(echo "${COMPATIBILITY_CODE_ROUTES}" | wc -l | tr -d ' ')"
  exit 0
fi

echo ""
if [[ "${WARN_ONLY}" == "true" ]]; then
  echo "⚠️  OpenAPI drift found (--warn-only: not failing build)."
  exit 0
else
  echo "❌ OpenAPI drift found. Update data-cloud.yaml/action-plane.yaml according to route ownership."
  exit 1
fi
