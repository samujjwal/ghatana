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
    }

    @Test
    void shouldExecuteLinearWorkflow() { // GH-90000
        // Register operators
        operatorRegistry.register("step1-op", (ctx, cfg) -> { // GH-90000
            ctx.put("step1", "done"); // GH-90000
            return Promise.of(ctx); // GH-90000
        });
        operatorRegistry.register("step2-op", (ctx, cfg) -> { // GH-90000
            ctx.put("step2", "done"); // GH-90000
            return Promise.of(ctx); // GH-90000
        });

        // Register definition
        WorkflowDefinition def = WorkflowDefinition.builder("linear-wf", "Linear WF") // GH-90000
            .addStep(WorkflowStepDefinition.action("s1", "Step 1", "step1-op").withNextStep("s2"))
            .addStep(WorkflowStepDefinition.action("s2", "Step 2", "step2-op") // GH-90000
                .withNextStep(null)) // terminal // GH-90000
            .build(); // GH-90000

        runPromise(() -> definitionRegistry.register(def)); // GH-90000

        WorkflowRun result = runPromise(() -> runtime.start( // GH-90000
            "linear-wf", "tenant-1", "corr-1", new java.util.HashMap<>())); // GH-90000

        assertThat(result.status()).isEqualTo(WorkflowRunStatus.COMPLETED); // GH-90000
        assertThat(result.variables()).containsEntry("step1", "done"); // GH-90000
        assertThat(result.variables()).containsEntry("step2", "done"); // GH-90000
    }

    @Test
    void shouldEmitLifecycleEvents() { // GH-90000
        operatorRegistry.register("noop-op", (ctx, cfg) -> Promise.of(ctx)); // GH-90000

        WorkflowDefinition def = WorkflowDefinition.builder("event-wf", "Event WF") // GH-90000
            .addStep(WorkflowStepDefinition.action("s1", "Step 1", "noop-op")) // GH-90000
            .build(); // GH-90000

        runPromise(() -> definitionRegistry.register(def)); // GH-90000

        runPromise(() -> runtime.start( // GH-90000
            "event-wf", "tenant-1", "corr-1", new java.util.HashMap<>())); // GH-90000

        List<WorkflowLifecycleEvent.Phase> phases = capturedEvents.stream() // GH-90000
            .map(WorkflowLifecycleEvent::phase) // GH-90000
            .toList(); // GH-90000

        assertThat(phases).contains( // GH-90000
            WorkflowLifecycleEvent.Phase.WORKFLOW_STARTED,
            WorkflowLifecycleEvent.Phase.STEP_STARTED,
            WorkflowLifecycleEvent.Phase.STEP_COMPLETED,
            WorkflowLifecycleEvent.Phase.WORKFLOW_COMPLETED);
    }

    @Test
    void shouldFailForMissingDefinition() { // GH-90000
        try {
            runPromise(() -> runtime.start( // GH-90000
                "nonexistent", "tenant-1", "corr-1", new java.util.HashMap<>())); // GH-90000
            fail("Should have thrown");
        } catch (Exception e) { // GH-90000
            assertThat(e).isInstanceOf(WorkflowDefinitionException.class); // GH-90000
        }
        clearFatalError(); // GH-90000
    }

    @Test
    void shouldFailForDisabledWorkflow() { // GH-90000
        WorkflowDefinition def = WorkflowDefinition.builder("disabled-wf", "Disabled") // GH-90000
            .addStep(WorkflowStepDefinition.action("s1", "Step", "op")) // GH-90000
            .enabled(false) // GH-90000
            .build(); // GH-90000

        runPromise(() -> definitionRegistry.register(def)); // GH-90000

        try {
            runPromise(() -> runtime.start( // GH-90000
                "disabled-wf", "tenant-1", "corr-1", new java.util.HashMap<>())); // GH-90000
            fail("Should have thrown");
        } catch (Exception e) { // GH-90000
            assertThat(e).isInstanceOf(WorkflowDefinitionException.class); // GH-90000
        }
        clearFatalError(); // GH-90000
    }

    @Test
    void shouldHandleStepFailure() { // GH-90000
        operatorRegistry.register("fail-op", (ctx, cfg) -> { // GH-90000
            throw new RuntimeException("step error");
        });

        WorkflowDefinition def = WorkflowDefinition.builder("fail-wf", "Fail WF") // GH-90000
            .addStep(WorkflowStepDefinition.action("s1", "Fail Step", "fail-op")) // GH-90000
            .build(); // GH-90000

        runPromise(() -> definitionRegistry.register(def)); // GH-90000

        WorkflowRun result = runPromise(() -> runtime.start( // GH-90000
            "fail-wf", "tenant-1", "corr-1", new java.util.HashMap<>())); // GH-90000

        assertThat(result.status()).isEqualTo(WorkflowRunStatus.FAILED); // GH-90000
        assertThat(result.errorMessage()).contains("step error");
    }

    @Test
    void shouldHandleDecisionStep() { // GH-90000
        operatorRegistry.register("high-op", (ctx, cfg) -> { // GH-90000
            ctx.put("path", "high"); // GH-90000
            return Promise.of(ctx); // GH-90000
        });
        operatorRegistry.register("low-op", (ctx, cfg) -> { // GH-90000
            ctx.put("path", "low"); // GH-90000
            return Promise.of(ctx); // GH-90000
        });

        WorkflowExpressionEvaluator evaluator = new WorkflowExpressionEvaluator() { // GH-90000
            @Override
            public Object evaluate(String expression, WorkflowContext context) { // GH-90000
                // Simplistic evaluator for testing
                if (expression.equals("ctx.amount > 1000")) {
                    Object amount = context.getVariables().get("amount");
                    if (amount instanceof Number n) { // GH-90000
                        return n.doubleValue() > 1000; // GH-90000
                    }
                }
                return false;
            }

            @Override
            public boolean evaluateBoolean(String expression, WorkflowContext context) { // GH-90000
                return (boolean) evaluate(expression, context); // GH-90000
            }

            @Override
            public void validate(String expression) { // GH-90000
                // no-op
            }
        };

        DurableWorkflowRuntime runtimeWithCel = DurableWorkflowRuntime.builder() // GH-90000
            .definitionRegistry(definitionRegistry) // GH-90000
            .stateStore(stateStore) // GH-90000
            .operatorRegistry(operatorRegistry) // GH-90000
            .expressionEvaluator(evaluator) // GH-90000
            .build(); // GH-90000

        WorkflowDefinition def = WorkflowDefinition.builder("decision-wf", "Decision WF") // GH-90000
            .addStep(WorkflowStepDefinition.decision( // GH-90000
                "check", "Check Amount", "ctx.amount > 1000", "high", "low"))
            .addStep(WorkflowStepDefinition.action("high", "High Path", "high-op")) // GH-90000
            .addStep(WorkflowStepDefinition.action("low", "Low Path", "low-op")) // GH-90000
            .entryStepId("check")
            .build(); // GH-90000

        runPromise(() -> definitionRegistry.register(def)); // GH-90000

        // High path
        java.util.HashMap<String, Object> highCtx = new java.util.HashMap<>(); // GH-90000
        highCtx.put("amount", 5000); // GH-90000
        WorkflowRun highResult = runPromise(() -> runtimeWithCel.start( // GH-90000
            "decision-wf", "t", "c1", highCtx));

        assertThat(highResult.status()).isEqualTo(WorkflowRunStatus.COMPLETED); // GH-90000
        assertThat(highResult.variables()).containsEntry("path", "high"); // GH-90000
    }

    @Test
    void shouldHandleWaitAndSignal() { // GH-90000
        operatorRegistry.register("post-wait-op", (ctx, cfg) -> { // GH-90000
            ctx.put("resumed", true); // GH-90000
            return Promise.of(ctx); // GH-90000
        });

        WorkflowDefinition def = WorkflowDefinition.builder("wait-wf", "Wait WF") // GH-90000
            .addStep(WorkflowStepDefinition.wait("wait-step", "Wait", Duration.ofHours(1)) // GH-90000
                .withNextStep("post-wait"))
            .addStep(WorkflowStepDefinition.action("post-wait", "Post Wait", "post-wait-op")) // GH-90000
            .build(); // GH-90000

        runPromise(() -> definitionRegistry.register(def)); // GH-90000

        // Start — should suspend on WAIT
        WorkflowRun waiting = runPromise(() -> runtime.start( // GH-90000
            "wait-wf", "tenant-1", "corr-1", new java.util.HashMap<>())); // GH-90000

        assertThat(waiting.status()).isEqualTo(WorkflowRunStatus.WAITING); // GH-90000

        // Signal to resume
        WorkflowRun resumed = runPromise(() -> runtime.signal( // GH-90000
            waiting.runId(), Map.of("signal", "approved"))); // GH-90000

        assertThat(resumed.status()).isEqualTo(WorkflowRunStatus.COMPLETED); // GH-90000
        assertThat(resumed.variables()).containsEntry("resumed", true); // GH-90000
    }

    @Test
    void shouldRetryFailingStepThenSucceed() { // GH-90000
        AtomicInteger attempts = new AtomicInteger(0); // GH-90000
        operatorRegistry.register("flaky-op", (ctx, cfg) -> { // GH-90000
            if (attempts.incrementAndGet() < 3) { // GH-90000
                throw new RuntimeException("transient error");
            }
            ctx.put("success", true); // GH-90000
            return Promise.of(ctx); // GH-90000
        });

        DurableWorkflowRuntime retryRuntime = DurableWorkflowRuntime.builder() // GH-90000
            .definitionRegistry(definitionRegistry) // GH-90000
            .stateStore(new InMemoryWorkflowStateStore()) // GH-90000
            .operatorRegistry(operatorRegistry) // GH-90000
            .defaultMaxRetries(5) // GH-90000
            .build(); // GH-90000

        WorkflowDefinition def = WorkflowDefinition.builder("retry-wf", "Retry WF") // GH-90000
            .addStep(WorkflowStepDefinition.action("s1", "Flaky Step", "flaky-op") // GH-90000
                .withRetries(5, Duration.ofMillis(10))) // GH-90000
            .build(); // GH-90000

        runPromise(() -> definitionRegistry.register(def)); // GH-90000

        WorkflowRun result = runPromise(() -> retryRuntime.start( // GH-90000
            "retry-wf", "tenant-1", "corr-1", new java.util.HashMap<>())); // GH-90000

        assertThat(result.status()).isEqualTo(WorkflowRunStatus.COMPLETED); // GH-90000
        assertThat(result.variables()).containsEntry("success", true); // GH-90000
        assertThat(attempts.get()).isEqualTo(3); // GH-90000
    }
}
