/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.platform.workflow.*;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PostgreSQL-backed implementation of {@link WorkflowStateStore}.
 *
 * <p>Persists {@link WorkflowRun} records to the {@code workflow_runs} table.
 * JSONB columns are used for the variables map.
 * All blocking JDBC calls are wrapped in {@code Promise.ofBlocking} to keep
 * the ActiveJ eventloop unblocked.
 *
 * @doc.type class
 * @doc.purpose PostgreSQL persistence for workflow run state
 * @doc.layer platform
 * @doc.pattern Repository
 */
public final class JdbcWorkflowStateStore implements WorkflowStateStore {
    private static final Logger log = LoggerFactory.getLogger(JdbcWorkflowStateStore.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private static final String UPSERT_SQL = """
        INSERT INTO workflow_runs
            (run_id, workflow_id, tenant_id, kind, status,
             current_step_id, error_message, started_at, completed_at,
             variables, triggered_by)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
        ON CONFLICT (run_id) DO UPDATE SET
            status = EXCLUDED.status,
            current_step_id = EXCLUDED.current_step_id,
            error_message = EXCLUDED.error_message,
            completed_at = EXCLUDED.completed_at,
            variables = EXCLUDED.variables
        """;

    private static final String SELECT_BY_RUN_ID = """
        SELECT run_id, workflow_id, tenant_id, kind, status,
               current_step_id, error_message, started_at, completed_at,
               variables, triggered_by
        FROM workflow_runs WHERE run_id = ?
        """;

    private static final String SELECT_BY_WORKFLOW_ID = """
        SELECT run_id, workflow_id, tenant_id, kind, status,
               current_step_id, error_message, started_at, completed_at,
               variables, triggered_by
        FROM workflow_runs WHERE workflow_id = ? ORDER BY started_at DESC
        """;

    private static final String SELECT_BY_STATUS = """
        SELECT run_id, workflow_id, tenant_id, kind, status,
               current_step_id, error_message, started_at, completed_at,
               variables, triggered_by
        FROM workflow_runs WHERE status = ? ORDER BY started_at DESC
        """;

    private static final String UPDATE_STATUS_SQL = """
        UPDATE workflow_runs SET status = ? WHERE run_id = ?
        """;

    private static final String DELETE_BY_RUN_ID = "DELETE FROM workflow_runs WHERE run_id = ?";

    private static final String SELECT_EXPIRED_WAITS = """
        SELECT r.run_id, r.workflow_id, r.tenant_id, r.kind, r.status,
               r.current_step_id, r.error_message, r.started_at, r.completed_at,
               r.variables, r.triggered_by
        FROM workflow_runs r
        JOIN workflow_wait_conditions w ON r.run_id = w.run_id
        WHERE w.fired = FALSE AND w.fire_at <= ?
        """;

    private final DataSource dataSource;
    private final ExecutorService executor;

    public JdbcWorkflowStateStore(@NotNull DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public Promise<Void> save(@NotNull WorkflowRun run) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(UPSERT_SQL)) {

                ps.setString(1, run.runId());
                ps.setString(2, run.workflowId());
                ps.setString(3, run.tenantId());
                ps.setString(4, run.kind().name());
                ps.setString(5, run.status().name());
                ps.setString(6, run.currentStepId());
                ps.setString(7, run.errorMessage());
                ps.setTimestamp(8, Timestamp.from(run.startedAt()));
                ps.setTimestamp(9, run.completedAt() != null ? Timestamp.from(run.completedAt()) : null);
                ps.setString(10, toJson(run.variables()));
                ps.setString(11, run.triggeredBy());
                ps.executeUpdate();
            }
        });
    }

    @Override
    public Promise<Optional<WorkflowRun>> findByRunId(@NotNull String runId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_BY_RUN_ID)) {
                ps.setString(1, runId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                    return Optional.<WorkflowRun>empty();
                }
            }
        });
    }

    @Override
    public Promise<List<WorkflowRun>> findByWorkflowId(@NotNull String workflowId) {
        return Promise.ofBlocking(executor, () -> executeListQuery(SELECT_BY_WORKFLOW_ID, workflowId));
    }

    @Override
    public Promise<List<WorkflowRun>> findByStatus(@NotNull WorkflowRunStatus status) {
        return Promise.ofBlocking(executor, () -> executeListQuery(SELECT_BY_STATUS, status.name()));
    }

    @Override
    public Promise<List<WorkflowRun>> findExpiredWaits(@NotNull Instant cutoff) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_EXPIRED_WAITS)) {
                ps.setTimestamp(1, Timestamp.from(cutoff));
                return mapRows(ps.executeQuery());
            }
        });
    }

    @Override
    public Promise<Void> updateStatus(@NotNull String runId, @NotNull WorkflowRunStatus status) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(UPDATE_STATUS_SQL)) {
                ps.setString(1, status.name());
                ps.setString(2, runId);
                ps.executeUpdate();
            }
        });
    }

    @Override
    public void delete(@NotNull String runId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_BY_RUN_ID)) {
            ps.setString(1, runId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to delete workflow run {}: {}", runId, e.getMessage());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private List<WorkflowRun> executeListQuery(String sql, String param) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, param);
            return mapRows(ps.executeQuery());
        }
    }

    private List<WorkflowRun> mapRows(ResultSet rs) throws SQLException {
        List<WorkflowRun> result = new ArrayList<>();
        while (rs.next()) {
            result.add(mapRow(rs));
        }
        return result;
    }

    private WorkflowRun mapRow(ResultSet rs) throws SQLException {
        WorkflowKind kind = WorkflowKind.valueOf(rs.getString("kind"));
        return new WorkflowRun(
            rs.getString("run_id"),
            rs.getString("workflow_id"),
            rs.getString("tenant_id"),
            kind,
            WorkflowRunStatus.valueOf(rs.getString("status")),
            kind == WorkflowKind.DURABLE ? WorkflowOptions.durable() : WorkflowOptions.ephemeral(),
            rs.getTimestamp("started_at").toInstant(),
            rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toInstant() : null,
            rs.getString("current_step_id"),
            fromJson(rs.getString("variables")),
            rs.getString("error_message"),
            rs.getString("triggered_by"),
            List.of() // History loaded separately if needed
        );
    }

    private String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize to JSON", e);
        }
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON context: {}", e.getMessage());
            return Map.of();
        }
    }
}
