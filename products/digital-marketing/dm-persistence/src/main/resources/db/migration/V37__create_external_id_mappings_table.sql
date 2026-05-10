-- P0-006: External system ID mappings table
-- Maintains bidirectional mappings between DMOS internal IDs and external system IDs

CREATE TABLE IF NOT EXISTS external_id_mappings (
    id VARCHAR(36) PRIMARY KEY,
    internal_id VARCHAR(36) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    external_system VARCHAR(50) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    tenant_id VARCHAR(100) NOT NULL,
    workspace_id VARCHAR(100) NOT NULL,
    correlation_id VARCHAR(36) NOT NULL,
    mapped_at TIMESTAMP WITH TIME ZONE NOT NULL,
    mapped_by VARCHAR(100) NOT NULL,
    UNIQUE (internal_id, external_system, resource_type)
);

CREATE INDEX IF NOT EXISTS idx_external_id_mappings_internal ON external_id_mappings(internal_id);
CREATE INDEX IF NOT EXISTS idx_external_id_mappings_external ON external_id_mappings(external_id);
CREATE INDEX IF NOT EXISTS idx_external_id_mappings_tenant ON external_id_mappings(tenant_id);
CREATE INDEX IF NOT EXISTS idx_external_id_mappings_system ON external_id_mappings(external_system);

COMMENT ON TABLE external_id_mappings IS 'External system ID mappings for DMOS resources (P0-006)';
COMMENT ON COLUMN external_id_mappings.internal_id IS 'DMOS internal resource ID';
COMMENT ON COLUMN external_id_mappings.external_id IS 'External system resource ID (e.g., Google Ads campaign ID)';
COMMENT ON COLUMN external_id_mappings.external_system IS 'External system name (e.g., google-ads)';
COMMENT ON COLUMN external_id_mappings.resource_type IS 'Resource type (e.g., campaign, ad_group)';
