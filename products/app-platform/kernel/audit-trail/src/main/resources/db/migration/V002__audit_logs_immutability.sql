-- Flyway V002: Immutability enforcement triggers on audit_logs.
--
-- Triggers are the last-resort immutability guard in addition to RLS and JDBC-level controls.

CREATE OR REPLACE FUNCTION prevent_audit_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Audit logs are immutable — UPDATE and DELETE are not permitted (audit_id: %)', OLD.audit_id;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_logs_block_update
    BEFORE UPDATE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_modification();

CREATE TRIGGER audit_logs_block_delete
    BEFORE DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_modification();

-- Revoke from app_user (applied only when the role exists — CI/prod environments)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_user') THEN
        REVOKE UPDATE, DELETE ON TABLE audit_logs FROM app_user;
    END IF;
END
$$;
