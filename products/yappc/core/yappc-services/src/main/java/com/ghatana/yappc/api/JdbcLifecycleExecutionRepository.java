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

                ps.executeUpdate();
                log.debug("Persisted lifecycle execution: executionId={}, tenantId={}, projectId={}",
                    execution.executionId(), execution.tenantId(), execution.projectId());
            } catch (SQLException e) {
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
}
