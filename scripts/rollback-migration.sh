#!/bin/bash

# ============================================================================
# Migration Rollback Script
# @doc.type script
# @doc.purpose Quick rollback for failed migrations
# @doc.layer tooling
# @doc.pattern automation
#
# Usage:
#   ./scripts/rollback-migration.sh [options]
#
# Options:
#   --list       List available backups
#   --latest     Restore from latest backup (default)
#   --select     Interactive selection of backup
#   --hard       Force restore without confirmation
#   --help       Show help
# ============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

BACKUP_DIR=".backup-migration"
HARD_MODE=false

# ============================================================================
# LOGGING FUNCTIONS
# ============================================================================

log_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

log_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

log_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

log_error() {
    echo -e "${RED}❌ $1${NC}"
}

# ============================================================================
# HELP
# ============================================================================

show_help() {
    cat << EOF
Migration Rollback Script

Usage: ./scripts/rollback-migration.sh [options]

Options:
  --list       List all available backups
  --latest     Restore from latest backup (default behavior)
  --select     Interactive backup selection
  --hard       Force restore without confirmation
  --help       Show this help message

Examples:
  ./scripts/rollback-migration.sh              # Restore latest backup
  ./scripts/rollback-migration.sh --list       # List all backups
  ./scripts/rollback-migration.sh --select     # Choose backup interactively
  ./scripts/rollback-migration.sh --hard       # Force restore latest

EOF
}

# ============================================================================
# LIST BACKUPS
# ============================================================================

