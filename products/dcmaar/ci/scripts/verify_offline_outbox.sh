#!/usr/bin/env bash
set -euo pipefail

# OBSOLETE: This script references services/agent-rs which has been removed
# TODO: Update to use appropriate agent package from workspace
exit 0

# Verify agent offline outbox behavior: persist during outage and drain on restore.
# Requirements:
# - Docker available for server container or server running locally
# - Agent binary runnable via cargo (workspace root)
#
# Usage:
#   scripts/verify_offline_outbox.sh
#
# Steps:
# 1) Start server and ensure healthy
# 2) Stop server to simulate outage
# 3) Run agent once with DCMAR_OUTBOX_DIR set and force a send attempt (metrics)
# 4) Confirm .outbox contains files
# 5) Start server
# 6) Run agent again to trigger drain
# 7) Confirm .outbox drained

ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT_DIR"

OUTBOX_DIR=".outbox"
mkdir -p "$OUTBOX_DIR"

info() { echo "[offline-outbox] $*"; }

die() { echo "[offline-outbox] ERROR: $*" >&2; exit 1; }

# Helper: check outbox count
count_outbox() {
  find "$OUTBOX_DIR" -type f 2>/dev/null | wc -l | tr -d ' '
}

# Bring server up (compose or make). If you already have server, skip.
if command -v make >/dev/null 2>&1; then
  info "Starting server (make up - detached)"
  make up >/dev/null 2>&1 || true
  sleep 3
fi

# Quick health check (optional)
if command -v grpcurl >/dev/null 2>&1; then
  grpcurl -plaintext -d '{}' localhost:50051 pb.dcmaar.v1.IngestService/Health >/dev/null 2>&1 || true
fi

# Stop server to simulate outage
if command -v make >/dev/null 2>&1; then
  info "Stopping server to simulate outage"
  make down >/dev/null 2>&1 || true
  sleep 2
fi

# Run agent one-shot to generate metrics and try to send (will fail -> outbox)
info "Running agent: expect failure and outbox persistence"
DCMAR_OUTBOX_DIR="$OUTBOX_DIR" \
RUST_LOG=info \
CONFIG_PATH=${CONFIG_PATH:-services/agent-rs/agent-config.toml} \
cargo run -p agent-rs --quiet >/dev/null 2>&1 || true

cnt=$(count_outbox)
if [ "$cnt" -eq 0 ]; then
  die "Expected outbox files during outage, found 0"
fi
info "Outbox contains $cnt file(s) after outage"

# Bring server back
if command -v make >/dev/null 2>&1; then
  info "Starting server again"
  make up >/dev/null 2>&1 || true
  sleep 3
fi

# Run agent again to trigger drain
info "Running agent to drain outbox"
DCMAR_OUTBOX_DIR="$OUTBOX_DIR" \
RUST_LOG=info \
CONFIG_PATH=${CONFIG_PATH:-services/agent-rs/agent-config.toml} \
cargo run -p agent-rs --quiet >/dev/null 2>&1 || true

post_cnt=$(count_outbox)
if [ "$post_cnt" -gt 0 ]; then
  die "Expected outbox to be drained, found $post_cnt file(s) remaining"
fi

info "Success: outbox drained successfully"
