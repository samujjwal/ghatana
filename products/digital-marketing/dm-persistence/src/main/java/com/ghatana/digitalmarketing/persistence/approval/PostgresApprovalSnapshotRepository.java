package com.ghatana.digitalmarketing.persistence.approval;

import com.ghatana.digitalmarketing.application.approval.ApprovalSnapshotRepository;
import com.ghatana.digitalmarketing.domain.approval.ApprovalSnapshot;
import com.ghatana.digitalmarketing.domain.approval.ApprovalTargetType;
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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Production PostgreSQL adapter for {@link ApprovalSnapshotRepository}.
 *
 * <p>Snapshots are immutable once stored (written once on approval submission). An upsert
 * is used only to support idempotent retries — the values written on conflict are
 * identical to the original, so concurrent writers are safe.</p>
 *
 * @doc.type class
 * @doc.purpose Production JDBC adapter for DMOS approval snapshot persistence (DMOS-P1-006)
 * @doc.layer product
 * @doc.pattern Adapter, Repository
 */
public final class PostgresApprovalSnapshotRepository implements ApprovalSnapshotRepository {

    private static final Logger LOG =
        LoggerFactory.getLogger(PostgresApprovalSnapshotRepository.class);

    private static final String UPSERT_SQL =
        "INSERT INTO dmos_approval_snapshots " +
        "  (request_id, workspace_id, target_type, target_id, target_workspace_id, " +
        "   snapshot_summary, validation_result_id, risk_level, required_approver_role, snapshot_at, version) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
        "ON CONFLICT (request_id, workspace_id) DO UPDATE SET " +
        "  target_type = EXCLUDED.target_type, " +
        "  target_id = EXCLUDED.target_id, " +
        "  target_workspace_id = EXCLUDED.target_workspace_id, " +
        "  snapshot_summary = EXCLUDED.snapshot_summary, " +
        "  validation_result_id = EXCLUDED.validation_result_id, " +
        "  risk_level = EXCLUDED.risk_level, " +
        "  required_approver_role = EXCLUDED.required_approver_role, " +
        "  snapshot_at = EXCLUDED.snapshot_at, " +
        "  version = dmos_approval_snapshots.version + 1 " +
        "WHERE dmos_approval_snapshots.version = ?";

    private static final String SELECT_SQL =
        "SELECT request_id, workspace_id, target_type, target_id, target_workspace_id, " +
        "       snapshot_summary, validation_result_id, risk_level, required_approver_role, snapshot_at, version " +
        "FROM dmos_approval_snapshots " +
        "WHERE request_id = ? AND workspace_id = ?";

    private final DataSource dataSource;
    private final Executor executor;

    public PostgresApprovalSnapshotRepository(DataSource dataSource, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    @Override
    public Promise<ApprovalSnapshot> save(String workspaceId, ApprovalSnapshot snapshot) {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(UPSERT_SQL)) {
                stmt.setString(1, snapshot.requestId());
                stmt.setString(2, workspaceId);
                stmt.setString(3, snapshot.targetType().name());
                stmt.setString(4, snapshot.targetId());
                stmt.setString(5, snapshot.targetWorkspaceId());
                stmt.setString(6, snapshot.snapshotSummary());
                stmt.setString(7, snapshot.validationResultId());
                stmt.setShort(8, (short) snapshot.riskLevel());
                stmt.setString(9, snapshot.requiredApproverRole());
                stmt.setTimestamp(10, Timestamp.from(snapshot.snapshotAt()));
                stmt.setLong(11, snapshot.version() + 1);
                stmt.setLong(12, snapshot.version());
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected == 0) {
                    throw new DmPersistenceException(
                        "Optimistic lock failure: approval snapshot version mismatch for requestId=" + snapshot.requestId(),
                        new IllegalStateException("Version mismatch"));
                }
                LOG.info("[DMOS-PERSIST] approval snapshot saved: requestId={} workspace={} riskLevel={} version={}",
                    snapshot.requestId(), workspaceId, snapshot.riskLevel(), snapshot.version());
                return new ApprovalSnapshot(
                    snapshot.requestId(),
                    snapshot.targetType(),
                    snapshot.targetId(),
                    snapshot.targetWorkspaceId(),
                    snapshot.snapshotSummary(),
                    snapshot.validationResultId(),
                    snapshot.riskLevel(),
                    snapshot.requiredApproverRole(),
                    snapshot.snapshotAt(),
                    snapshot.version() + 1
                );
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to save snapshot requestId={}: {}",
                    snapshot.requestId(), e.getMessage(), e);
                throw new DmPersistenceException(
                    "Failed to save approval snapshot: " + snapshot.requestId(), e);
            }
        });
    }

    @Override
    public Promise<Optional<ApprovalSnapshot>> findByRequestId(String workspaceId, String requestId) {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_SQL)) {
                stmt.setString(1, requestId);
                stmt.setString(2, workspaceId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to find snapshot requestId={}: {}",
                    requestId, e.getMessage(), e);
                throw new DmPersistenceException(
                    "Failed to find approval snapshot: " + requestId, e);
            }
        });
    }

    private static ApprovalSnapshot mapRow(ResultSet rs) throws SQLException {
        return new ApprovalSnapshot(
            rs.getString("request_id"),
            ApprovalTargetType.valueOf(rs.getString("target_type")),
            rs.getString("target_id"),
            rs.getString("target_workspace_id"),
            rs.getString("snapshot_summary"),
            rs.getString("validation_result_id"),
            rs.getShort("risk_level"),
            rs.getString("required_approver_role"),
            rs.getTimestamp("snapshot_at").toInstant(),
            rs.getLong("version")
        );
    }
}
