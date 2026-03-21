-- Flyway V003: Create agent registry tables
-- Maps to: com.ghatana.agent.registry.store.JpaAgentEntity (@Entity, table = "agent_specs")
-- Includes: agent_tags collection table (@ElementCollection)

CREATE TABLE IF NOT EXISTS agent_specs (
    id                  VARCHAR(255)    NOT NULL,
    name                VARCHAR(255)    NOT NULL,
    description         VARCHAR(4000),
    version             VARCHAR(255)    NOT NULL,
    implementation_type VARCHAR(255),
    implementation_uri  VARCHAR(1024),
    tenant_id           VARCHAR(255),
    deprecated          BOOLEAN,
    input_schema        TEXT,
    output_schema       TEXT,
    config_schema       TEXT,
    created_at          TIMESTAMPTZ     DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     DEFAULT NOW(),
    deleted             BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMPTZ,

    CONSTRAINT pk_agent_specs PRIMARY KEY (id)
);

-- Agent tags — element collection for flexible tagging
CREATE TABLE IF NOT EXISTS agent_tags (
    agent_id            VARCHAR(255)    NOT NULL,
    tag                 VARCHAR(255),

    CONSTRAINT fk_agent_tags_agent FOREIGN KEY (agent_id)
        REFERENCES agent_specs (id) ON DELETE CASCADE
);

CREATE INDEX idx_agent_tags_agent_id ON agent_tags (agent_id);

-- Query patterns: lookup by tenant, find by name, filter active/deprecated
CREATE INDEX idx_agent_specs_tenant ON agent_specs (tenant_id);
CREATE INDEX idx_agent_specs_name ON agent_specs (name);
CREATE INDEX idx_agent_specs_tenant_active ON agent_specs (tenant_id, deleted)
    WHERE deleted = FALSE;
CREATE INDEX idx_agent_specs_impl_type ON agent_specs (implementation_type);

-- Trigger to update updated_at on agent_specs
CREATE OR REPLACE FUNCTION update_agent_specs_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_agent_specs_updated_at
    BEFORE UPDATE ON agent_specs
    FOR EACH ROW EXECUTE FUNCTION update_agent_specs_updated_at();

-- Documentation
COMMENT ON TABLE agent_specs IS 'Agent registry — stores agent definitions, schemas, and lifecycle metadata. Supports soft-delete and multi-tenancy.';
COMMENT ON COLUMN agent_specs.id IS 'Unique agent identifier (e.g., "deterministic-v1")';
COMMENT ON COLUMN agent_specs.implementation_type IS 'Agent type: DETERMINISTIC, PROBABILISTIC, HYBRID, ADAPTIVE, COMPOSITE, REACTIVE';
COMMENT ON COLUMN agent_specs.implementation_uri IS 'URI reference to agent implementation (JAR, class, remote endpoint)';
COMMENT ON COLUMN agent_specs.input_schema IS 'JSON Schema definition for agent input validation';
COMMENT ON COLUMN agent_specs.output_schema IS 'JSON Schema definition for agent output contract';
COMMENT ON COLUMN agent_specs.config_schema IS 'JSON Schema definition for agent configuration';
COMMENT ON COLUMN agent_specs.deleted IS 'Soft-delete flag. TRUE = logically removed.';
COMMENT ON TABLE agent_tags IS 'Tags for agent classification and discovery. Many-to-one relationship with agent_specs.';
