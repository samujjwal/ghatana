/*
 * Copyright (c) 2026 Ghatana Technologies // GH-90000
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
 * {@link DurableWorkflowEngine} wiring (YAPPC-Ph9). // GH-90000
 *
 * <p>All async-backed tests use {@link EventloopTestBase#runPromise} to run
 * ActiveJ Promises on the managed event loop.
 *
 * @doc.type class
 * @doc.purpose Integration tests for YAPPC canonical workflow execution (YAPPC-Ph9) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 * @doc.gaa.lifecycle perceive
 */
@DisplayName("YAPPC-Ph9: Canonical Workflow Integration [GH-90000]")
class WorkflowIntegrationTest extends EventloopTestBase {

    private DurableWorkflowEngine engine;
    private LifecycleWorkflowService workflowService;

    @BeforeEach
    void setUp() { // GH-90000
        engine = DurableWorkflowEngine.builder() // GH-90000
                .stateStore(new DurableWorkflowEngine.InMemoryWorkflowStateStore()) // GH-90000
                .defaultTimeout(Duration.ofSeconds(10)) // GH-90000
                .defaultMaxRetries(1) // GH-90000
                .defaultRetryBackoff(Duration.ofMillis(100)) // GH-90000
                .build(); // GH-90000
        workflowService = new LifecycleWorkflowService(engine); // GH-90000
    }

    // =========================================================================
    // Template loading (materialize) // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("initialize() — template materialisation [GH-90000]")
    class InitializeTests {

        @Test
        @DisplayName("should load all templates from lifecycle-workflow-templates.yaml [GH-90000]")
        void shouldLoadTemplatesFromYaml() { // GH-90000
            int count = workflowService.initialize(); // GH-90000
            // The classpath YAML defines at least 3 workflows: new-feature, bug-fix, security-remediation
            assertThat(count).isGreaterThanOrEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("should register all template IDs from YAML [GH-90000]")
        void shouldRegisterExpectedTemplateIds() { // GH-90000
            workflowService.initialize(); // GH-90000
            Set<String> templates = workflowService.registeredTemplates(); // GH-90000
            assertThat(templates).contains("new-feature", "bug-fix", "security-remediation"); // GH-90000
        }

        @Test
        @DisplayName("initialize() is idempotent — calling twice keeps same count [GH-90000]")
        void initializeIsIdempotent() { // GH-90000
            int first  = workflowService.initialize(); // GH-90000
            int second = workflowService.initialize(); // GH-90000
            assertThat(first).isGreaterThan(0); // GH-90000
            // Second call returns 0 new templates (all already registered) // GH-90000
            assertThat(second).isEqualTo(0); // GH-90000
            assertThat(workflowService.registeredTemplates().size()).isEqualTo(first); // GH-90000
        }

        @Test
        @DisplayName("isRegistered() returns true for known template IDs [GH-90000]")
        void isRegisteredReturnsTrueForKnownTemplates() { // GH-90000
            workflowService.initialize(); // GH-90000
            assertThat(workflowService.isRegistered("new-feature [GH-90000]")).isTrue();
            assertThat(workflowService.isRegistered("bug-fix [GH-90000]")).isTrue();
        }

        @Test
        @DisplayName("isRegistered() returns false for unknown template ID [GH-90000]")
        void isRegisteredReturnsFalseForUnknown() { // GH-90000
            workflowService.initialize(); // GH-90000
            assertThat(workflowService.isRegistered("non-existent-workflow [GH-90000]")).isFalse();
        }

        @Test
        @DisplayName("registeredTemplates() returns immutable set [GH-90000]")
        void registeredTemplatesIsImmutable() { // GH-90000
            workflowService.initialize(); // GH-90000
            Set<String> templates = workflowService.registeredTemplates(); // GH-90000
            assertThatThrownBy(() -> templates.add("illegal [GH-90000]"))
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }
    }

    // =========================================================================
    // Workflow execution
    // =========================================================================

    @Nested
    @DisplayName("startWorkflow() — workflow execution [GH-90000]")
    class StartWorkflowTests {

        @BeforeEach
        void initTemplates() { // GH-90000
            workflowService.initialize(); // GH-90000
        }

