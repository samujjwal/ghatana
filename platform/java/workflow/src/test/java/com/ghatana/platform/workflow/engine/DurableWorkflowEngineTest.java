package com.ghatana.platform.workflow.engine;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.workflow.WorkflowContext;
import com.ghatana.platform.workflow.WorkflowLifecycleEvent;
import com.ghatana.platform.workflow.WorkflowLifecycleListener;
import com.ghatana.platform.workflow.engine.DurableWorkflowEngine.InMemoryWorkflowStateStore;
import com.ghatana.platform.workflow.engine.DurableWorkflowEngine.StepDefinition;
import com.ghatana.platform.workflow.engine.DurableWorkflowEngine.WorkflowRun;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Tests for DurableWorkflowEngine ensuring proper step execution, compensation, retry, timeout, and lifecycle events
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("DurableWorkflowEngine Tests [GH-90000]")
class DurableWorkflowEngineTest extends EventloopTestBase {

    private DurableWorkflowEngine engine;
    private DurableWorkflowEngine.WorkflowStateStore stateStore;
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

    private WorkflowContext context() { // GH-90000
        return WorkflowContext.forWorkflow("test-workflow", "test-tenant"); // GH-90000
    }

    /** Run a workflow and return its final context. */
    private WorkflowContext runWorkflow(String id, List<StepDefinition> steps) { // GH-90000
        return runPromise(() -> engine.submit(id, context(), steps).result()); // GH-90000
    }

    @Nested
    @DisplayName("Basic Workflow Execution [GH-90000]")
    class BasicExecution {

        @Test
        @DisplayName("should execute single step workflow successfully [GH-90000]")
        void shouldExecuteSingleStepSuccessfully() { // GH-90000
            AtomicInteger stepCounter = new AtomicInteger(0); // GH-90000

            StepDefinition step = StepDefinition.of("Validate", ctx -> { // GH-90000
                stepCounter.incrementAndGet(); // GH-90000
                return Promise.of(ctx); // GH-90000
            });

            WorkflowContext result = runWorkflow("single-step", List.of(step)); // GH-90000

            assertNotNull(result); // GH-90000
            assertEquals(1, stepCounter.get()); // GH-90000
        }

        @Test
        @DisplayName("should execute multi-step workflow sequentially [GH-90000]")
        void shouldExecuteMultiStepSequentially() { // GH-90000
            List<String> executionOrder = new ArrayList<>(); // GH-90000

            StepDefinition step1 = StepDefinition.of("Validate", ctx -> { // GH-90000
                executionOrder.add("validate [GH-90000]");
                return Promise.of(ctx); // GH-90000
            });
            StepDefinition step2 = StepDefinition.of("Process", ctx -> { // GH-90000
                executionOrder.add("process [GH-90000]");
                return Promise.of(ctx); // GH-90000
            });
            StepDefinition step3 = StepDefinition.of("Notify", ctx -> { // GH-90000
                executionOrder.add("notify [GH-90000]");
                return Promise.of(ctx); // GH-90000
            });

            WorkflowContext result = runWorkflow("multi-step", List.of(step1, step2, step3)); // GH-90000

            assertNotNull(result); // GH-90000
            assertEquals(List.of("validate", "process", "notify"), executionOrder); // GH-90000
            assertTrue(lifecycleListener.hasEvent( // GH-90000
                    event -> event.phase() == WorkflowLifecycleEvent.Phase.WORKFLOW_STARTED)); // GH-90000
            assertTrue(lifecycleListener.hasEvent( // GH-90000
                    event -> event.phase() == WorkflowLifecycleEvent.Phase.WORKFLOW_COMPLETED)); // GH-90000
        }

        @Test
        @DisplayName("should preserve context variables through workflow steps [GH-90000]")
        void shouldPreserveContextThroughSteps() { // GH-90000
            StepDefinition step1 = StepDefinition.of("SetValue", ctx -> { // GH-90000
                ctx.setVariable("key1", "value1"); // GH-90000
                return Promise.of(ctx); // GH-90000
            });
            StepDefinition step2 = StepDefinition.of("VerifyValue", ctx -> { // GH-90000
                Object value = ctx.getVariable("key1 [GH-90000]");
                assertTrue(value != null && "value1".equals(value.toString())); // GH-90000
                return Promise.of(ctx); // GH-90000
            });

            WorkflowContext result = runWorkflow("context-preservation", List.of(step1, step2)); // GH-90000
            assertNotNull(result); // GH-90000
        }
    }

    @Nested
    @DisplayName("Error Handling and Compensation [GH-90000]")
    class ErrorHandlingCompensation {

