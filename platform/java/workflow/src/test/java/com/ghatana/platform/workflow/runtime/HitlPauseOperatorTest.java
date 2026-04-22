/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.workflow.runtime;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.workflow.*;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link HitlPauseOperator} and HITL lifecycle in {@link DurableWorkflowRuntime}.
 */
@DisplayName("HitlPauseOperator tests [GH-90000]")
class HitlPauseOperatorTest extends EventloopTestBase {

    private InMemoryWorkflowDefinitionRegistry definitionRegistry;
    private InMemoryWorkflowStateStore stateStore;
    private DefaultStepOperatorRegistry operatorRegistry;
    private List<WorkflowLifecycleEvent> capturedEvents;
    private DurableWorkflowRuntime runtime;
    private HitlPauseOperator hitl;

    @Override
    protected Duration eventloopTimeout() { // GH-90000
        return Duration.ofSeconds(30); // GH-90000
    }

    @Override
    protected boolean breakOnFatalError() { // GH-90000
        return false;
    }

    @BeforeEach
    void setUp() { // GH-90000
        definitionRegistry = new InMemoryWorkflowDefinitionRegistry(); // GH-90000
        stateStore = new InMemoryWorkflowStateStore(); // GH-90000
        operatorRegistry = new DefaultStepOperatorRegistry(); // GH-90000
        capturedEvents = new ArrayList<>(); // GH-90000

        runtime = DurableWorkflowRuntime.builder() // GH-90000
            .definitionRegistry(definitionRegistry) // GH-90000
            .stateStore(stateStore) // GH-90000
            .operatorRegistry(operatorRegistry) // GH-90000
            .addListener(capturedEvents::add) // GH-90000
            .defaultMaxRetries(0) // GH-90000
            .build(); // GH-90000

        hitl = new HitlPauseOperator(runtime); // GH-90000
    }

    // ── HITL workflow pausing ──────────────────────────────────────────────

    @Nested
    @DisplayName("HITL pause behavior [GH-90000]")
    class HitlPauseBehavior {

        @Test
        @DisplayName("workflow pauses at HUMAN_IN_THE_LOOP step with WAITING_FOR_HITL status [GH-90000]")
        void pausesAtHitlStep() { // GH-90000
            registerHitlWorkflow("hitl-wf [GH-90000]");

            WorkflowRun result = runPromise(() -> runtime.start( // GH-90000
                "hitl-wf", "tenant-1", "corr-1", new HashMap<>())); // GH-90000

            assertThat(result.status()).isEqualTo(WorkflowRunStatus.WAITING_FOR_HITL); // GH-90000
        }

        @Test
        @DisplayName("paused run stores HITL step metadata in variables [GH-90000]")
        void storesHitlMetadataInVariables() { // GH-90000
            registerHitlWorkflow("hitl-wf [GH-90000]");

            WorkflowRun result = runPromise(() -> runtime.start( // GH-90000
                "hitl-wf", "tenant-1", "corr-1", new HashMap<>())); // GH-90000

            assertThat(result.variables()).containsKey("__hitlStepId [GH-90000]");
            assertThat(result.variables()).containsKey("__hitlNextStepId [GH-90000]");
            assertThat(result.variables().get("__hitlStepId [GH-90000]")).isEqualTo("hitl-step [GH-90000]");
            assertThat(result.variables().get("__hitlNextStepId [GH-90000]")).isEqualTo("final-step [GH-90000]");
        }

        @Test
        @DisplayName("pre-HITL action steps execute before pausing [GH-90000]")
        void preHitlStepsExecuteFirst() { // GH-90000
            registerHitlWorkflow("hitl-wf [GH-90000]");

            WorkflowRun result = runPromise(() -> runtime.start( // GH-90000
                "hitl-wf", "tenant-1", "corr-1", new HashMap<>())); // GH-90000

            assertThat(result.variables()).containsEntry("pre_hitl_done", "true"); // GH-90000
        }
    }

    // ── Resume: approve ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Approve resumes workflow [GH-90000]")
    class ApproveResumesWorkflow {

        @Test
        @DisplayName("approve() resumes execution and completes workflow [GH-90000]")
        void approveCompletesWorkflow() { // GH-90000
            registerHitlWorkflow("hitl-wf [GH-90000]");

            WorkflowRun paused = runPromise(() -> runtime.start( // GH-90000
                "hitl-wf", "tenant-1", "corr-1", new HashMap<>())); // GH-90000
            assertThat(paused.status()).isEqualTo(WorkflowRunStatus.WAITING_FOR_HITL); // GH-90000

            WorkflowRun completed = runPromise(() -> hitl.approve(paused.runId())); // GH-90000
            assertThat(completed.status()).isEqualTo(WorkflowRunStatus.COMPLETED); // GH-90000
        }

        @Test
        @DisplayName("approve() merges additional context into variables [GH-90000]")
        void approvesMergesContext() { // GH-90000
            registerHitlWorkflow("hitl-wf [GH-90000]");

            WorkflowRun paused = runPromise(() -> runtime.start( // GH-90000
                "hitl-wf", "tenant-1", "corr-1", new HashMap<>())); // GH-90000

            WorkflowRun completed = runPromise(() -> hitl.approve( // GH-90000
                paused.runId(), Map.of("reviewer", "alice"))); // GH-90000

            assertThat(completed.variables()).containsEntry("reviewer", "alice"); // GH-90000
        }