        @Test
        @DisplayName("should start new-feature workflow and return execution handle [GH-90000]")
        void shouldStartNewFeatureWorkflow() { // GH-90000
            DurableWorkflowEngine.WorkflowExecution execution =
                    workflowService.startWorkflow("new-feature", "tenant-abc", Map.of()); // GH-90000
            assertThat(execution).isNotNull(); // GH-90000
            assertThat(execution.workflowId()).startsWith("new-feature- [GH-90000]");
            assertThat(execution.run()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("should start bug-fix workflow with tenant binding in context [GH-90000]")
        void shouldStartBugFixWorkflowWithTenant() { // GH-90000
            DurableWorkflowEngine.WorkflowExecution execution =
                    workflowService.startWorkflow("bug-fix", "tenant-xyz", Map.of("priority", "P1")); // GH-90000
            assertThat(execution.workflowId()).startsWith("bug-fix- [GH-90000]");
        }

        @Test
        @DisplayName("each startWorkflow() generates a unique run ID [GH-90000]")
        void eachRunHasUniqueId() { // GH-90000
            DurableWorkflowEngine.WorkflowExecution exec1 =
                    workflowService.startWorkflow("bug-fix", "tenant-1", Map.of()); // GH-90000
            DurableWorkflowEngine.WorkflowExecution exec2 =
                    workflowService.startWorkflow("bug-fix", "tenant-1", Map.of()); // GH-90000
            assertThat(exec1.workflowId()).isNotEqualTo(exec2.workflowId()); // GH-90000
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for unknown template [GH-90000]")
        void shouldThrowForUnknownTemplate() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    workflowService.startWorkflow("non-existent", "tenant-1", Map.of())) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("non-existent [GH-90000]");
        }

        @Test
        @DisplayName("should run new-feature workflow to completion [GH-90000]")
        void shouldCompleteNewFeatureWorkflow() { // GH-90000
            DurableWorkflowEngine.WorkflowExecution execution =
                    workflowService.startWorkflow("new-feature", "tenant-1", Map.of()); // GH-90000

            // Wait for completion via ActiveJ Promise on the eventloop
            runPromise(execution::result); // GH-90000

            DurableWorkflowEngine.WorkflowRun run = execution.run(); // GH-90000
            assertThat(run.status()).isEqualTo(DurableWorkflowEngine.RunStatus.COMPLETED); // GH-90000
        }

        @Test
        @DisplayName("should run bug-fix workflow to completion [GH-90000]")
        void shouldCompleteBugFixWorkflow() { // GH-90000
            DurableWorkflowEngine.WorkflowExecution execution =
                    workflowService.startWorkflow("bug-fix", "tenant-1", Map.of()); // GH-90000

            runPromise(execution::result); // GH-90000

            assertThat(execution.run().status()) // GH-90000
                    .isEqualTo(DurableWorkflowEngine.RunStatus.COMPLETED); // GH-90000
        }

        @Test
        @DisplayName("should run security-remediation workflow to completion [GH-90000]")
        void shouldCompleteSecurityRemediationWorkflow() { // GH-90000
            DurableWorkflowEngine.WorkflowExecution execution =
                    workflowService.startWorkflow("security-remediation", "sec-tenant", Map.of()); // GH-90000

            runPromise(execution::result); // GH-90000

            assertThat(execution.run().status()) // GH-90000
                    .isEqualTo(DurableWorkflowEngine.RunStatus.COMPLETED); // GH-90000
        }
    }

    // =========================================================================
    // Run status tracking
    // =========================================================================

    @Nested
    @DisplayName("getRunStatus() — run state queries [GH-90000]")
    class RunStatusTests {

        @BeforeEach
        void initTemplates() { // GH-90000
            workflowService.initialize(); // GH-90000
        }

        @Test
        @DisplayName("getRunStatus() returns present after startWorkflow() [GH-90000]")
        void runStatusPresentAfterStart() { // GH-90000
            DurableWorkflowEngine.WorkflowExecution execution =
                    workflowService.startWorkflow("bug-fix", "tenant-1", Map.of()); // GH-90000
            String runId = execution.workflowId(); // GH-90000

            Optional<DurableWorkflowEngine.WorkflowRun> status =
                    workflowService.getRunStatus(runId); // GH-90000
            assertThat(status).isPresent(); // GH-90000
        }

