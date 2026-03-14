-- App-Platform local dev database initialisation
-- This runs automatically when postgres/ container is first started.

-- Create application role if it doesn't exist (idempotent)
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'app_user') THEN
        CREATE ROLE app_user LOGIN PASSWORD 'app_pass';
    END IF;
END
$$;

GRANT CONNECT ON DATABASE app_platform TO app_user;
GRANT USAGE ON SCHEMA public TO app_user;
GRANT CREATE ON SCHEMA public TO app_user;

-- K-05 event_store: app_user will have SELECT + INSERT only (enforced by Flyway V002)
-- K-07 audit_logs:  app_user will have SELECT + INSERT only (enforced by Flyway V002)
