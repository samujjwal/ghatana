/*
 * Copyright (c) 2026 Ghatana Inc.
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
@DisplayName("HitlPauseOperator tests")
class HitlPauseOperatorTest extends EventloopTestBase {

    private InMemoryWorkflowDefinitionRegistry definitionRegistry;
    private InMemoryWorkflowStateStore stateStore;
    private DefaultStepOperatorRegistry operatorRegistry;
    private List<WorkflowLifecycleEvent> capturedEvents;
    private DurableWorkflowRuntime runtime;
    private HitlPauseOperator hitl;

    @Override
    protected Duration eventloopTimeout() {
        return Duration.ofSeconds(30);
    }

    @Override
    protected boolean breakOnFatalError() {
        return false;
    }

    @BeforeEach
    void setUp() {
        definitionRegistry = new InMemoryWorkflowDefinitionRegistry();
        stateStore = new InMemoryWorkflowStateStore();
        operatorRegistry = new DefaultStepOperatorRegistry();
        capturedEvents = new ArrayList<>();

        runtime = DurableWorkflowRuntime.builder()
            .definitionRegistry(definitionRegistry)
            .stateStore(stateStore)
            .operatorRegistry(operatorRegistry)
            .addListener(capturedEvents::add)
            .defaultMaxRetries(0)
            .build();

        hitl = new HitlPauseOperator(runtime);
    }

    // ── HITL workflow pausing ──────────────────────────────────────────────

    @Nested
    @DisplayName("HITL pause behavior")
    class HitlPauseBehavior {

        @Test
        @DisplayName("workflow pauses at HUMAN_IN_THE_LOOP step with WAITING_FOR_HITL status")
        void pausesAtHitlStep() {
            registerHitlWorkflow("hitl-wf");

            WorkflowRun result = runPromise(() -> runtime.start(
                "hitl-wf", "tenant-1", "corr-1", new HashMap<>()));

            assertThat(result.status()).isEqualTo(WorkflowRunStatus.WAITING_FOR_HITL);
        }

        @Test
        @DisplayName("paused run stores HITL step metadata in variables")
        void storesHitlMetadataInVariables() {
            registerHitlWorkflow("hitl-wf");

            WorkflowRun result = runPromise(() -> runtime.start(
                "hitl-wf", "tenant-1", "corr-1", new HashMap<>()));

            assertThat(result.variables()).containsKey("__hitlStepId");
            assertThat(result.variables()).containsKey("__hitlNextStepId");
            assertThat(result.variables().get("__hitlStepId")).isEqualTo("hitl-step");
            assertThat(result.variables().get("__hitlNextStepId")).isEqualTo("final-step");
        }

        @Test
        @DisplayName("pre-HITL action steps execute before pausing")
        void preHitlStepsExecuteFirst() {
            registerHitlWorkflow("hitl-wf");

            WorkflowRun result = runPromise(() -> runtime.start(
                "hitl-wf", "tenant-1", "corr-1", new HashMap<>()));

            assertThat(result.variables()).containsEntry("pre_hitl_done", "true");
        }
    }

    // ── Resume: approve ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Approve resumes workflow")
    class ApproveResumesWorkflow {

        @Test
        @DisplayName("approve() resumes execution and completes workflow")
        void approveCompletesWorkflow() {
            registerHitlWorkflow("hitl-wf");

            WorkflowRun paused = runPromise(() -> runtime.start(
                "hitl-wf", "tenant-1", "corr-1", new HashMap<>()));
            assertThat(paused.status()).isEqualTo(WorkflowRunStatus.WAITING_FOR_HITL);

            WorkflowRun completed = runPromise(() -> hitl.approve(paused.runId()));
            assertThat(completed.status()).isEqualTo(WorkflowRunStatus.COMPLETED);
        }

        @Test
        @DisplayName("approve() merges additional context into variables")
        void approvesMergesContext() {
            registerHitlWorkflow("hitl-wf");

            WorkflowRun paused = runPromise(() -> runtime.start(
                "hitl-wf", "tenant-1", "corr-1", new HashMap<>()));

            WorkflowRun completed = runPromise(() -> hitl.approve(
                paused.runId(), Map.of("reviewer", "alice")));

            assertThat(completed.variables()).containsEntry("reviewer", "alice");
        }

        @Test
        @DisplayName("approved run has __hitlDecision=APPROVED in variables")
        void approvedRunHasDecisionVariable() {
            registerHitlWorkflow("hitl-wf");

            WorkflowRun paused = runPromise(() -> runtime.start(
                "hitl-wf", "tenant-1", "corr-1", new HashMap<>()));
            WorkflowRun completed = runPromise(() -> hitl.approve(paused.runId()));

            assertThat(completed.variables().get("__hitlDecision")).isEqualTo("APPROVED");
        }

        @Test
        @DisplayName("post-HITL steps execute after approval")
        void postHitlStepsExecute() {
            registerHitlWorkflow("hitl-wf");

            WorkflowRun paused = runPromise(() -> runtime.start(
                "hitl-wf", "tenant-1", "corr-1", new HashMap<>()));
            WorkflowRun completed = runPromise(() -> hitl.approve(paused.runId()));

            assertThat(completed.variables()).containsEntry("post_hitl_done", "true");
        }
    }

    // ── Resume: reject ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Reject cancels workflow")
    class RejectCancelsWorkflow {

        @Test
        @DisplayName("reject() cancels the workflow run")
        void rejectCancelsRun() {
            registerHitlWorkflow("hitl-wf");

            WorkflowRun paused = runPromise(() -> runtime.start(
                "hitl-wf", "tenant-1", "corr-1", new HashMap<>()));
            WorkflowRun cancelled = runPromise(() -> hitl.reject(paused.runId(), "Policy violation"));

            assertThat(cancelled.status()).isEqualTo(WorkflowRunStatus.CANCELLED);
        }

        @Test
        @DisplayName("rejected run has __hitlDecision=REJECTED in variables")
        void rejectedRunHasDecisionVariable() {
            registerHitlWorkflow("hitl-wf");

            WorkflowRun paused = runPromise(() -> runtime.start(
                "hitl-wf", "tenant-1", "corr-1", new HashMap<>()));
            WorkflowRun cancelled = runPromise(() -> hitl.reject(paused.runId(), "Not allowed"));

            assertThat(cancelled.variables().get("__hitlDecision")).isEqualTo("REJECTED");
        }
    }

    // ── Validation ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("HitlPauseOperator validation")
    class HitlPauseOperatorValidation {

        @Test
        @DisplayName("rejects blank runId in approve()")
        void rejectsBlankRunIdOnApprove() {
            assertThatThrownBy(() -> hitl.approve(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runId");
        }

        @Test
        @DisplayName("rejects blank runId in reject()")
        void rejectsBlankRunIdOnReject() {
            assertThatThrownBy(() -> hitl.reject("", "reason"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runId");
        }

        @Test
        @DisplayName("rejects null runtime in constructor")
        void rejectsNullRuntime() {
            assertThatThrownBy(() -> new HitlPauseOperator(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("resume() on non-WAITING_FOR_HITL run throws error")
        void resumeOnWrongStatusThrows() {
            registerHitlWorkflow("hitl-wf");
            // Start and complete immediately a workflow with no HITL step
            registerNoHitlWorkflow("no-hitl-wf");
            WorkflowRun completedRun = runPromise(() -> runtime.start(
                "no-hitl-wf", "tenant-1", "corr-2", new HashMap<>()));

            assertThatThrownBy(() ->
                    runPromise(() -> hitl.approve(completedRun.runId())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not waiting for HITL");
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void registerHitlWorkflow(String workflowId) {
        operatorRegistry.register("pre-action-op", (ctx, cfg) -> {
            ctx.put("pre_hitl_done", "true");
            return Promise.of(ctx);
        });
        operatorRegistry.register("post-action-op", (ctx, cfg) -> {
            ctx.put("post_hitl_done", "true");
            return Promise.of(ctx);
        });

        WorkflowDefinition def = WorkflowDefinition.builder(workflowId, "HITL Workflow")
            .addStep(WorkflowStepDefinition.action("pre-step", "Pre-HITL Action", "pre-action-op")
                .withNextStep("hitl-step"))
            .addStep(WorkflowStepDefinition.humanInTheLoop("hitl-step", "Human Checkpoint", "final-step"))
            .addStep(WorkflowStepDefinition.action("final-step", "Post-HITL Action", "post-action-op")
                .withNextStep(null))
            .build();

        runPromise(() -> definitionRegistry.register(def));
    }

    private void registerNoHitlWorkflow(String workflowId) {
        operatorRegistry.register("single-op", (ctx, cfg) -> Promise.of(ctx));
        WorkflowDefinition def = WorkflowDefinition.builder(workflowId, "No HITL Workflow")
            .addStep(WorkflowStepDefinition.action("only-step", "Only Step", "single-op")
                .withNextStep(null))
            .build();
        runPromise(() -> definitionRegistry.register(def));
    }
}
