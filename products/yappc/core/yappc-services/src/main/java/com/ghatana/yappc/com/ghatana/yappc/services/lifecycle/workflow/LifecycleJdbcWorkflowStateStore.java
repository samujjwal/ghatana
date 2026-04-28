/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle.workflow;

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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * JDBC-backed {@link WorkflowStateStore} scoped to the YAPPC lifecycle service.
 *
 * <p>Persists {@link WorkflowRun} state to the {@code lifecycle_workflow_runs} PostgreSQL
 * table (created by {@code V22__lifecycle_workflow_runs.sql}) so workflow execution
 * survives service restarts. Serialises step-status arrays as JSONB text for
 * efficient partial updates and querying.
 *
 * <p>Error policy:
 * <ul>
 *   <li>{@link #save} — throws {@link RuntimeException} on failure so the calling
 *       engine can route the run to the dead-letter queue</li>
 *   <li>{@link #load} — returns {@link Optional#empty()} on failure to allow the
 *       engine to treat the run as not-yet-started</li>
 * </ul>
 *
 * <p>All SQL is executed synchronously on the caller's thread; callers that require
 * non-blocking behaviour should wrap invocations with {@code Promise.ofBlocking}.
 *
 * @doc.type class
 * @doc.purpose Durable JDBC-backed workflow run persistence for the lifecycle service
 * @doc.layer product
 * @doc.pattern Repository
 */
public class LifecycleJdbcWorkflowStateStore implements WorkflowStateStore {

    private static final Logger log = LoggerFactory.getLogger(LifecycleJdbcWorkflowStateStore.class);

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    /** UPSERT so repeated saves from retried steps are idempotent. */
    private static final String UPSERT_SQL = """
            INSERT INTO lifecycle_workflow_runs
              (workflow_id, status, step_statuses, failure_reason, updated_at)
            VALUES (?, ?, ?::jsonb, ?, now())
            ON CONFLICT (workflow_id) DO UPDATE
              SET status         = EXCLUDED.status,
                  step_statuses  = EXCLUDED.step_statuses,
                  failure_reason = EXCLUDED.failure_reason,
                  updated_at     = now()
            """;

    private static final String SELECT_SQL = """
            SELECT workflow_id, status, step_statuses, failure_reason
            FROM lifecycle_workflow_runs
            WHERE workflow_id = ?
            """;

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new store.
     *
     * @param dataSource   pooled JDBC DataSource (must not be null)
     * @param objectMapper shared Jackson mapper for JSON serialisation
     */
    public LifecycleJdbcWorkflowStateStore(@NotNull DataSource dataSource,
                                           @NotNull ObjectMapper objectMapper) {
        this.dataSource   = dataSource;
        this.objectMapper = objectMapper;
    }

    /**
     * Persists or updates a {@link WorkflowRun} to the database.
     *
     * @param run the workflow run to persist
     * @throws RuntimeException if the JDBC write fails
     */
    @Override
    public void save(@NotNull WorkflowRun run) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPSERT_SQL)) {

            List<String> stepNames = Arrays.stream(run.stepStatuses())
                    .map(Enum::name)
                    .toList();

            ps.setString(1, run.workflowId());
            ps.setString(2, run.status().name());
            ps.setString(3, objectMapper.writeValueAsString(stepNames));
            ps.setString(4, run.failureReason());
            ps.executeUpdate();

            log.debug("Persisted workflow run: id={} status={}", run.workflowId(), run.status());

        } catch (SQLException | JsonProcessingException e) {
            log.error("Failed to persist lifecycle workflow run id={}", run.workflowId(), e);
            throw new RuntimeException(
                    "Lifecycle workflow state persistence failed for id=" + run.workflowId(), e);
        }
    }

    /**
     * Loads a {@link WorkflowRun} by its id.
     *
     * @param workflowId the run identifier
     * @return the run, or {@link Optional#empty()} if not found or load fails
     */
    @Override
    public Optional<WorkflowRun> load(@NotNull String workflowId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL)) {

            ps.setString(1, workflowId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }

                RunStatus   status      = RunStatus.valueOf(rs.getString("status"));
                String      stepJson    = rs.getString("step_statuses");
                String      failure     = rs.getString("failure_reason");

                List<String> stepNames  = objectMapper.readValue(stepJson, STRING_LIST);
                StepStatus[] stepStats  = stepNames.stream()
                        .map(StepStatus::valueOf)
                        .toArray(StepStatus[]::new);

                WorkflowRun run = WorkflowRun.restore(workflowId, stepStats, status, failure);
                log.debug("Loaded workflow run: id={} status={} steps={}", workflowId, status, stepNames.size());
                return Optional.of(run);
            }

        } catch (SQLException | JsonProcessingException e) {
            log.error("Failed to load lifecycle workflow run id={}", workflowId, e);
            return Optional.empty();
        }
    }
}
