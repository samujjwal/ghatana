package com.ghatana.digitalmarketing.persistence.suppression;

import com.ghatana.digitalmarketing.application.suppression.SuppressionRepository;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.suppression.SuppressionEntry;
import io.activej.promise.Promise;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * PostgreSQL adapter for suppression and do-not-contact records.
 *
 * @doc.type class
 * @doc.purpose Durable suppression repository with PII-safe contact point hashes
 * @doc.layer persistence
 * @doc.pattern Repository
 */
public final class PostgresSuppressionRepository implements SuppressionRepository {

    private final DataSource dataSource;
    private final Executor executor;

    public PostgresSuppressionRepository(DataSource dataSource, Executor executor) {
        this.dataSource = dataSource;
        this.executor = executor;
    }

    @Override
    public Promise<SuppressionEntry> save(SuppressionEntry entry) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                INSERT INTO dmos_suppression (
                    id, workspace_id, contact_point_hash, reason, active, created_at, updated_at, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    reason = EXCLUDED.reason,
                    active = EXCLUDED.active,
                    updated_at = EXCLUDED.updated_at
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, entry.getId());
                stmt.setString(2, entry.getWorkspaceId().getValue());
                stmt.setString(3, entry.getContactPointHash());
                stmt.setString(4, entry.getReason());
                stmt.setBoolean(5, entry.isActive());
                stmt.setTimestamp(6, Timestamp.from(entry.getCreatedAt()));
                stmt.setTimestamp(7, Timestamp.from(entry.getUpdatedAt()));
                stmt.setString(8, entry.getCreatedBy());
                stmt.executeUpdate();
                return entry;
            }
        });
    }

    @Override
    public Promise<Optional<SuppressionEntry>> findActiveByContactPointHash(
            DmWorkspaceId workspaceId,
            String contactPointHash) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT id, workspace_id, contact_point_hash, reason, active, created_at, updated_at, created_by
                FROM dmos_suppression
                WHERE workspace_id = ? AND contact_point_hash = ? AND active = TRUE
                """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, workspaceId.getValue());
                stmt.setString(2, contactPointHash);
                ResultSet rs = stmt.executeQuery();
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(rs));
            }
        });
    }

    private static SuppressionEntry mapRow(ResultSet rs) throws java.sql.SQLException {
        return SuppressionEntry.builder()
            .id(rs.getString("id"))
            .workspaceId(DmWorkspaceId.of(rs.getString("workspace_id")))
            .contactPointHash(rs.getString("contact_point_hash"))
            .reason(rs.getString("reason"))
            .active(rs.getBoolean("active"))
            .createdAt(rs.getTimestamp("created_at").toInstant())
            .updatedAt(rs.getTimestamp("updated_at").toInstant())
            .createdBy(rs.getString("created_by"))
            .build();
    }
}