        @Test
        @DisplayName("should trigger compensation on failure [GH-90000]")
        void shouldTriggerCompensationOnFailure() { // GH-90000
            List<String> history = new ArrayList<>(); // GH-90000

            StepDefinition step1 = StepDefinition.of("Reserve", ctx -> { // GH-90000
                history.add("reserve [GH-90000]");
                return Promise.of(ctx); // GH-90000
            }).withCompensation(ctx -> { // GH-90000
                history.add("unreserve [GH-90000]");
                return null;
            });

            StepDefinition step2 = StepDefinition.of("Charge", ctx -> { // GH-90000
                history.add("charge [GH-90000]");
                return Promise.ofException(new RuntimeException("Payment failed [GH-90000]"));
            });

            assertThatThrownBy(() -> runWorkflow("compensation-test", List.of(step1, step2))) // GH-90000
                    .isInstanceOf(RuntimeException.class); // GH-90000

            assertTrue(history.contains("unreserve [GH-90000]"), "Compensation should have been triggered");
            assertTrue(lifecycleListener.hasEvent( // GH-90000
                    event -> event.phase() == WorkflowLifecycleEvent.Phase.WORKFLOW_COMPENSATING)); // GH-90000
        }

        @Test
        @DisplayName("should execute compensation in reverse order [GH-90000]")
        void shouldExecuteCompensationInReverseOrder() { // GH-90000
            List<String> history = new ArrayList<>(); // GH-90000

            StepDefinition step1 = StepDefinition.of("Step1", ctx -> { // GH-90000
                history.add("step1 [GH-90000]");
                return Promise.of(ctx); // GH-90000
            }).withCompensation(ctx -> { // GH-90000
                history.add("compensate-step1 [GH-90000]");
                return null;
            });

            StepDefinition step2 = StepDefinition.of("Step2", ctx -> { // GH-90000
                history.add("step2 [GH-90000]");
                return Promise.of(ctx); // GH-90000
            }).withCompensation(ctx -> { // GH-90000
                history.add("compensate-step2 [GH-90000]");
                return null;
            });

            StepDefinition step3 = StepDefinition.of("Step3", ctx -> { // GH-90000
                history.add("step3 [GH-90000]");
                return Promise.ofException(new RuntimeException("Failed [GH-90000]"));
            });

            assertThatThrownBy(() -> runWorkflow("reverse-compensation", List.of(step1, step2, step3))) // GH-90000
                    .isInstanceOf(Exception.class); // GH-90000

            int compensateStep2Index = history.indexOf("compensate-step2 [GH-90000]");
            int compensateStep1Index = history.indexOf("compensate-step1 [GH-90000]");
            assertTrue(compensateStep2Index >= 0 && compensateStep1Index >= 0, // GH-90000
                    "Both compensation steps should be present");
            assertTrue(compensateStep2Index < compensateStep1Index, // GH-90000
                    "Compensation should be in reverse order");
        }

        @Test
        @DisplayName("should handle missing context variable gracefully [GH-90000]")
        void shouldHandleMissingInputGracefully() { // GH-90000
            StepDefinition step = StepDefinition.of("Process", ctx -> { // GH-90000
                Object missing = ctx.getVariable("nonexistent [GH-90000]");
                assertNull(missing); // GH-90000
                return Promise.of(ctx); // GH-90000
            });

            WorkflowContext result = runWorkflow("missing-input", List.of(step)); // GH-90000
            assertNotNull(result); // GH-90000
        }
    }

    @Nested
    @DisplayName("Retry Logic [GH-90000]")
    class RetryLogic {

        @Test
        @DisplayName("should retry failed step until success [GH-90000]")
        void shouldRetryFailedStep() { // GH-90000
            AtomicInteger attempts = new AtomicInteger(0); // GH-90000

            StepDefinition step = StepDefinition.of("RetryableStep", ctx -> { // GH-90000
                int attempt = attempts.incrementAndGet(); // GH-90000
                if (attempt < 3) { // GH-90000
                    return Promise.ofException(new RuntimeException("Temporary failure [GH-90000]"));
                }
                return Promise.of(ctx); // GH-90000
            }).withRetries(2, Duration.ofMillis(10)); // GH-90000

            WorkflowContext result = runWorkflow("retry-workflow", List.of(step)); // GH-90000

            assertNotNull(result); // GH-90000
            assertEquals(3, attempts.get(), "Step should have been retried twice before success"); // GH-90000
        }

        @Test
        @DisplayName("should fail after maximum retries are exhausted [GH-90000]")
        void shouldRespectMaximumRetries() { // GH-90000
            AtomicInteger attempts = new AtomicInteger(0); // GH-90000

            StepDefinition step = StepDefinition.of("TooManyFailures", ctx -> { // GH-90000
                attempts.incrementAndGet(); // GH-90000
                return Promise.ofException(new RuntimeException("Always fails [GH-90000]"));
            }).withRetries(2, Duration.ofMillis(10)); // GH-90000

            assertThatThrownBy(() -> runWorkflow("max-retry-test", List.of(step))) // GH-90000
                    .isInstanceOf(Exception.class); // GH-90000
            assertEquals(3, attempts.get(), "Should have attempted 1 + 2 retries = 3 total"); // GH-90000
        }
    }

    @Nested
    @DisplayName("Timeout Handling [GH-90000]")
    class TimeoutHandling {

