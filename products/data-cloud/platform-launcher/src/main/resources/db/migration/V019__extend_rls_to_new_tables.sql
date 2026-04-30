-- V019__extend_rls_to_new_tables.sql
-- Extends Row-Level Security (RLS) to tables added after V011.
--
-- V011 enabled RLS on: events, entities, collections_metadata, timeseries.
-- The following tables were created in V005 / V010 / V013–V018 without matching
-- RLS policies. This migration closes that gap.
--
-- Ticket: DC-SEC-019
-- Author: Data-Cloud Security Team

-- ====================================================================================
-- event_log (V005)
-- ====================================================================================

ALTER TABLE event_log ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_event_log ON event_log
    FOR ALL
    TO application_user
    USING (tenant_id = tenant_security.get_current_tenant())
    WITH CHECK (tenant_id = tenant_security.get_current_tenant());

CREATE POLICY admin_bypass_event_log ON event_log
    FOR ALL
    TO db_admin
    USING (true)
    WITH CHECK (true);

-- ====================================================================================
-- entity_relations (V010)
-- ====================================================================================

ALTER TABLE entity_relations ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_entity_relations ON entity_relations
    FOR ALL
    TO application_user
    USING (tenant_id = tenant_security.get_current_tenant())
    WITH CHECK (tenant_id = tenant_security.get_current_tenant());

CREATE POLICY admin_bypass_entity_relations ON entity_relations
    FOR ALL
    TO db_admin
    USING (true)
    WITH CHECK (true);

-- ====================================================================================
-- agent_releases (V013)
-- ====================================================================================

ALTER TABLE agent_releases ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_agent_releases ON agent_releases
    FOR ALL
    TO application_user
    USING (tenant_id = tenant_security.get_current_tenant())
    WITH CHECK (tenant_id = tenant_security.get_current_tenant());

CREATE POLICY admin_bypass_agent_releases ON agent_releases
    FOR ALL
    TO db_admin
    USING (true)
    WITH CHECK (true);

-- ====================================================================================
-- agent_rollouts (V014)
-- ====================================================================================

ALTER TABLE agent_rollouts ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_agent_rollouts ON agent_rollouts
    FOR ALL
    TO application_user
    USING (tenant_id = tenant_security.get_current_tenant())
    WITH CHECK (tenant_id = tenant_security.get_current_tenant());

CREATE POLICY admin_bypass_agent_rollouts ON agent_rollouts
    FOR ALL
    TO db_admin
    USING (true)
    WITH CHECK (true);

-- ====================================================================================
-- evaluation_results (V015)
-- ====================================================================================

ALTER TABLE evaluation_results ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_evaluation_results ON evaluation_results
    FOR ALL
    TO application_user
    USING (tenant_id = tenant_security.get_current_tenant())
    WITH CHECK (tenant_id = tenant_security.get_current_tenant());

CREATE POLICY admin_bypass_evaluation_results ON evaluation_results
    FOR ALL
    TO db_admin
    USING (true)
    WITH CHECK (true);

-- ====================================================================================
-- memory_namespaces (V016)
-- ====================================================================================

ALTER TABLE memory_namespaces ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_memory_namespaces ON memory_namespaces
    FOR ALL
    TO application_user
    USING (tenant_id = tenant_security.get_current_tenant())
    WITH CHECK (tenant_id = tenant_security.get_current_tenant());

CREATE POLICY admin_bypass_memory_namespaces ON memory_namespaces
    FOR ALL
    TO db_admin
    USING (true)
    WITH CHECK (true);

-- ====================================================================================
-- promotion_evidence (V017)
-- ====================================================================================

ALTER TABLE promotion_evidence ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_promotion_evidence ON promotion_evidence
    FOR ALL
    TO application_user
    USING (tenant_id = tenant_security.get_current_tenant())
    WITH CHECK (tenant_id = tenant_security.get_current_tenant());

CREATE POLICY admin_bypass_promotion_evidence ON promotion_evidence
    FOR ALL
    TO db_admin
    USING (true)
    WITH CHECK (true);

-- ====================================================================================
-- media_artifacts (V018)
-- ====================================================================================

ALTER TABLE media_artifacts ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_media_artifacts ON media_artifacts
    FOR ALL
    TO application_user
    USING (tenant_id = tenant_security.get_current_tenant())
    WITH CHECK (tenant_id = tenant_security.get_current_tenant());

CREATE POLICY admin_bypass_media_artifacts ON media_artifacts
    FOR ALL
    TO db_admin
    USING (true)
    WITH CHECK (true);