        @Test
        @DisplayName("approved run has __hitlDecision=APPROVED in variables [GH-90000]")
        void approvedRunHasDecisionVariable() { // GH-90000
            registerHitlWorkflow("hitl-wf [GH-90000]");

            WorkflowRun paused = runPromise(() -> runtime.start( // GH-90000
                "hitl-wf", "tenant-1", "corr-1", new HashMap<>())); // GH-90000
            WorkflowRun completed = runPromise(() -> hitl.approve(paused.runId())); // GH-90000

            assertThat(completed.variables().get("__hitlDecision [GH-90000]")).isEqualTo("APPROVED [GH-90000]");
        }

        @Test
        @DisplayName("post-HITL steps execute after approval [GH-90000]")
        void postHitlStepsExecute() { // GH-90000
            registerHitlWorkflow("hitl-wf [GH-90000]");

            WorkflowRun paused = runPromise(() -> runtime.start( // GH-90000
                "hitl-wf", "tenant-1", "corr-1", new HashMap<>())); // GH-90000
            WorkflowRun completed = runPromise(() -> hitl.approve(paused.runId())); // GH-90000

            assertThat(completed.variables()).containsEntry("post_hitl_done", "true"); // GH-90000
        }
    }

    // ── Resume: reject ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Reject cancels workflow [GH-90000]")
    class RejectCancelsWorkflow {

        @Test
        @DisplayName("reject() cancels the workflow run [GH-90000]")
        void rejectCancelsRun() { // GH-90000
            registerHitlWorkflow("hitl-wf [GH-90000]");

            WorkflowRun paused = runPromise(() -> runtime.start( // GH-90000
                "hitl-wf", "tenant-1", "corr-1", new HashMap<>())); // GH-90000
            WorkflowRun cancelled = runPromise(() -> hitl.reject(paused.runId(), "Policy violation")); // GH-90000

            assertThat(cancelled.status()).isEqualTo(WorkflowRunStatus.CANCELLED); // GH-90000
        }

        @Test
        @DisplayName("rejected run has __hitlDecision=REJECTED in variables [GH-90000]")
        void rejectedRunHasDecisionVariable() { // GH-90000
            registerHitlWorkflow("hitl-wf [GH-90000]");

            WorkflowRun paused = runPromise(() -> runtime.start( // GH-90000
                "hitl-wf", "tenant-1", "corr-1", new HashMap<>())); // GH-90000
            WorkflowRun cancelled = runPromise(() -> hitl.reject(paused.runId(), "Not allowed")); // GH-90000

            assertThat(cancelled.variables().get("__hitlDecision [GH-90000]")).isEqualTo("REJECTED [GH-90000]");
        }
    }

    // ── Validation ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("HitlPauseOperator validation [GH-90000]")
    class HitlPauseOperatorValidation {

        @Test
        @DisplayName("rejects blank runId in approve() [GH-90000]")
        void rejectsBlankRunIdOnApprove() { // GH-90000
            assertThatThrownBy(() -> hitl.approve(" [GH-90000]"))
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("runId [GH-90000]");
        }

        @Test
        @DisplayName("rejects blank runId in reject() [GH-90000]")
        void rejectsBlankRunIdOnReject() { // GH-90000
            assertThatThrownBy(() -> hitl.reject("", "reason")) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("runId [GH-90000]");
        }

        @Test
        @DisplayName("rejects null runtime in constructor [GH-90000]")
        void rejectsNullRuntime() { // GH-90000
            assertThatThrownBy(() -> new HitlPauseOperator(null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("resume() on non-WAITING_FOR_HITL run throws error [GH-90000]")
        void resumeOnWrongStatusThrows() { // GH-90000
            registerHitlWorkflow("hitl-wf [GH-90000]");
            // Start and complete immediately a workflow with no HITL step
            registerNoHitlWorkflow("no-hitl-wf [GH-90000]");
            WorkflowRun completedRun = runPromise(() -> runtime.start( // GH-90000
                "no-hitl-wf", "tenant-1", "corr-2", new HashMap<>())); // GH-90000

            assertThatThrownBy(() -> // GH-90000
                    runPromise(() -> hitl.approve(completedRun.runId()))) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("not waiting for HITL [GH-90000]");
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void registerHitlWorkflow(String workflowId) { // GH-90000
        operatorRegistry.register("pre-action-op", (ctx, cfg) -> { // GH-90000
            ctx.put("pre_hitl_done", "true"); // GH-90000
            return Promise.of(ctx); // GH-90000
        });
        operatorRegistry.register("post-action-op", (ctx, cfg) -> { // GH-90000
            ctx.put("post_hitl_done", "true"); // GH-90000
            return Promise.of(ctx); // GH-90000
        });

        WorkflowDefinition def = WorkflowDefinition.builder(workflowId, "HITL Workflow") // GH-90000
            .addStep(WorkflowStepDefinition.action("pre-step", "Pre-HITL Action", "pre-action-op") // GH-90000
                .withNextStep("hitl-step [GH-90000]"))
            .addStep(WorkflowStepDefinition.humanInTheLoop("hitl-step", "Human Checkpoint", "final-step")) // GH-90000
            .addStep(WorkflowStepDefinition.action("final-step", "Post-HITL Action", "post-action-op") // GH-90000
                .withNextStep(null)) // GH-90000
            .build(); // GH-90000

        runPromise(() -> definitionRegistry.register(def)); // GH-90000
    }

    private void registerNoHitlWorkflow(String workflowId) { // GH-90000
        operatorRegistry.register("single-op", (ctx, cfg) -> Promise.of(ctx)); // GH-90000
        WorkflowDefinition def = WorkflowDefinition.builder(workflowId, "No HITL Workflow") // GH-90000
            .addStep(WorkflowStepDefinition.action("only-step", "Only Step", "single-op") // GH-90000
                .withNextStep(null)) // GH-90000
            .build(); // GH-90000
        runPromise(() -> definitionRegistry.register(def)); // GH-90000
    }
}
