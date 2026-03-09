-- Ghatana Shared PostgreSQL - Product Databases Initialization
-- Creates all product-specific databases for the shared PostgreSQL instance
-- This file is executed automatically on first container startup

-- Create flashit_dev user and databases
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'flashit_dev') THEN
    CREATE USER flashit_dev WITH PASSWORD 'flashit123';
  END IF;
END $$;

-- Create eventcloud user
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'eventcloud') THEN
    CREATE USER eventcloud WITH PASSWORD 'ghatana123';
  END IF;
END $$;

-- Create ghatana user for development (used by many services)
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ghatana') THEN
    CREATE USER ghatana WITH PASSWORD 'ghatana123';
  END IF;
END $$;

-- Grant default privileges to flashit_dev user
ALTER DEFAULT PRIVILEGES GRANT ALL ON SCHEMAS TO flashit_dev;
ALTER DEFAULT PRIVILEGES GRANT ALL ON TABLES TO flashit_dev;
ALTER DEFAULT PRIVILEGES GRANT ALL ON SEQUENCES TO flashit_dev;

-- Create databases for all products
SELECT 'CREATE DATABASE flashit_dev' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'flashit_dev')\gexec
SELECT 'CREATE DATABASE flashit_test' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'flashit_test')\gexec
SELECT 'CREATE DATABASE software_org_dev' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'software_org_dev')\gexec
SELECT 'CREATE DATABASE virtual_org' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'virtual_org')\gexec
SELECT 'CREATE DATABASE yappc' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'yappc')\gexec
SELECT 'CREATE DATABASE ai_requirements' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'ai_requirements')\gexec
SELECT 'CREATE DATABASE guardian' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'guardian')\gexec
SELECT 'CREATE DATABASE eventcloud' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'eventcloud')\gexec
SELECT 'CREATE DATABASE eventcloud_test' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'eventcloud_test')\gexec
SELECT 'CREATE DATABASE pipeline_registry' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'pipeline_registry')\gexec
SELECT 'CREATE DATABASE ghatana' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'ghatana')\gexec
SELECT 'CREATE DATABASE event_log' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'event_log')\gexec
SELECT 'CREATE DATABASE tutorputor' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'tutorputor')\gexec
SELECT 'CREATE DATABASE nlreporting' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'nlreporting')\gexec

-- Grant flashit_dev user access to flashit databases
GRANT ALL PRIVILEGES ON DATABASE flashit_dev TO flashit_dev;
GRANT ALL PRIVILEGES ON DATABASE flashit_test TO flashit_dev;

-- Grant eventcloud user access to eventcloud databases
GRANT ALL PRIVILEGES ON DATABASE eventcloud TO eventcloud;
GRANT ALL PRIVILEGES ON DATABASE eventcloud_test TO eventcloud;

-- Note: pgvector extension must be enabled per database
-- This will be done by individual product migration scripts or on first connection

-- Log completion
SELECT 'Ghatana shared PostgreSQL - all product databases created' AS initialization_status;
