-- YAPPC Database Performance Optimization
-- Migration: V7_0_0__YAPPC_PERFORMANCE_INDEX_OPTIMIZATION.sql
-- Adds composite and partial indexes for common tenant-scoped operational queries.

-- Workflow executions: most frequent query pattern is tenant + status + recency.
CREATE INDEX IF NOT EXISTS idx_workflow_executions_tenant_status_created_at
    ON workflow_executions (tenant_id, status, created_at DESC);

-- Stories and tasks: boards frequently query by tenant/project status and priority.
CREATE INDEX IF NOT EXISTS idx_stories_tenant_project_status_priority
    ON stories (tenant_id, project_id, status, priority);

CREATE INDEX IF NOT EXISTS idx_tasks_tenant_status_due_date
    ON tasks (tenant_id, status, due_date);

-- Alerts and incidents: active records are read far more often than resolved records.
CREATE INDEX IF NOT EXISTS idx_alerts_active_tenant_created_at
    ON alerts (tenant_id, created_at DESC)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_incidents_open_tenant_started_at
    ON incidents (tenant_id, started_at DESC)
    WHERE status = 'OPEN';

-- Security operations: triage views prioritize unresolved high severity findings.
CREATE INDEX IF NOT EXISTS idx_vulnerabilities_open_tenant_severity_updated
    ON vulnerabilities (tenant_id, severity, updated_at DESC)
    WHERE status = 'OPEN';

CREATE INDEX IF NOT EXISTS idx_security_scans_tenant_status_created_at
    ON security_scans (tenant_id, status, created_at DESC);

-- Time-series lookups: optimize tenant-scoped metric and trace windows.
CREATE INDEX IF NOT EXISTS idx_metrics_tenant_name_timestamp
    ON metrics (tenant_id, name, timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_traces_tenant_service_start_time
    ON traces (tenant_id, service_name, start_time DESC);

-- Log exploration: high-volume filtering by tenant and severity over recent windows.
CREATE INDEX IF NOT EXISTS idx_log_entries_tenant_level_timestamp
    ON log_entries (tenant_id, level, timestamp DESC);

-- JSONB acceleration for metadata-heavy ad hoc filters used in diagnostics.
CREATE INDEX IF NOT EXISTS idx_projects_metadata_gin
    ON projects USING GIN (metadata);

CREATE INDEX IF NOT EXISTS idx_stories_metadata_gin
    ON stories USING GIN (metadata);
