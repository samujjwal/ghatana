/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime.change;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * PostgreSQL-backed {@link ChangeApprovalWorkflow}.
 *
 * <p>Change requests are persisted in the {@code change_requests} table
 * (created by Flyway migration {@code V020__create_retention_policies.sql}).
 * Risk scoring logic mirrors {@link InMemoryChangeApprovalWorkflow}
 * so that existing tests and thresholds remain consistent.
 *
 * @doc.type class
 * @doc.purpose Durable change-approval workflow backed by PostgreSQL
 * @doc.layer platform
 * @doc.pattern Repository
 */
public final class PostgresChangeApprovalWorkflow implements ChangeApprovalWorkflow {

    private static final Logger log = LoggerFactory.getLogger(PostgresChangeApprovalWorkflow.class);
    private static final int DEFAULT_AUTO_APPROVE_THRESHOLD = 60;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final DataSource dataSource;
    private final Executor executor;
    private final int autoApproveThreshold;
    private final ObjectMapper objectMapper;

    /**
     * @param dataSource         pooled JDBC data source (HikariCP); never {@code null}
     * @param executor           blocking-I/O thread pool; never {@code null}
     * @param autoApproveThreshold changes with risk score &lt; this value are auto-approved [0, 100]
     */
    public PostgresChangeApprovalWorkflow(DataSource dataSource, Executor executor,
                                          int autoApproveThreshold) {
        this.dataSource           = Objects.requireNonNull(dataSource, "dataSource");
        this.executor             = Objects.requireNonNull(executor,   "executor");
        this.objectMapper         = new ObjectMapper();
        if (autoApproveThreshold < 0 || autoApproveThreshold > 100) {
            throw new IllegalArgumentException("autoApproveThreshold must be in [0, 100]");
        }
        this.autoApproveThreshold = autoApproveThreshold;
    }

    /**
     * Convenience constructor with the default auto-approve threshold (60).
     */
    public PostgresChangeApprovalWorkflow(DataSource dataSource, Executor executor) {
        this(dataSource, executor, DEFAULT_AUTO_APPROVE_THRESHOLD);
    }

    /**
     * Convenience constructor that creates a small dedicated blocking thread pool.
     */
    public PostgresChangeApprovalWorkflow(DataSource dataSource) {
        this(dataSource, Executors.newFixedThreadPool(4,
            r -> { Thread t = new Thread(r, "change-jdbc"); t.setDaemon(true); return t; }));
    }

    // ---- ChangeApprovalWorkflow -------------------------------------------

