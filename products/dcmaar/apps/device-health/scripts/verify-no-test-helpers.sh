#!/usr/bin/env bash
set -euo pipefail

# Verify that built dist artifacts do not contain test-only helper symbols.
# Usage: run from services/extension directory or via package.json postbuild.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "Verifying built artifacts for test-only helpers..."

PATTERN='mountOptions\|OptionsUI\|optionsManager\|__DCMAAR_TEST_HELPERS\|window.__DCMAAR_TEST_HELPERS'

matches=$(grep -R -n -- "${PATTERN}" dist || true)

if [ -n "${matches}" ]; then
  echo "ERROR: Test-only helper symbols found in built artifacts:" >&2
  echo "${matches}" >&2
  exit 1
fi

echo "OK: No test-only helper symbols found in dist."

exit 0
