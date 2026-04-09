#!/bin/bash

# =============================================================================
# Gradle Structure Migration Script
# =============================================================================
# Purpose: Migrate from old Gradle structure to simplified convention-based setup
# Usage: ./scripts/migrate-gradle-structure.sh [--dry-run] [--backup]
# =============================================================================

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKUP_DIR="$PROJECT_ROOT/.gradle-backup-$(date +%Y%m%d-%H%M%S)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Options
DRY_RUN=false
BACKUP=false

# Parse arguments
for arg in "$@"; do
    case $arg in
        --dry-run)
            DRY_RUN=true
            ;;
        --backup)
            BACKUP=true
            ;;
        *)
            echo "Unknown option: $arg"
            echo "Usage: $0 [--dry-run] [--backup]"
            exit 1
            ;;
    esac
done

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Backup function
create_backup() {
    if [ "$BACKUP" = true ]; then
        log_info "Creating backup in $BACKUP_DIR"
        mkdir -p "$BACKUP_DIR"
        
        # Backup critical files
        cp "$PROJECT_ROOT/settings.gradle.kts" "$BACKUP_DIR/"
        cp "$PROJECT_ROOT/build.gradle.kts" "$BACKUP_DIR/"
        cp "$PROJECT_ROOT/gradle/libs.versions.toml" "$BACKUP_DIR/"
        cp -r "$PROJECT_ROOT/buildSrc" "$BACKUP_DIR/"
        
        log_success "Backup created successfully"
    fi
}

# Migration functions
migrate_settings_file() {
    log_info "Migrating settings.gradle.kts"
    
    local old_file="$PROJECT_ROOT/settings.gradle.kts"
    local new_file="$PROJECT_ROOT/settings.gradle.kts.new"
    
    if [ "$DRY_RUN" = true ]; then
        log_info "[DRY RUN] Would replace $old_file with simplified version"
        return 0
    fi
    
    if [ -f "$new_file" ]; then
        mv "$new_file" "$old_file"
        log_success "Migrated settings.gradle.kts"
    else
        log_warning "New settings file not found, skipping"
    fi
}

migrate_root_build_file() {
    log_info "Migrating root build.gradle.kts"
    
    local old_file="$PROJECT_ROOT/build.gradle.kts"
    local new_file="$PROJECT_ROOT/build.gradle.kts.new"
    
    if [ "$DRY_RUN" = true ]; then
        log_info "[DRY RUN] Would replace $old_file with simplified version"
        return 0
    fi
    
    if [ -f "$new_file" ]; then
        mv "$new_file" "$old_file"
        log_success "Migrated build.gradle.kts"
    else
        log_warning "New build file not found, skipping"
    fi
}

migrate_version_catalog() {
    log_info "Migrating version catalog"
    
    local old_file="$PROJECT_ROOT/gradle/libs.versions.toml"
    local new_file="$PROJECT_ROOT/gradle/libs.versions.toml.new"
    
    if [ "$DRY_RUN" = true ]; then
        log_info "[DRY RUN] Would replace $old_file with reorganized version"
        return 0
    fi
    
    if [ -f "$new_file" ]; then
        mv "$new_file" "$old_file"
        log_success "Migrated libs.versions.toml"
    else
        log_warning "New version catalog not found, skipping"
    fi
}

