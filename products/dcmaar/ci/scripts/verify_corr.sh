#!/usr/bin/env bash
set -euo pipefail

# OBSOLETE: This script references services/agent-rs which has been removed
# TODO: Update to use appropriate agent package from workspace
exit 0

# Corr ID end-to-end verification script
# - Starts server container (if not already)
# - Runs agent one-shot with deterministic corr_id
# - Greps server logs for that corr_id
# - Verifies HTTP /healthz and /metrics
# - Calls PolicyService with grpcurl, passing corr_id header

ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT_DIR"

require() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "[verify-corr] required tool missing: $1" >&2
    exit 2
  fi
}

require docker
require curl
require python3
require cargo
if ! command -v grpcurl >/dev/null 2>&1; then
  echo "[verify-corr] grpcurl not found; PolicyService check will be skipped" >&2
  SKIP_GRPCURL=1
else
  SKIP_GRPCURL=0
fi

echo "[verify-corr] Ensuring protos are generated..."
make -s proto >/dev/null || true

echo "[verify-corr] Starting server container..."
docker compose up -d --quiet-pull server

echo "[verify-corr] Waiting for gRPC on 127.0.0.1:50051 ..."
for i in $(seq 1 30); do
  (echo > /dev/tcp/127.0.0.1/50051) >/dev/null 2>&1 && { echo "[verify-corr] gRPC is up"; break; }
  sleep 1
  if [ "$i" -eq 30 ]; then echo "[verify-corr] Server did not come up in time" >&2; exit 1; fi
done

CORR_ID=$(python3 - <<'PY'
import uuid
print(uuid.uuid4())
PY
)
echo "[verify-corr] Using corr_id=$CORR_ID"

echo "[verify-corr] Running agent one-shot with corr_id..."
(
  cd services/agent-rs
  ONE_SHOT=1 DCMAR_TEST_CORR_ID="$CORR_ID" cargo run --quiet || true
)

echo "[verify-corr] Checking server logs for corr_id..."
if ! docker compose logs --no-color --tail=200 server | grep -q "$CORR_ID"; then
  echo "[verify-corr] WARNING: corr_id not found in last 200 log lines" >&2
fi

echo "[verify-corr] Submitting an example action to exercise policy + audit..."
# Ensure a deny policy exists for subject smoke-user (resource '*') in ClickHouse
echo "[verify-corr] Seeding deny policy for subject=smoke-user in ClickHouse..."
curl -sS "http://localhost:8123/?query=CREATE%20TABLE%20IF%20NOT%20EXISTS%20policies%20(subject%20String%2C%20resource%20String%2C%20version%20String%2C%20data%20String%2C%20schema_version%20UInt32)%20ENGINE%3DMergeTree%20ORDER%20BY%20(subject%2C%20resource)" \
  --user dcmaar:dcmaar123 >/dev/null || true
curl -sS "http://localhost:8123/?query=ALTER%20TABLE%20policies%20DELETE%20WHERE%20subject%3D'smoke-user'" \
  --user dcmaar:dcmaar123 >/dev/null || true
curl -sS -d "INSERT INTO policies (subject,resource,version,data,schema_version) VALUES ('smoke-user','*','test','{\"rules\":[{\"effect\":\"deny\",\"resources\":[\"*\"]}]}',1)" \
  "http://localhost:8123/" --user dcmaar:dcmaar123 >/dev/null || true

echo "[verify-corr] Submitting an example action to exercise policy + audit (expect PermissionDenied)..."
grpcurl -plaintext \
  -H "x-corr-id: $CORR_ID" \
  -H "authorization: Bearer $JWT_TOKEN" \
  -d '{"command":"echo","args":["hello"]}' \
  localhost:50051 pb.dcmaar.v1.ActionService/SubmitAction || true

echo "[verify-corr] Verifying HTTP endpoints..."
curl -fsS http://127.0.0.1:8080/healthz >/dev/null || { echo "[verify-corr] /healthz failed" >&2; exit 1; }
METRICS=$(curl -fsS http://127.0.0.1:8080/metrics) || { echo "[verify-corr] /metrics failed" >&2; exit 1; }
echo "$METRICS" | grep -q "ingest_dedupe_events_total" || { echo "[verify-corr] missing ingest_dedupe_events_total in metrics" >&2; exit 1; }
echo "$METRICS" | grep -q "ingest_idempotent_envelopes_skipped_total" || { echo "[verify-corr] missing ingest_idempotent_envelopes_skipped_total in metrics" >&2; exit 1; }
echo "$METRICS" | grep -q "policy_denied_total" || { echo "[verify-corr] missing policy_denied_total in metrics" >&2; exit 1; }

# Build a simple unsigned JWT with sub and roles for IdentityMiddleware
JWT_TOKEN=$(python3 - <<'PY'
import json,base64
hdr=base64.urlsafe_b64encode(json.dumps({"alg":"none","typ":"JWT"}).encode()).rstrip(b'=')
pld=base64.urlsafe_b64encode(json.dumps({"sub":"smoke-user","roles":["admin","dev"]}).encode()).rstrip(b'=')
print((hdr+b'.'+pld+b'.').decode())
PY
)
echo "[verify-corr] Calling /whoami with Authorization: Bearer (dev JWT)"
curl -fsS -H "Authorization: Bearer $JWT_TOKEN" http://127.0.0.1:8080/whoami || { echo "[verify-corr] /whoami failed" >&2; exit 1; }

if [ "$SKIP_GRPCURL" -eq 0 ]; then
  echo "[verify-corr] Calling PolicyService with corr header..."
  echo "[verify-corr] PolicyService response:" 
  grpcurl -plaintext \
    -H "x-corr-id: $CORR_ID" \
    -d '{"subject":"smoke-user","resources":["*"]}' \
    localhost:50051 pb.dcmaar.v1.PolicyService/GetPolicy || {
      echo "[verify-corr] PolicyService call failed" >&2; exit 1; }
  echo "[verify-corr] Calling gRPC Health with Authorization Bearer (dev JWT)"
  grpcurl -plaintext \
    -H "authorization: Bearer $JWT_TOKEN" \
    -d '{}' \
    localhost:50051 pb.dcmaar.v1.IngestService/Health || {
      echo "[verify-corr] gRPC Health call failed" >&2; exit 1; }
else
  echo "[verify-corr] Skipping grpcurl step (tool not installed)"
fi

echo "[verify-corr] Complete. To clean up: docker compose down -v"
