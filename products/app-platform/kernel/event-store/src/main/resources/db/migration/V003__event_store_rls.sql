-- Flyway V003: Row-Level Security for multi-tenant event_store isolation.
-- Per-tenant RLS via metadata->>'tenant_id'

ALTER TABLE event_store ENABLE ROW LEVEL SECURITY;

-- Policy: a session can only see/write rows where the tenant_id in metadata
-- matches the session-local variable set by the connection pool on checkout.
CREATE POLICY event_store_tenant_isolation ON event_store
    USING (
        metadata->>'tenant_id' = current_setting('app.tenant_id', true)
    );

COMMENT ON TABLE event_store IS
    'Append-only DDD aggregate event store. Tenant-isolated via RLS using '
    'app.tenant_id session variable. UPDATE and DELETE revoked.';
