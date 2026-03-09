#!/usr/bin/env bash
set -euo pipefail

# Run a Playwright test matrix locally in isolated processes per browser.
# This helps reproduce CI matrix runs without test-runner global collisions.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "Building browser bundles..."
pnpm run build:chrome
pnpm run build:firefox
pnpm run build:edge

echo "Starting Playwright per-browser in isolated processes..."

# Accept optional test path or flags as args (e.g. tests/setup-wizard.spec.ts -u)
ARGS=("$@")

# Launch each Playwright project in its own background process and capture logs/PIDs
nohup pnpm exec playwright test --project=chromium "${ARGS[@]}" > ../playwright-chromium.log 2>&1 &
echo $! > ../playwright-chromium.pid

nohup pnpm exec playwright test --project=firefox "${ARGS[@]}" > ../playwright-firefox.log 2>&1 &
echo $! > ../playwright-firefox.pid

nohup pnpm exec playwright test --project=edge "${ARGS[@]}" > ../playwright-edge.log 2>&1 &
echo $! > ../playwright-edge.pid

echo "Launched Playwright processes (PIDs):"
cat ../playwright-*.pid || true

echo "Logs:"
ls -1 ../playwright-*.log || true

echo "To follow a log run: tail -f ../playwright-chromium.log"
