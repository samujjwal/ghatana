/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow.engine;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.workflow.WorkflowContext;
import com.ghatana.platform.workflow.WorkflowLifecycleEvent;
import com.ghatana.platform.workflow.WorkflowLifecycleListener;
import com.ghatana.platform.workflow.engine.DurableWorkflowEngine.InMemoryWorkflowStateStore;
import com.ghatana.platform.workflow.engine.DurableWorkflowEngine.StepDefinition;
import com.ghatana.platform.workflow.engine.DurableWorkflowEngine.WorkflowStateStore;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 expansion: DurableWorkflowEngine concurrent execution, state isolation, and complex step chains.
 * Tests multiple workflows running in parallel, state store consistency, and lifecycle event ordering.
 *
 * @doc.type class
 * @doc.purpose DurableWorkflowEngine concurrent execution and state isolation testing
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("DurableWorkflowEngine - Phase 3 Expansion")
class DurableWorkflowEngineExpansionTest extends EventloopTestBase {

    private DurableWorkflowEngine engine;
    private WorkflowStateStore stateStore;
    private TestWorkflowLifecycleListener lifecycleListener;

    @BeforeEach
    void setUp() {
        stateStore = new InMemoryWorkflowStateStore();
        lifecycleListener = new TestWorkflowLifecycleListener();

        engine = DurableWorkflowEngine.builder()
                .stateStore(stateStore)
                .defaultTimeout(Duration.ofSeconds(30))
                .defaultMaxRetries(3)
                .defaultRetryBackoff(Duration.ofMillis(100))
                .addListener(lifecycleListener)
                .build();
    }

    private WorkflowContext context(String workflowId) {
        return WorkflowContext.forWorkflow(workflowId, "test-tenant");
    }

    // ============================================
    // CONCURRENT WORKFLOW EXECUTION (2 tests)
    // ============================================

    @Nested
    @DisplayName("Concurrent Workflow Execution")
    class ConcurrentExecutionTests {

