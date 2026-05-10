package com.ghatana.digitalmarketing.persistence.command;

import com.ghatana.digitalmarketing.application.command.DmDeadLetterQueue;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.command.DmCommand;
import io.activej.promise.Promise;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * P0-006: PostgreSQL implementation of dead-letter queue for permanently failed commands.
 *
 * @doc.type class
 * @doc.purpose PostgreSQL DLQ implementation for failed commands (P0-006)
 * @doc.layer product
 * @doc.pattern Dead-Letter Queue, Persistence
 */
public final class PostgresDmDeadLetterQueue implements DmDeadLetterQueue {

    private final DataSource dataSource;
    private final Executor executor;

    public PostgresDmDeadLetterQueue(DataSource dataSource, Executor executor) {
        this.dataSource = dataSource;
        this.executor = executor;
    }

    @Override
    public Promise<Void> moveToDlq(DmOperationContext ctx, DmCommand command, String finalFailureReason) {
        return Promise.ofBlocking(executor, () -> {
            String dlqEntryId = UUID.randomUUID().toString();
            String sql = """
                INSERT INTO dm_command_dlq (
                    id, original_command_id, command_type, tenant_id, workspace_id,
                    serialized_payload, failure_reason, attempt_count, moved_to_dlq_at,
                    original_created_at, correlation_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (original_command_id) DO NOTHING
                """;

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, dlqEntryId);
                ps.setString(2, command.getId());
                ps.setString(3, command.getCommandType().name());
                ps.setString(4, command.getTenantId());
                ps.setString(5, command.getWorkspaceId());
                ps.setString(6, command.getSerializedPayload());
                ps.setString(7, finalFailureReason);
                ps.setInt(8, command.getAttemptCount());
                ps.setObject(9, Instant.now());
                ps.setObject(10, command.getCreatedAt());
                ps.setString(11, command.getCorrelationId());
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Promise<Optional<DlqEntry>> findById(DmOperationContext ctx, String commandId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT id, original_command_id, command_type, tenant_id, workspace_id,
                       serialized_payload, failure_reason, attempt_count, moved_to_dlq_at,
                       original_created_at, correlation_id
                FROM dm_command_dlq
                WHERE tenant_id = ? AND original_command_id = ?
                """;

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, ctx.getTenantId().getValue());
                ps.setString(2, commandId);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    @Override
    public Promise<List<DlqEntry>> list(DmOperationContext ctx, int limit) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT id, original_command_id, command_type, tenant_id, workspace_id,
                       serialized_payload, failure_reason, attempt_count, moved_to_dlq_at,
                       original_created_at, correlation_id
                FROM dm_command_dlq
                WHERE tenant_id = ?
                ORDER BY moved_to_dlq_at DESC
                LIMIT ?
                """;

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, ctx.getTenantId().getValue());
                ps.setInt(2, limit);

                List<DlqEntry> entries = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        entries.add(mapRow(rs));
                    }
                }
                return entries;
            }
        });
    }

    @Override
    public Promise<String> replay(DmOperationContext ctx, String dlqEntryId, String replayedBy) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT serialized_payload, command_type, workspace_id, correlation_id
                FROM dm_command_dlq
                WHERE id = ? AND tenant_id = ?
                """;

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, dlqEntryId);
                ps.setString(2, ctx.getTenantId().getValue());

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("DLQ entry not found: " + dlqEntryId);
                    }

                    String payload = rs.getString("serialized_payload");
                    String commandType = rs.getString("command_type");
                    String workspaceId = rs.getString("workspace_id");
                    String correlationId = rs.getString("correlation_id");

                    // Create a new command by re-issuing through DmCommandService
                    // This is handled by the caller - we just return the payload info
                    // The actual replay is done by creating a new command with the same payload
                    return payload; // Return payload for the caller to create a new command
                }
            }
        });
    }

    @Override
    public Promise<Void> delete(DmOperationContext ctx, String dlqEntryId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                DELETE FROM dm_command_dlq
                WHERE id = ? AND tenant_id = ?
                """;

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, dlqEntryId);
                ps.setString(2, ctx.getTenantId().getValue());
                ps.executeUpdate();
            }
            return null;
        });
    }

    private DlqEntry mapRow(ResultSet rs) throws SQLException {
        return new DlqEntry(
            rs.getString("id"),
            rs.getString("original_command_id"),
            rs.getString("command_type"),
            rs.getString("tenant_id"),
            rs.getString("workspace_id"),
            rs.getString("serialized_payload"),
            rs.getString("failure_reason"),
            rs.getInt("attempt_count"),
            rs.getObject("moved_to_dlq_at", Instant.class),
            rs.getObject("original_created_at", Instant.class),
            rs.getString("correlation_id")
        );
    }
}
