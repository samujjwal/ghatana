#!/bin/bash

# Go Code Cleanup Script
# Purpose: Safely remove Go code after migration to Java
# Date: 2025-09-29

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$PROJECT_ROOT"

echo "=== Go Code Cleanup Script ==="
echo "Project Root: $PROJECT_ROOT"
echo

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${GREEN}✓${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

# Function to confirm action
confirm() {
    read -p "$1 (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        return 1
    fi
    return 0
}

echo "This script will perform SAFE cleanup actions:"
echo "1. Remove proto_backup/ directory"
echo "2. Archive obsolete Go examples"
echo "3. Update .gitignore for Go artifacts"
echo
echo "These actions are safe because:"
echo "- proto_backup is a backup no longer needed"
echo "- Examples are archived, not deleted"
echo "- .gitignore changes don't affect existing files"
echo

if ! confirm "Do you want to proceed?"; then
    echo "Cleanup cancelled."
    exit 0
fi

echo
echo "=== Phase 1: Safe Cleanup Actions ==="
echo

# 1. Remove proto_backup
if [ -d "proto_backup" ]; then
    echo "Removing proto_backup directory..."
    rm -rf proto_backup/
    print_status "Removed proto_backup/"
else
    print_warning "proto_backup/ not found, skipping"
fi

# 2. Archive obsolete examples
echo
echo "Archiving obsolete Go examples..."
mkdir -p scripts/archive/examples

if [ -d "examples/plugins/hello" ]; then
    mv examples/plugins/hello scripts/archive/examples/
    print_status "Archived examples/plugins/hello"
else
    print_warning "examples/plugins/hello not found, skipping"
fi

# 3. Update .gitignore
echo
echo "Updating .gitignore..."

# Check if Go artifacts are already in .gitignore
if ! grep -q "# Go build artifacts" .gitignore 2>/dev/null; then
    cat >> .gitignore << 'EOF'

# Go build artifacts (added during migration cleanup)
*.exe
*.exe~
*.dll
*.so
*.dylib
*.test
*.out
go.work
EOF
    print_status "Updated .gitignore with Go artifacts"
else
    print_warning ".gitignore already contains Go artifacts section"
fi

echo
echo "=== Cleanup Summary ==="
echo
print_status "proto_backup/ removed"
print_status "Obsolete examples archived"
print_status ".gitignore updated"

echo
echo "=== Next Steps ==="
echo
echo "1. Review changes:"
echo "   git status"
echo
echo "2. Commit changes:"
echo "   git add -A"
echo "   git commit -m 'Clean up obsolete Go code and backups'"
echo
echo "3. For additional cleanup after validation, see:"
echo "   docs/operations/GO_CLEANUP_PLAN.md"
echo

print_status "Safe cleanup complete!"
