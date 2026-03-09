#!/usr/bin/env bash
set -euo pipefail

# Run Playwright tests sequentially in-foreground for each browser.
# This runs each browser job one after another in the same terminal so you
# can observe logs live and stop on first failure.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "Building browser bundles..."
pnpm run build:chrome
pnpm run build:firefox
pnpm run build:edge

# Accept optional test path or flags as args (e.g. tests/setup-wizard.spec.ts -u)
FAIL_FAST=0
ARGS=()
for a in "$@"; do
	if [ "$a" = "--fail-fast" ]; then
		FAIL_FAST=1
	else
		ARGS+=("$a")
	fi
done

echo
echo "=== Running Playwright (chromium) ==="
pnpm exec playwright test --project=chromium "${ARGS[@]}" || {
	echo "Chromium tests failed"
	if [ "$FAIL_FAST" -eq 1 ]; then exit 1; fi
}

echo
echo "=== Running Playwright (firefox) ==="
# Some firefox tests need a profile setup
node scripts/setup-firefox-profile.js
pnpm exec playwright test --project=firefox "${ARGS[@]}" || {
	echo "Firefox tests failed"
	if [ "$FAIL_FAST" -eq 1 ]; then exit 1; fi
}

echo
echo "=== Running Playwright (edge) ==="
pnpm exec playwright test --project=edge "${ARGS[@]}" || {
	echo "Edge tests failed"
	if [ "$FAIL_FAST" -eq 1 ]; then exit 1; fi
}

echo
echo "All Playwright jobs completed."