        @Test
        @DisplayName("should timeout long-running step [GH-90000]")
        void shouldTimeoutLongRunningStep() { // GH-90000
            StepDefinition step = StepDefinition.of("SlowStep", ctx -> { // GH-90000
                try {
                    Thread.sleep(500); // GH-90000
                    return Promise.of(ctx); // GH-90000
                } catch (InterruptedException e) { // GH-90000
                    Thread.currentThread().interrupt(); // GH-90000
                    return Promise.ofException(e); // GH-90000
                }
            }).withTimeout(Duration.ofMillis(50)); // GH-90000

            assertThatThrownBy(() -> runWorkflow("timeout-test", List.of(step))) // GH-90000
                    .isInstanceOf(Exception.class); // GH-90000
        }

        @Test
        @DisplayName("should complete within generous timeout for fast step [GH-90000]")
        void shouldCompleteWithinTimeout() { // GH-90000
            StepDefinition step = StepDefinition.of("FastStep", ctx -> Promise.of(ctx)) // GH-90000
                    .withTimeout(Duration.ofSeconds(10)); // GH-90000

            WorkflowContext result = runWorkflow("no-timeout", List.of(step)); // GH-90000
            assertNotNull(result); // GH-90000
        }
    }

    @Nested
    @DisplayName("State Management and Persistence [GH-90000]")
    class StateManagement {

        @Test
        @DisplayName("should save workflow state after execution [GH-90000]")
        void shouldSaveWorkflowState() { // GH-90000
            StepDefinition step = StepDefinition.of("SaveState", ctx -> Promise.of(ctx)); // GH-90000

            runWorkflow("state-save", List.of(step)); // GH-90000

            Optional<WorkflowRun> run = stateStore.load("state-save [GH-90000]");
            assertTrue(run.isPresent(), "Workflow state should have been persisted"); // GH-90000
        }

        @Test
        @DisplayName("should track step execution [GH-90000]")
        void shouldUpdateStepStatus() { // GH-90000
            List<String> statusUpdates = new ArrayList<>(); // GH-90000

            StepDefinition step1 = StepDefinition.of("Step1", ctx -> { // GH-90000
                statusUpdates.add("step1-running [GH-90000]");
                return Promise.of(ctx); // GH-90000
            });
            StepDefinition step2 = StepDefinition.of("Step2", ctx -> { // GH-90000
                statusUpdates.add("step2-running [GH-90000]");
                return Promise.of(ctx); // GH-90000
            });

            runWorkflow("status-update", List.of(step1, step2)); // GH-90000
            assertFalse(statusUpdates.isEmpty(), "Steps should have executed"); // GH-90000
        }
    }

    @Nested
    @DisplayName("Lifecycle Events [GH-90000]")
    class LifecycleEvents {

        @Test
        @DisplayName("should emit workflow start event [GH-90000]")
        void shouldEmitStartEvent() { // GH-90000
            StepDefinition step = StepDefinition.of("Test", ctx -> Promise.of(ctx)); // GH-90000
            runWorkflow("lifecycle-start", List.of(step)); // GH-90000
            assertTrue(lifecycleListener.hasEvent( // GH-90000
                    event -> event.phase() == WorkflowLifecycleEvent.Phase.WORKFLOW_STARTED), // GH-90000
                    "Should emit WORKFLOW_STARTED event");
        }

        @Test
        @DisplayName("should emit step start and completion events [GH-90000]")
        void shouldEmitStepEvents() { // GH-90000
            StepDefinition step = StepDefinition.of("TestStep", ctx -> Promise.of(ctx)); // GH-90000
            runWorkflow("lifecycle-steps", List.of(step)); // GH-90000
            assertTrue(lifecycleListener.hasEvent( // GH-90000
                    event -> event.phase() == WorkflowLifecycleEvent.Phase.STEP_STARTED), // GH-90000
                    "Should emit STEP_STARTED event");
            assertTrue(lifecycleListener.hasEvent( // GH-90000
                    event -> event.phase() == WorkflowLifecycleEvent.Phase.STEP_COMPLETED), // GH-90000
                    "Should emit STEP_COMPLETED event");
        }

        @Test
        @DisplayName("should emit workflow completion event [GH-90000]")
        void shouldEmitCompletionEvent() { // GH-90000
            StepDefinition step = StepDefinition.of("Test", ctx -> Promise.of(ctx)); // GH-90000
            runWorkflow("lifecycle-complete", List.of(step)); // GH-90000
            assertTrue(lifecycleListener.hasEvent( // GH-90000
                    event -> event.phase() == WorkflowLifecycleEvent.Phase.WORKFLOW_COMPLETED), // GH-90000
                    "Should emit WORKFLOW_COMPLETED event");
        }
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private static class TestWorkflowLifecycleListener implements WorkflowLifecycleListener {
        private final List<WorkflowLifecycleEvent> events = new ArrayList<>(); // GH-90000

        @Override
        public void onEvent(WorkflowLifecycleEvent event) { // GH-90000
            events.add(event); // GH-90000
        }

        public boolean hasEvent(Predicate<WorkflowLifecycleEvent> predicate) { // GH-90000
            return events.stream().anyMatch(predicate); // GH-90000
        }
    }
}
