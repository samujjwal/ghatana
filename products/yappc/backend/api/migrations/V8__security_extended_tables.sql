-- YAPPC Database Migration: V8 - Extended Security Tables
-- PostgreSQL 16
-- Adds support for Phase 6: Security (access policies, security incidents, threat detection, audit logs)

-- ========== Access Policies Table ==========
CREATE TABLE IF NOT EXISTS yappc.access_policies (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    policy_type VARCHAR(32) NOT NULL,
    resource_type VARCHAR(64),
    resource_pattern VARCHAR(256),
    subject_type VARCHAR(32) NOT NULL,
    subject_pattern VARCHAR(256),
    actions JSONB NOT NULL,
    conditions JSONB,
    effect VARCHAR(16) NOT NULL DEFAULT 'ALLOW',
    priority INTEGER DEFAULT 0,
    enabled BOOLEAN DEFAULT TRUE,
    created_by UUID REFERENCES yappc.users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    CONSTRAINT chk_policy_type CHECK (policy_type IN ('RBAC', 'ABAC', 'CUSTOM')),
    CONSTRAINT chk_policy_subject_type CHECK (subject_type IN ('USER', 'TEAM', 'ROLE', 'API_KEY')),
    CONSTRAINT chk_policy_effect CHECK (effect IN ('ALLOW', 'DENY'))
);

CREATE INDEX idx_access_policies_tenant ON yappc.access_policies(tenant_id);
CREATE INDEX idx_access_policies_type ON yappc.access_policies(tenant_id, policy_type);
CREATE INDEX idx_access_policies_enabled ON yappc.access_policies(tenant_id, enabled) WHERE enabled = TRUE;
CREATE INDEX idx_access_policies_priority ON yappc.access_policies(priority DESC);

CREATE TRIGGER trigger_access_policies_updated_at 
    BEFORE UPDATE ON yappc.access_policies 
    FOR EACH ROW EXECUTE FUNCTION yappc.update_updated_at();

-- ========== Security Incidents Table ==========
CREATE TABLE IF NOT EXISTS yappc.security_incidents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    project_id UUID REFERENCES yappc.projects(id) ON DELETE CASCADE,
    incident_type VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    title VARCHAR(256) NOT NULL,
    description TEXT,
    source VARCHAR(64),
    affected_resources JSONB DEFAULT '[]'::jsonb,
    attack_vector VARCHAR(64),
    indicators JSONB DEFAULT '[]'::jsonb,
    mitigation_steps TEXT,
    resolution TEXT,
    assigned_to UUID REFERENCES yappc.users(id) ON DELETE SET NULL,
    reported_by UUID REFERENCES yappc.users(id) ON DELETE SET NULL,
    detected_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    acknowledged_at TIMESTAMP WITH TIME ZONE,
    mitigated_at TIMESTAMP WITH TIME ZONE,
    resolved_at TIMESTAMP WITH TIME ZONE,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    CONSTRAINT chk_security_incident_type CHECK (incident_type IN (
        'UNAUTHORIZED_ACCESS', 'DATA_BREACH', 'MALWARE', 'PHISHING', 
        'DDoS', 'SQL_INJECTION', 'XSS', 'CSRF', 'PRIVILEGE_ESCALATION', 'OTHER'
    )),
    CONSTRAINT chk_security_incident_severity CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT chk_security_incident_status CHECK (status IN (
        'OPEN', 'ACKNOWLEDGED', 'INVESTIGATING', 'MITIGATED', 'RESOLVED', 'CLOSED'
    ))
);

CREATE INDEX idx_security_incidents_tenant ON yappc.security_incidents(tenant_id);
CREATE INDEX idx_security_incidents_project ON yappc.security_incidents(project_id);
CREATE INDEX idx_security_incidents_type ON yappc.security_incidents(tenant_id, incident_type);
CREATE INDEX idx_security_incidents_severity ON yappc.security_incidents(tenant_id, severity);
CREATE INDEX idx_security_incidents_status ON yappc.security_incidents(tenant_id, status);
CREATE INDEX idx_security_incidents_detected ON yappc.security_incidents(detected_at DESC);

CREATE TRIGGER trigger_security_incidents_updated_at 
    BEFORE UPDATE ON yappc.security_incidents 
    FOR EACH ROW EXECUTE FUNCTION yappc.update_updated_at();

