-- DMOS P0/P1: Durable connector and Google Ads persistence tables

CREATE TABLE IF NOT EXISTS dmos_connector_configs (
    id VARCHAR(128) PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    workspace_id VARCHAR(128),
    name VARCHAR(255) NOT NULL,
    connector_type VARCHAR(64) NOT NULL,
    status VARCHAR(64) NOT NULL,
    settings_json TEXT,
    external_account_id VARCHAR(255),
    failure_reason TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    last_health_check_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dmos_connector_configs_tenant_type
    ON dmos_connector_configs(tenant_id, connector_type);

CREATE INDEX IF NOT EXISTS idx_dmos_connector_configs_tenant_status
    ON dmos_connector_configs(tenant_id, status);

CREATE TABLE IF NOT EXISTS dmos_google_ads_credentials (
    id VARCHAR(128) PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    connector_id VARCHAR(128) NOT NULL,
    access_token TEXT NOT NULL,
    refresh_token TEXT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    scopes TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dmos_google_ads_credentials_tenant_connector
    ON dmos_google_ads_credentials(tenant_id, connector_id);

CREATE INDEX IF NOT EXISTS idx_dmos_google_ads_credentials_revoked
    ON dmos_google_ads_credentials(revoked);

CREATE TABLE IF NOT EXISTS dmos_google_ads_campaign_links (
    id VARCHAR(128) PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    connector_id VARCHAR(128) NOT NULL,
    internal_campaign_id VARCHAR(128) NOT NULL UNIQUE,
    external_campaign_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_dmos_google_ads_campaign_links_tenant_connector
    ON dmos_google_ads_campaign_links(tenant_id, connector_id);
