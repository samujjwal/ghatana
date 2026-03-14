-- V004__iam_ownership.sql
-- Beneficial-ownership entities and links for K01-019 / K01-020 / K01-021.

CREATE TABLE iam_ownership_entities (
    entity_id   VARCHAR(128)        NOT NULL,
    entity_name VARCHAR(512)        NOT NULL,
    entity_type VARCHAR(32)         NOT NULL,   -- PERSON | LEGAL_ENTITY | ACCOUNT
    tenant_id   VARCHAR(128)        NOT NULL,
    created_at  TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_iam_ownership_entities PRIMARY KEY (entity_id, tenant_id)
);

CREATE TABLE iam_ownership_links (
    link_id           BIGSERIAL        PRIMARY KEY,
    parent_id         VARCHAR(128)     NOT NULL,
    child_id          VARCHAR(128)     NOT NULL,
    relationship_type VARCHAR(32)      NOT NULL,  -- OWNS_PERCENTAGE | CONTROLS | BENEFICIARY_OF
    percentage        NUMERIC(7, 4)   NOT NULL     CHECK (percentage >= 0 AND percentage <= 100),
    tenant_id         VARCHAR(128)     NOT NULL,
    valid_from        TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    valid_to          TIMESTAMPTZ      NULL,
    CONSTRAINT fk_iol_parent FOREIGN KEY (parent_id, tenant_id)
        REFERENCES iam_ownership_entities (entity_id, tenant_id),
    CONSTRAINT fk_iol_child  FOREIGN KEY (child_id, tenant_id)
        REFERENCES iam_ownership_entities (entity_id, tenant_id)
);

-- Indexes for recursive CTE traversal
CREATE INDEX idx_iol_child_tenant   ON iam_ownership_links (child_id,  tenant_id);
CREATE INDEX idx_iol_parent_tenant  ON iam_ownership_links (parent_id, tenant_id);
CREATE INDEX idx_iol_active_links   ON iam_ownership_links (child_id,  tenant_id) WHERE valid_to IS NULL;
