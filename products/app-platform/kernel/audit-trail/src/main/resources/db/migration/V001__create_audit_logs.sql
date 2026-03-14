-- Flyway V001: Audit logs table with SHA-256 cryptographic hash chain.
--
-- Key design decisions:
-- 1. UNIQUE (tenant_id, sequence_number) enforces per-tenant hash-chain ordering.
-- 2. UPDATE/DELETE triggers make modifications impossible (see V002).
-- 3. Dual-calendar timestamps: timestamp_gregorian (canonical) + timestamp_bs (calendar-service enriched).

CREATE TABLE IF NOT EXISTS audit_logs (
    audit_id             VARCHAR(255)  NOT NULL,
    sequence_number      BIGSERIAL     NOT NULL,
    action               VARCHAR(255)  NOT NULL,
    -- Actor: who performed the action (JSON: user_id, role, ip_address, session_id)
    actor                JSONB         NOT NULL,
    -- Resource: what was acted upon (JSON: type, id, parent_id)
    resource             JSONB         NOT NULL,
    -- Arbitrary action-specific details (encrypted for sensitive data)
    details              JSONB,
    outcome              VARCHAR(20)   NOT NULL,
    tenant_id            VARCHAR(255)  NOT NULL,
    trace_id             VARCHAR(255),
    -- SHA-256 of previous entry in the chain. Genesis entry uses 64 zeros.
    previous_hash        VARCHAR(64)   NOT NULL,
    -- SHA-256(previous_hash || canonical_json(event_data))
    current_hash         VARCHAR(64)   NOT NULL,
    -- BS (Bikram Sambat) date string — populated by calendar-service, may be empty until wired
    timestamp_bs         VARCHAR(10)   NOT NULL DEFAULT '',
    timestamp_gregorian  TIMESTAMPTZ   NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_audit_logs PRIMARY KEY (audit_id),
    CONSTRAINT chk_audit_outcome CHECK (outcome IN ('SUCCESS','FAILURE','PARTIAL')),
    -- Per-tenant sequence is the basis of the hash chain
    CONSTRAINT uq_audit_tenant_seq UNIQUE (tenant_id, sequence_number)
);

-- Tenant + sequence: primary hash-chain traversal
CREATE INDEX idx_audit_logs_tenant_seq
    ON audit_logs (tenant_id, sequence_number ASC);

-- Action + time: compliance search queries
CREATE INDEX idx_audit_logs_action_ts
    ON audit_logs (tenant_id, action, timestamp_gregorian DESC);

-- Actor user id: user-level audit trails
CREATE INDEX idx_audit_logs_actor_userid
    ON audit_logs ((actor->>'user_id'), timestamp_gregorian DESC);

-- Resource search: entity audit trail
CREATE INDEX idx_audit_logs_resource
    ON audit_logs ((resource->>'type'), (resource->>'id'));

-- Distributed tracing correlation
CREATE INDEX idx_audit_logs_trace
    ON audit_logs (trace_id);

COMMENT ON TABLE audit_logs IS
    'Immutable audit trail with SHA-256 hash chain for tamper detection. '
    'Hash chain: current_hash = SHA256(previous_hash || sorted_canonical_json(event_data)). '
    'Genesis row: previous_hash = 64 zero characters.';

COMMENT ON COLUMN audit_logs.sequence_number IS
    'Per-tenant monotonic counter used to order the hash chain. Auto-incremented by DB.';
COMMENT ON COLUMN audit_logs.previous_hash IS
    'SHA-256 hex of the immediately preceding entry for this tenant. '
    'Genesis rows use ''0000000000000000000000000000000000000000000000000000000000000000''.';
COMMENT ON COLUMN audit_logs.current_hash IS
    'SHA-256 hex of (previous_hash || canonical JSON of this entry). '
    'Used to verify chain integrity.';
COMMENT ON COLUMN audit_logs.timestamp_bs IS
    'Bikram Sambat calendar date (YYYY-MM-DD). Empty string until calendar-service kernel is wired.';
