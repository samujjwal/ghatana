-- Flyway V007: Create aep_checkpoints table for PostgresCheckpointStorage
-- Replaces InMemoryCheckpointStorage for production durability.
-- Package: com.ghatana.statestore.checkpoint

-- ============================================================================
-- AEP operator checkpoints — persists CheckpointMetadata for fault recovery
-- ============================================================================
CREATE TABLE IF NOT EXISTS aep_checkpoints (
    id               VARCHAR(255)    NOT NULL,
    type             VARCHAR(20)     NOT NULL DEFAULT 'CHECKPOINT',
    status           VARCHAR(20)     NOT NULL DEFAULT 'IN_PROGRESS',
    start_time       TIMESTAMPTZ     NOT NULL,
    complete_time    TIMESTAMPTZ,
    failure_reason   TEXT,
    operator_acks    JSONB           NOT NULL DEFAULT '{}',
    checkpoint_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_aep_checkpoints PRIMARY KEY (id),
    CONSTRAINT chk_aep_checkpoint_type   CHECK (type   IN ('CHECKPOINT', 'SAVEPOINT')),
    CONSTRAINT chk_aep_checkpoint_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'FAILED', 'CANCELLED'))
);

CREATE INDEX idx_aep_checkpoints_type   ON aep_checkpoints (type);
CREATE INDEX idx_aep_checkpoints_status ON aep_checkpoints (status);
CREATE INDEX idx_aep_checkpoints_at     ON aep_checkpoints (checkpoint_at);
