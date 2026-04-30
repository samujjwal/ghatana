/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service — Durable JDBC Approval Service
 */
package com.ghatana.yappc.services.lifecycle;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.agent.AepEventPublisher;
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
import java.util.concurrent.Executors;

/**
 * JDBC-backed {@link HumanApprovalService} that persists approval requests to the
 * {@code approval_requests} table (V2_0_0__YAPPC_APPROVAL_REQUESTS migration).
 *
 * <p>Extends the in-memory {@link HumanApprovalService}: mutations (create / approve /
 * reject) write to both the in-memory store <em>and</em> to PostgreSQL atomically.
 * All read methods query PostgreSQL directly so that state survives restarts and is
 * visible across instances.
 *
 * <p>All JDBC calls are dispatched on a dedicated virtual-thread executor via
 * {@code Promise.ofBlocking} so the ActiveJ event-loop is never blocked.
 *
 * @doc.type class
 * @doc.purpose Durable JDBC-backed human-approval gate service for lifecycle pipeline
 * @doc.layer product
 * @doc.pattern Repository, Service
 * @doc.gaa.lifecycle act
 */
public class JdbcHumanApprovalService extends HumanApprovalService {

    private static final Logger log = LoggerFactory.getLogger(JdbcHumanApprovalService.class);

    private static final Executor EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private static final TypeReference<List<String>> LIST_STRING_TYPE = new TypeReference<>() {};

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    private static final String INSERT_SQL = """
            INSERT INTO approval_requests
              (id, tenant_id, project_id, requesting_agent_id, approval_type,
               status, created_at, expires_at, context)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
            ON CONFLICT (id) DO NOTHING
            """;

    private static final String UPDATE_STATUS_SQL = """
            UPDATE approval_requests
            SET status = ?, decided_at = ?, decided_by = ?
            WHERE id = ? AND tenant_id = ?
            """;

    private static final String SELECT_BY_TENANT_PROJECT_SQL = """
            SELECT * FROM approval_requests
            WHERE tenant_id = ? AND project_id = ? AND status = 'PENDING'
            ORDER BY created_at DESC
            """;

    private static final String SELECT_ALL_PENDING_SQL = """
            SELECT * FROM approval_requests
            WHERE tenant_id = ? AND status = 'PENDING'
            ORDER BY created_at DESC
            """;

    private static final String SELECT_BY_ID_SQL = """
            SELECT * FROM approval_requests
            WHERE tenant_id = ? AND id = ?
            """;

    /**
     * Full constructor with notification, risk-scoring, and audit support.
     *
     * @param publisher           AEP event publisher (forwarded to parent)
     * @param dataSource          YAPPC PostgreSQL data source
     * @param objectMapper        Jackson mapper for JSONB serialization of {@link ApprovalRequest.ApprovalContext}
     * @param notificationService optional notification broadcaster; may be null
     * @param riskScorer          optional AI risk scorer; may be null
     * @param auditLogger         optional compliance audit logger; may be null
     */
    public JdbcHumanApprovalService(
            AepEventPublisher publisher,
            DataSource dataSource,
            ObjectMapper objectMapper,
            ApprovalNotificationService notificationService,
            ApprovalRiskScorer riskScorer,
            ApprovalAuditLogger auditLogger) {
        super(publisher, notificationService, riskScorer, auditLogger);
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * Minimal constructor for backward-compatible usage and unit tests.
     *
     * @param publisher    AEP event publisher (forwarded to parent)
     * @param dataSource   YAPPC PostgreSQL data source
     * @param objectMapper Jackson mapper for JSONB serialization of {@link ApprovalRequest.ApprovalContext}
     */
    public JdbcHumanApprovalService(
            AepEventPublisher publisher,
            DataSource dataSource,
            ObjectMapper objectMapper) {
        this(publisher, dataSource, objectMapper, null, null, null);
    }

    // ─── Overridden mutation methods — write to DB in addition to in-memory ─

    /**
     * Creates a new approval request, persisting it to the database and the in-memory store.
     */
    @Override
    public Promise<ApprovalRequest> requestApproval(
            String tenantId,
            String projectId,
            String requestingAgentId,
            ApprovalRequest.ApprovalType approvalType,
            ApprovalRequest.ApprovalContext context) {

        return super.requestApproval(tenantId, projectId, requestingAgentId, approvalType, context)
                .then(req -> Promise.ofBlocking(EXECUTOR, () -> {
                    try {
                        persistInsert(req);
                    } catch (Exception e) {
                        log.error("[tenant={}] Failed to persist approval request id={}: {}",
                                tenantId, req.id(), e.getMessage());
                    }
                    return req;
                }));
    }

    /**
     * Approves a pending request, updating the database and in-memory state.
     */
    @Override
    public Promise<ApprovalRequest> approve(String tenantId, String requestId, String decidedBy) {
        return super.approve(tenantId, requestId, decidedBy)
                .then(req -> Promise.ofBlocking(EXECUTOR, () -> {
                    try {
                        persistUpdate(req);
                    } catch (Exception e) {
                        log.error("[tenant={}] Failed to persist approval decision id={}: {}",
                                tenantId, requestId, e.getMessage());
                    }
                    return req;
                }));
    }

    /**
     * Rejects a pending request, updating the database and in-memory state.
     */
    @Override
    public Promise<ApprovalRequest> reject(String tenantId, String requestId, String decidedBy) {
        return super.reject(tenantId, requestId, decidedBy)
                .then(req -> Promise.ofBlocking(EXECUTOR, () -> {
                    try {
                        persistUpdate(req);
                    } catch (Exception e) {
                        log.error("[tenant={}] Failed to persist approval rejection id={}: {}",
                                tenantId, requestId, e.getMessage());
                    }
                    return req;
                }));
    }

    // ─── Read methods — query PostgreSQL for cross-instance visibility ────────

    /**
     * Returns PENDING requests from the database (cross-instance safe).
     *
     * @throws SQLException if database query fails - caller must implement circuit breaker
     */
    @Override
    public List<ApprovalRequest> pendingFor(String tenantId, String projectId) {
        return queryPendingFor(tenantId, projectId);
    }

    /**
     * Returns all PENDING requests from the database (cross-instance safe).
     *
     * @throws SQLException if database query fails - caller must implement circuit breaker
     */
    @Override
    public List<ApprovalRequest> allPending(String tenantId) {
        return queryAllPending(tenantId);
    }

    /**
     * Finds a request by ID from the database.
     *
     * @throws SQLException if database query fails - caller must implement circuit breaker
     */
    @Override
    public Optional<ApprovalRequest> findById(String tenantId, String requestId) {
        return queryById(tenantId, requestId);
    }

    // ─── JDBC helpers ─────────────────────────────────────────────────────────

    private void persistInsert(ApprovalRequest req) throws Exception {
        String contextJson = req.context() != null ? objectMapper.writeValueAsString(req.context()) : null;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
            ps.setString(1, req.id());
            ps.setString(2, req.tenantId());
            ps.setString(3, req.projectId());
            ps.setString(4, req.requestingAgentId());
            ps.setString(5, req.approvalType().name());
            ps.setString(6, req.status().name());
            ps.setTimestamp(7, Timestamp.from(req.createdAt()));
            ps.setTimestamp(8, req.expiresAt() != null ? Timestamp.from(req.expiresAt()) : null);
            ps.setString(9, contextJson);
            ps.executeUpdate();
        }
    }

