CREATE TABLE IF NOT EXISTS phr_users (
    user_id VARCHAR(255) PRIMARY KEY,
    username VARCHAR(255),
    email VARCHAR(255),
    provider_id VARCHAR(255),
    access_level VARCHAR(255),
    roles_json TEXT NOT NULL DEFAULT '[]',
    permissions_json TEXT NOT NULL DEFAULT '[]',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    password_hash TEXT,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    lockout_until TIMESTAMP NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_phr_users_username ON phr_users (username);

CREATE TABLE IF NOT EXISTS phr_patient_consents (
    consent_id VARCHAR(255) PRIMARY KEY,
    patient_id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255),
    purpose VARCHAR(255) NOT NULL,
    granted BOOLEAN NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NULL,
    granted_by VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_phr_patient_consents_patient_id ON phr_patient_consents (patient_id);
CREATE INDEX IF NOT EXISTS idx_phr_patient_consents_purpose ON phr_patient_consents (purpose);

CREATE TABLE IF NOT EXISTS phr_patient_records (
    record_id VARCHAR(255) PRIMARY KEY,
    patient_id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255),
    record_type VARCHAR(255),
    data_json TEXT NOT NULL DEFAULT '{}',
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_phr_patient_records_patient_id ON phr_patient_records (patient_id);
CREATE INDEX IF NOT EXISTS idx_phr_patient_records_tenant_id ON phr_patient_records (tenant_id);

CREATE TABLE IF NOT EXISTS phr_tenant_configs (
    tenant_id VARCHAR(255) PRIMARY KEY,
    tenant_name VARCHAR(255),
    allowed_regions_json TEXT NOT NULL DEFAULT '[]',
    hipaa_compliant BOOLEAN NOT NULL DEFAULT TRUE,
    mfa_required BOOLEAN NOT NULL DEFAULT TRUE
);