-- Flyway V010: Create entity_relations table for FK-constrained entity relationships
-- DC3-L4: Adds proper FK constraints between related entities to enforce referential integrity.
--
-- Purpose: enable typed, queryable relationships between entities (e.g. order→customer,
-- device→telemetry stream) with cascade-delete semantics and tenant isolation.

CREATE TABLE IF NOT EXISTS entity_relations (
    id              UUID            NOT NULL,
    tenant_id       VARCHAR(255)    NOT NULL,
    source_id       UUID            NOT NULL,
    target_id       UUID            NOT NULL,
    relation_type   VARCHAR(100)    NOT NULL,
    properties      JSONB           DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by      VARCHAR(255),

    CONSTRAINT pk_entity_relations PRIMARY KEY (id),

    -- FK to entities table; cascade-delete cleans up orphaned relations automatically.
    CONSTRAINT fk_entity_relations_source
        FOREIGN KEY (source_id) REFERENCES entities (id) ON DELETE CASCADE,
    CONSTRAINT fk_entity_relations_target
        FOREIGN KEY (target_id) REFERENCES entities (id) ON DELETE CASCADE,

    -- Prevent duplicate (directional) relation between the same pair.
    CONSTRAINT uq_entity_relations_pair
        UNIQUE (tenant_id, source_id, target_id, relation_type)
);

-- Indexes for common query patterns
CREATE INDEX idx_entity_relations_tenant     ON entity_relations (tenant_id);
CREATE INDEX idx_entity_relations_source     ON entity_relations (tenant_id, source_id, relation_type);
CREATE INDEX idx_entity_relations_target     ON entity_relations (tenant_id, target_id, relation_type);
CREATE INDEX idx_entity_relations_created_at ON entity_relations (tenant_id, created_at DESC);

-- Documentation
COMMENT ON TABLE entity_relations IS 'Directed, typed relationships between entities. FK constraints enforce referential integrity; CASCADE DELETE removes orphaned relations automatically.';
COMMENT ON COLUMN entity_relations.source_id      IS 'UUID of the source entity (FK → entities.id, CASCADE DELETE)';
COMMENT ON COLUMN entity_relations.target_id      IS 'UUID of the target entity (FK → entities.id, CASCADE DELETE)';
COMMENT ON COLUMN entity_relations.relation_type  IS 'Relationship type label, e.g. OWNED_BY, REFERENCES, PART_OF';
COMMENT ON COLUMN entity_relations.properties     IS 'Extensible relationship metadata as JSONB';