-- ========== Threat Detections Table ==========
CREATE TABLE IF NOT EXISTS yappc.threat_detections (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    project_id UUID REFERENCES yappc.projects(id) ON DELETE CASCADE,
    detection_type VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    confidence NUMERIC(3, 2) NOT NULL,
    source VARCHAR(64) NOT NULL,
    source_ip VARCHAR(45),
    user_id UUID REFERENCES yappc.users(id) ON DELETE SET NULL,
    resource_type VARCHAR(64),
    resource_id VARCHAR(128),
    description TEXT NOT NULL,
    indicators JSONB DEFAULT '[]'::jsonb,
    raw_data JSONB,
    false_positive BOOLEAN DEFAULT FALSE,
    security_incident_id UUID REFERENCES yappc.security_incidents(id) ON DELETE SET NULL,
    reviewed BOOLEAN DEFAULT FALSE,
    reviewed_by UUID REFERENCES yappc.users(id) ON DELETE SET NULL,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    detected_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    metadata JSONB DEFAULT '{}'::jsonb,
    
    CONSTRAINT chk_threat_detection_type CHECK (detection_type IN (
        'ANOMALOUS_BEHAVIOR', 'BRUTE_FORCE', 'SUSPICIOUS_LOGIN', 
        'DATA_EXFILTRATION', 'MALICIOUS_PAYLOAD', 'RATE_LIMIT_EXCEEDED', 
        'INVALID_TOKEN', 'PRIVILEGE_ABUSE', 'OTHER'
    )),
    CONSTRAINT chk_threat_severity CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO')),
    CONSTRAINT chk_threat_confidence CHECK (confidence >= 0 AND confidence <= 1)
);

CREATE INDEX idx_threat_detections_tenant ON yappc.threat_detections(tenant_id);
CREATE INDEX idx_threat_detections_project ON yappc.threat_detections(project_id);
CREATE INDEX idx_threat_detections_type ON yappc.threat_detections(tenant_id, detection_type);
CREATE INDEX idx_threat_detections_severity ON yappc.threat_detections(tenant_id, severity);
CREATE INDEX idx_threat_detections_user ON yappc.threat_detections(user_id) WHERE user_id IS NOT NULL;
CREATE INDEX idx_threat_detections_unreviewed ON yappc.threat_detections(tenant_id, reviewed) WHERE reviewed = FALSE;
CREATE INDEX idx_threat_detections_detected ON yappc.threat_detections(detected_at DESC);

-- ========== Audit Logs Table ==========
CREATE TABLE IF NOT EXISTS yappc.audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    user_id UUID REFERENCES yappc.users(id) ON DELETE SET NULL,
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128),
    resource_name VARCHAR(256),
    ip_address VARCHAR(45),
    user_agent TEXT,
    session_id UUID,
    status VARCHAR(16) NOT NULL,
    details JSONB DEFAULT '{}'::jsonb,
    changes JSONB,
    error_message TEXT,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    CONSTRAINT chk_audit_status CHECK (status IN ('SUCCESS', 'FAILURE', 'PARTIAL'))
);

CREATE INDEX idx_audit_logs_tenant ON yappc.audit_logs(tenant_id);
CREATE INDEX idx_audit_logs_user ON yappc.audit_logs(user_id, timestamp DESC);
CREATE INDEX idx_audit_logs_action ON yappc.audit_logs(tenant_id, action, timestamp DESC);
CREATE INDEX idx_audit_logs_resource ON yappc.audit_logs(resource_type, resource_id);
CREATE INDEX idx_audit_logs_timestamp ON yappc.audit_logs(timestamp DESC);
CREATE INDEX idx_audit_logs_status ON yappc.audit_logs(tenant_id, status) WHERE status = 'FAILURE';

