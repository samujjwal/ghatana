-- K01-001: IAM schema for client credentials OAuth 2.0 flow
-- Creates tables for client credentials and RBAC model

-- ─── Client credentials table ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS iam_client_credentials (
    client_id           UUID            PRIMARY KEY,
    client_id_str       VARCHAR(128)    NOT NULL UNIQUE,   -- human-readable client ID
    client_secret_hash  VARCHAR(256)    NOT NULL,           -- bcrypt hash of client secret
    tenant_id           UUID            NOT NULL,
    granted_scopes      TEXT[]          NOT NULL DEFAULT '{}',
    status              VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE'
                            CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED')),
    description         VARCHAR(512),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    last_used_at        TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_iam_cc_client_id_str ON iam_client_credentials (client_id_str);
CREATE INDEX IF NOT EXISTS idx_iam_cc_tenant        ON iam_client_credentials (tenant_id);

-- ─── Roles and permissions table ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS iam_roles_permissions (
    role_name       VARCHAR(128)    NOT NULL,
    permission      VARCHAR(256)    NOT NULL,               -- format: resource:action e.g. ledger:post
    tenant_id       UUID,                                   -- NULL = platform-level role
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (role_name, permission, COALESCE(tenant_id, '00000000-0000-0000-0000-000000000000'::UUID))
);

CREATE INDEX IF NOT EXISTS idx_iam_rp_role   ON iam_roles_permissions (role_name);
CREATE INDEX IF NOT EXISTS idx_iam_rp_tenant ON iam_roles_permissions (tenant_id);

-- ─── Signing key store (public key material only) ─────────────────────────────
-- Private keys are stored in secrets-management (K-14)
CREATE TABLE IF NOT EXISTS iam_signing_keys (
    key_id          VARCHAR(128)    PRIMARY KEY,
    algorithm       VARCHAR(32)     NOT NULL DEFAULT 'RS256',
    public_key_pem  TEXT            NOT NULL,
    status          VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE'
                        CHECK (status IN ('ACTIVE', 'ROTATING', 'RETIRED')),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    retired_at      TIMESTAMPTZ
);

-- ─── Seed platform roles ──────────────────────────────────────────────────────
INSERT INTO iam_roles_permissions (role_name, permission) VALUES
    ('platform:admin',   'ledger:read'),
    ('platform:admin',   'ledger:post'),
    ('platform:admin',   'secrets:read'),
    ('platform:admin',   'secrets:write'),
    ('platform:viewer',  'ledger:read'),
    ('service:ledger',   'ledger:post'),
    ('service:ledger',   'ledger:read')
ON CONFLICT DO NOTHING;