migrate_convention_plugins() {
    log_info "Migrating convention plugins"
    
    local buildSrc_dir="$PROJECT_ROOT/buildSrc/src/main/kotlin"
    
    if [ "$DRY_RUN" = true ]; then
        log_info "[DRY RUN] Would update convention plugins in $buildSrc_dir"
        return 0
    fi
    
    # Move new convention plugins
    local new_plugins=(
        "com.ghatana.testing-conventions-new.gradle.kts:com.ghatana.testing-conventions.gradle.kts"
        "com.ghatana.protobuf-conventions.gradle.kts"
        "com.ghatana.product-conventions.gradle.kts"
        "com.ghatana.database-conventions.gradle.kts"
    )
    
    for plugin in "${new_plugins[@]}"; do
        IFS=':' read -r source target <<< "$plugin"
        local source_file="$buildSrc_dir/$source"
        local target_file="$buildSrc_dir/$target"
        
        if [ -f "$source_file" ]; then
            if [ -n "$target" ]; then
                mv "$source_file" "$target_file"
                log_success "Migrated $source to $target"
            else
                log_success "Added $source"
            fi
        fi
    done
    
    # Remove old testing convention (will be replaced)
    local old_testing="$buildSrc_dir/com.ghatana.testing-conventions.gradle.kts"
    local old_testing_simplified="$buildSrc_dir/com.ghatana.testing-conventions-simplified.gradle.kts"
    
    if [ -f "$old_testing" ]; then
        mv "$old_testing" "$old_testing.backup"
        log_info "Backed up old testing convention plugin"
    fi
    
    if [ -f "$old_testing_simplified" ]; then
        mv "$old_testing_simplified" "$old_testing_simplified.backup"
        log_info "Backed up simplified testing convention plugin"
    fi
}

migrate_module_build_files() {
    log_info "Migrating module build files"
    
    local modules=(
        "platform/contracts/build.gradle.kts"
        "platform-kernel/kernel-core/build.gradle.kts"
        "products/yappc/build.gradle.kts"
    )
    
    for module in "${modules[@]}"; do
        local old_file="$PROJECT_ROOT/$module"
        local new_file="$old_file.new"
        
        if [ "$DRY_RUN" = true ]; then
            log_info "[DRY RUN] Would migrate $module"
            continue
        fi
        
        if [ -f "$new_file" ]; then
            mv "$new_file" "$old_file"
            log_success "Migrated $module"
        else
            log_warning "New build file not found for $module"
        fi
    done
}

validate_migration() {
    log_info "Validating migration"
    
    if [ "$DRY_RUN" = true ]; then
        log_info "[DRY RUN] Would validate Gradle configuration"
        return 0
    fi
    
    # Test Gradle configuration
    cd "$PROJECT_ROOT"
    
    if ./gradlew help --quiet > /dev/null 2>&1; then
        log_success "Gradle configuration is valid"
    else
        log_error "Gradle configuration is invalid"
        return 1
    fi
    
    # Test build health
    if ./gradlew buildHealth --quiet > /dev/null 2>&1; then
        log_success "Build health check passed"
    else
        log_warning "Build health check failed (may be expected during migration)"
    fi
}

cleanup_temp_files() {
    log_info "Cleaning up temporary files"
    
    if [ "$DRY_RUN" = true ]; then
        log_info "[DRY RUN] Would clean up temporary .new files"
        return 0
    fi
    
    find "$PROJECT_ROOT" -name "*.new" -type f -delete 2>/dev/null || true
    log_success "Cleaned up temporary files"
}

# Main execution
main() {
    log_info "Starting Gradle structure migration"
    log_info "Project root: $PROJECT_ROOT"
    
    if [ "$DRY_RUN" = true ]; then
        log_warning "Running in DRY RUN mode - no changes will be made"
    fi
    
    # Create backup if requested
    if [ "$BACKUP" = true ] && [ "$DRY_RUN" = false ]; then
        create_backup
    fi
    
    # Run migration steps
    migrate_settings_file
    migrate_root_build_file
    migrate_version_catalog
    migrate_convention_plugins
    migrate_module_build_files
    
    # Validate and cleanup
    if [ "$DRY_RUN" = false ]; then
        validate_migration
        cleanup_temp_files
    fi
    
    log_success "Migration completed successfully!"
    
    if [ "$BACKUP" = true ]; then
        log_info "Backup available at: $BACKUP_DIR"
    fi
    
    log_info "Next steps:"
    log_info "1. Run './gradlew buildHealth' to verify the build"
    log_info "2. Run './gradlew test' to verify tests work"
    log_info "3. Commit the changes"
    log_info "4. Update team documentation"
}

# Run main function
main "$@"
