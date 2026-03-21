#!/bin/bash

# Safe cleanup script for deprecated kernel classes
# This script removes deprecated classes that have no active usage

set -e

echo "🔍 Starting deprecated classes cleanup..."

# Define deprecated classes to remove
declare -a DEPRECATED_CLASSES=(
    "platform/java/kernel/src/main/java/com/ghatana/kernel/capability/KernelCapability.java"
    "platform/java/kernel/src/main/java/com/ghatana/kernel/audit/CrossProductAuditService.java"
    "platform/java/kernel/src/main/java/com/ghatana/kernel/boundary/ProductBoundaryEnforcer.java"
)

declare -a DEPRECATED_APPPLATFORM_WORKFLOWS=(
    "products/app-platform/kernel/workflow-orchestration/src/main/java/com/ghatana/appplatform/workflow/CorporateActionWorkflowService.java"
    "products/app-platform/kernel/workflow-orchestration/src/main/java/com/ghatana/appplatform/workflow/RegulatoryReportSubmissionWorkflowService.java"
    "products/app-platform/kernel/workflow-orchestration/src/main/java/com/ghatana/appplatform/workflow/ReconciliationOrchestrationWorkflowService.java"
)

# Function to check if a class has active usage (excluding tests and docs)
check_usage() {
    local file_path="$1"
    local class_name=$(basename "$file_path" .java)
    
    echo "  🔍 Checking usage of $class_name..."
    
    # Look for active usage (excluding test files, docs, build artifacts, and the deprecated class itself)
    local usage_count=$(find /Users/samujjwal/Development/ghatana \
        -name "*.java" \
        -not -path "*/test/*" \
        -not -path "*/docs/*" \
        -not -path "*/build/*" \
        -not -path "$(dirname "$file_path")" \
        -exec grep -l "$class_name" {} \; | wc -l)
    
    if [ "$usage_count" -eq 0 ]; then
        echo "  ✅ No active usage found - safe to remove"
        return 0
    else
        echo "  ⚠️  Found $usage_count files with usage - keeping for now"
        return 1
    fi
}

# Function to backup and remove a file
backup_and_remove() {
    local file_path="$1"
    local backup_dir="backup/$(date +%Y%m%d_%H%M%S)"
    
    echo "  📦 Backing up to $backup_dir/$(basename "$file_path")"
    mkdir -p "$backup_dir"
    cp "$file_path" "$backup_dir/"
    
    echo "  🗑️  Removing $file_path"
    rm "$file_path"
}

# Process kernel deprecated classes
echo "📋 Processing kernel deprecated classes..."
for class_file in "${DEPRECATED_CLASSES[@]}"; do
    if [ -f "$class_file" ]; then
        echo "🔍 Processing $class_file..."
        if check_usage "$class_file"; then
            backup_and_remove "$class_file"
        fi
    else
        echo "⚠️  File not found: $class_file"
    fi
done

# Process AppPlatform deprecated workflows
echo "📋 Processing AppPlatform deprecated workflows..."
for workflow_file in "${DEPRECATED_APPPLATFORM_WORKFLOWS[@]}"; do
    if [ -f "$workflow_file" ]; then
        echo "🔍 Processing $workflow_file..."
        if check_usage "$workflow_file"; then
            backup_and_remove "$workflow_file"
        fi
    else
        echo "⚠️  File not found: $workflow_file"
    fi
done

# Test compilation after removal
echo "🧪 Testing compilation after cleanup..."
cd /Users/samujjwal/Development/ghatana/platform/java

if ./gradlew compileJava > /dev/null 2>&1; then
    echo "✅ Compilation successful - cleanup completed safely"
else
    echo "❌ Compilation failed - restoring from backup"
    # Find the most recent backup and restore
    latest_backup=$(ls -t ../backup/ | head -1)
    echo "🔄 Restoring from $latest_backup"
    cp ../backup/"$latest_backup"/* . 2>/dev/null || true
    exit 1
fi

echo "🎉 Deprecated classes cleanup completed successfully!"
echo "📦 Backups created in backup/ directory"
echo "📊 Summary:"
echo "  - Kernel deprecated classes: ${#DEPRECATED_CLASSES[@]}"
echo "  - AppPlatform deprecated workflows: ${#DEPRECATED_APPPLATFORM_WORKFLOWS[@]}"
echo "  - Total files processed: $((${#DEPRECATED_CLASSES[@]} + ${#DEPRECATED_APPPLATFORM_WORKFLOWS[@]}))"
