/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * JDBC-backed implementation of LifecycleExecutionRepository.
 *
 * <p>Provides durable, transactional persistence of lifecycle execution results.
 * All operations are dispatched on a virtual thread executor to avoid blocking
 * the ActiveJ event loop.
 *
 * @doc.type class
 * @doc.purpose JDBC-backed durable lifecycle execution repository
 * @doc.layer api
 * @doc.pattern Repository
 */
public final class JdbcLifecycleExecutionRepository implements LifecycleExecutionRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcLifecycleExecutionRepository.class);
    private static final Executor EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private static final String INSERT_SQL = """
            INSERT INTO lifecycle_executions
              (execution_id, tenant_id, workspace_id, project_id, actor_id, correlation_id,
               idempotency_key, started_at, completed_at, total_duration_ms, executed_phases,
               phase_durations_ms, status, intent_result, shape_result, validation_result,
               generation_result, run_result, observation_result, learning_result,
               evolution_result, metadata)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?::jsonb, ?::jsonb,
                    ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb)
            ON CONFLICT (execution_id) DO UPDATE SET
              completed_at = EXCLUDED.completed_at,
              total_duration_ms = EXCLUDED.total_duration_ms,
              status = EXCLUDED.status,
              intent_result = EXCLUDED.intent_result,
              shape_result = EXCLUDED.shape_result,
              validation_result = EXCLUDED.validation_result,
              generation_result = EXCLUDED.generation_result,
              run_result = EXCLUDED.run_result,
              observation_result = EXCLUDED.observation_result,
              learning_result = EXCLUDED.learning_result,
              evolution_result = EXCLUDED.evolution_result,
              metadata = EXCLUDED.metadata
            """;
    private static final String UPSERT_PHASE_STATE_SQL = """
            INSERT INTO lifecycle_phase_states
              (tenant_id, workspace_id, project_id, phase, status, version, current_execution_id,
               gate_context, artifacts, evidence, runtime_health, feature_flags, tenant_entitlements,
               entered_at, exited_at)
            VALUES (?, ?, ?, ?, ?, 1, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?)
            ON CONFLICT (tenant_id, workspace_id, project_id, phase) DO UPDATE SET
              status = EXCLUDED.status,
              current_execution_id = EXCLUDED.current_execution_id,
              gate_context = EXCLUDED.gate_context,
              artifacts = EXCLUDED.artifacts,
              evidence = EXCLUDED.evidence,
              runtime_health = EXCLUDED.runtime_health,
              feature_flags = EXCLUDED.feature_flags,
              tenant_entitlements = EXCLUDED.tenant_entitlements,
              entered_at = COALESCE(lifecycle_phase_states.entered_at, EXCLUDED.entered_at),
              exited_at = EXCLUDED.exited_at,
              version = lifecycle_phase_states.version + 1,
              updated_at = CURRENT_TIMESTAMP
            RETURNING phase_state_id, version
            """;
    private static final String INSERT_PHASE_HISTORY_SQL = """
            INSERT INTO lifecycle_phase_state_history
              (phase_state_id, tenant_id, workspace_id, project_id, phase, version, transition_event,
               execution_id, actor_id, correlation_id, status, payload)
            VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
            """;
    private static final String INSERT_AUDIT_SQL = """
            INSERT INTO lifecycle_audit_events
              (id, tenant_id, event_type, occurred_at, payload)
            VALUES (?, ?, ?, ?, ?::jsonb)
            ON CONFLICT (id) DO NOTHING
            """;

    private static final String SELECT_BY_ID_SQL = """
            SELECT execution_id, tenant_id, workspace_id, project_id, actor_id, correlation_id,
                   idempotency_key, started_at, completed_at, total_duration_ms, executed_phases,
                   phase_durations_ms, status, intent_result, shape_result, validation_result,
                   generation_result, run_result, observation_result, learning_result,
                   evolution_result, metadata
            FROM lifecycle_executions
            WHERE execution_id = ?
            """;

    private static final String SELECT_BY_PROJECT_SQL = """
            SELECT execution_id, tenant_id, workspace_id, project_id, actor_id, correlation_id,
                   idempotency_key, started_at, completed_at, total_duration_ms, executed_phases,
                   phase_durations_ms, status, intent_result, shape_result, validation_result,
                   generation_result, run_result, observation_result, learning_result,
                   evolution_result, metadata
            FROM lifecycle_executions
            WHERE tenant_id = ? AND project_id = ?
            ORDER BY completed_at DESC
            LIMIT ?
            """;

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public JdbcLifecycleExecutionRepository(@NotNull DataSource dataSource, @NotNull ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    @Override
    public Promise<Void> persist(@NotNull LifecycleExecution execution) {
        return Promise.ofBlocking(EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
                boolean previousAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                ps.setString(1, execution.executionId());
                ps.setString(2, execution.tenantId());
                ps.setString(3, execution.workspaceId());
                ps.setString(4, execution.projectId());
                ps.setString(5, execution.actorId());
                ps.setString(6, execution.correlationId());
                ps.setString(7, execution.idempotencyKey());
                ps.setTimestamp(8, Timestamp.from(execution.startedAt()));
                ps.setTimestamp(9, Timestamp.from(execution.completedAt()));
                ps.setLong(10, execution.totalDurationMs());
                ps.setString(11, toJson(execution.executedPhases()));
                ps.setString(12, toJson(execution.phaseDurationsMs()));
                ps.setString(13, execution.status());
                ps.setString(14, toJson(execution.intentResult()));
                ps.setString(15, toJson(execution.shapeResult()));
                ps.setString(16, toJson(execution.validationResult()));
                ps.setString(17, toJson(execution.generationResult()));
                ps.setString(18, toJson(execution.runResult()));
                ps.setString(19, toJson(execution.observationResult()));
                ps.setString(20, toJson(execution.learningResult()));
                ps.setString(21, toJson(execution.evolutionResult()));
                ps.setString(22, toJson(execution.metadata()));

                try {
                    ps.executeUpdate();
                    persistPhaseStates(conn, execution);
                    conn.commit();
                } catch (SQLException | JsonProcessingException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(previousAutoCommit);
                }
                log.debug("Persisted lifecycle execution: executionId={}, tenantId={}, projectId={}",
                    execution.executionId(), execution.tenantId(), execution.projectId());
            } catch (SQLException | JsonProcessingException e) {
                log.error("Failed to persist lifecycle execution: executionId={}, tenantId={}, projectId={}",
                    execution.executionId(), execution.tenantId(), execution.projectId(), e);
                throw new RuntimeException("Failed to persist lifecycle execution", e);
            }
        });
    }

    @Override
    public Promise<LifecycleExecution> findById(@NotNull String executionId) {
        return Promise.ofBlocking(EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_BY_ID_SQL)) {

                ps.setString(1, executionId);
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    return mapRowToLifecycleExecution(rs);
                }
                return null;
            } catch (SQLException e) {
                log.error("Failed to find lifecycle execution by id: executionId={}", executionId, e);
                throw new RuntimeException("Failed to find lifecycle execution", e);
            }
        });
    }

    @Override
    public Promise<List<LifecycleExecution>> findByProject(
        @NotNull String tenantId,
        @NotNull String projectId,
        int limit
    ) {
        return Promise.ofBlocking(EXECUTOR, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_BY_PROJECT_SQL)) {

                ps.setString(1, tenantId);
                ps.setString(2, projectId);
                ps.setInt(3, limit);

                ResultSet rs = ps.executeQuery();
                List<LifecycleExecution> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapRowToLifecycleExecution(rs));
                }
                return results;
            } catch (SQLException e) {
                log.error("Failed to find lifecycle executions by project: tenantId={}, projectId={}",
                    tenantId, projectId, e);
                throw new RuntimeException("Failed to find lifecycle executions", e);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private LifecycleExecution mapRowToLifecycleExecution(ResultSet rs) throws SQLException {
        try {
            return new LifecycleExecution(
                rs.getString("execution_id"),
                rs.getString("tenant_id"),
                rs.getString("workspace_id"),
                rs.getString("project_id"),
                rs.getString("actor_id"),
                rs.getString("correlation_id"),
                rs.getString("idempotency_key"),
                rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("completed_at").toInstant(),
                rs.getLong("total_duration_ms"),
                fromJson(rs.getString("executed_phases"), List.class),
                fromJson(rs.getString("phase_durations_ms"), Map.class),
                rs.getString("status"),
                fromJson(rs.getString("intent_result"), Map.class),
                fromJson(rs.getString("shape_result"), Map.class),
                fromJson(rs.getString("validation_result"), Map.class),
                fromJson(rs.getString("generation_result"), Map.class),
                fromJson(rs.getString("run_result"), Map.class),
                fromJson(rs.getString("observation_result"), Map.class),
                fromJson(rs.getString("learning_result"), Map.class),
                fromJson(rs.getString("evolution_result"), Map.class),
                fromJson(rs.getString("metadata"), Map.class)
            );
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize lifecycle execution from database", e);
            throw new RuntimeException("Failed to deserialize lifecycle execution", e);
        }
    }

    private String toJson(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value);
    }

    private <T> T fromJson(String json, Class<T> clazz) throws JsonProcessingException {
        if (json == null || json.isBlank()) {
            return null;
        }
        return objectMapper.readValue(json, clazz);
    }

    private void persistPhaseStates(Connection conn, LifecycleExecution execution) throws SQLException, JsonProcessingException {
        Instant startedAt = execution.startedAt();
        Instant completedAt = execution.completedAt();
        for (String rawPhase : execution.executedPhases()) {
            if (rawPhase == null || rawPhase.isBlank()) {
                continue;
            }
            String phase = rawPhase.toUpperCase(java.util.Locale.ROOT);
            LifecyclePhaseTruthMapper.PhaseTruthSnapshot truth =
                    LifecyclePhaseTruthMapper.fromExecution(execution, phase);
            String status = "FAILED".equalsIgnoreCase(execution.status()) ? "BLOCKED" : "COMPLETED";
            String phaseStateId;
            int version;
            try (PreparedStatement ps = conn.prepareStatement(UPSERT_PHASE_STATE_SQL)) {
                ps.setString(1, execution.tenantId());
                ps.setString(2, execution.workspaceId());
                ps.setString(3, execution.projectId());
                ps.setString(4, phase);
                ps.setString(5, status);
                ps.setString(6, execution.executionId());
                ps.setString(7, toJson(truth.gateContext()));
                ps.setString(8, toJson(truth.artifacts()));
                ps.setString(9, toJson(truth.evidence()));
                ps.setString(10, toJson(truth.runtimeHealth()));
                ps.setString(11, toJson(truth.featureFlags()));
                ps.setString(12, toJson(truth.tenantEntitlements()));
                ps.setTimestamp(13, Timestamp.from(startedAt));
                ps.setTimestamp(14, Timestamp.from(completedAt));
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("phase state upsert returned no row for phase " + phase);
                    }
                    phaseStateId = rs.getString("phase_state_id");
                    version = rs.getInt("version");
                }
            }

            Map<String, Object> historyPayload = Map.of(
                    "executionId", execution.executionId(),
                    "phaseDurationsMs", execution.phaseDurationsMs(),
                    "gateContext", truth.gateContext(),
                    "artifacts", truth.artifacts(),
                    "evidence", truth.evidence(),
                    "runtimeHealth", truth.runtimeHealth(),
                    "featureFlags", truth.featureFlags(),
                    "tenantEntitlements", truth.tenantEntitlements(),
                    "metadata", execution.metadata());
            insertPhaseHistory(conn, execution, phaseStateId, version, phase, status, historyPayload);
            insertPhaseAudit(conn, execution, phaseStateId, version, phase, status, historyPayload);
        }
    }

    private void insertPhaseHistory(
            Connection conn,
            LifecycleExecution execution,
            String phaseStateId,
            int version,
            String phase,
            String status,
            Map<String, Object> payload) throws SQLException, JsonProcessingException {
        try (PreparedStatement ps = conn.prepareStatement(INSERT_PHASE_HISTORY_SQL)) {
            ps.setString(1, phaseStateId);
            ps.setString(2, execution.tenantId());
            ps.setString(3, execution.workspaceId());
            ps.setString(4, execution.projectId());
            ps.setString(5, phase);
            ps.setInt(6, version);
            ps.setString(7, "phase-state-persisted");
            ps.setString(8, execution.executionId());
            ps.setString(9, execution.actorId());
            ps.setString(10, execution.correlationId());
            ps.setString(11, status);
            ps.setString(12, toJson(payload));
            ps.executeUpdate();
        }
    }

    private void insertPhaseAudit(
            Connection conn,
            LifecycleExecution execution,
            String phaseStateId,
            int version,
            String phase,
            String status,
            Map<String, Object> payload) throws SQLException, JsonProcessingException {
        Map<String, Object> auditPayload = new java.util.LinkedHashMap<>(payload);
        auditPayload.put("type", "lifecycle.phase_state.persisted");
        auditPayload.put("phaseStateId", phaseStateId);
        auditPayload.put("phase", phase);
        auditPayload.put("version", version);
        auditPayload.put("status", status);
        auditPayload.put("tenant_id", execution.tenantId());
        auditPayload.put("workspaceId", execution.workspaceId());
        auditPayload.put("projectId", execution.projectId());
        auditPayload.put("actorId", execution.actorId());
        auditPayload.put("correlationId", execution.correlationId());

        try (PreparedStatement ps = conn.prepareStatement(INSERT_AUDIT_SQL)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, execution.tenantId());
            ps.setString(3, "lifecycle.phase_state.persisted");
            ps.setTimestamp(4, Timestamp.from(Instant.now()));
            ps.setString(5, toJson(auditPayload));
            ps.executeUpdate();
        }
    }

}
