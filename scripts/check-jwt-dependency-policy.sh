#!/bin/bash
#
# Enforce canonical JWT dependency policy:
# - no io.jsonwebtoken dependencies in build files
# - no io.jsonwebtoken imports in Java sources
#

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

cd "$ROOT_DIR"

EXIT_CODE=0

echo "Checking JWT dependency policy (Nimbus-only)..."

SEARCH_TOOL=""
if command -v rg >/dev/null 2>&1; then
  SEARCH_TOOL="rg"
else
  SEARCH_TOOL="grep"
fi

echo ""
echo "Scanning Gradle files for io.jsonwebtoken..."
if [[ "$SEARCH_TOOL" == "rg" ]]; then
  if rg "io\\.jsonwebtoken" --glob "**/*.gradle" --glob "**/*.gradle.kts" --glob "!**/build/**" --glob "!**/node_modules/**"; then
    echo ""
    echo "Policy violation: found io.jsonwebtoken in build files."
    EXIT_CODE=1
  else
    echo "No build-file violations found."
  fi
else
  GRADLE_MATCHES="$(find . \( -name "*.gradle" -o -name "*.gradle.kts" \) -not -path "*/build/*" -not -path "*/node_modules/*" -print0 | xargs -0 grep -nE "io\.jsonwebtoken" 2>/dev/null || true)"
  if [[ -n "$GRADLE_MATCHES" ]]; then
    echo "$GRADLE_MATCHES"
    echo ""
    echo "Policy violation: found io.jsonwebtoken in build files."
    EXIT_CODE=1
  else
    echo "No build-file violations found."
  fi
fi

echo ""
echo "Scanning Java sources for io.jsonwebtoken imports..."
if [[ "$SEARCH_TOOL" == "rg" ]]; then
  if rg "^import\\s+io\\.jsonwebtoken" --glob "**/*.java" --glob "!**/build/**" --glob "!**/node_modules/**"; then
    echo ""
    echo "Policy violation: found io.jsonwebtoken imports in Java sources."
    EXIT_CODE=1
  else
    echo "No Java import violations found."
  fi
else
  JAVA_MATCHES="$(find . -name "*.java" -not -path "*/build/*" -not -path "*/node_modules/*" -print0 | xargs -0 grep -nE "^import[[:space:]]+io\.jsonwebtoken" 2>/dev/null || true)"
  if [[ -n "$JAVA_MATCHES" ]]; then
    echo "$JAVA_MATCHES"
    echo ""
    echo "Policy violation: found io.jsonwebtoken imports in Java sources."
    EXIT_CODE=1
  else
    echo "No Java import violations found."
  fi
fi

if [[ "$EXIT_CODE" -eq 0 ]]; then
  echo ""
  echo "JWT dependency policy check passed."
fi

exit "$EXIT_CODE"
