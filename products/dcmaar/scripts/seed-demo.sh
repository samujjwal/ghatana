#!/usr/bin/env bash
set -euo pipefail

# seed-demo.sh
# Creates demo users and sample data for local development.
# Usage:
#   DRY_RUN=1 ./scripts/seed-demo.sh        # prints commands and SQL without executing
#   ./scripts/seed-demo.sh                   # performs inserts (requires psql reachable)
#   PASSWORD="MyPass!" ./scripts/seed-demo.sh  # override default password
#   CLEANUP=1 ./scripts/seed-demo.sh         # delete demo rows instead of inserting

REPO_ROOT="/home/samujjwal/Developments/ghatana/products/dcmaar/apps/guardian"
SCRIPT_DIR="${REPO_ROOT}/scripts"
DEFAULT_DB="postgresql://postgres:postgres@localhost:5432/guardian_dev"
DB_URL="${DATABASE_URL:-$DEFAULT_DB}"
PASSWORD="${PASSWORD:-Password123!}"
HASH_TMP="/tmp/guardian_demo_password.hash"

if [ "$PWD" != "$REPO_ROOT" ]; then
  echo "* Warning: running seed script from $PWD (recommended to run from $REPO_ROOT)"
fi

function ensure_node() {
  if ! command -v node >/dev/null 2>&1; then
    echo "ERROR: node is required but not found in PATH." >&2
    exit 2
  fi
}

function ensure_pnpm() {
  if ! command -v pnpm >/dev/null 2>&1; then
    echo "ERROR: pnpm is required to install bcryptjs. Please install pnpm." >&2
    exit 2
  fi
}

function ensure_bcryptjs() {
  # Try requiring bcryptjs in node; if missing, install a dev dependency in workspace
  if node -e "try{require('bcryptjs');process.exit(0);}catch(e){process.exit(1);}" 2>/dev/null; then
    return 0
  fi
  echo "bcryptjs not found - installing as workspace devDependency (pnpm add -w -D bcryptjs)"
  ensure_pnpm
  pnpm add -w -D bcryptjs >/dev/null 2>&1 || {
    echo "Failed to install bcryptjs via pnpm" >&2
    exit 3
  }
}

function generate_hash() {
  ensure_node
  ensure_bcryptjs
  node -e "console.log(require('bcryptjs').hashSync(process.argv[1], 10))" -- "$PASSWORD"
}

function usage() {
  cat <<USAGE
Usage: $0 [options]

Environment variables:
  DATABASE_URL    Postgres connection string (default: $DEFAULT_DB)
  PASSWORD        Password to seed for demo accounts (default: Password123!)
  DRY_RUN=1       Print SQL and commands but do not run psql
  CLEANUP=1       Delete demo rows instead of inserting

Examples:
  DRY_RUN=1 PASSWORD=Password123! $0
  DATABASE_URL=postgresql://postgres:postgres@localhost:5432/guardian_dev $0
  CLEANUP=1 $0
USAGE
}

if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
  usage
  exit 0
fi

DRY_RUN="${DRY_RUN:-0}"
CLEANUP="${CLEANUP:-0}"

echo "Repository root: $REPO_ROOT"
echo "Database URL: ${DB_URL}"
echo "Demo password: (hidden)"

HASH=$(generate_hash)
echo "$HASH" > "$HASH_TMP"
echo "Generated bcrypt hash written to: $HASH_TMP"

SQL_INSERTS=$(cat <<SQL
-- Insert demo users
INSERT INTO users (email, password_hash, role, created_at)
VALUES ('admin@example.test', '${HASH}', 'admin', now());

INSERT INTO users (email, password_hash, role, created_at)
VALUES ('parent1@example.test', '${HASH}', 'parent', now());
SQL
)

SQL_CLEANUP=$(cat <<SQL
-- Delete demo users
DELETE FROM users WHERE email IN ('admin@example.test','parent1@example.test');
SQL
)

if [ "$DRY_RUN" = "1" ]; then
  echo "---- DRY RUN: SQL to execute (not executing) ----"
  if [ "$CLEANUP" = "1" ]; then
    echo "$SQL_CLEANUP"
  else
    echo "$SQL_INSERTS"
  fi
  echo "---- end DRY RUN ----"
  exit 0
fi

if [ "$CLEANUP" = "1" ]; then
  echo "Running cleanup SQL against $DB_URL"
  psql "$DB_URL" -v ON_ERROR_STOP=1 <<SQL
$SQL_CLEANUP
SQL
  echo "Cleanup complete"
  exit 0
fi

echo "Executing insertion SQL against $DB_URL"
psql "$DB_URL" -v ON_ERROR_STOP=1 <<SQL
$SQL_INSERTS
SQL

echo "Seed complete. Verify by attempting login or inspecting users table."
