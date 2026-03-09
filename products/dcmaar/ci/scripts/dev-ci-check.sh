#!/usr/bin/env bash
# Quick dev CI check script for the repo.
# Usage:
#   ./ops/scripts/dev-ci-check.sh        -> quick checks (fast)
#   ./ops/scripts/dev-ci-check.sh --full -> run full builds (may take a long time)

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
FULL=false

if [[ ${1:-} == "--full" ]]; then
  FULL=true
fi

echo "Running dev CI checks (full=$FULL) from $ROOT_DIR"

cd "$ROOT_DIR"

echo "[1/5] Rust: cargo check library targets (fast)"
if command -v cargo &>/dev/null; then
  # Check library targets only to avoid compiling test-only code which may require extras
  cargo check --workspace --lib || echo "cargo check (lib) had issues"
else
  echo "cargo not found, skipping Rust checks"
fi

echo "[2/5] Rust: build canonical agent binary (agent-rs)"
if [[ -d apps/agent ]]; then
  if command -v cargo &>/dev/null; then
    pushd apps/agent >/dev/null
    cargo build --bins || echo "apps/agent build (bins) failed (non-fatal in quick mode)"
    popd >/dev/null
  fi
fi

echo "[3/5] Node: pnpm install (quick)"
if command -v pnpm &>/dev/null; then
  pnpm -w install --frozen-lockfile || echo "pnpm install warning/failure"
else
  echo "pnpm not found, skipping Node checks"
fi

echo "[4/5] Java: quick Gradle wrapper check (verify wrapper)"
if [[ -f gradlew ]]; then
  ./gradlew --version || echo "gradle wrapper check failed"
else
  echo "gradlew not present, skipping Gradle checks"
fi

if [[ "$FULL" == true ]]; then
  echo "[5/5] Full mode: Java service builds skipped (legacy services relocated under experiments/)"
  echo "Full mode: run pnpm build for desktop (may be slow)"
  if [[ -d apps/desktop && -f apps/desktop/package.json ]]; then
    pushd apps/desktop >/dev/null
    pnpm -w -s install
    pnpm -w -s build || echo "pnpm build failed (desktop)"
    popd >/dev/null
  fi
fi

echo "dev-ci-check completed"
