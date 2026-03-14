-- V003: MFA / TOTP enrollment per user (K01-004)

CREATE TABLE iam_mfa_enrollments (
    user_id         VARCHAR(128) NOT NULL,
    tenant_id       VARCHAR(64)  NOT NULL,
    totp_secret_b32 VARCHAR(64)  NOT NULL,       -- Base32-encoded TOTP secret
    backup_code_hashes TEXT[]    NOT NULL,        -- SHA-256 hex hashes; removed as consumed
    enrolled_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_mfa_enrollment PRIMARY KEY (user_id, tenant_id)
);

-- Fast lookup by tenant for admin ops
CREATE INDEX idx_mfa_enrollments_tenant ON iam_mfa_enrollments (tenant_id);
