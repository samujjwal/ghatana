#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

EXIT_CODE=0

if grep -RIn "apps/guardian" "$REPO_ROOT/framework" >/dev/null 2>&1; then
  echo "ERROR: framework/* must not import from apps/guardian/*."
  EXIT_CODE=1
fi

if grep -RIn "apps/device-health" "$REPO_ROOT/framework" >/dev/null 2>&1; then
  echo "ERROR: framework/* must not import from apps/device-health/*."
  EXIT_CODE=1
fi

if grep -RIn "../apps/" "$REPO_ROOT/framework" >/dev/null 2>&1; then
  echo "ERROR: framework/* must not use relative imports into apps/*."
  EXIT_CODE=1
fi

if [ "$EXIT_CODE" -eq 0 ]; then
  echo "Architecture guardrails check passed."
fi

exit "$EXIT_CODE"
