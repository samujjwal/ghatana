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
MIN_EVENT_BURST_THROUGHPUT="${DATACLOUD_LOAD_MIN_EVENT_BURST_THROUGHPUT_OPS_PER_SECOND:-0}"
EVENT_BURST_BATCH_SIZE="${DATACLOUD_LOAD_EVENT_BURST_BATCH_SIZE:-10000}"
MAX_HEAP_DELTA_MB="${DATACLOUD_LOAD_MAX_HEAP_DELTA_MB:-256}"
MAX_P95_ENTITY_SAVE_MS="${DATACLOUD_LOAD_MAX_P95_ENTITY_SAVE_MS:-2500}"
MAX_P95_EVENT_APPEND_MS="${DATACLOUD_LOAD_MAX_P95_EVENT_APPEND_MS:-2500}"
MAX_P95_QUERY_MS="${DATACLOUD_LOAD_MAX_P95_QUERY_MS:-2500}"
MAX_P99_ENTITY_SAVE_MS="${DATACLOUD_LOAD_MAX_P99_ENTITY_SAVE_MS:-0}"
MAX_P99_QUERY_MS="${DATACLOUD_LOAD_MAX_P99_QUERY_MS:-0}"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required to run DurableMultiTenantLoadIntegrationTest." >&2
  exit 1
fi

mkdir -p "${OUTPUT_DIR}"

# Gradle precompiled script metadata can become stale across convention edits.
# Clearing only generated build output keeps the dedicated load runner repeatable.
if [[ -d "${REPO_ROOT}/build-logic/conventions/build" ]]; then
  find "${REPO_ROOT}/build-logic/conventions/build" -depth -mindepth 1 -delete
  rmdir "${REPO_ROOT}/build-logic/conventions/build"
fi

DATACLOUD_DURABLE_LOAD_ENABLED=true \
TESTCONTAINERS_RYUK_DISABLED=true \
TESTCONTAINERS_HOST_OVERRIDE=localhost \
"${REPO_ROOT}/gradlew" -p "${REPO_ROOT}/products/data-cloud" :products:data-cloud:platform-plugins:test \
  --no-daemon \
  --no-parallel \
  --no-configuration-cache \
  -Dkotlin.incremental=false \
  --tests "*DurableMultiTenantLoadIntegrationTest" \
  --rerun-tasks \
  -Ddatacloud.load.tenants="${TENANTS}" \
  -Ddatacloud.load.entityOpsPerTenant="${ENTITY_OPS}" \
  -Ddatacloud.load.eventOpsPerTenant="${EVENT_OPS}" \
  -Ddatacloud.load.iterations="${ITERATIONS}" \
  -Ddatacloud.load.timeoutSeconds="${TIMEOUT_SECONDS}" \
  -Ddatacloud.load.minThroughputOpsPerSecond="${MIN_THROUGHPUT}" \
  -Ddatacloud.load.minEventBurstThroughputOpsPerSecond="${MIN_EVENT_BURST_THROUGHPUT}" \
  -Ddatacloud.load.eventBurstBatchSize="${EVENT_BURST_BATCH_SIZE}" \
  -Ddatacloud.load.maxHeapDeltaMb="${MAX_HEAP_DELTA_MB}" \
  -Ddatacloud.load.maxP95EntitySaveMs="${MAX_P95_ENTITY_SAVE_MS}" \
  -Ddatacloud.load.maxP95EventAppendMs="${MAX_P95_EVENT_APPEND_MS}" \
  -Ddatacloud.load.maxP95QueryMs="${MAX_P95_QUERY_MS}" \
  -Ddatacloud.load.maxP99EntitySaveMs="${MAX_P99_ENTITY_SAVE_MS}" \
  -Ddatacloud.load.maxP99QueryMs="${MAX_P99_QUERY_MS}" \
  -Ddatacloud.load.metricsOutput="${OUTPUT_FILE}"

echo "Durable load metrics written to ${OUTPUT_FILE}"