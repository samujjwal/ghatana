-- V004: Merkle tree anchoring table for tamper-evident audit verification (K07-015/016)

CREATE TABLE audit_merkle_anchors (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    window_start    TIMESTAMPTZ  NOT NULL,
    window_end      TIMESTAMPTZ  NOT NULL,
    merkle_root     VARCHAR(64)  NOT NULL,  -- SHA-256 hex
    leaf_count      INTEGER      NOT NULL,
    anchored_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_anchor_window UNIQUE (tenant_id, window_start, window_end)
);

-- Immutability: prevent modification of existing anchors
CREATE OR REPLACE FUNCTION prevent_anchor_update()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.merkle_root IS DISTINCT FROM NEW.merkle_root
       AND NEW.anchored_at < NOW() - INTERVAL '1 minute' THEN
        RAISE EXCEPTION 'Merkle anchor is immutable after 1 minute: id=%', OLD.id;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_anchor_immutable
    BEFORE UPDATE ON audit_merkle_anchors
    FOR EACH ROW EXECUTE FUNCTION prevent_anchor_update();

-- Tenant-scoped lookup index
CREATE INDEX idx_merkle_anchors_tenant ON audit_merkle_anchors (tenant_id, window_start);

COMMENT ON TABLE audit_merkle_anchors IS
  'Merkle root anchors for verifying audit log integrity over time windows (K07-015)';
