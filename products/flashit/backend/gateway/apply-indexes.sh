#!/bin/bash

# Database Performance Index Migration Script
# Applies performance optimization indexes to the Flashit database
# Usage: ./apply-indexes.sh [options]

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
SQL_FILE="$SCRIPT_DIR/prisma/migrations/performance_indexes.sql"

# Default values
DRY_RUN=false
SKIP_BACKUP=false
DATABASE_URL="${DATABASE_URL:-}"

# Help function
show_help() {
    cat << EOF
Database Performance Index Migration Script

Usage: ./apply-indexes.sh [OPTIONS]

Options:
    -h, --help          Show this help message
    -d, --dry-run       Show what would be done without executing
    -n, --no-backup     Skip creating a backup before applying indexes
    -u, --url URL       Database URL (overrides DATABASE_URL env var)
    
Environment Variables:
    DATABASE_URL        PostgreSQL connection string
                        Example: postgresql://user:pass@localhost:5432/flashit

Examples:
    # Apply indexes (requires DATABASE_URL env var)
    ./apply-indexes.sh

    # Dry run to see what will be executed
    ./apply-indexes.sh --dry-run

    # Apply with explicit URL
    ./apply-indexes.sh --url "postgresql://user:pass@host:5432/db"

    # Skip backup (faster, but not recommended for production)
    ./apply-indexes.sh --no-backup

EOF
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            show_help
            exit 0
            ;;
        -d|--dry-run)
            DRY_RUN=true
            shift
            ;;
        -n|--no-backup)
            SKIP_BACKUP=true
            shift
            ;;
        -u|--url)
            DATABASE_URL="$2"
            shift 2
            ;;
        *)
            echo -e "${RED}Error: Unknown option $1${NC}"
            show_help
            exit 1
            ;;
    esac
done

# Validate DATABASE_URL
if [ -z "$DATABASE_URL" ]; then
    echo -e "${RED}Error: DATABASE_URL is not set${NC}"
    echo "Please set DATABASE_URL environment variable or use --url option"
    exit 1
fi

# Validate SQL file exists
if [ ! -f "$SQL_FILE" ]; then
    echo -e "${RED}Error: SQL file not found: $SQL_FILE${NC}"
    exit 1
fi

# Extract database name for backup
DB_NAME=$(echo "$DATABASE_URL" | sed -n 's|.*://[^/]*/\([^?]*\).*|\1|p')

echo -e "${GREEN}=== Flashit Database Index Migration ===${NC}"
echo "Database: $DB_NAME"
echo "SQL File: $SQL_FILE"
echo ""

# Dry run mode
if [ "$DRY_RUN" = true ]; then
    echo -e "${YELLOW}DRY RUN MODE - No changes will be made${NC}"
    echo ""
    echo "SQL commands that would be executed:"
    echo "======================================"
    grep -E "^CREATE INDEX" "$SQL_FILE" | head -20
    echo "... (see $SQL_FILE for full list)"
    exit 0
fi

# Create backup (unless skipped)
if [ "$SKIP_BACKUP" = false ]; then
    BACKUP_FILE="flashit_pre_index_$(date +%Y%m%d_%H%M%S).sql"
    echo -e "${YELLOW}Creating database backup: $BACKUP_FILE${NC}"
    
    if command -v pg_dump &> /dev/null; then
        pg_dump "$DATABASE_URL" > "$BACKUP_FILE"
        echo -e "${GREEN}✓ Backup created successfully${NC}"
    else
        echo -e "${YELLOW}Warning: pg_dump not found, skipping backup${NC}"
        read -p "Continue without backup? (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    fi
fi

# Apply indexes
echo ""
echo -e "${YELLOW}Applying performance indexes...${NC}"
echo "This may take a few minutes depending on data volume."
echo ""

# Execute SQL file
if psql "$DATABASE_URL" -f "$SQL_FILE"; then
    echo ""
    echo -e "${GREEN}✓ Performance indexes applied successfully!${NC}"
    echo ""
    
    # Show created indexes
    echo "Verifying indexes..."
    psql "$DATABASE_URL" -c "
        SELECT 
            schemaname,
            tablename,
            indexname
        FROM pg_stat_user_indexes
        WHERE schemaname = 'public'
        AND indexname LIKE 'idx_%'
        ORDER BY tablename, indexname
        LIMIT 30;
    "
    
    echo ""
    echo -e "${GREEN}=== Migration Complete ===${NC}"
    echo ""
    echo "Next steps:"
    echo "1. Monitor query performance: Check slow query logs"
    echo "2. Analyze index usage after 24 hours:"
    echo "   psql \$DATABASE_URL -c \"SELECT * FROM pg_stat_user_indexes WHERE idx_scan < 10;\""
    echo "3. Review index hit rates in production"
    
    if [ "$SKIP_BACKUP" = false ] && [ -f "$BACKUP_FILE" ]; then
        echo ""
        echo "Backup saved to: $BACKUP_FILE"
        echo "(Keep this safe for 7 days, then remove)"
    fi
else
    echo ""
    echo -e "${RED}✗ Failed to apply indexes${NC}"
    echo "Check the error messages above for details"
    
    if [ "$SKIP_BACKUP" = false ] && [ -f "$BACKUP_FILE" ]; then
        echo ""
        echo "Restore from backup if needed:"
        echo "  psql \$DATABASE_URL < $BACKUP_FILE"
    fi
    
    exit 1
fi
