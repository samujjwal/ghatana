/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.workflow.engine.DurableWorkflowEngine.RunStatus;
import com.ghatana.platform.workflow.engine.DurableWorkflowEngine.StepStatus;
import com.ghatana.platform.workflow.engine.DurableWorkflowEngine.WorkflowRun;
import com.ghatana.platform.workflow.engine.DurableWorkflowEngine.WorkflowStateStore;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.List;
import java.util.Optional;

/**
 * JDBC-backed implementation of {@link WorkflowStateStore}.
 *
 * <p>All workflow runs are persisted to PostgreSQL so they survive server restarts.
 * {@link WorkflowRun} state is serialised as follows:
 * <ul>
 *   <li>{@code step_statuses} — JSONB array of {@link StepStatus} names</li>
 *   <li>{@code status} — TEXT column holding the {@link RunStatus} name</li>
 *   <li>{@code failure_reason} — nullable TEXT</li>
 * </ul>
 *
 * <p>Schema: see {@code V13__workflow_runs.sql}.
 *
 * @doc.type class
 * @doc.purpose Durable JDBC-backed workflow run persistence
 * @doc.layer product
 * @doc.pattern Repository
 */
public class JdbcWorkflowStateStore implements WorkflowStateStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcWorkflowStateStore.class);

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private static final String UPSERT_SQL = """
            INSERT INTO workflow_runs
              (workflow_id, status, step_statuses, failure_reason, updated_at)
            VALUES (?, ?, ?::jsonb, ?, now())
            ON CONFLICT (workflow_id) DO UPDATE
              SET status        = EXCLUDED.status,
                  step_statuses = EXCLUDED.step_statuses,
                  failure_reason = EXCLUDED.failure_reason,
                  updated_at    = now()
            """;

    private static final String SELECT_SQL = """
            SELECT workflow_id, status, step_statuses, failure_reason
            FROM workflow_runs
            WHERE workflow_id = ?
            """;

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public JdbcWorkflowStateStore(@NotNull DataSource dataSource,
                                  @NotNull ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(@NotNull WorkflowRun run) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPSERT_SQL)) {

            StepStatus[] steps = run.stepStatuses();
            List<String> stepNames = java.util.Arrays.stream(steps)
                    .map(Enum::name)
                    .toList();

            ps.setString(1, run.workflowId());
            ps.setString(2, run.status().name());
            ps.setString(3, objectMapper.writeValueAsString(stepNames));
            ps.setString(4, run.failureReason());

            ps.executeUpdate();
            log.debug("Saved workflow run: id={} status={}", run.workflowId(), run.status());

        } catch (SQLException | JsonProcessingException e) {
            log.error("Failed to persist workflow run id={}", run.workflowId(), e);
            throw new RuntimeException("Workflow state persistence failed for id=" + run.workflowId(), e);
        }
    }

    @Override
    public Optional<WorkflowRun> load(@NotNull String workflowId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL)) {

            ps.setString(1, workflowId);
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                return Optional.empty();
            }

            String statusStr = rs.getString("status");
            String stepJson  = rs.getString("step_statuses");
            String failure   = rs.getString("failure_reason");

            RunStatus status = RunStatus.valueOf(statusStr);

            List<String> stepNames = objectMapper.readValue(stepJson, STRING_LIST);
            StepStatus[] stepStatuses = stepNames.stream()
                    .map(StepStatus::valueOf)
                    .toArray(StepStatus[]::new);

            WorkflowRun run = WorkflowRun.restore(workflowId, stepStatuses, status, failure);
            log.debug("Loaded workflow run: id={} status={} steps={}", workflowId, status, stepNames.size());
            return Optional.of(run);

        } catch (SQLException | JsonProcessingException e) {
            log.error("Failed to load workflow run id={}", workflowId, e);
            return Optional.empty();
        }
    }
}