        @Test
        @DisplayName("getRunStatus() is empty for unknown run IDs [GH-90000]")
        void runStatusEmptyForUnknownId() { // GH-90000
            Optional<DurableWorkflowEngine.WorkflowRun> status =
                    workflowService.getRunStatus("definitely-not-a-run-id [GH-90000]");
            assertThat(status).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("completed run has COMPLETED status in getRunStatus() [GH-90000]")
        void completedRunShowsCompletedStatus() { // GH-90000
            DurableWorkflowEngine.WorkflowExecution execution =
                    workflowService.startWorkflow("bug-fix", "tenant-1", Map.of()); // GH-90000

            runPromise(execution::result); // GH-90000

            Optional<DurableWorkflowEngine.WorkflowRun> status =
                    workflowService.getRunStatus(execution.workflowId()); // GH-90000
            assertThat(status).isPresent(); // GH-90000
            assertThat(status.get().status()) // GH-90000
                    .isEqualTo(DurableWorkflowEngine.RunStatus.COMPLETED); // GH-90000
        }

        @Test
        @DisplayName("completed run has all steps marked COMPLETED [GH-90000]")
        void completedRunHasAllStepsCompleted() { // GH-90000
            DurableWorkflowEngine.WorkflowExecution execution =
                    workflowService.startWorkflow("bug-fix", "tenant-1", Map.of()); // GH-90000

            runPromise(execution::result); // GH-90000

            DurableWorkflowEngine.WorkflowRun run =
                    workflowService.getRunStatus(execution.workflowId()).orElseThrow(); // GH-90000
            for (DurableWorkflowEngine.StepStatus s : run.stepStatuses()) { // GH-90000
                assertThat(s).isEqualTo(DurableWorkflowEngine.StepStatus.COMPLETED); // GH-90000
            }
        }

        @Test
        @DisplayName("multiple concurrent runs tracked independently [GH-90000]")
        void multipleConcurrentRunsTrackedIndependently() { // GH-90000
            DurableWorkflowEngine.WorkflowExecution e1 =
                    workflowService.startWorkflow("bug-fix", "tenant-1", Map.of()); // GH-90000
            DurableWorkflowEngine.WorkflowExecution e2 =
                    workflowService.startWorkflow("new-feature", "tenant-2", Map.of()); // GH-90000

            // Both tracked
            assertThat(workflowService.getRunStatus(e1.workflowId())).isPresent(); // GH-90000
            assertThat(workflowService.getRunStatus(e2.workflowId())).isPresent(); // GH-90000
            // IDs are different
            assertThat(e1.workflowId()).isNotEqualTo(e2.workflowId()); // GH-90000
        }
    }

    // =========================================================================
    // DurableWorkflowEngine direct — unit-level sanity checks
    // =========================================================================

    @Nested
    @DisplayName("DurableWorkflowEngine direct usage [GH-90000]")
    class DurableEngineDirectTests {

        @Test
        @DisplayName("engine builder produces non-null engine [GH-90000]")
        void builderProducesEngine() { // GH-90000
            DurableWorkflowEngine eng = DurableWorkflowEngine.builder() // GH-90000
                    .stateStore(new DurableWorkflowEngine.InMemoryWorkflowStateStore()) // GH-90000
                    .defaultTimeout(Duration.ofSeconds(5)) // GH-90000
                    .build(); // GH-90000
            assertThat(eng).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("single-step workflow completes successfully [GH-90000]")
        void singleStepWorkflowCompletes() { // GH-90000
            var ctx = new com.ghatana.platform.workflow.DefaultWorkflowContext("wf-1", "t1"); // GH-90000
            var step = DurableWorkflowEngine.StepDefinition.of("my-step", // GH-90000
                    context -> {
                        context.setVariable("done", true); // GH-90000
                        return io.activej.promise.Promise.of(context); // GH-90000
                    });

            DurableWorkflowEngine.WorkflowExecution exec =
                    engine.submit("wf-1", ctx, java.util.List.of(step)); // GH-90000

            runPromise(exec::result); // GH-90000

            assertThat(exec.run().status()) // GH-90000
                    .isEqualTo(DurableWorkflowEngine.RunStatus.COMPLETED); // GH-90000
        }
    }
}
