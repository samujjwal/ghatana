/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.workflow;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.workflow.WorkflowContext;
import com.ghatana.platform.workflow.engine.DurableWorkflowEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link WorkflowMaterializer}.
 *
 * <p>Covers:
 * <ul>
 *   <li>7.3.6 — Materialize canonical templates and start a new-feature workflow run</li>
 *   <li>7.3.7 — Checkpoint recovery via state store restore</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Unit tests for WorkflowMaterializer template loading and workflow execution
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("WorkflowMaterializer Tests")
class WorkflowMaterializerTest extends EventloopTestBase {

    private DurableWorkflowEngine.InMemoryWorkflowStateStore stateStore;
    private DurableWorkflowEngine engine;
    private WorkflowMaterializer materializer;

    @BeforeEach
    void setUp() {
        stateStore = new DurableWorkflowEngine.InMemoryWorkflowStateStore();
        engine = DurableWorkflowEngine.builder()
                .stateStore(stateStore)
                .build();
        materializer = new WorkflowMaterializer(engine);
    }

    // -------------------------------------------------------------------------
    // 7.3.1-2: Template loading
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("materializeAll() should load canonical workflow templates from classpath YAML")
    void shouldLoadTemplatesFromYaml() {
        // WHEN
        int loaded = materializer.materializeAll();

        // THEN — at least the 3 templates defined in lifecycle-workflow-templates.yaml
        assertThat(loaded).isGreaterThanOrEqualTo(3);
        assertThat(materializer.registeredTemplates())
                .contains("new-feature", "bug-fix", "security-remediation");
    }

    @Test
    @DisplayName("materializeAll() is idempotent — duplicate calls do not double-register")
    void shouldBeIdempotent() {
        // WHEN
        int first  = materializer.materializeAll();
        int second = materializer.materializeAll();

        // THEN — second call adds 0 new templates
        assertThat(second).isEqualTo(0);
        assertThat(materializer.registeredTemplates().size()).isEqualTo(first);
    }

    // -------------------------------------------------------------------------
    // 7.3.6: Start a new-feature workflow run
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("7.3.6 — startWorkflow('new-feature') should complete with COMPLETED status")
    void shouldRunNewFeatureWorkflowToCompletion() {
        // GIVEN
        materializer.materializeAll();

        // WHEN — run the workflow inside the eventloop so the Promise resolves properly
        WorkflowContext finalCtx = runPromise(() -> {
            DurableWorkflowEngine.WorkflowExecution exec =
                    materializer.startWorkflow("new-feature", "test-tenant",
                            Map.of("project", "PROJ-001"));
            return exec.result();
        });

        // THEN
        assertThat(finalCtx).isNotNull();
        assertThat(finalCtx.getWorkflowId()).startsWith("new-feature-");
        assertThat(finalCtx.getTenantId()).isEqualTo("test-tenant");

        // Run should be COMPLETED in the run registry
        Optional<DurableWorkflowEngine.WorkflowRun> run =
                materializer.getRunStatus(finalCtx.getWorkflowId());
        assertThat(run).isPresent();
        assertThat(run.get().status()).isEqualTo(DurableWorkflowEngine.RunStatus.COMPLETED);
    }

    @Test
    @DisplayName("7.3.6 — startWorkflow('bug-fix') should complete with all steps COMPLETED")
    void shouldRunBugFixWorkflowToCompletion() {
        // GIVEN
        materializer.materializeAll();

        // WHEN
        WorkflowContext finalCtx = runPromise(() -> {
            DurableWorkflowEngine.WorkflowExecution exec =
                    materializer.startWorkflow("bug-fix", "test-tenant", Collections.emptyMap());
            return exec.result();
        });

        // THEN — all steps completed
        String runId = finalCtx.getWorkflowId();
        DurableWorkflowEngine.WorkflowRun run = materializer.getRunStatus(runId).orElseThrow();
        assertThat(run.status()).isEqualTo(DurableWorkflowEngine.RunStatus.COMPLETED);
        for (DurableWorkflowEngine.StepStatus stepStatus : run.stepStatuses()) {
            assertThat(stepStatus).isEqualTo(DurableWorkflowEngine.StepStatus.COMPLETED);
        }
    }

