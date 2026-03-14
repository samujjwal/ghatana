-- Flyway V002: Revoke mutating privileges on event_store from the app role.
--
-- NOTE: Replace 'app_user' with the actual DB role your application connects as.
-- This is a defence-in-depth control; application code must also never issue
-- UPDATE/DELETE against event_store.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_user') THEN
        REVOKE UPDATE, DELETE ON TABLE event_store FROM app_user;
    END IF;
END
$$;

COMMENT ON TABLE event_store IS
    'Append-only DDD aggregate event store. UPDATE and DELETE are revoked at DB level. '
    'Row-Level Security (RLS) tenant isolation is added in V003.';
