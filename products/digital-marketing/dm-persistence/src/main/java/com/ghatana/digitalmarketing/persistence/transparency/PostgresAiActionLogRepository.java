package com.ghatana.digitalmarketing.persistence.transparency;

import com.ghatana.digitalmarketing.application.transparency.AiActionLogRepository;
import com.ghatana.digitalmarketing.domain.transparency.AiActionLogEntry;
import com.ghatana.digitalmarketing.domain.transparency.AiActionStatus;
import com.ghatana.digitalmarketing.domain.transparency.AiActionType;
import com.ghatana.digitalmarketing.persistence.campaign.DmPersistenceException;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Production PostgreSQL adapter for {@link AiActionLogRepository}.
 *
 * <p>Action log entries are append-only — writes use {@code INSERT … ON CONFLICT DO NOTHING}
 * for idempotent retries. Array columns ({@code evidence_links}, {@code policy_checks}) are
 * stored as PostgreSQL {@code TEXT[]}.</p>
 *
 * @doc.type class
 * @doc.purpose Production JDBC adapter for DMOS AI action log persistence (DMOS-P1-006)
 * @doc.layer product
 * @doc.pattern Adapter, Repository
 */
public final class PostgresAiActionLogRepository implements AiActionLogRepository {

    private static final Logger LOG =
        LoggerFactory.getLogger(PostgresAiActionLogRepository.class);

    private static final String INSERT_SQL =
        "INSERT INTO dmos_ai_action_log " +
        "  (action_id, workspace_id, correlation_id, action_type, status, actor, " +
        "   initiated_by_ai, confidence, evidence_links, policy_checks, " +
        "   summary, details, related_entity_id, occurred_at, version) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1) " +
        "ON CONFLICT (action_id, workspace_id) DO NOTHING";

    private static final String UPDATE_SQL =
        "UPDATE dmos_ai_action_log SET " +
        "  action_type = ?, status = ?, actor = ?, initiated_by_ai = ?, confidence = ?, " +
        "  evidence_links = ?, policy_checks = ?, summary = ?, details = ?, " +
        "  related_entity_id = ?, occurred_at = ?, version = version + 1 " +
        "WHERE action_id = ? AND workspace_id = ? AND version = ?";

    private static final String SELECT_BY_ID_SQL =
        "SELECT action_id, workspace_id, correlation_id, action_type, status, actor, " +
        "       initiated_by_ai, confidence, evidence_links, policy_checks, " +
        "       summary, details, related_entity_id, occurred_at, version " +
        "FROM dmos_ai_action_log " +
        "WHERE action_id = ? AND workspace_id = ?";

    private static final String SELECT_BY_WORKSPACE_SQL =
        "SELECT action_id, workspace_id, correlation_id, action_type, status, actor, " +
        "       initiated_by_ai, confidence, evidence_links, policy_checks, " +
        "       summary, details, related_entity_id, occurred_at, version " +
        "FROM dmos_ai_action_log " +
        "WHERE workspace_id = ? " +
        "  AND (? IS NULL OR correlation_id = ?) " +
        "  AND (? IS NULL OR related_entity_id = ?) " +
        "ORDER BY occurred_at DESC " +
        "LIMIT ?";

    private final DataSource dataSource;
    private final Executor executor;

