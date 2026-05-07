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
-- Role Setup (for test environments)
-- ====================================================================================

-- Create roles if they don't exist (for test environments)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'application_user') THEN
        CREATE ROLE application_user;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'db_admin') THEN
        CREATE ROLE db_admin;
    END IF;
END $$;

-- ====================================================================================
-- event_log (V005)
-- ====================================================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'event_log' AND table_schema = 'public') THEN
        EXECUTE 'ALTER TABLE event_log ENABLE ROW LEVEL SECURITY';
        EXECUTE 'DROP POLICY IF EXISTS tenant_isolation_event_log ON event_log';
        EXECUTE 'CREATE POLICY tenant_isolation_event_log ON event_log FOR ALL TO application_user USING (tenant_id = tenant_security.get_current_tenant()) WITH CHECK (tenant_id = tenant_security.get_current_tenant())';
        EXECUTE 'DROP POLICY IF EXISTS admin_bypass_event_log ON event_log';
        EXECUTE 'CREATE POLICY admin_bypass_event_log ON event_log FOR ALL TO db_admin USING (true) WITH CHECK (true)';
    END IF;
END $$;

-- ====================================================================================
-- entity_relations (V010)
-- ====================================================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'entity_relations' AND table_schema = 'public') THEN
        EXECUTE 'ALTER TABLE entity_relations ENABLE ROW LEVEL SECURITY';
        EXECUTE 'DROP POLICY IF EXISTS tenant_isolation_entity_relations ON entity_relations';
        EXECUTE 'CREATE POLICY tenant_isolation_entity_relations ON entity_relations FOR ALL TO application_user USING (tenant_id = tenant_security.get_current_tenant()) WITH CHECK (tenant_id = tenant_security.get_current_tenant())';
        EXECUTE 'DROP POLICY IF EXISTS admin_bypass_entity_relations ON entity_relations';
        EXECUTE 'CREATE POLICY admin_bypass_entity_relations ON entity_relations FOR ALL TO db_admin USING (true) WITH CHECK (true)';
    END IF;
END $$;

-- ====================================================================================
-- agent_releases (V013)
-- ====================================================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'agent_releases' AND table_schema = 'public') THEN
        EXECUTE 'ALTER TABLE agent_releases ENABLE ROW LEVEL SECURITY';
        EXECUTE 'DROP POLICY IF EXISTS tenant_isolation_agent_releases ON agent_releases';
        EXECUTE 'CREATE POLICY tenant_isolation_agent_releases ON agent_releases FOR ALL TO application_user USING (tenant_id = tenant_security.get_current_tenant()) WITH CHECK (tenant_id = tenant_security.get_current_tenant())';
        EXECUTE 'DROP POLICY IF EXISTS admin_bypass_agent_releases ON agent_releases';
        EXECUTE 'CREATE POLICY admin_bypass_agent_releases ON agent_releases FOR ALL TO db_admin USING (true) WITH CHECK (true)';
    END IF;
END $$;

-- ====================================================================================
-- agent_rollouts (V014)
-- ====================================================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'agent_rollouts' AND table_schema = 'public') THEN
        EXECUTE 'ALTER TABLE agent_rollouts ENABLE ROW LEVEL SECURITY';
        EXECUTE 'DROP POLICY IF EXISTS tenant_isolation_agent_rollouts ON agent_rollouts';
        EXECUTE 'CREATE POLICY tenant_isolation_agent_rollouts ON agent_rollouts FOR ALL TO application_user USING (tenant_id = tenant_security.get_current_tenant()) WITH CHECK (tenant_id = tenant_security.get_current_tenant())';
        EXECUTE 'DROP POLICY IF EXISTS admin_bypass_agent_rollouts ON agent_rollouts';
        EXECUTE 'CREATE POLICY admin_bypass_agent_rollouts ON agent_rollouts FOR ALL TO db_admin USING (true) WITH CHECK (true)';
    END IF;
END $$;

-- ====================================================================================
-- evaluation_results (V015)
-- ====================================================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'evaluation_results' AND table_schema = 'public') THEN
        EXECUTE 'ALTER TABLE evaluation_results ENABLE ROW LEVEL SECURITY';
        EXECUTE 'DROP POLICY IF EXISTS tenant_isolation_evaluation_results ON evaluation_results';
        EXECUTE 'CREATE POLICY tenant_isolation_evaluation_results ON evaluation_results FOR ALL TO application_user USING (tenant_id = tenant_security.get_current_tenant()) WITH CHECK (tenant_id = tenant_security.get_current_tenant())';
        EXECUTE 'DROP POLICY IF EXISTS admin_bypass_evaluation_results ON evaluation_results';
        EXECUTE 'CREATE POLICY admin_bypass_evaluation_results ON evaluation_results FOR ALL TO db_admin USING (true) WITH CHECK (true)';
    END IF;
END $$;

-- ====================================================================================
-- memory_namespaces (V016)
-- ====================================================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'memory_namespaces' AND table_schema = 'public') THEN
        EXECUTE 'ALTER TABLE memory_namespaces ENABLE ROW LEVEL SECURITY';
        EXECUTE 'DROP POLICY IF EXISTS tenant_isolation_memory_namespaces ON memory_namespaces';
        EXECUTE 'CREATE POLICY tenant_isolation_memory_namespaces ON memory_namespaces FOR ALL TO application_user USING (tenant_id = tenant_security.get_current_tenant()) WITH CHECK (tenant_id = tenant_security.get_current_tenant())';
        EXECUTE 'DROP POLICY IF EXISTS admin_bypass_memory_namespaces ON memory_namespaces';
        EXECUTE 'CREATE POLICY admin_bypass_memory_namespaces ON memory_namespaces FOR ALL TO db_admin USING (true) WITH CHECK (true)';
    END IF;
END $$;

-- ====================================================================================
-- promotion_evidence (V017)
-- ====================================================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'promotion_evidence' AND table_schema = 'public') THEN
        EXECUTE 'ALTER TABLE promotion_evidence ENABLE ROW LEVEL SECURITY';
        EXECUTE 'DROP POLICY IF EXISTS tenant_isolation_promotion_evidence ON promotion_evidence';
        EXECUTE 'CREATE POLICY tenant_isolation_promotion_evidence ON promotion_evidence FOR ALL TO application_user USING (tenant_id = tenant_security.get_current_tenant()) WITH CHECK (tenant_id = tenant_security.get_current_tenant())';
        EXECUTE 'DROP POLICY IF EXISTS admin_bypass_promotion_evidence ON promotion_evidence';
        EXECUTE 'CREATE POLICY admin_bypass_promotion_evidence ON promotion_evidence FOR ALL TO db_admin USING (true) WITH CHECK (true)';
    END IF;
END $$;

-- ====================================================================================
-- media_artifacts (V018)
-- ====================================================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'media_artifacts' AND table_schema = 'public') THEN
        EXECUTE 'ALTER TABLE media_artifacts ENABLE ROW LEVEL SECURITY';
        EXECUTE 'DROP POLICY IF EXISTS tenant_isolation_media_artifacts ON media_artifacts';
        EXECUTE 'CREATE POLICY tenant_isolation_media_artifacts ON media_artifacts FOR ALL TO application_user USING (tenant_id = tenant_security.get_current_tenant()) WITH CHECK (tenant_id = tenant_security.get_current_tenant())';
        EXECUTE 'DROP POLICY IF EXISTS admin_bypass_media_artifacts ON media_artifacts';
        EXECUTE 'CREATE POLICY admin_bypass_media_artifacts ON media_artifacts FOR ALL TO db_admin USING (true) WITH CHECK (true)';
    END IF;
END $$;
