#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT_DIR"

require() { command -v "$1" >/dev/null 2>&1 || { echo "missing: $1" >&2; exit 2; }; }
require grpcurl
require curl

echo "[rego] Ensuring test env (ClickHouse/Redis) is up..."
make -s test-env-up

echo "[rego] Building server with -tags=opa..."
make -s server-opa

CONFIG_FILE="$ROOT_DIR/services/server/config/config.rego.yaml"
SERVER_BIN="$ROOT_DIR/services/server/bin/server-opa"

echo "[rego] Starting server..."
CONFIG_FILE="$CONFIG_FILE" "$SERVER_BIN" >/tmp/dcmaar-server-opa.log 2>&1 &
PID=$!
trap 'kill $PID 2>/dev/null || true' EXIT

for i in $(seq 1 30); do
  (echo > /dev/tcp/127.0.0.1/50051) >/dev/null 2>&1 && { echo "[rego] gRPC up"; break; }
  sleep 1
  if [ "$i" -eq 30 ]; then echo "[rego] server did not start" >&2; exit 1; fi
done

JWT_TOKEN=$(python3 - <<'PY'
import json,base64
hdr=base64.urlsafe_b64encode(json.dumps({"alg":"none","typ":"JWT"}).encode()).rstrip(b'=')
pld=base64.urlsafe_b64encode(json.dumps({"sub":"rego-user","roles":["admin"]}).encode()).rstrip(b'=')
print((hdr+b'.'+pld+b'.').decode())
PY
)

echo "[rego] SubmitAction allow (echo)..."
grpcurl -plaintext \
  -H "authorization: Bearer $JWT_TOKEN" \
  -d '{"command":"echo","args":["hello"]}' \
  127.0.0.1:50051 pb.dcmaar.v1.ActionService/SubmitAction

echo "[rego] SubmitAction deny (unknown, default-deny)..."
set +e
grpcurl -plaintext \
  -H "authorization: Bearer $JWT_TOKEN" \
  -d '{"command":"delete","args":["resource"]}' \
  127.0.0.1:50051 pb.dcmaar.v1.ActionService/SubmitAction
RC=$?
set -e
if [ "$RC" -eq 0 ]; then
  echo "[rego] expected PermissionDenied but call succeeded" >&2
  exit 1
fi

echo "[rego] OK — Rego allow/deny behavior observed"

