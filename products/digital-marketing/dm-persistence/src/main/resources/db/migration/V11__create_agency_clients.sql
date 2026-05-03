-- Migration V11: Create agency clients table (DMOS-P3-003)
CREATE TABLE IF NOT EXISTS dmos_agency_clients (
    client_id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL UNIQUE,
    client_name VARCHAR(255) NOT NULL,
    contact_email VARCHAR(255),
    contact_phone VARCHAR(255),
    branding_theme VARCHAR(100),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_agency_clients_tenant ON dmos_agency_clients(tenant_id);
CREATE INDEX idx_agency_clients_workspace ON dmos_agency_clients(workspace_id);
CREATE INDEX idx_agency_clients_active ON dmos_agency_clients(tenant_id, active);
