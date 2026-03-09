-- V3: Add columns from L2 domain entities merged into L3 (yappc-domain)
-- Part of Phase 1 - Domain Consolidation (split-package resolution)

-- ============================================================
-- Project: add key and archived_at
-- ============================================================
ALTER TABLE projects ADD COLUMN IF NOT EXISTS key VARCHAR(20);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS archived_at TIMESTAMPTZ;

CREATE UNIQUE INDEX IF NOT EXISTS idx_projects_key ON projects (key) WHERE key IS NOT NULL;

-- ============================================================
-- Incident: add project_id, owner_id, tags
-- ============================================================
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS project_id UUID;
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS owner_id UUID;
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS tags JSONB DEFAULT '[]';

CREATE INDEX IF NOT EXISTS idx_incidents_project_id ON incidents (project_id);
CREATE INDEX IF NOT EXISTS idx_incidents_owner_id ON incidents (owner_id);

-- ============================================================
-- ScanJob: add scanner_name, scanner_version, target, info_count
-- ============================================================
ALTER TABLE scan_jobs ADD COLUMN IF NOT EXISTS scanner_name VARCHAR(200);
ALTER TABLE scan_jobs ADD COLUMN IF NOT EXISTS scanner_version VARCHAR(50);
ALTER TABLE scan_jobs ADD COLUMN IF NOT EXISTS target VARCHAR(500);
ALTER TABLE scan_jobs ADD COLUMN IF NOT EXISTS info_count INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_scan_jobs_scanner_name ON scan_jobs (scanner_name);

-- ============================================================
-- SecurityAlert: add project_id, incident_id, rule_id, rule_name,
--                detected_at, assigned_to, metadata, affected_resources
-- ============================================================
ALTER TABLE security_alerts ADD COLUMN IF NOT EXISTS project_id UUID;
ALTER TABLE security_alerts ADD COLUMN IF NOT EXISTS incident_id UUID;
ALTER TABLE security_alerts ADD COLUMN IF NOT EXISTS rule_id VARCHAR(200);
ALTER TABLE security_alerts ADD COLUMN IF NOT EXISTS rule_name VARCHAR(500);
ALTER TABLE security_alerts ADD COLUMN IF NOT EXISTS detected_at TIMESTAMPTZ;
ALTER TABLE security_alerts ADD COLUMN IF NOT EXISTS assigned_to UUID;
ALTER TABLE security_alerts ADD COLUMN IF NOT EXISTS metadata JSONB DEFAULT '{}';
ALTER TABLE security_alerts ADD COLUMN IF NOT EXISTS affected_resources JSONB DEFAULT '[]';

CREATE INDEX IF NOT EXISTS idx_security_alerts_project_id ON security_alerts (project_id);
CREATE INDEX IF NOT EXISTS idx_security_alerts_incident_id ON security_alerts (incident_id);
CREATE INDEX IF NOT EXISTS idx_security_alerts_rule_id ON security_alerts (rule_id);
CREATE INDEX IF NOT EXISTS idx_security_alerts_assigned_to ON security_alerts (assigned_to);

-- ============================================================
-- ComplianceAssessment: add project_id, assessment_date, due_date,
--                       assessor_name, assessment_type, notes
-- ============================================================
ALTER TABLE compliance_assessments ADD COLUMN IF NOT EXISTS project_id UUID;
ALTER TABLE compliance_assessments ADD COLUMN IF NOT EXISTS assessment_date DATE;
ALTER TABLE compliance_assessments ADD COLUMN IF NOT EXISTS due_date DATE;
ALTER TABLE compliance_assessments ADD COLUMN IF NOT EXISTS assessor_name VARCHAR(200);
ALTER TABLE compliance_assessments ADD COLUMN IF NOT EXISTS assessment_type VARCHAR(50);
ALTER TABLE compliance_assessments ADD COLUMN IF NOT EXISTS notes TEXT;

CREATE INDEX IF NOT EXISTS idx_compliance_assessments_project_id ON compliance_assessments (project_id);
CREATE INDEX IF NOT EXISTS idx_compliance_assessments_type ON compliance_assessments (assessment_type);

-- ============================================================
-- Dashboard: add key, title, persona, config, filters, created_by_id
-- ============================================================
ALTER TABLE dashboards ADD COLUMN IF NOT EXISTS key VARCHAR(100);
ALTER TABLE dashboards ADD COLUMN IF NOT EXISTS title VARCHAR(255);
ALTER TABLE dashboards ADD COLUMN IF NOT EXISTS persona VARCHAR(50);
ALTER TABLE dashboards ADD COLUMN IF NOT EXISTS config JSONB DEFAULT '{}';
ALTER TABLE dashboards ADD COLUMN IF NOT EXISTS filters JSONB DEFAULT '{}';
ALTER TABLE dashboards ADD COLUMN IF NOT EXISTS created_by_id UUID;

CREATE UNIQUE INDEX IF NOT EXISTS idx_dashboards_key ON dashboards (key) WHERE key IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_dashboards_persona ON dashboards (persona);
CREATE INDEX IF NOT EXISTS idx_dashboards_created_by_id ON dashboards (created_by_id);