    public PostgresAiActionLogRepository(DataSource dataSource, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    @Override
    public Promise<AiActionLogEntry> save(AiActionLogEntry entry) {
        Objects.requireNonNull(entry, "entry must not be null");
        if (entry.version() == 0) {
            return Promise.ofBlocking(executor, () -> {
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(INSERT_SQL)) {
                    stmt.setString(1, entry.actionId());
                    stmt.setString(2, entry.workspaceId());
                    stmt.setString(3, entry.correlationId());
                    stmt.setString(4, entry.actionType().name());
                    stmt.setString(5, entry.status().name());
                    stmt.setString(6, entry.actor());
                    stmt.setBoolean(7, entry.initiatedByAi());
                    if (entry.confidence() != null) {
                        stmt.setDouble(8, entry.confidence());
                    } else {
                        stmt.setNull(8, Types.DOUBLE);
                    }
                    stmt.setArray(9, toTextArray(conn, entry.evidenceLinks()));
                    stmt.setArray(10, toTextArray(conn, entry.policyChecks()));
                    stmt.setString(11, entry.summary());
                    stmt.setString(12, entry.details());
                    stmt.setString(13, entry.relatedEntityId());
                    stmt.setTimestamp(14, Timestamp.from(entry.occurredAt()));
                    // version=1 is hardcoded in SQL for new inserts
                    int inserted = stmt.executeUpdate();
                    if (inserted == 0) {
                        // Idempotent retry: row already exists — return the stored entry unchanged
                        LOG.debug("[DMOS-PERSIST] ai action log already exists (idempotent): actionId={}", entry.actionId());
                        try (PreparedStatement sel = conn.prepareStatement(SELECT_BY_ID_SQL)) {
                            sel.setString(1, entry.actionId());
                            sel.setString(2, entry.workspaceId());
                            try (ResultSet rs = sel.executeQuery()) {
                                if (rs.next()) {
                                    return mapRow(rs);
                                }
                            }
                        }
                        throw new DmPersistenceException(
                            "Entry vanished after conflict: " + entry.actionId(),
                            new IllegalStateException("row not found after DO NOTHING"));
                    }
                    LOG.info("[DMOS-PERSIST] ai action log inserted: actionId={} workspace={} type={}",
                        entry.actionId(), entry.workspaceId(), entry.actionType());
                    return new AiActionLogEntry(
                        entry.actionId(), entry.workspaceId(), entry.correlationId(),
                        entry.actionType(), entry.status(), entry.actor(),
                        entry.initiatedByAi(), entry.confidence(), entry.evidenceLinks(),
                        entry.policyChecks(), entry.summary(), entry.details(),
                        entry.relatedEntityId(), entry.occurredAt(), 1L);
                } catch (DmPersistenceException e) {
                    throw e;
                } catch (SQLException e) {
                    LOG.error("[DMOS-PERSIST] failed to insert action log entry actionId={}: {}",
                        entry.actionId(), e.getMessage(), e);
                    throw new DmPersistenceException(
                        "Failed to save AI action log entry: " + entry.actionId(), e);
                }
            });
        } else {
            return Promise.ofBlocking(executor, () -> {
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(UPDATE_SQL)) {
                    stmt.setString(1, entry.actionType().name());
                    stmt.setString(2, entry.status().name());
                    stmt.setString(3, entry.actor());
                    stmt.setBoolean(4, entry.initiatedByAi());
                    if (entry.confidence() != null) {
                        stmt.setDouble(5, entry.confidence());
                    } else {
                        stmt.setNull(5, Types.DOUBLE);
                    }
                    stmt.setArray(6, toTextArray(conn, entry.evidenceLinks()));
                    stmt.setArray(7, toTextArray(conn, entry.policyChecks()));
                    stmt.setString(8, entry.summary());
                    stmt.setString(9, entry.details());
                    stmt.setString(10, entry.relatedEntityId());
                    stmt.setTimestamp(11, Timestamp.from(entry.occurredAt()));
                    stmt.setString(12, entry.actionId());
                    stmt.setString(13, entry.workspaceId());
                    stmt.setLong(14, entry.version());
                    int updated = stmt.executeUpdate();
                    if (updated == 0) {
                        throw new DmPersistenceException(
                            "Optimistic lock failure: AI action log version mismatch for actionId=" + entry.actionId(),
                            new IllegalStateException("Version mismatch"));
                    }
                    long newVersion = entry.version() + 1;
                    LOG.info("[DMOS-PERSIST] ai action log updated: actionId={} workspace={} newVersion={}",
                        entry.actionId(), entry.workspaceId(), newVersion);
                    return new AiActionLogEntry(
                        entry.actionId(), entry.workspaceId(), entry.correlationId(),
                        entry.actionType(), entry.status(), entry.actor(),
                        entry.initiatedByAi(), entry.confidence(), entry.evidenceLinks(),
                        entry.policyChecks(), entry.summary(), entry.details(),
                        entry.relatedEntityId(), entry.occurredAt(), newVersion);
                } catch (DmPersistenceException e) {
                    throw e;
                } catch (SQLException e) {
                    LOG.error("[DMOS-PERSIST] failed to update action log entry actionId={}: {}",
                        entry.actionId(), e.getMessage(), e);
                    throw new DmPersistenceException(
                        "Failed to update AI action log entry: " + entry.actionId(), e);
                }
            });
        }
    }

    @Override
    public Promise<Optional<AiActionLogEntry>> findById(String workspaceId, String actionId) {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(actionId, "actionId must not be null");
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_BY_ID_SQL)) {
                stmt.setString(1, actionId);
                stmt.setString(2, workspaceId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to find action log actionId={}: {}",
                    actionId, e.getMessage(), e);
                throw new DmPersistenceException(
                    "Failed to find AI action log entry: " + actionId, e);
            }
        });
    }

    @Override
    public Promise<List<AiActionLogEntry>> findByWorkspace(
            String workspaceId,
            String correlationId,
            String relatedEntityId,
            int limit) {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        int effectiveLimit = limit <= 0 ? 50 : Math.min(limit, 200);
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_BY_WORKSPACE_SQL)) {
                String corrId = (correlationId != null && !correlationId.isBlank())
                    ? correlationId : null;
                String relEntityId = (relatedEntityId != null && !relatedEntityId.isBlank())
                    ? relatedEntityId : null;
                stmt.setString(1, workspaceId);
                stmt.setString(2, corrId);
                stmt.setString(3, corrId);
                stmt.setString(4, relEntityId);
                stmt.setString(5, relEntityId);
                stmt.setInt(6, effectiveLimit);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<AiActionLogEntry> results = new ArrayList<>();
                    while (rs.next()) {
                        results.add(mapRow(rs));
                    }
                    return results;
                }
            } catch (SQLException e) {
                LOG.error("[DMOS-PERSIST] failed to query action log workspace={}: {}",
                    workspaceId, e.getMessage(), e);
                throw new DmPersistenceException(
                    "Failed to query AI action log for workspace: " + workspaceId, e);
            }
        });
    }

    private static AiActionLogEntry mapRow(ResultSet rs) throws SQLException {
        Array evidenceArray = rs.getArray("evidence_links");
        List<String> evidenceLinks = evidenceArray != null
            ? Arrays.asList((String[]) evidenceArray.getArray())
            : List.of();

        Array policyArray = rs.getArray("policy_checks");
        List<String> policyChecks = policyArray != null
            ? Arrays.asList((String[]) policyArray.getArray())
            : List.of();

        double rawConfidence = rs.getDouble("confidence");
        Double confidence = rs.wasNull() ? null : rawConfidence;

        return new AiActionLogEntry(
            rs.getString("action_id"),
            rs.getString("workspace_id"),
            rs.getString("correlation_id"),
            AiActionType.valueOf(rs.getString("action_type")),
            AiActionStatus.valueOf(rs.getString("status")),
            rs.getString("actor"),
            rs.getBoolean("initiated_by_ai"),
            confidence,
            evidenceLinks,
            policyChecks,
            rs.getString("summary"),
            rs.getString("details"),
            rs.getString("related_entity_id"),
            rs.getTimestamp("occurred_at").toInstant(),
            rs.getLong("version")
        );
    }

    private static Array toTextArray(Connection conn, List<String> values) throws SQLException {
        return conn.createArrayOf("text",
            values != null ? values.toArray(new String[0]) : new String[0]);
    }
}