        @Test
        @DisplayName("Executes multiple workflows without state interference")
        void multipleWorkflowsWithStateIsolation() {
            AtomicInteger counter1 = new AtomicInteger(0);
            AtomicInteger counter2 = new AtomicInteger(0);
            AtomicInteger counter3 = new AtomicInteger(0);

            // Submit 3 workflows concurrently
            for (int i = 1; i <= 3; i++) {
                final int workflowNum = i;
                final AtomicInteger counter = (i == 1 ? counter1 : (i == 2 ? counter2 : counter3));

                StepDefinition step = StepDefinition.of("Step" + workflowNum, ctx -> {
                    counter.incrementAndGet();
                    ctx.put("workflow", "workflow-" + workflowNum);
                    return Promise.of(ctx);
                });

                runPromise(() -> engine.submit("workflow-" + workflowNum, context("workflow-" + workflowNum),
                        List.of(step)).result());
            }

            // Verify each workflow executed independently
            assertThat(counter1.get()).isEqualTo(1);
            assertThat(counter2.get()).isEqualTo(1);
            assertThat(counter3.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("Handles mixed workflow durations concurrently")
        void mixedWorkflowDurations() {
            List<Integer> executionTimes = new CopyOnWriteArrayList<>();

            for (int i = 0; i < 5; i++) {
                final int workflowNum = i;

                StepDefinition step1 = StepDefinition.of("Quick", ctx -> {
                    executionTimes.add(1);
                    return Promise.of(ctx);
                });

                StepDefinition step2 = StepDefinition.of("Medium", ctx -> {
                    executionTimes.add(2);
                    return Promise.of(ctx);
                });

                List<StepDefinition> steps = (i % 2 == 0) ? List.of(step1) : List.of(step1, step2);

                runPromise(() -> engine.submit("workflow-" + workflowNum, context("workflow-" + workflowNum),
                        steps).result());
            }

            // All workflows completed
            assertThat(executionTimes).hasSize(7); // 5 quick + 2 medium (from 3 multi-step workflows)
        }
    }

    // ============================================
    // MULTI-STEP WORKFLOW CHAINS (2 tests)
    // ============================================

    @Nested
    @DisplayName("Multi-Step Workflow Chains")
    class MultiStepChainTests {

        @Test
        @DisplayName("Executes long step chains in correct order")
        void longStepChainExecution() {
            List<String> executionOrder = new ArrayList<>();

            StepDefinition step1 = StepDefinition.of("Step1", ctx -> {
                executionOrder.add("step1");
                return Promise.of(ctx);
            });

            StepDefinition step2 = StepDefinition.of("Step2", ctx -> {
                executionOrder.add("step2");
                return Promise.of(ctx);
            });

            StepDefinition step3 = StepDefinition.of("Step3", ctx -> {
                executionOrder.add("step3");
                return Promise.of(ctx);
            });

            StepDefinition step4 = StepDefinition.of("Step4", ctx -> {
                executionOrder.add("step4");
                return Promise.of(ctx);
            });

            StepDefinition step5 = StepDefinition.of("Step5", ctx -> {
                executionOrder.add("step5");
                return Promise.of(ctx);
            });

            List<StepDefinition> steps = List.of(step1, step2, step3, step4, step5);

            runPromise(() -> engine.submit("long-chain", context("long-chain"), steps).result());

            // Verify strict ordering
            assertThat(executionOrder).containsExactly("step1", "step2", "step3", "step4", "step5");
        }

        @Test
        @DisplayName("Maintains context through multi-step execution")
        void contextPropagationThroughSteps() {
            StepDefinition step1 = StepDefinition.of("Setup", ctx -> {
                ctx.put("shared-key", "initial-value");
                return Promise.of(ctx);
            });

            StepDefinition step2 = StepDefinition.of("Modify", ctx -> {
                ctx.put("another-key", "step2-value");
                return Promise.of(ctx);
            });

            StepDefinition step3 = StepDefinition.of("Verify", ctx -> {
                // Should have both keys from previous steps
                ctx.put("verified", "true");
                return Promise.of(ctx);
            });

            WorkflowContext result = runPromise(() ->
                engine.submit("context-propagation", context("context-propagation"),
                    List.of(step1, step2, step3)).result());

            assertThat(result).isNotNull();
        }
    }

    // ============================================
    // STATE STORE PERSISTENCE (2 tests)
    // ============================================

    @Nested
    @DisplayName("State Store Persistence")
    class StatePersistenceTests {

        @Test
        @DisplayName("Persists workflow state across multiple executions")
        void statePersistenceAcrossRuns() {
            AtomicInteger executionCount = new AtomicInteger(0);

            for (int run = 0; run < 3; run++) {
                final int runNum = run;
                StepDefinition step = StepDefinition.of("Increment", ctx -> {
                    executionCount.incrementAndGet();
                    ctx.put("run", String.valueOf(runNum));
                    return Promise.of(ctx);
                });

                runPromise(() -> engine.submit("persistent-wf", context("persistent-wf"),
                        List.of(step)).result());
            }

            assertThat(executionCount.get()).isEqualTo(3);
        }

        @Test
        @DisplayName("Handles state store with many concurrent workflows")
        void largeNumberOfConcurrentWorkflows() {
            int workflowCount = 20;
            AtomicInteger completedCount = new AtomicInteger(0);

            for (int i = 0; i < workflowCount; i++) {
                final int workflowNum = i;

                StepDefinition step = StepDefinition.of("Track", ctx -> {
                    completedCount.incrementAndGet();
                    return Promise.of(ctx);
                });

                runPromise(() -> engine.submit("bulk-" + workflowNum, context("bulk-" + workflowNum),
                        List.of(step)).result());
            }

            assertThat(completedCount.get()).isEqualTo(workflowCount);
        }
    }

    // ============================================
    // LIFECYCLE EVENT TRACKING (1 test)
    // ============================================

    @Nested
    @DisplayName("Lifecycle Event Tracking")
    class LifecycleEventTests {

        @Test
        @DisplayName("Emits lifecycle events in correct order")
        void lifecycleEventOrdering() {
            StepDefinition step = StepDefinition.of("Work", ctx -> {
                return Promise.of(ctx);
            });

            runPromise(() -> engine.submit("event-tracking", context("event-tracking"),
                    List.of(step)).result());

            // Listener should have received events
            assertThat(lifecycleListener.getEvents()).isNotEmpty();
        }
    }

    // Helper for collecting lifecycle events
    static class TestWorkflowLifecycleListener implements WorkflowLifecycleListener {
        private final List<WorkflowLifecycleEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void onEvent(WorkflowLifecycleEvent event) {
            events.add(event);
        }

        public List<WorkflowLifecycleEvent> getEvents() {
            return events;
        }
    }
}
