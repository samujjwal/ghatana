#!/bin/bash
# DC-P2-02: Secret scan validation for production readiness
# Scans for hardcoded secrets, API keys, and credentials in source code

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
SOURCE_DIR="$PROJECT_ROOT"

echo "[DC-P2-02] Running secret scan validation..."

# Patterns that indicate hardcoded secrets
PATTERNS=(
    "password\s*=\s*['\"]"
    "secret\s*=\s*['\"]"
    "api[_-]?key\s*=\s*['\"]"
    "access[_-]?token\s*=\s*['\"]"
    "private[_-]?key\s*=\s*['\"]"
    "aws[_-]?secret[_-]?access[_-]?key"
    "github[_-]?token"
    "bearer\s+[a-zA-Z0-9\-_]{20,}"
)

# Exclude common false positives
EXCLUDE_DIRS=(
    "node_modules"
    "build"
    "dist"
    ".gradle"
    "target"
    ".git"
    "vendor"
    "scripts"
    "test"
    "tests"
    "__tests__"
    ".vscode"
    ".idea"
)

# Build exclude arguments for grep
EXCLUDE_ARGS=()
for dir in "${EXCLUDE_DIRS[@]}"; do
    EXCLUDE_ARGS+=("--exclude-dir=$dir")
done

VIOLATIONS_FOUND=0

for pattern in "${PATTERNS[@]}"; do
    echo "[DC-P2-02] Checking for pattern: $pattern"
    if grep -rEi "$pattern" "$SOURCE_DIR" "${EXCLUDE_ARGS[@]}" \
        --include="*.java" \
        --include="*.ts" \
        --include="*.tsx" \
        --include="*.js" \
        --include="*.json" \
        --include="*.yaml" \
        --include="*.yml" \
        --include="*.properties" \
        --include="*.gradle" \
        --include="*.kts" 2>/dev/null; then
        echo "[ERROR] Potential secret found matching pattern: $pattern"
        VIOLATIONS_FOUND=1
    fi
done

# Check for environment variable usage in production code
echo "[DC-P2-02] Checking for hardcoded environment variable values..."
if grep -r "process\.env\." "$SOURCE_DIR/delivery/ui/src" \
    --include="*.ts" \
    --include="*.tsx" \
    --exclude-dir=node_modules 2>/dev/null | grep -v "process.env.NODE_ENV\|process.env.PUBLIC" | grep -q .; then
    echo "[WARNING] Environment variable usage detected - ensure secrets are not hardcoded"
fi

# Check for base64-encoded strings that might be secrets
echo "[DC-P2-02] Checking for suspicious base64 strings..."
if grep -rE "[A-Za-z0-9+/]{40,}={0,2}" "$SOURCE_DIR" \
    --include="*.java" \
    --include="*.ts" \
    --include="*.tsx" \
    --include="*.properties" \
    --include="*.yaml" \
    --include="*.yml" \
    "${EXCLUDE_ARGS[@]}" 2>/dev/null | grep -i "key\|secret\|token\|password" | grep -q .; then
    echo "[WARNING] Suspicious base64 strings found - review manually"
fi

if [ $VIOLATIONS_FOUND -eq 0 ]; then
    echo "[DC-P2-02] ✓ Secret scan validation passed - no hardcoded secrets detected"
else
    echo "[ERROR] DC-P2-02: Secret scan validation failed - hardcoded secrets detected"
    echo "Review the violations above and remove any hardcoded credentials"
    exit 1
fi