list_backups() {
    if [ ! -d "$BACKUP_DIR" ]; then
        log_error "No backup directory found at $BACKUP_DIR"
        exit 1
    fi

    log_info "Available backups:"
    echo ""
    
    local count=0
    for backup in "$BACKUP_DIR"/*/; do
        if [ -d "$backup" ]; then
            count=$((count + 1))
            local name=$(basename "$backup")
            local manifest="$backup/manifest.json"
            
            if [ -f "$manifest" ]; then
                local timestamp=$(jq -r '.timestamp' "$manifest" 2>/dev/null || echo "unknown")
                local file_count=$(jq -r '.fileCount' "$manifest" 2>/dev/null || echo "unknown")
                echo "  $count. $name"
                echo "     Time: $timestamp"
                echo "     Files: $file_count"
            else
                echo "  $count. $name (no manifest)"
            fi
            echo ""
        fi
    done

    if [ $count -eq 0 ]; then
        log_warning "No backups found"
        exit 1
    fi

    echo "Total: $count backup(s)"
}

# ============================================================================
# SELECT BACKUP INTERACTIVELY
# ============================================================================

select_backup() {
    if [ ! -d "$BACKUP_DIR" ]; then
        log_error "No backup directory found"
        exit 1
    fi

    # Get list of backups
    local backups=()
    for backup in "$BACKUP_DIR"/*/; do
        if [ -d "$backup" ]; then
            backups+=("$backup")
        fi
    done

    if [ ${#backups[@]} -eq 0 ]; then
        log_error "No backups found"
        exit 1
    fi

    # Show options
    echo "Select a backup to restore:"
    for i in "${!backups[@]}"; do
        local num=$((i + 1))
        local name=$(basename "${backups[$i]}")
        echo "  $num. $name"
    done

    # Get selection
    echo ""
    read -p "Enter number (1-${#backups[@]}): " selection

    # Validate selection
    if ! [[ "$selection" =~ ^[0-9]+$ ]] || [ "$selection" -lt 1 ] || [ "$selection" -gt ${#backups[@]} ]; then
        log_error "Invalid selection"
        exit 1
    fi

    local index=$((selection - 1))
    echo "${backups[$index]}"
}

# ============================================================================
# GET LATEST BACKUP
# ============================================================================

get_latest_backup() {
    if [ ! -d "$BACKUP_DIR" ]; then
        log_error "No backup directory found"
        exit 1
    fi

    local latest=""
    local latest_time=0

    for backup in "$BACKUP_DIR"/*/; do
        if [ -d "$backup" ]; then
            local mtime=$(stat -c %Y "$backup" 2>/dev/null || stat -f %m "$backup" 2>/dev/null)
            if [ "$mtime" -gt "$latest_time" ]; then
                latest_time=$mtime
                latest="$backup"
            fi
        fi
    done

    if [ -z "$latest" ]; then
        log_error "No backups found"
        exit 1
    fi

    echo "$latest"
}

# ============================================================================
# RESTORE BACKUP
# ============================================================================

restore_backup() {
    local backup_dir="$1"
    local manifest="$backup_dir/manifest.json"

    if [ ! -f "$manifest" ]; then
        log_error "Backup manifest not found: $manifest"
        exit 1
    fi

    # Read manifest
    local timestamp=$(jq -r '.timestamp' "$manifest")
    local file_count=$(jq -r '.fileCount' "$manifest")
    local files=$(jq -r '.files[]' "$manifest")

    echo ""
    log_info "Backup Details:"
    echo "  Timestamp: $timestamp"
    echo "  Files: $file_count"
    echo "  Source: $(basename "$backup_dir")"
    echo ""

    # Confirmation (unless hard mode)
    if [ "$HARD_MODE" = false ]; then
        read -p "⚠️  This will restore $file_count files. Continue? (yes/no): " confirm
        if [ "$confirm" != "yes" ]; then
            log_info "Rollback cancelled"
            exit 0
        fi
    fi

    # Restore files
    log_info "Restoring files..."
    local restored=0
    local failed=0

    for file in $files; do
        local backup_file="$backup_dir/$file"
        local target_file="./$file"

        if [ -f "$backup_file" ]; then
            # Create directory if needed
            local target_dir=$(dirname "$target_file")
            if [ ! -d "$target_dir" ]; then
                mkdir -p "$target_dir"
            fi

            # Copy file
            if cp "$backup_file" "$target_file"; then
                restored=$((restored + 1))
                echo -n "."
            else
                failed=$((failed + 1))
                echo -n "X"
            fi
        else
            log_warning "Backup file not found: $backup_file"
            failed=$((failed + 1))
        fi
    done

    echo ""
    echo ""

    if [ $failed -eq 0 ]; then
        log_success "Rollback complete! Restored $restored files"
    else
        log_warning "Rollback completed with issues: $restored restored, $failed failed"
    fi

    # Post-rollback validation
    echo ""
    log_info "Post-rollback validation:"
    
    if command -v pnpm &> /dev/null; then
        echo "  Checking for TypeScript errors..."
        if pnpm typecheck 2>&1 | head -20; then
            log_success "TypeScript check passed"
        else
            log_warning "TypeScript errors detected - manual review needed"
        fi
    fi
}

# ============================================================================
# MAIN
# ============================================================================

main() {
    # Parse arguments
    local list_mode=false
    local select_mode=false
    local latest_mode=true

    while [[ $# -gt 0 ]]; do
        case $1 in
            --list)
                list_mode=true
                latest_mode=false
                shift
                ;;
            --select)
                select_mode=true
                latest_mode=false
                shift
                ;;
            --latest)
                latest_mode=true
                shift
                ;;
            --hard)
                HARD_MODE=true
                shift
                ;;
            --help)
                show_help
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                show_help
                exit 1
                ;;
        esac
    done

    # Execute based on mode
    if [ "$list_mode" = true ]; then
        list_backups
        exit 0
    fi

    if [ "$select_mode" = true ]; then
        local backup=$(select_backup)
        restore_backup "$backup"
        exit 0
    fi

    if [ "$latest_mode" = true ]; then
        local backup=$(get_latest_backup)
        log_info "Latest backup: $(basename "$backup")"
        restore_backup "$backup"
        exit 0
    fi
}

# Check dependencies
if ! command -v jq &> /dev/null; then
    log_error "jq is required but not installed. Please install jq."
    exit 1
fi

# Run main
main "$@"
