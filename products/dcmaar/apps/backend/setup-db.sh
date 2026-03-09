#!/bin/bash

# Guardian Database Setup Script
# This script sets up the PostgreSQL database and user for Guardian

set -e

DB_HOST=${DB_HOST:-localhost}
DB_PORT=${DB_PORT:-5432}
DB_NAME=${DB_NAME:-guardian_db}
DB_USER=${DB_USER:-guardian}
DB_PASSWORD=${DB_PASSWORD:-guardian123}
POSTGRES_USER=${POSTGRES_USER:-postgres}

echo "Setting up Guardian database..."
echo "Database: $DB_NAME"
echo "User: $DB_USER"
echo "Host: $DB_HOST:$DB_PORT"

# Check if PostgreSQL is running
if ! command -v psql &> /dev/null; then
    echo "Error: psql (PostgreSQL client) is not installed"
    exit 1
fi

# Try to connect to postgres to verify it's running
if ! psql -h "$DB_HOST" -U "$POSTGRES_USER" -c "SELECT 1" &>/dev/null; then
    echo "Warning: Could not connect to PostgreSQL as $POSTGRES_USER"
    echo "Make sure PostgreSQL is running and you have the correct credentials"
    exit 1
fi

echo "Creating database user and database..."

# Create the guardian user if it doesn't exist
psql -h "$DB_HOST" -U "$POSTGRES_USER" << EOF
-- Create guardian role if it doesn't exist
DO \$\$
BEGIN
  CREATE ROLE $DB_USER WITH LOGIN ENCRYPTED PASSWORD '$DB_PASSWORD';
EXCEPTION WHEN DUPLICATE_OBJECT THEN
  ALTER ROLE $DB_USER WITH LOGIN ENCRYPTED PASSWORD '$DB_PASSWORD';
END
\$\$;

-- Grant privileges
ALTER ROLE $DB_USER CREATEDB;
ALTER ROLE $DB_USER CREATEROLE;

-- Create database if it doesn't exist
SELECT 'CREATE DATABASE $DB_NAME OWNER $DB_USER' WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = '$DB_NAME')\gexec

-- Connect to the new database and create extensions/schema
\c $DB_NAME

EOF

echo "Applying schema..."
psql -h "$DB_HOST" -d "$DB_NAME" -U "$DB_USER" -f "$(dirname "$0")/src/db/schema.sql"

echo "Database setup complete!"
echo ""
echo "Connection details:"
echo "  Host: $DB_HOST"
echo "  Port: $DB_PORT"
echo "  Database: $DB_NAME"
echo "  User: $DB_USER"
echo "  Password: $DB_PASSWORD"
echo ""
echo "Test connection with:"
echo "  psql -h $DB_HOST -U $DB_USER -d $DB_NAME"