    @Override
    public Promise<ChangeRequest> submitChange(
            String tenantId, String requestingAgent,
            ChangeType changeType, String description,
            Map<String, Object> metadata) {
        Objects.requireNonNull(tenantId,        "tenantId");
        Objects.requireNonNull(requestingAgent, "requestingAgent");
        Objects.requireNonNull(changeType,      "changeType");
        Objects.requireNonNull(description,     "description");

        return Promise.ofBlocking(executor, () -> {
            int risk = riskScore(changeType);
            ChangeStatus status = risk < autoApproveThreshold
                ? ChangeStatus.APPROVED
                : ChangeStatus.PENDING_REVIEW;
            UUID changeIdUuid = UUID.randomUUID();
            String changeId   = changeIdUuid.toString();
            Instant now       = Instant.now();
            String reviewerId = status == ChangeStatus.APPROVED ? "system" : null;
            String reviewNote = status == ChangeStatus.APPROVED
                ? "Auto-approved (risk=" + risk + " < threshold=" + autoApproveThreshold + ")"
                : null;
            Instant reviewedAt = status == ChangeStatus.APPROVED ? now : null;

            String metadataJson = objectMapper.writeValueAsString(
                metadata != null ? metadata : Map.of());

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("""
                     INSERT INTO change_requests
                         (change_id, tenant_id, requesting_agent, change_type, description,
                          metadata, status, risk_score, reviewer_id, review_notes,
                          submitted_at, reviewed_at)
                     VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?)
                     """)) {
                int i = 1;
                ps.setObject(i++, changeIdUuid);
                ps.setString(i++, tenantId);
                ps.setString(i++, requestingAgent);
                ps.setString(i++, changeType.name());
                ps.setString(i++, description);
                ps.setString(i++, metadataJson);
                ps.setString(i++, status.name());
                ps.setInt(i++, risk);
                ps.setString(i++, reviewerId);
                ps.setString(i++, reviewNote);
                ps.setTimestamp(i++, Timestamp.from(now));
                ps.setTimestamp(i, reviewedAt != null ? Timestamp.from(reviewedAt) : null);
                ps.executeUpdate();
            }

            log.info("[change] Submitted changeId={} tenant={} type={} risk={} status={}",
                changeId, tenantId, changeType, risk, status);

            return new ChangeRequest(changeId, tenantId, requestingAgent, changeType,
                description, metadata != null ? Map.copyOf(metadata) : Map.of(),
                status, risk, reviewerId, reviewNote, now, reviewedAt);
        });
    }

    @Override
    public Promise<ChangeRequest> approve(String changeId, String reviewerId, String notes) {
        return transition(changeId, ChangeStatus.APPROVED, reviewerId, notes);
    }

    @Override
    public Promise<ChangeRequest> reject(String changeId, String reviewerId, String reason) {
        return transition(changeId, ChangeStatus.REJECTED, reviewerId, reason);
    }

    @Override
    public Promise<ChangeRequest> withdraw(String changeId) {
        return transition(changeId, ChangeStatus.WITHDRAWN, "requester", "Withdrawn by requester");
    }

    @Override
    public Promise<ChangeRequest> getChange(String changeId) {
        Objects.requireNonNull(changeId, "changeId");

        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("""
                    SELECT change_id, tenant_id, requesting_agent, change_type, description,
                            metadata::text, status, risk_score, reviewer_id, review_notes,
                            submitted_at, reviewed_at
                    FROM change_requests WHERE change_id = ?
                     """)) {
                ps.setObject(1, UUID.fromString(changeId));
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("No change request found: " + changeId);
                    }
                    return mapRow(rs);
                }
            }
        });
    }

    @Override
    public Promise<List<ChangeRequest>> listPending(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");

        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("""
                    SELECT change_id, tenant_id, requesting_agent, change_type, description,
                            metadata::text, status, risk_score, reviewer_id, review_notes,
                            submitted_at, reviewed_at
                     FROM change_requests
                     WHERE tenant_id = ? AND status = 'PENDING_REVIEW'
                     ORDER BY submitted_at DESC
                     """)) {
                ps.setString(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<ChangeRequest> results = new ArrayList<>();
                    while (rs.next()) {
                        results.add(mapRow(rs));
                    }
                    return results;
                }
            }
        });
    }

    // ---- Internals -------------------------------------------------------

    private Promise<ChangeRequest> transition(
            String changeId, ChangeStatus target,
            String reviewerId, String notes) {
        Objects.requireNonNull(changeId,   "changeId");
        Objects.requireNonNull(reviewerId, "reviewerId");

        return Promise.ofBlocking(executor, () -> {
            Instant reviewedAt = Instant.now();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("""
                     UPDATE change_requests
                     SET status = ?, reviewer_id = ?, review_notes = ?, reviewed_at = ?
                     WHERE change_id = ? AND status = 'PENDING_REVIEW'
                     RETURNING change_id, tenant_id, requesting_agent, change_type, description,
                               metadata::text, status, risk_score, reviewer_id, review_notes,
                               submitted_at, reviewed_at
                     """)) {
                ps.setString(1, target.name());
                ps.setString(2, reviewerId);
                ps.setString(3, notes);
                ps.setTimestamp(4, Timestamp.from(reviewedAt));
                ps.setObject(5, UUID.fromString(changeId));
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        // Fetch the current state to provide a good error
                        throw new IllegalStateException(
                            "Change " + changeId + " is not in PENDING_REVIEW state or does not exist");
                    }
                    return mapRow(rs);
                }
            }
        });
    }

    private ChangeRequest mapRow(ResultSet rs) throws Exception {
        Timestamp reviewedTs = rs.getTimestamp("reviewed_at");
        String metadataText  = rs.getString("metadata::text") != null
            ? rs.getString("metadata::text")
            : rs.getString("metadata");

        Map<String, Object> metadataMap = metadataText != null && !metadataText.isBlank()
            ? objectMapper.readValue(metadataText, MAP_TYPE)
            : Map.of();

        return new ChangeRequest(
            rs.getString("change_id"),
            rs.getString("tenant_id"),
            rs.getString("requesting_agent"),
            ChangeType.valueOf(rs.getString("change_type")),
            rs.getString("description"),
            metadataMap,
            ChangeStatus.valueOf(rs.getString("status")),
            rs.getInt("risk_score"),
            rs.getString("reviewer_id"),
            rs.getString("review_notes"),
            rs.getTimestamp("submitted_at").toInstant(),
            reviewedTs != null ? reviewedTs.toInstant() : null
        );
    }

    /**
     * Returns the baseline risk score [0–100] for the given change type.
     * Mirrors {@link InMemoryChangeApprovalWorkflow} for consistency.
     */
    private static int riskScore(ChangeType type) {
        return switch (type) {
            case PERMISSION_GRANT    -> 80;
            case POLICY_UPDATE       -> 70;
            case AGENT_DEPLOYMENT    -> 65;
            case TOOL_REGISTRATION   -> 60;
            case DATA_SCHEMA_CHANGE  -> 55;
            case CONFIG_CHANGE       -> 40;
            case FEATURE_FLAG        -> 20;
        };
    }
}