-- ========== API Keys Table ==========
CREATE TABLE IF NOT EXISTS yappc.api_keys (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    user_id UUID NOT NULL REFERENCES yappc.users(id) ON DELETE CASCADE,
    name VARCHAR(128) NOT NULL,
    key_hash VARCHAR(256) NOT NULL,
    key_prefix VARCHAR(16) NOT NULL,
    scopes JSONB DEFAULT '[]'::jsonb,
    rate_limit INTEGER,
    rate_limit_window INTEGER,
    ip_whitelist JSONB DEFAULT '[]'::jsonb,
    enabled BOOLEAN DEFAULT TRUE,
    last_used_at TIMESTAMP WITH TIME ZONE,
    usage_count INTEGER DEFAULT 0,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    revoked_at TIMESTAMP WITH TIME ZONE,
    
    UNIQUE(key_hash)
);

CREATE INDEX idx_api_keys_tenant ON yappc.api_keys(tenant_id);
CREATE INDEX idx_api_keys_user ON yappc.api_keys(user_id);
CREATE INDEX idx_api_keys_prefix ON yappc.api_keys(key_prefix);
CREATE INDEX idx_api_keys_enabled ON yappc.api_keys(tenant_id, enabled) WHERE enabled = TRUE;

-- ========== Compliance Checks Table ==========
CREATE TABLE IF NOT EXISTS yappc.compliance_checks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(64) NOT NULL,
    project_id UUID REFERENCES yappc.projects(id) ON DELETE CASCADE,
    framework VARCHAR(64) NOT NULL,
    control_id VARCHAR(64) NOT NULL,
    control_name VARCHAR(256) NOT NULL,
    category VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    compliance_level NUMERIC(3, 2),
    findings JSONB DEFAULT '[]'::jsonb,
    evidence JSONB DEFAULT '[]'::jsonb,
    remediation TEXT,
    assigned_to UUID REFERENCES yappc.users(id) ON DELETE SET NULL,
    last_checked_at TIMESTAMP WITH TIME ZONE,
    next_check_at TIMESTAMP WITH TIME ZONE,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    CONSTRAINT chk_compliance_framework CHECK (framework IN (
        'SOC2', 'ISO27001', 'GDPR', 'HIPAA', 'PCI_DSS', 'NIST', 'CIS', 'OTHER'
    )),
    CONSTRAINT chk_compliance_status CHECK (status IN (
        'COMPLIANT', 'NON_COMPLIANT', 'PARTIAL', 'NOT_APPLICABLE', 'PENDING'
    ))
);

CREATE INDEX idx_compliance_checks_tenant ON yappc.compliance_checks(tenant_id);
CREATE INDEX idx_compliance_checks_project ON yappc.compliance_checks(project_id);
CREATE INDEX idx_compliance_checks_framework ON yappc.compliance_checks(tenant_id, framework);
CREATE INDEX idx_compliance_checks_status ON yappc.compliance_checks(tenant_id, status);
CREATE INDEX idx_compliance_checks_control ON yappc.compliance_checks(framework, control_id);

CREATE TRIGGER trigger_compliance_checks_updated_at 
    BEFORE UPDATE ON yappc.compliance_checks 
    FOR EACH ROW EXECUTE FUNCTION yappc.update_updated_at();

-- ========== Security Scan Results Table (Extended) ==========
CREATE TABLE IF NOT EXISTS yappc.security_scan_results (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    scan_id UUID NOT NULL REFERENCES yappc.security_scans(id) ON DELETE CASCADE,
    tenant_id VARCHAR(64) NOT NULL,
    finding_type VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    title VARCHAR(256) NOT NULL,
    description TEXT,
    file_path VARCHAR(512),
    line_number INTEGER,
    code_snippet TEXT,
    cwe_id VARCHAR(16),
    owasp_category VARCHAR(64),
    recommendation TEXT,
    false_positive BOOLEAN DEFAULT FALSE,
    suppressed BOOLEAN DEFAULT FALSE,
    suppressed_by UUID REFERENCES yappc.users(id) ON DELETE SET NULL,
    suppressed_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    CONSTRAINT chk_scan_result_severity CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'))
);

CREATE INDEX idx_security_scan_results_scan ON yappc.security_scan_results(scan_id);
CREATE INDEX idx_security_scan_results_severity ON yappc.security_scan_results(tenant_id, severity);
CREATE INDEX idx_security_scan_results_type ON yappc.security_scan_results(tenant_id, finding_type);
CREATE INDEX idx_security_scan_results_unsuppressed ON yappc.security_scan_results(tenant_id, suppressed) 
    WHERE suppressed = FALSE AND false_positive = FALSE;
