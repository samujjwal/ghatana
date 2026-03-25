/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.workflow.engine.DurableWorkflowEngine;
import com.ghatana.yappc.services.lifecycle.workflow.LifecycleWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link LifecycleWorkflowService} and the underlying
 * {@link DurableWorkflowEngine} wiring (YAPPC-Ph9).
 *
 * <p>All async-backed tests use {@link EventloopTestBase#runPromise} to run
 * ActiveJ Promises on the managed event loop.
 *
 * @doc.type class
 * @doc.purpose Integration tests for YAPPC canonical workflow execution (YAPPC-Ph9)
 * @doc.layer product
 * @doc.pattern Test
 * @doc.gaa.lifecycle perceive
 */
@DisplayName("YAPPC-Ph9: Canonical Workflow Integration")
class WorkflowIntegrationTest extends EventloopTestBase {

    private DurableWorkflowEngine engine;
    private LifecycleWorkflowService workflowService;

    @BeforeEach
    void setUp() {
        engine = DurableWorkflowEngine.builder()
                .stateStore(new DurableWorkflowEngine.InMemoryWorkflowStateStore())
                .defaultTimeout(Duration.ofSeconds(10))
                .defaultMaxRetries(1)
                .defaultRetryBackoff(Duration.ofMillis(100))
                .build();
        workflowService = new LifecycleWorkflowService(engine);
    }

    // =========================================================================
    // Template loading (materialize)
    // =========================================================================

    @Nested
    @DisplayName("initialize() — template materialisation")
    class InitializeTests {

        @Test
        @DisplayName("should load all templates from lifecycle-workflow-templates.yaml")
        void shouldLoadTemplatesFromYaml() {
            int count = workflowService.initialize();
            // The classpath YAML defines at least 3 workflows: new-feature, bug-fix, security-remediation
            assertThat(count).isGreaterThanOrEqualTo(3);
        }

        @Test
        @DisplayName("should register all template IDs from YAML")
        void shouldRegisterExpectedTemplateIds() {
            workflowService.initialize();
            Set<String> templates = workflowService.registeredTemplates();
            assertThat(templates).contains("new-feature", "bug-fix", "security-remediation");
        }

        @Test
        @DisplayName("initialize() is idempotent — calling twice keeps same count")
        void initializeIsIdempotent() {
            int first  = workflowService.initialize();
            int second = workflowService.initialize();
            assertThat(first).isGreaterThan(0);
            // Second call returns 0 new templates (all already registered)
            assertThat(second).isEqualTo(0);
            assertThat(workflowService.registeredTemplates().size()).isEqualTo(first);
        }

        @Test
        @DisplayName("isRegistered() returns true for known template IDs")
        void isRegisteredReturnsTrueForKnownTemplates() {
            workflowService.initialize();
            assertThat(workflowService.isRegistered("new-feature")).isTrue();
            assertThat(workflowService.isRegistered("bug-fix")).isTrue();
        }

        @Test
        @DisplayName("isRegistered() returns false for unknown template ID")
        void isRegisteredReturnsFalseForUnknown() {
            workflowService.initialize();
            assertThat(workflowService.isRegistered("non-existent-workflow")).isFalse();
        }

        @Test
        @DisplayName("registeredTemplates() returns immutable set")
        void registeredTemplatesIsImmutable() {
            workflowService.initialize();
            Set<String> templates = workflowService.registeredTemplates();
            assertThatThrownBy(() -> templates.add("illegal"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // =========================================================================
    // Workflow execution
    // =========================================================================

    @Nested
    @DisplayName("startWorkflow() — workflow execution")
    class StartWorkflowTests {

        @BeforeEach
        void initTemplates() {
            workflowService.initialize();
        }

        @Test
        @DisplayName("should start new-feature workflow and return execution handle")
        void shouldStartNewFeatureWorkflow() {
            DurableWorkflowEngine.WorkflowExecution execution =
                    workflowService.startWorkflow("new-feature", "tenant-abc", Map.of());
            assertThat(execution).isNotNull();
            assertThat(execution.workflowId()).startsWith("new-feature-");
            assertThat(execution.run()).isNotNull();
        }

        @Test
        @DisplayName("should start bug-fix workflow with tenant binding in context")
        void shouldStartBugFixWorkflowWithTenant() {
            DurableWorkflowEngine.WorkflowExecution execution =
                    workflowService.startWorkflow("bug-fix", "tenant-xyz", Map.of("priority", "P1"));
            assertThat(execution.workflowId()).startsWith("bug-fix-");
        }

        @Test
        @DisplayName("each startWorkflow() generates a unique run ID")
        void eachRunHasUniqueId() {
            DurableWorkflowEngine.WorkflowExecution exec1 =
                    workflowService.startWorkflow("bug-fix", "tenant-1", Map.of());
            DurableWorkflowEngine.WorkflowExecution exec2 =
                    workflowService.startWorkflow("bug-fix", "tenant-1", Map.of());
            assertThat(exec1.workflowId()).isNotEqualTo(exec2.workflowId());
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for unknown template")
        void shouldThrowForUnknownTemplate() {
            assertThatThrownBy(() ->
                    workflowService.startWorkflow("non-existent", "tenant-1", Map.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("non-existent");
        }

        @Test
        @DisplayName("should run new-feature workflow to completion")
        void shouldCompleteNewFeatureWorkflow() {
            DurableWorkflowEngine.WorkflowExecution execution =
                    workflowService.startWorkflow("new-feature", "tenant-1", Map.of());

            // Wait for completion via ActiveJ Promise on the eventloop
            runPromise(execution::result);

            DurableWorkflowEngine.WorkflowRun run = execution.run();
            assertThat(run.status()).isEqualTo(DurableWorkflowEngine.RunStatus.COMPLETED);
        }

        @Test
        @DisplayName("should run bug-fix workflow to completion")
        void shouldCompleteBugFixWorkflow() {
            DurableWorkflowEngine.WorkflowExecution execution =
                    workflowService.startWorkflow("bug-fix", "tenant-1", Map.of());

            runPromise(execution::result);

            assertThat(execution.run().status())
                    .isEqualTo(DurableWorkflowEngine.RunStatus.COMPLETED);
        }

        @Test
        @DisplayName("should run security-remediation workflow to completion")
        void shouldCompleteSecurityRemediationWorkflow() {
            DurableWorkflowEngine.WorkflowExecution execution =
                    workflowService.startWorkflow("security-remediation", "sec-tenant", Map.of());

            runPromise(execution::result);

            assertThat(execution.run().status())
                    .isEqualTo(DurableWorkflowEngine.RunStatus.COMPLETED);
        }
    }

    // =========================================================================
    // Run status tracking
    // =========================================================================

    @Nested
    @DisplayName("getRunStatus() — run state queries")
    class RunStatusTests {

        @BeforeEach
        void initTemplates() {
            workflowService.initialize();
        }

        @Test
        @DisplayName("getRunStatus() returns present after startWorkflow()")
        void runStatusPresentAfterStart() {
            DurableWorkflowEngine.WorkflowExecution execution =
                    workflowService.startWorkflow("bug-fix", "tenant-1", Map.of());
            String runId = execution.workflowId();

            Optional<DurableWorkflowEngine.WorkflowRun> status =
                    workflowService.getRunStatus(runId);
            assertThat(status).isPresent();
        }

        @Test
        @DisplayName("getRunStatus() is empty for unknown run IDs")
        void runStatusEmptyForUnknownId() {
            Optional<DurableWorkflowEngine.WorkflowRun> status =
                    workflowService.getRunStatus("definitely-not-a-run-id");
            assertThat(status).isEmpty();
        }

        @Test
        @DisplayName("completed run has COMPLETED status in getRunStatus()")
        void completedRunShowsCompletedStatus() {
            DurableWorkflowEngine.WorkflowExecution execution =
                    workflowService.startWorkflow("bug-fix", "tenant-1", Map.of());

            runPromise(execution::result);

            Optional<DurableWorkflowEngine.WorkflowRun> status =
                    workflowService.getRunStatus(execution.workflowId());
            assertThat(status).isPresent();
            assertThat(status.get().status())
                    .isEqualTo(DurableWorkflowEngine.RunStatus.COMPLETED);
        }

        @Test
        @DisplayName("completed run has all steps marked COMPLETED")
        void completedRunHasAllStepsCompleted() {
            DurableWorkflowEngine.WorkflowExecution execution =
                    workflowService.startWorkflow("bug-fix", "tenant-1", Map.of());

            runPromise(execution::result);

            DurableWorkflowEngine.WorkflowRun run =
                    workflowService.getRunStatus(execution.workflowId()).orElseThrow();
            for (DurableWorkflowEngine.StepStatus s : run.stepStatuses()) {
                assertThat(s).isEqualTo(DurableWorkflowEngine.StepStatus.COMPLETED);
            }
        }

        @Test
        @DisplayName("multiple concurrent runs tracked independently")
        void multipleConcurrentRunsTrackedIndependently() {
            DurableWorkflowEngine.WorkflowExecution e1 =
                    workflowService.startWorkflow("bug-fix", "tenant-1", Map.of());
            DurableWorkflowEngine.WorkflowExecution e2 =
                    workflowService.startWorkflow("new-feature", "tenant-2", Map.of());

            // Both tracked
            assertThat(workflowService.getRunStatus(e1.workflowId())).isPresent();
            assertThat(workflowService.getRunStatus(e2.workflowId())).isPresent();
            // IDs are different
            assertThat(e1.workflowId()).isNotEqualTo(e2.workflowId());
        }
    }

    // =========================================================================
    // DurableWorkflowEngine direct — unit-level sanity checks
    // =========================================================================

    @Nested
    @DisplayName("DurableWorkflowEngine direct usage")
    class DurableEngineDirectTests {

        @Test
        @DisplayName("engine builder produces non-null engine")
        void builderProducesEngine() {
            DurableWorkflowEngine eng = DurableWorkflowEngine.builder()
                    .stateStore(new DurableWorkflowEngine.InMemoryWorkflowStateStore())
                    .defaultTimeout(Duration.ofSeconds(5))
                    .build();
            assertThat(eng).isNotNull();
        }

        @Test
        @DisplayName("single-step workflow completes successfully")
        void singleStepWorkflowCompletes() {
            var ctx = new com.ghatana.platform.workflow.DefaultWorkflowContext("wf-1", "t1");
            var step = DurableWorkflowEngine.StepDefinition.of("my-step",
                    context -> {
                        context.setVariable("done", true);
                        return io.activej.promise.Promise.of(context);
                    });

            DurableWorkflowEngine.WorkflowExecution exec =
                    engine.submit("wf-1", ctx, java.util.List.of(step));

            runPromise(exec::result);

            assertThat(exec.run().status())
                    .isEqualTo(DurableWorkflowEngine.RunStatus.COMPLETED);
        }
    }
}
