#!/usr/bin/env bash
# P0-05: CI check script to validate route security and runtime-truth drift.
#
# This is a thin orchestration gate over the canonical checks:
# - OpenAPI ownership parity against DataCloudRouterBuilder
# - RouteSecurityRegistry parity against DataCloudRouterBuilder
# - Generated UI/runtime/security metadata freshness

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PRODUCT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "[P0-05] Checking route security drift..."

bash "${SCRIPT_DIR}/check-openapi-drift.sh"

node "${SCRIPT_DIR}/generate-route-manifest.mjs" --check

node "${SCRIPT_DIR}/generate-route-security-metadata.mjs" --check

echo "[P0-05] Route security drift check completed"
