#!/bin/bash

###############################################################################
# YAPPC Cleanup Script
#
# Removes unnecessary files, empty directories, and temporary artifacts
# after the consolidation implementation.
#
# Usage: ./scripts/cleanup-unnecessary-files.sh [--dry-run]
###############################################################################

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
YAPPC_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

DRY_RUN=false
if [[ "$1" == "--dry-run" ]]; then
    DRY_RUN=true
    echo "🔍 Running in DRY RUN mode - no files will be deleted"
fi

echo "🧹 YAPPC Cleanup - Removing Unnecessary Files"
echo "=============================================="
echo ""

CLEANUP_COUNT=0

###############################################################################
# Function to remove directory
###############################################################################
remove_dir() {
    local dir="$1"
    local reason="$2"
    
    if [[ -d "$dir" ]]; then
        echo "  🗑️  Removing: $dir"
        echo "      Reason: $reason"
        if [[ "$DRY_RUN" == false ]]; then
            rm -rf "$dir"
            ((CLEANUP_COUNT++))
        fi
    fi
}

###############################################################################
# Function to remove file
###############################################################################
remove_file() {
    local file="$1"
    local reason="$2"
    
    if [[ -f "$file" ]]; then
        echo "  🗑️  Removing: $file"
        echo "      Reason: $reason"
        if [[ "$DRY_RUN" == false ]]; then
            rm -f "$file"
            ((CLEANUP_COUNT++))
        fi
    fi
}

###############################################################################
# Clean up frontend
###############################################################################
echo "📦 Cleaning up frontend..."
echo ""

# Remove empty AI subdirectories
remove_dir "$YAPPC_ROOT/frontend/libs/ai/src/ui/ml" "Empty directory"
remove_dir "$YAPPC_ROOT/frontend/libs/ai/src/core/cache" "Empty directory"
remove_dir "$YAPPC_ROOT/frontend/libs/ai/src/core/security" "Empty directory"
remove_dir "$YAPPC_ROOT/frontend/libs/ai/src/nlp/parser" "Empty directory"
remove_dir "$YAPPC_ROOT/frontend/libs/ai/src/nlp/prompts" "Empty directory"
remove_dir "$YAPPC_ROOT/frontend/libs/ai/src/nlp/sentiment" "Empty directory"
remove_dir "$YAPPC_ROOT/frontend/libs/ai/src/nlp/validation" "Empty directory"

# Remove backup files
remove_file "$YAPPC_ROOT/frontend/package.json.backup" "Backup file (consolidation complete)"

# Remove temporary build artifacts
remove_dir "$YAPPC_ROOT/frontend/.eslint-fixes" "Temporary eslint fixes"
remove_dir "$YAPPC_ROOT/frontend/.governance" "Empty governance directory"
remove_dir "$YAPPC_ROOT/frontend/.husky" "Empty husky directory"
remove_dir "$YAPPC_ROOT/frontend/.storybook" "Empty storybook directory"

# Remove duplicate/obsolete documentation
remove_file "$YAPPC_ROOT/frontend/CANVAS_STATE_CONSOLIDATION.md" "Obsolete documentation"
remove_file "$YAPPC_ROOT/frontend/COMPLETE_WORK_SUMMARY.md" "Obsolete documentation"
remove_file "$YAPPC_ROOT/frontend/FINAL_STATUS_REPORT.md" "Obsolete documentation"
remove_file "$YAPPC_ROOT/frontend/MIGRATION_COMPLETE.md" "Obsolete documentation"
remove_file "$YAPPC_ROOT/frontend/MIGRATION_STATUS.md" "Obsolete documentation"
remove_file "$YAPPC_ROOT/frontend/PRE_EXISTING_ISSUES.md" "Obsolete documentation"
remove_file "$YAPPC_ROOT/frontend/REMAINING_ISSUES_DETAILED.md" "Obsolete documentation"
remove_file "$YAPPC_ROOT/frontend/STUB_PAGES_TRACKER.md" "Obsolete documentation"
remove_file "$YAPPC_ROOT/frontend/WORK_COMPLETED_SUMMARY.md" "Obsolete documentation"

###############################################################################
# Clean up backend
###############################################################################
echo ""
echo "📦 Cleaning up backend..."
echo ""

# Remove empty scaanmkdir directory (typo directory)
remove_dir "$YAPPC_ROOT/core/scaanmkdir" "Typo directory (empty)"

# Remove backup files
remove_file "$YAPPC_ROOT/settings.gradle.kts.backup" "Backup file (consolidation complete)"

###############################################################################
# Clean up root
###############################################################################
echo ""
echo "📦 Cleaning up root directory..."
echo ""

# Remove obsolete implementation reports (now archived)
remove_file "$YAPPC_ROOT/YAPPC_AGENTIC_PLATFORM_ARCHITECTURE_REVIEW.md" "Archived to docs/archive/"
remove_file "$YAPPC_ROOT/YAPPC_AGENTIC_PLATFORM_IMPLEMENTATION_PLAN.md" "Archived to docs/archive/"
remove_file "$YAPPC_ROOT/YAPPC_LIFECYCLE_INTELLIGENCE_ARCHITECTURE_REPORT.md" "Archived to docs/archive/"
remove_file "$YAPPC_ROOT/FEATURE_DEEP_INSPECTION_REPORT.md" "Archived to docs/archive/"
remove_file "$YAPPC_ROOT/YAPPC_AGENT_CATALOG.md" "Obsolete (replaced by agent-catalog.yaml)"

###############################################################################
# Clean up TODOs directory
###############################################################################
echo ""
echo "📦 Cleaning up TODOs directory..."
echo ""

# Remove TODO tracking files (replaced by docs/TODO_REDUCTION_REPORT.md)
remove_dir "$YAPPC_ROOT/TODOs" "Replaced by docs/TODO_REDUCTION_REPORT.md"

###############################################################################
# Remove empty node_modules in libs
###############################################################################
echo ""
echo "📦 Cleaning up empty node_modules..."
echo ""

# Find and remove empty node_modules directories
if [[ "$DRY_RUN" == false ]]; then
    find "$YAPPC_ROOT/frontend/libs" -type d -name "node_modules" -empty -delete 2>/dev/null || true
fi

###############################################################################
# Summary
###############################################################################
echo ""
echo "✅ Cleanup Complete!"
echo ""
echo "📊 Summary:"
echo "  Items cleaned: $CLEANUP_COUNT"
echo ""
echo "🎯 Benefits:"
echo "  - Removed obsolete documentation"
echo "  - Removed empty directories"
echo "  - Removed backup files"
echo "  - Removed temporary artifacts"
echo "  - Cleaner project structure"
echo ""

if [[ "$DRY_RUN" == true ]]; then
    echo "⚠️  This was a DRY RUN - no files were deleted"
    echo "    Run without --dry-run to execute cleanup"
fi