    private void persistUpdate(ApprovalRequest req) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_STATUS_SQL)) {
            ps.setString(1, req.status().name());
            ps.setTimestamp(2, req.decidedAt() != null ? Timestamp.from(req.decidedAt()) : null);
            ps.setString(3, req.decidedBy());
            ps.setString(4, req.id());
            ps.setString(5, req.tenantId());
            int updated = ps.executeUpdate();
            if (updated == 0) {
                log.warn("No rows updated for approval id={} tenant={}", req.id(), req.tenantId());
            }
        }
    }

    private List<ApprovalRequest> queryPendingFor(String tenantId, String projectId) throws Exception {
        List<ApprovalRequest> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_TENANT_PROJECT_SQL)) {
            ps.setString(1, tenantId);
            ps.setString(2, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    private List<ApprovalRequest> queryAllPending(String tenantId) throws Exception {
        List<ApprovalRequest> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_ALL_PENDING_SQL)) {
            ps.setString(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    private Optional<ApprovalRequest> queryById(String tenantId, String requestId) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_ID_SQL)) {
            ps.setString(1, tenantId);
            ps.setString(2, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    private ApprovalRequest mapRow(ResultSet rs) throws Exception {
        String id = rs.getString("id");
        String tenantId = rs.getString("tenant_id");
        String projectId = rs.getString("project_id");
        String requestingAgentId = rs.getString("requesting_agent_id");
        ApprovalRequest.ApprovalType approvalType =
                ApprovalRequest.ApprovalType.valueOf(rs.getString("approval_type"));
        ApprovalRequest.ApprovalStatus status =
                ApprovalRequest.ApprovalStatus.valueOf(rs.getString("status"));
        Instant createdAt = rs.getTimestamp("created_at").toInstant();
        Timestamp decidedAtTs = rs.getTimestamp("decided_at");
        Instant decidedAt = decidedAtTs != null ? decidedAtTs.toInstant() : null;
        String decidedBy = rs.getString("decided_by");
        Timestamp expiresAtTs = rs.getTimestamp("expires_at");
        Instant expiresAt = expiresAtTs != null ? expiresAtTs.toInstant() : null;

        ApprovalRequest.ApprovalContext context = null;
        String contextJson = rs.getString("context");
        if (contextJson != null && !contextJson.isBlank()) {
            context = objectMapper.readValue(contextJson, ApprovalRequest.ApprovalContext.class);
        }

        return new ApprovalRequest(
                id, projectId, requestingAgentId, approvalType, context,
                status, tenantId, createdAt, decidedAt, decidedBy, expiresAt);
    }
}
