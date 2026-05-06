package com.ghatana.digitalmarketing.persistence.command;

import com.ghatana.digitalmarketing.application.command.DmCommandRepository;
import com.ghatana.digitalmarketing.domain.command.DmCommand;
import com.ghatana.digitalmarketing.domain.command.DmCommandStatus;
import com.ghatana.digitalmarketing.domain.command.DmCommandType;
import com.ghatana.digitalmarketing.persistence.campaign.DmPersistenceException;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Production PostgreSQL adapter for {@link DmCommandRepository}.
 *
 * <p>Implements the outbox pattern persistence for durable workflow commands.
 * Wraps all blocking JDBC I/O in {@code Promise.ofBlocking()} to remain event-loop safe.</p>
 *
 * @doc.type class
 * @doc.purpose Production JDBC adapter for DMOS command store (P1-023)
 * @doc.layer product
 * @doc.pattern Adapter, Repository, Outbox
 */
public final class PostgresDmCommandRepository implements DmCommandRepository {

    private static final Logger LOG = LoggerFactory.getLogger(PostgresDmCommandRepository.class);

    private static final int MAX_PAGE_SIZE = 100;

    private static final String INSERT_SQL =
        "INSERT INTO dmos_commands (id, command_type, tenant_id, workspace_id, correlation_id, " +
        "issued_by, serialized_payload, status, attempt_count, created_at, scheduled_at, " +
        "executed_at, completed_at, failure_reason, idempotency_key, parent_command_id, workflow_id) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
        "ON CONFLICT (idempotency_key) DO NOTHING";

    private static final String UPDATE_SQL =
        "UPDATE dmos_commands SET status = ?, attempt_count = ?, executed_at = ?, " +
        "completed_at = ?, failure_reason = ? WHERE id = ? AND tenant_id = ?";

    private static final String SELECT_BY_ID_SQL =
        "SELECT id, command_type, tenant_id, workspace_id, correlation_id, issued_by, " +
        "serialized_payload, status, attempt_count, created_at, scheduled_at, " +
        "executed_at, completed_at, failure_reason, idempotency_key, parent_command_id, workflow_id " +
        "FROM dmos_commands WHERE id = ?";

    private static final String SELECT_PENDING_SQL =
        "SELECT id, command_type, tenant_id, workspace_id, correlation_id, issued_by, " +
        "serialized_payload, status, attempt_count, created_at, scheduled_at, " +
        "executed_at, completed_at, failure_reason, idempotency_key, parent_command_id, workflow_id " +
        "FROM dmos_commands WHERE tenant_id = ? AND status IN ('PENDING', 'FAILED') " +
        "AND scheduled_at <= ? ORDER BY scheduled_at ASC, created_at ASC LIMIT ?";

    private static final String SELECT_BY_TYPE_AND_STATUS_SQL =
        "SELECT id, command_type, tenant_id, workspace_id, correlation_id, issued_by, " +
        "serialized_payload, status, attempt_count, created_at, scheduled_at, " +
        "executed_at, completed_at, failure_reason, idempotency_key, parent_command_id, workflow_id " +
        "FROM dmos_commands WHERE tenant_id = ? AND command_type = ?::dm_command_type AND status = ?::dm_command_status " +
        "ORDER BY created_at DESC LIMIT ?";

    private static final String COUNT_BY_STATUS_SQL =
        "SELECT COUNT(*) FROM dmos_commands WHERE tenant_id = ? AND status = ?::dm_command_status";

    private final DataSource dataSource;
    private final Executor executor;

