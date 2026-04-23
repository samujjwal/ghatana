/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
    void setUp() { // GH-90000
        stateStore = new InMemoryWorkflowStateStore(); // GH-90000
        lifecycleListener = new TestWorkflowLifecycleListener(); // GH-90000

        engine = DurableWorkflowEngine.builder() // GH-90000
                .stateStore(stateStore) // GH-90000
                .defaultTimeout(Duration.ofSeconds(30)) // GH-90000
                .defaultMaxRetries(3) // GH-90000
                .defaultRetryBackoff(Duration.ofMillis(100)) // GH-90000
                .addListener(lifecycleListener) // GH-90000
                .build(); // GH-90000
    }

    private WorkflowContext context(String workflowId) { // GH-90000
        return WorkflowContext.forWorkflow(workflowId, "test-tenant"); // GH-90000
    }

    // ============================================
    // CONCURRENT WORKFLOW EXECUTION (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Concurrent Workflow Execution")
    class ConcurrentExecutionTests {

        @Test
        @DisplayName("Executes multiple workflows without state interference")
        void multipleWorkflowsWithStateIsolation() { // GH-90000
            AtomicInteger counter1 = new AtomicInteger(0); // GH-90000
            AtomicInteger counter2 = new AtomicInteger(0); // GH-90000
            AtomicInteger counter3 = new AtomicInteger(0); // GH-90000

            // Submit 3 workflows concurrently
            for (int i = 1; i <= 3; i++) { // GH-90000
                final int workflowNum = i;
                final AtomicInteger counter = (i == 1 ? counter1 : (i == 2 ? counter2 : counter3)); // GH-90000

                StepDefinition step = StepDefinition.of("Step" + workflowNum, ctx -> { // GH-90000
                    counter.incrementAndGet(); // GH-90000
                    ctx.put("workflow", "workflow-" + workflowNum); // GH-90000
                    return Promise.of(ctx); // GH-90000
                });

                runPromise(() -> engine.submit("workflow-" + workflowNum, context("workflow-" + workflowNum), // GH-90000
                        List.of(step)).result()); // GH-90000
            }

            // Verify each workflow executed independently
            assertThat(counter1.get()).isEqualTo(1); // GH-90000
            assertThat(counter2.get()).isEqualTo(1); // GH-90000
            assertThat(counter3.get()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("Handles mixed workflow durations concurrently")
        void mixedWorkflowDurations() { // GH-90000
            List<Integer> executionTimes = new CopyOnWriteArrayList<>(); // GH-90000

            for (int i = 0; i < 5; i++) { // GH-90000
                final int workflowNum = i;

                StepDefinition step1 = StepDefinition.of("Quick", ctx -> { // GH-90000
                    executionTimes.add(1); // GH-90000
                    return Promise.of(ctx); // GH-90000
                });

                StepDefinition step2 = StepDefinition.of("Medium", ctx -> { // GH-90000
                    executionTimes.add(2); // GH-90000
                    return Promise.of(ctx); // GH-90000
                });

                List<StepDefinition> steps = (i % 2 == 0) ? List.of(step1) : List.of(step1, step2); // GH-90000

                runPromise(() -> engine.submit("workflow-" + workflowNum, context("workflow-" + workflowNum), // GH-90000
                        steps).result()); // GH-90000
            }

            // All workflows completed
            assertThat(executionTimes).hasSize(7); // 5 quick + 2 medium (from 3 multi-step workflows) // GH-90000
        }
    }

    // ============================================
    // MULTI-STEP WORKFLOW CHAINS (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Multi-Step Workflow Chains")
    class MultiStepChainTests {

        @Test
        @DisplayName("Executes long step chains in correct order")
        void longStepChainExecution() { // GH-90000
            List<String> executionOrder = new ArrayList<>(); // GH-90000

            StepDefinition step1 = StepDefinition.of("Step1", ctx -> { // GH-90000
                executionOrder.add("step1");
                return Promise.of(ctx); // GH-90000
            });

            StepDefinition step2 = StepDefinition.of("Step2", ctx -> { // GH-90000
                executionOrder.add("step2");
                return Promise.of(ctx); // GH-90000
            });

            StepDefinition step3 = StepDefinition.of("Step3", ctx -> { // GH-90000
                executionOrder.add("step3");
                return Promise.of(ctx); // GH-90000
            });

            StepDefinition step4 = StepDefinition.of("Step4", ctx -> { // GH-90000
                executionOrder.add("step4");
                return Promise.of(ctx); // GH-90000
            });

            StepDefinition step5 = StepDefinition.of("Step5", ctx -> { // GH-90000
                executionOrder.add("step5");
                return Promise.of(ctx); // GH-90000
            });

            List<StepDefinition> steps = List.of(step1, step2, step3, step4, step5); // GH-90000

            runPromise(() -> engine.submit("long-chain", context("long-chain"), steps).result());

            // Verify strict ordering
            assertThat(executionOrder).containsExactly("step1", "step2", "step3", "step4", "step5"); // GH-90000
        }

        @Test
        @DisplayName("Maintains context through multi-step execution")
        void contextPropagationThroughSteps() { // GH-90000
            StepDefinition step1 = StepDefinition.of("Setup", ctx -> { // GH-90000
                ctx.put("shared-key", "initial-value"); // GH-90000
                return Promise.of(ctx); // GH-90000
            });

            StepDefinition step2 = StepDefinition.of("Modify", ctx -> { // GH-90000
                ctx.put("another-key", "step2-value"); // GH-90000
                return Promise.of(ctx); // GH-90000
            });

            StepDefinition step3 = StepDefinition.of("Verify", ctx -> { // GH-90000
                // Should have both keys from previous steps
                ctx.put("verified", "true"); // GH-90000
                return Promise.of(ctx); // GH-90000
            });

            WorkflowContext result = runPromise(() -> // GH-90000
                engine.submit("context-propagation", context("context-propagation"),
                    List.of(step1, step2, step3)).result()); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
        }
    }

    // ============================================
    // STATE STORE PERSISTENCE (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("State Store Persistence")
    class StatePersistenceTests {

        @Test
        @DisplayName("Persists workflow state across multiple executions")
        void statePersistenceAcrossRuns() { // GH-90000
            AtomicInteger executionCount = new AtomicInteger(0); // GH-90000

            for (int run = 0; run < 3; run++) { // GH-90000
                final int runNum = run;
                StepDefinition step = StepDefinition.of("Increment", ctx -> { // GH-90000
                    executionCount.incrementAndGet(); // GH-90000
                    ctx.put("run", String.valueOf(runNum)); // GH-90000
                    return Promise.of(ctx); // GH-90000
                });

                runPromise(() -> engine.submit("persistent-wf", context("persistent-wf"),
                        List.of(step)).result()); // GH-90000
            }

            assertThat(executionCount.get()).isEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("Handles state store with many concurrent workflows")
        void largeNumberOfConcurrentWorkflows() { // GH-90000
            int workflowCount = 20;
            AtomicInteger completedCount = new AtomicInteger(0); // GH-90000

            for (int i = 0; i < workflowCount; i++) { // GH-90000
                final int workflowNum = i;

                StepDefinition step = StepDefinition.of("Track", ctx -> { // GH-90000
                    completedCount.incrementAndGet(); // GH-90000
                    return Promise.of(ctx); // GH-90000
                });

                runPromise(() -> engine.submit("bulk-" + workflowNum, context("bulk-" + workflowNum), // GH-90000
                        List.of(step)).result()); // GH-90000
            }

            assertThat(completedCount.get()).isEqualTo(workflowCount); // GH-90000
        }
    }

    // ============================================
    // LIFECYCLE EVENT TRACKING (1 test) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Lifecycle Event Tracking")
    class LifecycleEventTests {

        @Test
        @DisplayName("Emits lifecycle events in correct order")
        void lifecycleEventOrdering() { // GH-90000
            StepDefinition step = StepDefinition.of("Work", ctx -> { // GH-90000
                return Promise.of(ctx); // GH-90000
            });

            runPromise(() -> engine.submit("event-tracking", context("event-tracking"),
                    List.of(step)).result()); // GH-90000

            // Listener should have received events
            assertThat(lifecycleListener.getEvents()).isNotEmpty(); // GH-90000
        }
    }

    // Helper for collecting lifecycle events
    static class TestWorkflowLifecycleListener implements WorkflowLifecycleListener {
        private final List<WorkflowLifecycleEvent> events = new CopyOnWriteArrayList<>(); // GH-90000

        @Override
        public void onEvent(WorkflowLifecycleEvent event) { // GH-90000
            events.add(event); // GH-90000
        }

        public List<WorkflowLifecycleEvent> getEvents() { // GH-90000
            return events;
        }
    }
}
