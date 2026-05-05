package com.ghatana.plugin.approval.impl;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.plugin.PluginType;
import com.ghatana.plugin.approval.ApprovalDecision;
import com.ghatana.plugin.approval.ApprovalRecord;
import com.ghatana.plugin.approval.ApprovalRequest;
import com.ghatana.plugin.approval.ApprovalStatus;
import com.ghatana.plugin.approval.HumanApprovalPlugin;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * Durable JDBC-backed implementation of {@link HumanApprovalPlugin} for production use.
 *
 * <p>All approval records survive process restarts. Idempotency is enforced by the
 * {@code request_id} primary key — duplicate calls to {@link #requestApproval} with
 * the same {@code requestId} return the stored record unchanged.
 *
 * <p>Quorum (multi-reviewer) votes are stored in {@code plugin_approval_votes}. The
 * required approvals count is stored alongside the main record and respected on every
 * {@link #completeApproval} call.
 *
 * <p>Call {@link #ensureSchema()} once at startup (e.g. from a Flyway migration or
 * an initialisation hook) before the plugin is started.
 *
 * <p>For development and test scenarios that do not require durability, prefer
 * {@link StandardHumanApprovalPlugin} instead.
 *
 * @doc.type class
 * @doc.purpose Durable JDBC-backed human-approval plugin for regulated production workflows
 * @doc.layer platform
 * @doc.pattern Plugin Implementation, Adapter
 * @since 1.1.0
 */
public final class DurableHumanApprovalPlugin implements HumanApprovalPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(DurableHumanApprovalPlugin.class);

    private static final String APPROVALS_TABLE = "plugin_approval_records";
    private static final String VOTES_TABLE = "plugin_approval_votes";

    private static final PluginMetadata METADATA = PluginMetadata.builder()
            .id("com.ghatana.plugin.human-approval.durable")
            .name("Durable Human Approval Plugin")
            .version("1.1.0")
            .description("JDBC-backed human-in-the-loop approval plugin for regulated production workloads")
            .type(PluginType.GOVERNANCE)
            .author("Ghatana")
            .license("Proprietary")
            .capability("approval:request", "approval:complete", "approval:cancel",
                    "approval:query", "approval:quorum", "approval:durable")
            .properties(Map.of(
                    "variant", "durable-jdbc",
                    "durability", "durable",
                    "idempotency", "enforced-by-pk"
            ))
            .build();

    private final DataSource dataSource;
    private PluginState state = PluginState.UNLOADED;

    /**
     * Creates a new durable human approval plugin backed by the given data source.
     *
     * @param dataSource the JDBC data source; must not be null
     */
    public DurableHumanApprovalPlugin(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    /**
     * Creates the backing tables if they do not yet exist. Idempotent — safe to call on every
     * application startup.
     */
    public void ensureSchema() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS %s (
                      request_id        VARCHAR(256)   PRIMARY KEY,
                      subject_id        VARCHAR(256)   NOT NULL,
                      requested_by      VARCHAR(256)   NOT NULL,
                      action            VARCHAR(512)   NOT NULL,
                      purpose           VARCHAR(1024)  NOT NULL,
                      status            VARCHAR(32)    NOT NULL DEFAULT 'PENDING',
                      required_approvals INT           NOT NULL DEFAULT 1,
                      requested_at      BIGINT         NOT NULL,
                      expires_at        BIGINT,
                      decided_at        BIGINT,
                      reviewer_id       VARCHAR(256),
                      reviewer_notes    VARCHAR(4096),
                      workspace_id      VARCHAR(256)
                    )
                    """.formatted(APPROVALS_TABLE));

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS %s (
                      request_id  VARCHAR(256) NOT NULL,
                      reviewer_id VARCHAR(256) NOT NULL,
                      voted_at    BIGINT       NOT NULL,
                      PRIMARY KEY (request_id, reviewer_id)
                    )
                    """.formatted(VOTES_TABLE));

        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to ensure DurableHumanApprovalPlugin schema", ex);
        }
    }

    // ── Plugin lifecycle ──────────────────────────────────────────────────────

    @Override
    public PluginMetadata metadata() { return METADATA; }

    @Override
    public PluginState getState() { return state; }

    @Override
    public Promise<Void> initialize(PluginContext context) {
        state = PluginState.INITIALIZED;
        LOG.info("DurableHumanApprovalPlugin initialized");
        return Promise.complete();
    }

    @Override
    public Promise<Void> start() {
        state = PluginState.RUNNING;
        LOG.info("DurableHumanApprovalPlugin started");
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        state = PluginState.STOPPED;
        LOG.info("DurableHumanApprovalPlugin stopped");
        return Promise.complete();
    }

    @Override
    public Promise<Void> shutdown() {
        state = PluginState.UNLOADED;
        LOG.info("DurableHumanApprovalPlugin shutdown");
        return Promise.complete();
    }

    // ── HumanApprovalPlugin operations ───────────────────────────────────────

    @Override
    public Promise<ApprovalRecord> requestApproval(ApprovalRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        try (Connection conn = dataSource.getConnection()) {
            // Idempotency: return existing record if requestId already present
            Optional<ApprovalRecord> existing = findByRequestId(conn, request.requestId());
            if (existing.isPresent()) {
                LOG.debug("Idempotent requestApproval — request {} already exists with status {}",
                        request.requestId(), existing.get().status());
                return Promise.of(applyTimeoutEscalation(conn, existing.get()));
            }

            int requiredApprovals = extractRequiredApprovals(request.context());
            long nowEpoch = request.requestedAt().toEpochMilli();
            Long expiresEpoch = request.expiresAt() != null ? request.expiresAt().toEpochMilli() : null;

            String sql = """
                    INSERT INTO %s
                      (request_id, subject_id, requested_by, action, purpose, status,
                       required_approvals, requested_at, expires_at, workspace_id)
                    VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, ?)
                    """.formatted(APPROVALS_TABLE);

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, request.requestId());
                ps.setString(2, request.subjectId());
                ps.setString(3, request.requestedBy());
                ps.setString(4, request.action());
                ps.setString(5, request.purpose());
                ps.setInt(6, requiredApprovals);
                ps.setLong(7, nowEpoch);
                if (expiresEpoch != null) {
                    ps.setLong(8, expiresEpoch);
                } else {
                    ps.setNull(8, Types.BIGINT);
                }
                // P1-013: Extract workspace_id from context
                String workspaceId = extractWorkspaceId(request.context());
                if (workspaceId != null) {
                    ps.setString(9, workspaceId);
                } else {
                    ps.setNull(9, Types.VARCHAR);
                }
                ps.executeUpdate();
            }

            ApprovalRecord record = ApprovalRecord.pending(request);
            LOG.info("Created durable approval request {} for subject {} action={}",
                    request.requestId(), request.subjectId(), request.action());
            return Promise.of(record);

        } catch (SQLException ex) {
            LOG.error("Database error creating approval request {}", request.requestId(), ex);
            return Promise.ofException(ex);
        }
    }

    @Override
    public Promise<Optional<ApprovalRecord>> getApprovalStatus(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("requestId must not be blank"));
        }
        try (Connection conn = dataSource.getConnection()) {
            Optional<ApprovalRecord> record = findByRequestId(conn, requestId)
                    .map(r -> applyTimeoutEscalation(conn, r));
            return Promise.of(record);
        } catch (SQLException ex) {
            LOG.error("Database error fetching approval status for {}", requestId, ex);
            return Promise.ofException(ex);
        }
    }

    @Override
    public Promise<ApprovalRecord> completeApproval(String requestId, ApprovalDecision decision,
                                                     String reviewerId, String notes) {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(decision, "decision must not be null");
        Objects.requireNonNull(reviewerId, "reviewerId must not be null");

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            Optional<ApprovalRecord> existingOpt = findByRequestId(conn, requestId);
            if (existingOpt.isEmpty()) {
                conn.rollback();
                return Promise.ofException(
                        new IllegalArgumentException("Approval request not found: " + requestId));
            }

            ApprovalRecord existing = applyTimeoutEscalation(conn, existingOpt.get());

            if (existing.status() != ApprovalStatus.PENDING) {
                LOG.debug("completeApproval no-op — request {} already in status {}",
                        requestId, existing.status());
                conn.commit();
                return Promise.of(existing);
            }

            if (decision == ApprovalDecision.APPROVED) {
                int requiredApprovals = fetchRequiredApprovals(conn, requestId);
                if (requiredApprovals > 1) {
                    recordVote(conn, requestId, reviewerId);
                    int voteCount = countVotes(conn, requestId);
                    if (voteCount < requiredApprovals) {
                        LOG.info("Approval {} awaiting quorum {}/{}", requestId, voteCount, requiredApprovals);
                        conn.commit();
                        return Promise.of(existing);
                    }
                }
            }

            Instant decidedAt = Instant.now();
            ApprovalRecord decided = existing.withDecision(decision, reviewerId, notes, decidedAt);
            updateDecision(conn, requestId, decision, reviewerId, notes, decidedAt);
            conn.commit();

            LOG.info("Approval {} decided {} by {}", requestId, decision, reviewerId);
            return Promise.of(decided);

        } catch (SQLException ex) {
            LOG.error("Database error completing approval {}", requestId, ex);
            return Promise.ofException(ex);
        }
    }

    @Override
    public Promise<List<ApprovalRecord>> listPendingForSubject(String subjectId) {
        Objects.requireNonNull(subjectId, "subjectId must not be null");

        String sql = "SELECT * FROM %s WHERE subject_id = ? AND status = 'PENDING'"
                .formatted(APPROVALS_TABLE);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, subjectId);
            List<ApprovalRecord> results = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(applyTimeoutEscalation(conn, mapRow(rs)));
                }
            }
            return Promise.of(Collections.unmodifiableList(results));
        } catch (SQLException ex) {
            LOG.error("Database error listing pending approvals for subject {}", subjectId, ex);
            return Promise.ofException(ex);
        }
    }

    @Override
    public Promise<List<ApprovalRecord>> listPendingForWorkspace(String workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");

        String sql = "SELECT * FROM %s WHERE workspace_id = ? AND status = 'PENDING'"
                .formatted(APPROVALS_TABLE);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, workspaceId);
            List<ApprovalRecord> results = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(applyTimeoutEscalation(conn, mapRow(rs)));
                }
            }
            return Promise.of(Collections.unmodifiableList(results));
        } catch (SQLException ex) {
            LOG.error("Database error listing pending approvals for workspace {}", workspaceId, ex);
            return Promise.ofException(ex);
        }
    }

    @Override
    public Promise<Void> cancelApproval(String requestId, String reason) {
        Objects.requireNonNull(requestId, "requestId must not be null");

        String sql = "UPDATE %s SET status = 'CANCELLED', reviewer_notes = ? WHERE request_id = ? AND status = 'PENDING'"
                .formatted(APPROVALS_TABLE);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reason);
            ps.setString(2, requestId);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                LOG.debug("cancelApproval no-op — request {} not PENDING", requestId);
            } else {
                LOG.info("Approval {} cancelled: {}", requestId, reason);
            }
            return Promise.complete();
        } catch (SQLException ex) {
            LOG.error("Database error cancelling approval {}", requestId, ex);
            return Promise.ofException(ex);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Optional<ApprovalRecord> findByRequestId(Connection conn, String requestId) throws SQLException {
        String sql = "SELECT * FROM %s WHERE request_id = ?".formatted(APPROVALS_TABLE);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    private ApprovalRecord mapRow(ResultSet rs) throws SQLException {
        String requestId  = rs.getString("request_id");
        String subjectId  = rs.getString("subject_id");
        String requestedBy = rs.getString("requested_by");
        String action     = rs.getString("action");
        ApprovalStatus status = ApprovalStatus.valueOf(rs.getString("status"));
        Instant requestedAt = Instant.ofEpochMilli(rs.getLong("requested_at"));

        long expiresEpoch = rs.getLong("expires_at");
        Instant expiresAt = rs.wasNull() ? null : Instant.ofEpochMilli(expiresEpoch);

        long decidedEpoch = rs.getLong("decided_at");
        Instant decidedAt = rs.wasNull() ? null : Instant.ofEpochMilli(decidedEpoch);

        String reviewerId    = rs.getString("reviewer_id");
        String reviewerNotes = rs.getString("reviewer_notes");

        // P1-013: Read workspace_id from database
        String workspaceId = rs.getString("workspace_id");
        Map<String, Object> context = new HashMap<>();
        if (workspaceId != null) {
            context.put("workspaceId", workspaceId);
        }

        return new ApprovalRecord(
                requestId, subjectId, requestedBy, action, status,
                requestedAt, expiresAt, decidedAt, reviewerId, reviewerNotes, context);
    }

    private ApprovalRecord applyTimeoutEscalation(Connection conn, ApprovalRecord record) {
        if (record.status() != ApprovalStatus.PENDING) {
            return record;
        }
        if (record.expiresAt() == null || !Instant.now().isAfter(record.expiresAt())) {
            return record;
        }
        // Mark as expired in DB
        try {
            String sql = "UPDATE %s SET status = 'EXPIRED' WHERE request_id = ? AND status = 'PENDING'"
                    .formatted(APPROVALS_TABLE);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, record.requestId());
                ps.executeUpdate();
            }
        } catch (SQLException ex) {
            LOG.warn("Failed to persist EXPIRED state for approval {}", record.requestId(), ex);
        }
        return new ApprovalRecord(
                record.requestId(), record.subjectId(), record.requestedBy(), record.action(),
                ApprovalStatus.EXPIRED,
                record.requestedAt(), record.expiresAt(), Instant.now(),
                null, "Auto-expired: deadline passed");
    }

    private void recordVote(Connection conn, String requestId, String reviewerId) throws SQLException {
        String sql = """
                MERGE INTO %s (request_id, reviewer_id, voted_at)
                KEY (request_id, reviewer_id)
                VALUES (?, ?, ?)
                """.formatted(VOTES_TABLE);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, requestId);
            ps.setString(2, reviewerId);
            ps.setLong(3, Instant.now().toEpochMilli());
            ps.executeUpdate();
        }
    }

    private int countVotes(Connection conn, String requestId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM %s WHERE request_id = ?".formatted(VOTES_TABLE);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private int fetchRequiredApprovals(Connection conn, String requestId) throws SQLException {
        String sql = "SELECT required_approvals FROM %s WHERE request_id = ?".formatted(APPROVALS_TABLE);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 1;
            }
        }
    }

    private void updateDecision(Connection conn, String requestId, ApprovalDecision decision,
                                String reviewerId, String notes, Instant decidedAt) throws SQLException {
        String sql = """
                UPDATE %s
                SET status = ?, reviewer_id = ?, reviewer_notes = ?, decided_at = ?
                WHERE request_id = ?
                """.formatted(APPROVALS_TABLE);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, decision == ApprovalDecision.APPROVED ? "APPROVED" : "REJECTED");
            ps.setString(2, reviewerId);
            ps.setString(3, notes);
            ps.setLong(4, decidedAt.toEpochMilli());
            ps.setString(5, requestId);
            ps.executeUpdate();
        }
    }

    private static int extractRequiredApprovals(Map<String, Object> context) {
        Object val = context.get("requiredApprovals");
        if (val instanceof Number num) {
            return Math.max(1, num.intValue());
        }
        return 1;
    }

    private static String extractWorkspaceId(Map<String, Object> context) {
        Object val = context.get("workspaceId");
        if (val instanceof String str) {
            return str;
        }
        return null;
    }
}
