#!/usr/bin/env bash
# Generated Artifact Leak Detection
#
# Checks for committed generated artifacts that should not be in version control.
# Based on docs/GENERATED_ARTIFACT_POLICY.md
#
# Exit: 0 = clean, 1 = generated artifacts committed

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

# Patterns that should never be committed
GENERATED_PATTERNS=(
  # Java proto stubs
  "build/generated/source/proto/"
  "*.bin"
  "*.pb"
  
  # Compiled classes and JARs
  "build/classes/"
  "*.class"
  "build/libs/*.jar"
  
  # Gradle reports
  "build/reports/"
  
  # TypeScript build output
  "/dist/"
  "*.js.map"
  "*.d.ts.map"
  
  # pnpm cache
  ".pnpm-store/"
  "node_modules/"
  
  # Next.js cache
  ".next/"
  
  # Coverage reports
  "/coverage/"
  "*.lcov"
  
  # OpenAPI client stubs
  "build/generated-sources/"
  
  # Playwright test results
  "test-results/"
  "playwright-report/"
  
  # Prisma generated files (except in allowed locations)
  "prisma/generated/"
)

VIOLATIONS=0

echo "Checking for committed generated artifacts..."

for pattern in "${GENERATED_PATTERNS[@]}"; do
  if git ls-files --error-unmatch -- "$pattern" 2>/dev/null | grep -q .; then
    echo "ERROR: Found committed files matching pattern: $pattern"
    git ls-files -- "$pattern"
    VIOLATIONS=$((VIOLATIONS + 1))
  fi
done

# Check for dist/ directories in platform/typescript packages
if git ls-files --error-unmatch "platform/typescript/*/dist" 2>/dev/null | grep -q .; then
  echo "ERROR: Found committed dist/ directories in platform/typescript packages"
  git ls-files -- "platform/typescript/*/dist"
  VIOLATIONS=$((VIOLATIONS + 1))
fi

# Check for build/ directories in platform/java modules (except specific allowed patterns)
if git ls-files --error-unmatch "platform/java/*/build/classes" 2>/dev/null | grep -q .; then
  echo "ERROR: Found committed build/classes in platform/java modules"
  git ls-files -- "platform/java/*/build/classes"
  VIOLATIONS=$((VIOLATIONS + 1))
fi

if [ $VIOLATIONS -eq 0 ]; then
  echo "OK: no committed generated artifacts found."
  exit 0
fi

echo "FAIL: found $VIOLATIONS pattern violation(s) of committed generated artifacts."
echo "See docs/GENERATED_ARTIFACT_POLICY.md for the complete policy."
exit 1
