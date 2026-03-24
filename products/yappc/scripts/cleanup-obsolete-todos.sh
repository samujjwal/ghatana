#!/bin/bash

###############################################################################
# Cleanup Obsolete TODOs
#
# Removes obsolete TODOs identified by the reduction analysis
# Creates backup before removal for safety
###############################################################################

set -e

YAPPC_ROOT="/Users/samujjwal/Development/ghatana/products/yappc"
BACKUP_DIR="$YAPPC_ROOT/.todo-cleanup-backup-$(date +%Y%m%d-%H%M%S)"

echo "🧹 Starting TODO cleanup..."
echo ""

# Create backup directory
mkdir -p "$BACKUP_DIR"
echo "📦 Backup directory: $BACKUP_DIR"
echo ""

# Patterns for obsolete TODOs to remove
OBSOLETE_PATTERNS=(
    "update.*java\s*\d+"
    "migrate.*new.*api"
    "fix.*later"
    "cleanup\s*$"
    "improve\s*$"
    "consider\s*$"
    "maybe\s*$"
    "placeholder"
    "refactor.*this"
    "optimize.*this"
)

# Function to backup and clean a file
cleanup_file() {
    local file="$1"
    local pattern="$2"
    
    if [ ! -f "$file" ]; then
        return
    fi
    
    # Check if file contains the pattern
    if grep -qiE "$pattern" "$file" 2>/dev/null; then
        # Create backup
        local rel_path="${file#$YAPPC_ROOT/}"
        local backup_file="$BACKUP_DIR/$rel_path"
        mkdir -p "$(dirname "$backup_file")"
        cp "$file" "$backup_file"
        
        # Remove lines with obsolete TODOs
        sed -i.bak -E "/TODO.*$pattern/Id" "$file"
        sed -i.bak -E "/FIXME.*$pattern/Id" "$file"
        rm -f "${file}.bak"
        
        echo "  ✓ Cleaned: $rel_path"
    fi
}

# Count files to process
echo "🔍 Scanning for obsolete TODOs..."
total_files=0
for pattern in "${OBSOLETE_PATTERNS[@]}"; do
    count=$(find "$YAPPC_ROOT/core" "$YAPPC_ROOT/frontend" -type f \( -name "*.java" -o -name "*.ts" -o -name "*.tsx" \) -exec grep -liE "TODO.*$pattern|FIXME.*$pattern" {} \; 2>/dev/null | wc -l | tr -d ' ')
    total_files=$((total_files + count))
done

echo "📊 Found $total_files files with obsolete TODOs"
echo ""

if [ "$total_files" -eq 0 ]; then
    echo "✅ No obsolete TODOs found to clean"
    exit 0
fi

echo "🧹 Cleaning obsolete TODOs..."
echo ""

cleaned_count=0

# Process each pattern
for pattern in "${OBSOLETE_PATTERNS[@]}"; do
    echo "Processing pattern: $pattern"
    
    # Find and clean Java files
    while IFS= read -r file; do
        cleanup_file "$file" "$pattern"
        cleaned_count=$((cleaned_count + 1))
    done < <(find "$YAPPC_ROOT/core" -type f -name "*.java" -exec grep -liE "TODO.*$pattern|FIXME.*$pattern" {} \; 2>/dev/null || true)
    
    # Find and clean TypeScript files
    while IFS= read -r file; do
        cleanup_file "$file" "$pattern"
        cleaned_count=$((cleaned_count + 1))
    done < <(find "$YAPPC_ROOT/frontend" -type f \( -name "*.ts" -o -name "*.tsx" \) -exec grep -liE "TODO.*$pattern|FIXME.*$pattern" {} \; 2>/dev/null || true)
done

echo ""
echo "✅ Cleanup complete!"
echo ""
echo "📊 Summary:"
echo "  Files processed: $cleaned_count"
echo "  Backup location: $BACKUP_DIR"
echo ""
echo "🔍 Verifying cleanup..."

# Re-scan to verify
./scripts/scan-todos.sh

echo ""
echo "💡 To restore from backup if needed:"
echo "   cp -r $BACKUP_DIR/* $YAPPC_ROOT/"
