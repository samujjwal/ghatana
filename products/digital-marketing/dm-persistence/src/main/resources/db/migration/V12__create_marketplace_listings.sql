-- Migration V12: Create marketplace listings table (DMOS-P3-004)
CREATE TABLE IF NOT EXISTS dmos_marketplace_listings (
    listing_id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    author_tenant_id VARCHAR(64) NOT NULL,
    version VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    rating DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    download_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_marketplace_listings_author ON dmos_marketplace_listings(author_tenant_id);
CREATE INDEX idx_marketplace_listings_status ON dmos_marketplace_listings(status);
CREATE INDEX idx_marketplace_listings_published ON dmos_marketplace_listings(status) WHERE status = 'PUBLISHED';
