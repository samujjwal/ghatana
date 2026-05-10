package com.ghatana.digitalmarketing.persistence.command;

import com.ghatana.digitalmarketing.application.command.ExternalIdMappingRepository;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import io.activej.promise.Promise;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * P0-006: PostgreSQL implementation of external ID mapping repository.
 *
 * @doc.type class
 * @doc.purpose PostgreSQL external ID mapping implementation (P0-006)
 * @doc.layer product
 * @doc.pattern Repository, Persistence
 */
public final class PostgresExternalIdMappingRepository implements ExternalIdMappingRepository {

    private final DataSource dataSource;
    private final Executor executor;

    public PostgresExternalIdMappingRepository(DataSource dataSource, Executor executor) {
        this.dataSource = dataSource;
        this.executor = executor;
    }

    @Override
    public Promise<Void> save(DmOperationContext ctx, ExternalIdMapping mapping) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                INSERT INTO external_id_mappings (
                    id, internal_id, external_id, external_system, resource_type,
                    tenant_id, workspace_id, correlation_id, mapped_at, mapped_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (internal_id, external_system, resource_type) 
                DO UPDATE SET external_id = EXCLUDED.external_id, mapped_at = EXCLUDED.mapped_at
                """;

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, mapping.id() != null ? mapping.id() : UUID.randomUUID().toString());
                ps.setString(2, mapping.internalId());
                ps.setString(3, mapping.externalId());
                ps.setString(4, mapping.externalSystem());
                ps.setString(5, mapping.resourceType());
                ps.setString(6, mapping.tenantId());
                ps.setString(7, mapping.workspaceId());
                ps.setString(8, mapping.correlationId());
                ps.setObject(9, mapping.mappedAt() != null ? mapping.mappedAt() : Instant.now());
                ps.setString(10, mapping.mappedBy());
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Promise<Optional<String>> findExternalId(DmOperationContext ctx, String internalId, String externalSystem) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT external_id
                FROM external_id_mappings
                WHERE tenant_id = ? AND internal_id = ? AND external_system = ?
                ORDER BY mapped_at DESC
                LIMIT 1
                """;

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, ctx.getTenantId().getValue());
                ps.setString(2, internalId);
                ps.setString(3, externalSystem);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(rs.getString("external_id"));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    @Override
    public Promise<Optional<String>> findInternalId(DmOperationContext ctx, String externalId, String externalSystem) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT internal_id
                FROM external_id_mappings
                WHERE tenant_id = ? AND external_id = ? AND external_system = ?
                ORDER BY mapped_at DESC
                LIMIT 1
                """;

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, ctx.getTenantId().getValue());
                ps.setString(2, externalId);
                ps.setString(3, externalSystem);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(rs.getString("internal_id"));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    @Override
    public Promise<Void> delete(DmOperationContext ctx, String internalId, String externalSystem) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                DELETE FROM external_id_mappings
                WHERE tenant_id = ? AND internal_id = ? AND external_system = ?
                """;

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, ctx.getTenantId().getValue());
                ps.setString(2, internalId);
                ps.setString(3, externalSystem);
                ps.executeUpdate();
            }
            return null;
        });
    }
}
