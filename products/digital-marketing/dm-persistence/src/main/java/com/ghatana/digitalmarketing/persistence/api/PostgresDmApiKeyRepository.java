package com.ghatana.digitalmarketing.persistence.api;

import com.ghatana.digitalmarketing.application.api.DmApiKeyRepository;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.api.DmApiKey;
import io.activej.promise.Promise;

import java.sql.*;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * PostgreSQL adapter for {@link DmApiKeyRepository} (DMOS-P1-016).
 *
 * @doc.type class
 * @doc.purpose PostgreSQL adapter for API key storage (DMOS-P1-016)
 * @doc.layer persistence
 * @doc.pattern Repository
 */
public final class PostgresDmApiKeyRepository implements DmApiKeyRepository {

    private final Connection connection;
    private final Executor executor;

    public PostgresDmApiKeyRepository(Connection connection, Executor executor) {
        this.connection = connection;
        this.executor = executor;
    }

    @Override
    public Promise<DmApiKey> save(DmApiKey apiKey) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                INSERT INTO dmos_api_keys (
                    id, tenant_id, workspace_id, key_prefix, key_hash,
                    rate_limit_plan, created_at, last_used_at, expires_at,
                    revoked, revoked_at, revoked_by, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, workspace_id, key_prefix) DO UPDATE SET
                    key_hash = EXCLUDED.key_hash,
                    rate_limit_plan = EXCLUDED.rate_limit_plan,
                    last_used_at = EXCLUDED.last_used_at,
                    expires_at = EXCLUDED.expires_at,
                    revoked = EXCLUDED.revoked,
                    revoked_at = EXCLUDED.revoked_at,
                    revoked_by = EXCLUDED.revoked_by
                """;

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, apiKey.getId());
                stmt.setString(2, apiKey.getTenantId().getValue());
                stmt.setString(3, apiKey.getWorkspaceId().getValue());
                stmt.setString(4, apiKey.getKeyPrefix());
                stmt.setString(5, apiKey.getKeyHash());
                stmt.setString(6, apiKey.getRateLimitPlan());
                stmt.setTimestamp(7, Timestamp.from(apiKey.getCreatedAt()));
                stmt.setTimestamp(8, apiKey.getLastUsedAt() != null ? Timestamp.from(apiKey.getLastUsedAt()) : null);
                stmt.setTimestamp(9, apiKey.getExpiresAt() != null ? Timestamp.from(apiKey.getExpiresAt()) : null);
                stmt.setBoolean(10, apiKey.isRevoked());
                stmt.setTimestamp(11, apiKey.getRevokedAt() != null ? Timestamp.from(apiKey.getRevokedAt()) : null);
                stmt.setString(12, apiKey.getRevokedBy());
                stmt.setString(13, apiKey.getCreatedBy());

                stmt.executeUpdate();
                return apiKey;
            }
        });
    }

    @Override
    public Promise<Optional<DmApiKey>> findById(String id) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT id, tenant_id, workspace_id, key_prefix, key_hash,
                       rate_limit_plan, created_at, last_used_at, expires_at,
                       revoked, revoked_at, revoked_by, created_by
                FROM dmos_api_keys
                WHERE id = ?
                """;

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, id);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        });
    }

    @Override
    public Promise<Optional<DmApiKey>> findByKeyPrefix(String keyPrefix, DmTenantId tenantId, DmWorkspaceId workspaceId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT id, tenant_id, workspace_id, key_prefix, key_hash,
                       rate_limit_plan, created_at, last_used_at, expires_at,
                       revoked, revoked_at, revoked_by, created_by
                FROM dmos_api_keys
                WHERE key_prefix = ? AND tenant_id = ? AND workspace_id = ?
                """;

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, keyPrefix);
                stmt.setString(2, tenantId.getValue());
                stmt.setString(3, workspaceId.getValue());
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        });
    }

    @Override
    public Promise<DmApiKey> update(DmApiKey apiKey) {
        return save(apiKey);
    }

    @Override
    public Promise<Void> delete(String id) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "DELETE FROM dmos_api_keys WHERE id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, id);
                stmt.executeUpdate();
                return null;
            }
        });
    }

    private DmApiKey mapRow(ResultSet rs) throws SQLException {
        return DmApiKey.builder()
            .id(rs.getString("id"))
            .tenantId(DmTenantId.of(rs.getString("tenant_id")))
            .workspaceId(DmWorkspaceId.of(rs.getString("workspace_id")))
            .keyPrefix(rs.getString("key_prefix"))
            .keyHash(rs.getString("key_hash"))
            .rateLimitPlan(rs.getString("rate_limit_plan"))
            .createdAt(rs.getTimestamp("created_at").toInstant())
            .lastUsedAt(rs.getTimestamp("last_used_at") != null ? rs.getTimestamp("last_used_at").toInstant() : null)
            .expiresAt(rs.getTimestamp("expires_at") != null ? rs.getTimestamp("expires_at").toInstant() : null)
            .revoked(rs.getBoolean("revoked"))
            .revokedAt(rs.getTimestamp("revoked_at") != null ? rs.getTimestamp("revoked_at").toInstant() : null)
            .revokedBy(rs.getString("revoked_by"))
            .createdBy(rs.getString("created_by"))
            .build();
    }
}
