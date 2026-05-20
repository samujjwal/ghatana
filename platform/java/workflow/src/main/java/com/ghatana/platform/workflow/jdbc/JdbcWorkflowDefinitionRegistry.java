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
import com.ghatana.platform.workflow.SagaPolicy;
import com.ghatana.platform.workflow.runtime.WorkflowDefinition;
import com.ghatana.platform.workflow.runtime.WorkflowDefinitionRegistry;
import com.ghatana.platform.workflow.runtime.WorkflowStepDefinition;
import com.ghatana.platform.workflow.runtime.WorkflowTriggerType;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PostgreSQL-backed implementation of {@link WorkflowDefinitionRegistry}.
 *
 * <p>Persists {@link WorkflowDefinition} records with JSONB steps column.
 * Supports versioned definitions — the same workflow ID can have multiple versions.
 *
 * @doc.type class
 * @doc.purpose PostgreSQL persistence for workflow definitions
 * @doc.layer platform
 * @doc.pattern Repository
 */
public final class JdbcWorkflowDefinitionRegistry implements WorkflowDefinitionRegistry {
    private static final Logger log = LoggerFactory.getLogger(JdbcWorkflowDefinitionRegistry.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final TypeReference<List<WorkflowStepDefinition>> STEPS_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, String>> META_TYPE = new TypeReference<>() {};
    private static final List<WorkflowStepDefinition> NO_WORKFLOW_STEPS = List.of();

    private static final String UPSERT_SQL = """
        INSERT INTO workflow_definitions
            (workflow_id, version, name, trigger_type, trigger_filter,
             steps, entry_step_id, timeout_ms, saga_policy, metadata, enabled, created_at)
        VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?::jsonb, ?, ?)
        ON CONFLICT (workflow_id, version) DO UPDATE SET
            name = EXCLUDED.name,
            trigger_type = EXCLUDED.trigger_type,
            trigger_filter = EXCLUDED.trigger_filter,
            steps = EXCLUDED.steps,
            entry_step_id = EXCLUDED.entry_step_id,
            timeout_ms = EXCLUDED.timeout_ms,
            saga_policy = EXCLUDED.saga_policy,
            metadata = EXCLUDED.metadata,
            enabled = EXCLUDED.enabled
        """;

    private static final String SELECT_LATEST = """
        SELECT workflow_id, version, name, trigger_type, trigger_filter,
               steps, entry_step_id, timeout_ms, saga_policy, metadata, enabled, created_at
        FROM workflow_definitions
        WHERE workflow_id = ?
        ORDER BY version DESC LIMIT 1
        """;

    private static final String SELECT_BY_VERSION = """
        SELECT workflow_id, version, name, trigger_type, trigger_filter,
               steps, entry_step_id, timeout_ms, saga_policy, metadata, enabled, created_at
        FROM workflow_definitions
        WHERE workflow_id = ? AND version = ?
        """;

    private static final String SELECT_ALL_LATEST = """
        SELECT DISTINCT ON (workflow_id)
               workflow_id, version, name, trigger_type, trigger_filter,
               steps, entry_step_id, timeout_ms, saga_policy, metadata, enabled, created_at
        FROM workflow_definitions
        ORDER BY workflow_id, version DESC
        """;

    private static final String DELETE_BY_ID = "DELETE FROM workflow_definitions WHERE workflow_id = ?";

    private final DataSource dataSource;
    private final ExecutorService executor;

    public JdbcWorkflowDefinitionRegistry(@NotNull DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public Promise<Void> register(@NotNull WorkflowDefinition def) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(UPSERT_SQL)) {

                ps.setString(1, def.workflowId());
                ps.setInt(2, def.version());
                ps.setString(3, def.name());
                ps.setString(4, def.triggerType().name());
                ps.setString(5, def.triggerFilter());
                ps.setString(6, toJson(def.steps()));
                ps.setString(7, def.entryStepId());
                ps.setObject(8, def.timeout() != null ? def.timeout().toMillis() : null);
                ps.setString(9, def.sagaPolicy().name());
                ps.setString(10, toJson(def.metadata()));
                ps.setBoolean(11, def.enabled());
                ps.setTimestamp(12, Timestamp.from(def.createdAt()));
                ps.executeUpdate();
            }
        });
    }

    @Override
    public Promise<Optional<WorkflowDefinition>> findLatest(@NotNull String workflowId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_LATEST)) {
                ps.setString(1, workflowId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return Optional.of(mapRow(rs));
                    return Optional.<WorkflowDefinition>empty();
                }
            }
        });
    }

    @Override
    public Promise<Optional<WorkflowDefinition>> findByVersion(@NotNull String workflowId, int version) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_BY_VERSION)) {
                ps.setString(1, workflowId);
                ps.setInt(2, version);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return Optional.of(mapRow(rs));
                    return Optional.<WorkflowDefinition>empty();
                }
            }
        });
    }

    @Override
    public Promise<List<WorkflowDefinition>> listAll() {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_ALL_LATEST);
                 ResultSet rs = ps.executeQuery()) {
                List<WorkflowDefinition> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
                return List.copyOf(result);
            }
        });
    }

    @Override
    public Promise<Void> remove(@NotNull String workflowId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(DELETE_BY_ID)) {
                ps.setString(1, workflowId);
                ps.executeUpdate();
            }
        });
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private WorkflowDefinition mapRow(ResultSet rs) throws SQLException {
        Long timeoutMs = rs.getObject("timeout_ms") != null ? rs.getLong("timeout_ms") : null;
        return new WorkflowDefinition(
            rs.getString("workflow_id"),
            rs.getString("name"),
            rs.getInt("version"),
            WorkflowTriggerType.valueOf(rs.getString("trigger_type")),
            rs.getString("trigger_filter"),
            fromJsonSteps(rs.getString("steps")),
            rs.getString("entry_step_id"),
            timeoutMs != null ? Duration.ofMillis(timeoutMs) : null,
            SagaPolicy.valueOf(rs.getString("saga_policy")),
            fromJsonMeta(rs.getString("metadata")),
            rs.getTimestamp("created_at").toInstant(),
            rs.getBoolean("enabled")
        );
    }

    private String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize to JSON", e);
        }
    }

    private List<WorkflowStepDefinition> fromJsonSteps(String json) {
        if (json == null || json.isBlank()) return NO_WORKFLOW_STEPS;
        try {
            return MAPPER.readValue(json, STEPS_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse steps JSON: {}", e.getMessage());
            return NO_WORKFLOW_STEPS;
        }
    }

    private Map<String, String> fromJsonMeta(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return MAPPER.readValue(json, META_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse metadata JSON: {}", e.getMessage());
            return Map.of();
        }
    }
}