    // -------------------------------------------------------------------------
    // 7.3.7: Checkpoint recovery — state store restoration
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("7.3.7 — Completed run persisted to state store and restorable by runId")
    void shouldPersistRunToStateStore() {
        // GIVEN
        materializer.materializeAll();

        // WHEN — run completes
        WorkflowContext finalCtx = runPromise(() -> {
            DurableWorkflowEngine.WorkflowExecution exec =
                    materializer.startWorkflow("new-feature", "test-tenant", Collections.emptyMap());
            return exec.result();
        });

        // THEN — state is persisted in the underlying store (JDBC in prod, in-memory here)
        String runId = finalCtx.getWorkflowId();
        Optional<DurableWorkflowEngine.WorkflowRun> restored = stateStore.load(runId);
        assertThat(restored).isPresent();
        assertThat(restored.get().workflowId()).isEqualTo(runId);
        assertThat(restored.get().status()).isEqualTo(DurableWorkflowEngine.RunStatus.COMPLETED);
    }

    @Test
    @DisplayName("7.3.7 — WorkflowRun.restore() recreates mid-step checkpoint state")
    void shouldRestoreRunFromCheckpoint() {
        // GIVEN — simulate a previously checkpointed run (3 steps, 2 completed, 1 running)
        int stepCount = 3;
        DurableWorkflowEngine.StepStatus[] checkpointStatuses = {
            DurableWorkflowEngine.StepStatus.COMPLETED,
            DurableWorkflowEngine.StepStatus.COMPLETED,
            DurableWorkflowEngine.StepStatus.RUNNING
        };
        String checkpointRunId = "new-feature-checkpoint-test";

        // WHEN — restore run from checkpoint
        DurableWorkflowEngine.WorkflowRun restoredRun = DurableWorkflowEngine.WorkflowRun.restore(
                checkpointRunId,
                checkpointStatuses,
                DurableWorkflowEngine.RunStatus.RUNNING,
                null);

        // THEN
        assertThat(restoredRun.workflowId()).isEqualTo(checkpointRunId);
        assertThat(restoredRun.status()).isEqualTo(DurableWorkflowEngine.RunStatus.RUNNING);
        assertThat(restoredRun.stepStatuses()).containsExactly(
                DurableWorkflowEngine.StepStatus.COMPLETED,
                DurableWorkflowEngine.StepStatus.COMPLETED,
                DurableWorkflowEngine.StepStatus.RUNNING);
        assertThat(restoredRun.failureReason()).isNull();
    }

    // -------------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("startWorkflow() should throw when template is not registered")
    void shouldThrowForUnknownTemplate() {
        // GIVEN — no templates loaded
        // THEN
        assertThatThrownBy(() ->
                materializer.startWorkflow("nonexistent-template", "test-tenant", Collections.emptyMap()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nonexistent-template");
    }

    @Test
    @DisplayName("getRunStatus() should return empty for unknown runId")
    void shouldReturnEmptyForUnknownRun() {
        assertThat(materializer.getRunStatus("unknown-run-id")).isEmpty();
    }

    @Test
    @DisplayName("startWorkflow() should propagate initial variables into workflow context")
    void shouldPropagateVariables() {
        // GIVEN
        materializer.materializeAll();
        Map<String, Object> vars = Map.of("projectId", "PROJ-42", "requestedBy", "alice@example.com");

        // WHEN
        WorkflowContext finalCtx = runPromise(() -> {
            DurableWorkflowEngine.WorkflowExecution exec =
                    materializer.startWorkflow("security-remediation", "tenant-sec", vars);
            return exec.result();
        });

        // THEN — initial variables are in the final context
        assertThat(finalCtx.getVariable("projectId")).isEqualTo("PROJ-42");
        assertThat(finalCtx.getVariable("requestedBy")).isEqualTo("alice@example.com");
    }
}
