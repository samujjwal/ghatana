#!/bin/bash
# CI Check: Fastify Version Alignment
# Ensures all modules use consistent Fastify version

set -e

TARGET_VERSION="5.7.4"
ALLOWED_VERSIONS=("^5.7.4" "~5.7.4" ">=5.0.0 <6.0.0")

echo "Fastify Version Check"
echo "====================="
echo "Target version: $TARGET_VERSION"
echo ""

# Find all package.json files
PACKAGE_FILES=$(find . -name "package.json" -not -path "*/node_modules/*" -not -path "*/dist/*")

VIOLATIONS=()

for file in $PACKAGE_FILES; do
  if grep -q '"fastify"' "$file" 2>/dev/null; then
    version=$(grep -o '"fastify": "[^"]*"' "$file" | cut -d'"' -f4)
    module=$(dirname "$file" | sed 's|^\./||')
    
    # Check if version is allowed
    is_allowed=false
    for allowed in "${ALLOWED_VERSIONS[@]}"; do
      if [[ "$version" == "$allowed" ]]; then
        is_allowed=true
        break
      fi
    done
    
    if [ "$is_allowed" = false ]; then
      VIOLATIONS+=("$module: $version")
      echo "❌ $module - Found: $version (Expected: ^5.7.4)"
    else
      echo "✅ $module - $version"
    fi
  fi
done

echo ""

if [ ${#VIOLATIONS[@]} -eq 0 ]; then
  echo "✅ All modules use aligned Fastify version"
  exit 0
else
  echo "❌ Found ${#VIOLATIONS[@]} module(s) with misaligned Fastify versions:"
  for v in "${VIOLATIONS[@]}"; do
    echo "   - $v"
  done
  echo ""
  echo "To fix, update package.json files to use: \"fastify\": \"^5.7.4\""
  exit 1
fi
