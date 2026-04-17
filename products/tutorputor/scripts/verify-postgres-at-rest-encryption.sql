-- TutorPutor Postgres encryption posture probe
-- Run with: psql "$DATABASE_URL" -f scripts/verify-postgres-at-rest-encryption.sql

SELECT current_setting('server_version') AS server_version;
SELECT current_setting('ssl', true) AS ssl_enabled;

SELECT name, setting
FROM pg_settings
WHERE name IN ('ssl', 'data_checksums');

SELECT extname
FROM pg_extension
WHERE extname IN ('pgcrypto');

SELECT now() AS collected_at;
