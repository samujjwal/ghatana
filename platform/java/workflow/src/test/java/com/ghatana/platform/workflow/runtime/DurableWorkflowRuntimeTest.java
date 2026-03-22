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
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DurableWorkflowRuntime Tests")
class DurableWorkflowRuntimeTest extends EventloopTestBase {

    private InMemoryWorkflowDefinitionRegistry definitionRegistry;
    private InMemoryWorkflowStateStore stateStore;
    private DefaultStepOperatorRegistry operatorRegistry;
    private List<WorkflowLifecycleEvent> capturedEvents;
    private DurableWorkflowRuntime runtime;

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
    }

    @Test
    void shouldExecuteLinearWorkflow() {
        // Register operators
        operatorRegistry.register("step1-op", (ctx, cfg) -> {
            ctx.put("step1", "done");
            return Promise.of(ctx);
        });
        operatorRegistry.register("step2-op", (ctx, cfg) -> {
            ctx.put("step2", "done");
            return Promise.of(ctx);
        });

        // Register definition
        WorkflowDefinition def = WorkflowDefinition.builder("linear-wf", "Linear WF")
            .addStep(WorkflowStepDefinition.action("s1", "Step 1", "step1-op").withNextStep("s2"))
            .addStep(WorkflowStepDefinition.action("s2", "Step 2", "step2-op")
                .withNextStep(null)) // terminal
            .build();

        runPromise(() -> definitionRegistry.register(def));

        WorkflowRun result = runPromise(() -> runtime.start(
            "linear-wf", "tenant-1", "corr-1", new java.util.HashMap<>()));

        assertThat(result.status()).isEqualTo(WorkflowRunStatus.COMPLETED);
        assertThat(result.variables()).containsEntry("step1", "done");
        assertThat(result.variables()).containsEntry("step2", "done");
    }

    @Test
    void shouldEmitLifecycleEvents() {
        operatorRegistry.register("noop-op", (ctx, cfg) -> Promise.of(ctx));

        WorkflowDefinition def = WorkflowDefinition.builder("event-wf", "Event WF")
            .addStep(WorkflowStepDefinition.action("s1", "Step 1", "noop-op"))
            .build();

        runPromise(() -> definitionRegistry.register(def));

        runPromise(() -> runtime.start(
            "event-wf", "tenant-1", "corr-1", new java.util.HashMap<>()));

        List<WorkflowLifecycleEvent.Phase> phases = capturedEvents.stream()
            .map(WorkflowLifecycleEvent::phase)
            .toList();

        assertThat(phases).contains(
            WorkflowLifecycleEvent.Phase.WORKFLOW_STARTED,
            WorkflowLifecycleEvent.Phase.STEP_STARTED,
            WorkflowLifecycleEvent.Phase.STEP_COMPLETED,
            WorkflowLifecycleEvent.Phase.WORKFLOW_COMPLETED);
    }

    @Test
    void shouldFailForMissingDefinition() {
        try {
            runPromise(() -> runtime.start(
                "nonexistent", "tenant-1", "corr-1", new java.util.HashMap<>()));
            fail("Should have thrown");
        } catch (Exception e) {
            assertThat(e).isInstanceOf(WorkflowDefinitionException.class);
        }
        clearFatalError();
    }

    @Test
    void shouldFailForDisabledWorkflow() {
        WorkflowDefinition def = WorkflowDefinition.builder("disabled-wf", "Disabled")
            .addStep(WorkflowStepDefinition.action("s1", "Step", "op"))
            .enabled(false)
            .build();

        runPromise(() -> definitionRegistry.register(def));

        try {
            runPromise(() -> runtime.start(
                "disabled-wf", "tenant-1", "corr-1", new java.util.HashMap<>()));
            fail("Should have thrown");
        } catch (Exception e) {
            assertThat(e).isInstanceOf(WorkflowDefinitionException.class);
        }
        clearFatalError();
    }

    @Test
    void shouldHandleStepFailure() {
        operatorRegistry.register("fail-op", (ctx, cfg) -> {
            throw new RuntimeException("step error");
        });

        WorkflowDefinition def = WorkflowDefinition.builder("fail-wf", "Fail WF")
            .addStep(WorkflowStepDefinition.action("s1", "Fail Step", "fail-op"))
            .build();

        runPromise(() -> definitionRegistry.register(def));

        WorkflowRun result = runPromise(() -> runtime.start(
            "fail-wf", "tenant-1", "corr-1", new java.util.HashMap<>()));

        assertThat(result.status()).isEqualTo(WorkflowRunStatus.FAILED);
        assertThat(result.errorMessage()).contains("step error");
    }

    @Test
    void shouldHandleDecisionStep() {
        operatorRegistry.register("high-op", (ctx, cfg) -> {
            ctx.put("path", "high");
            return Promise.of(ctx);
        });
        operatorRegistry.register("low-op", (ctx, cfg) -> {
            ctx.put("path", "low");
            return Promise.of(ctx);
        });

        WorkflowExpressionEvaluator evaluator = new WorkflowExpressionEvaluator() {
            @Override
            public Object evaluate(String expression, WorkflowContext context) {
                // Simplistic evaluator for testing
                if (expression.equals("ctx.amount > 1000")) {
                    Object amount = context.getVariables().get("amount");
                    if (amount instanceof Number n) {
                        return n.doubleValue() > 1000;
                    }
                }
                return false;
            }

            @Override
            public boolean evaluateBoolean(String expression, WorkflowContext context) {
                return (boolean) evaluate(expression, context);
            }

            @Override
            public void validate(String expression) {
                // no-op
            }
        };

        DurableWorkflowRuntime runtimeWithCel = DurableWorkflowRuntime.builder()
            .definitionRegistry(definitionRegistry)
            .stateStore(stateStore)
            .operatorRegistry(operatorRegistry)
            .expressionEvaluator(evaluator)
            .build();

        WorkflowDefinition def = WorkflowDefinition.builder("decision-wf", "Decision WF")
            .addStep(WorkflowStepDefinition.decision(
                "check", "Check Amount", "ctx.amount > 1000", "high", "low"))
            .addStep(WorkflowStepDefinition.action("high", "High Path", "high-op"))
            .addStep(WorkflowStepDefinition.action("low", "Low Path", "low-op"))
            .entryStepId("check")
            .build();

        runPromise(() -> definitionRegistry.register(def));

        // High path
        java.util.HashMap<String, Object> highCtx = new java.util.HashMap<>();
        highCtx.put("amount", 5000);
        WorkflowRun highResult = runPromise(() -> runtimeWithCel.start(
            "decision-wf", "t", "c1", highCtx));

        assertThat(highResult.status()).isEqualTo(WorkflowRunStatus.COMPLETED);
        assertThat(highResult.variables()).containsEntry("path", "high");
    }

    @Test
    void shouldHandleWaitAndSignal() {
        operatorRegistry.register("post-wait-op", (ctx, cfg) -> {
            ctx.put("resumed", true);
            return Promise.of(ctx);
        });

        WorkflowDefinition def = WorkflowDefinition.builder("wait-wf", "Wait WF")
            .addStep(WorkflowStepDefinition.wait("wait-step", "Wait", Duration.ofHours(1))
                .withNextStep("post-wait"))
            .addStep(WorkflowStepDefinition.action("post-wait", "Post Wait", "post-wait-op"))
            .build();

        runPromise(() -> definitionRegistry.register(def));

        // Start — should suspend on WAIT
        WorkflowRun waiting = runPromise(() -> runtime.start(
            "wait-wf", "tenant-1", "corr-1", new java.util.HashMap<>()));

        assertThat(waiting.status()).isEqualTo(WorkflowRunStatus.WAITING);

        // Signal to resume
        WorkflowRun resumed = runPromise(() -> runtime.signal(
            waiting.runId(), Map.of("signal", "approved")));

        assertThat(resumed.status()).isEqualTo(WorkflowRunStatus.COMPLETED);
        assertThat(resumed.variables()).containsEntry("resumed", true);
    }

    @Test
    void shouldRetryFailingStepThenSucceed() {
        AtomicInteger attempts = new AtomicInteger(0);
        operatorRegistry.register("flaky-op", (ctx, cfg) -> {
            if (attempts.incrementAndGet() < 3) {
                throw new RuntimeException("transient error");
            }
            ctx.put("success", true);
            return Promise.of(ctx);
        });

        DurableWorkflowRuntime retryRuntime = DurableWorkflowRuntime.builder()
            .definitionRegistry(definitionRegistry)
            .stateStore(new InMemoryWorkflowStateStore())
            .operatorRegistry(operatorRegistry)
            .defaultMaxRetries(5)
            .build();

        WorkflowDefinition def = WorkflowDefinition.builder("retry-wf", "Retry WF")
            .addStep(WorkflowStepDefinition.action("s1", "Flaky Step", "flaky-op")
                .withRetries(5, Duration.ofMillis(10)))
            .build();

        runPromise(() -> definitionRegistry.register(def));

        WorkflowRun result = runPromise(() -> retryRuntime.start(
            "retry-wf", "tenant-1", "corr-1", new java.util.HashMap<>()));

        assertThat(result.status()).isEqualTo(WorkflowRunStatus.COMPLETED);
        assertThat(result.variables()).containsEntry("success", true);
        assertThat(attempts.get()).isEqualTo(3);
    }
}
