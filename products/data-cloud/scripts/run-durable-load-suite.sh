#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
OUTPUT_DIR="${REPO_ROOT}/products/data-cloud/build/reports/load-tests"
OUTPUT_FILE="${OUTPUT_DIR}/durable-multi-tenant-load.json"

TENANTS="${DATACLOUD_LOAD_TENANTS:-100}"
ENTITY_OPS="${DATACLOUD_LOAD_ENTITY_OPS_PER_TENANT:-25}"
EVENT_OPS="${DATACLOUD_LOAD_EVENT_OPS_PER_TENANT:-25}"
ITERATIONS="${DATACLOUD_LOAD_ITERATIONS:-1}"
TIMEOUT_SECONDS="${DATACLOUD_LOAD_TIMEOUT_SECONDS:-1800}"
MIN_THROUGHPUT="${DATACLOUD_LOAD_MIN_THROUGHPUT_OPS_PER_SECOND:-0}"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required to run DurableMultiTenantLoadIntegrationTest." >&2
  exit 1
fi

mkdir -p "${OUTPUT_DIR}"

cd "${REPO_ROOT}"

./gradlew :products:data-cloud:platform-plugins:test \
  --tests "*DurableMultiTenantLoadIntegrationTest" \
  -Ddatacloud.load.tenants="${TENANTS}" \
  -Ddatacloud.load.entityOpsPerTenant="${ENTITY_OPS}" \
  -Ddatacloud.load.eventOpsPerTenant="${EVENT_OPS}" \
  -Ddatacloud.load.iterations="${ITERATIONS}" \
  -Ddatacloud.load.timeoutSeconds="${TIMEOUT_SECONDS}" \
  -Ddatacloud.load.minThroughputOpsPerSecond="${MIN_THROUGHPUT}" \
  -Ddatacloud.load.metricsOutput="${OUTPUT_FILE}"

echo "Durable load metrics written to ${OUTPUT_FILE}"