    public PostgresDmCommandRepository(DataSource dataSource, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    @Override
    public Promise<DmCommand> save(DmCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(INSERT_SQL)) {
                stmt.setString(1, command.getId());
                stmt.setString(2, command.getCommandType().name());
                stmt.setString(3, command.getTenantId());
                stmt.setString(4, command.getWorkspaceId());
                stmt.setString(5, command.getCorrelationId());
                stmt.setString(6, command.getIssuedBy());
                stmt.setString(7, command.getSerializedPayload());
                stmt.setString(8, command.getStatus().name());
                stmt.setInt(9, command.getAttemptCount());
                stmt.setTimestamp(10, Timestamp.from(command.getCreatedAt()));
                stmt.setTimestamp(11, Timestamp.from(command.getScheduledAt()));
                stmt.setTimestamp(12, command.getExecutedAt() != null ? Timestamp.from(command.getExecutedAt()) : null);
                stmt.setTimestamp(13, command.getCompletedAt() != null ? Timestamp.from(command.getCompletedAt()) : null);
                stmt.setString(14, command.getFailureReason());
                stmt.setString(15, null); // idempotency_key - set via context
                stmt.setString(16, null); // parent_command_id
                stmt.setString(17, null); // workflow_id

                int rows = stmt.executeUpdate();
                if (rows == 0) {
                    LOG.warn("[DMOS-PERSIST] Command insert skipped (idempotency conflict): id={}", command.getId());
                } else {
                    LOG.info("[DMOS-PERSIST] Command stored: id={} type={} status={}",
                        command.getId(), command.getCommandType(), command.getStatus());
                }
                return command;
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] Failed to save command id={}: {}", command.getId(), e.getMessage(), e);
                throw new DmPersistenceException("Failed to save command: " + command.getId(), e);
            }
        });
    }

    @Override
    public Promise<Optional<DmCommand>> findById(String id) {
        Objects.requireNonNull(id, "id must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_BY_ID_SQL)) {
                stmt.setString(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] Failed to find command id={}: {}", id, e.getMessage(), e);
                throw new DmPersistenceException("Failed to find command: " + id, e);
            }
        });
    }

    @Override
    public Promise<List<DmCommand>> findPending(String tenantId, int limit) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        int boundedLimit = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);

        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_PENDING_SQL)) {
                stmt.setString(1, tenantId);
                stmt.setTimestamp(2, Timestamp.from(Instant.now()));
                stmt.setInt(3, boundedLimit);

                try (ResultSet rs = stmt.executeQuery()) {
                    List<DmCommand> results = new ArrayList<>();
                    while (rs.next()) {
                        results.add(mapRow(rs));
                    }
                    LOG.debug("[DMOS-PERSIST] Found {} pending commands for tenant {}",
                        results.size(), tenantId);
                    return results;
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] Failed to find pending commands for tenant {}: {}",
                    tenantId, e.getMessage(), e);
                throw new DmPersistenceException("Failed to find pending commands for tenant: " + tenantId, e);
            }
        });
    }

    @Override
    public Promise<List<DmCommand>> findByTypeAndStatus(
            String tenantId, DmCommandType commandType, DmCommandStatus status, int limit) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(commandType, "commandType must not be null");
        Objects.requireNonNull(status, "status must not be null");
        int boundedLimit = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);

        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_BY_TYPE_AND_STATUS_SQL)) {
                stmt.setString(1, tenantId);
                stmt.setString(2, commandType.name());
                stmt.setString(3, status.name());
                stmt.setInt(4, boundedLimit);

                try (ResultSet rs = stmt.executeQuery()) {
                    List<DmCommand> results = new ArrayList<>();
                    while (rs.next()) {
                        results.add(mapRow(rs));
                    }
                    return results;
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] Failed to find commands type={} status={} for tenant {}: {}",
                    commandType, status, tenantId, e.getMessage(), e);
                throw new DmPersistenceException(
                    "Failed to find commands by type and status for tenant: " + tenantId, e);
            }
        });
    }

    @Override
    public Promise<DmCommand> update(DmCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(UPDATE_SQL)) {
                stmt.setString(1, command.getStatus().name());
                stmt.setInt(2, command.getAttemptCount());
                stmt.setTimestamp(3, command.getExecutedAt() != null ? Timestamp.from(command.getExecutedAt()) : null);
                stmt.setTimestamp(4, command.getCompletedAt() != null ? Timestamp.from(command.getCompletedAt()) : null);
                stmt.setString(5, command.getFailureReason());
                stmt.setString(6, command.getId());
                stmt.setString(7, command.getTenantId());

                int rows = stmt.executeUpdate();
                if (rows == 0) {
                    LOG.warn("[DMOS-PERSIST] Command update affected 0 rows: id={}", command.getId());
                } else {
                    LOG.info("[DMOS-PERSIST] Command updated: id={} status={} attempts={}",
                        command.getId(), command.getStatus(), command.getAttemptCount());
                }
                return command;
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] Failed to update command id={}: {}", command.getId(), e.getMessage(), e);
                throw new DmPersistenceException("Failed to update command: " + command.getId(), e);
            }
        });
    }

    @Override
    public Promise<Long> countByStatus(String tenantId, DmCommandStatus status) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(status, "status must not be null");

        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(COUNT_BY_STATUS_SQL)) {
                stmt.setString(1, tenantId);
                stmt.setString(2, status.name());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                    return 0L;
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] Failed to count commands by status for tenant {}: {}",
                    tenantId, e.getMessage(), e);
                throw new DmPersistenceException("Failed to count commands by status for tenant: " + tenantId, e);
            }
        });
    }

    private static DmCommand mapRow(ResultSet rs) throws SQLException {
        Timestamp executedAtTs = rs.getTimestamp("executed_at");
        Timestamp completedAtTs = rs.getTimestamp("completed_at");

        return DmCommand.builder()
            .id(rs.getString("id"))
            .commandType(DmCommandType.valueOf(rs.getString("command_type")))
            .tenantId(rs.getString("tenant_id"))
            .workspaceId(rs.getString("workspace_id"))
            .correlationId(rs.getString("correlation_id"))
            .issuedBy(rs.getString("issued_by"))
            .serializedPayload(rs.getString("serialized_payload"))
            .status(DmCommandStatus.valueOf(rs.getString("status")))
            .attemptCount(rs.getInt("attempt_count"))
            .createdAt(rs.getTimestamp("created_at").toInstant())
            .scheduledAt(rs.getTimestamp("scheduled_at").toInstant())
            .executedAt(executedAtTs != null ? executedAtTs.toInstant() : null)
            .completedAt(completedAtTs != null ? completedAtTs.toInstant() : null)
            .failureReason(rs.getString("failure_reason"))
            .build();
    }
}
