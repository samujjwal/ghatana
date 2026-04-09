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
@DisplayName("DurableWorkflowEngine Tests")
class DurableWorkflowEngineTest extends EventloopTestBase {

    private DurableWorkflowEngine engine;
    private DurableWorkflowEngine.WorkflowStateStore stateStore;
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

    private WorkflowContext context() {
        return WorkflowContext.forWorkflow("test-workflow", "test-tenant");
    }

    /** Run a workflow and return its final context. */
    private WorkflowContext runWorkflow(String id, List<StepDefinition> steps) {
        return runPromise(() -> engine.submit(id, context(), steps).result());
    }

    @Nested
    @DisplayName("Basic Workflow Execution")
    class BasicExecution {

        @Test
        @DisplayName("should execute single step workflow successfully")
        void shouldExecuteSingleStepSuccessfully() {
            AtomicInteger stepCounter = new AtomicInteger(0);

            StepDefinition step = StepDefinition.of("Validate", ctx -> {
                stepCounter.incrementAndGet();
                return Promise.of(ctx);
            });

            WorkflowContext result = runWorkflow("single-step", List.of(step));

            assertNotNull(result);
            assertEquals(1, stepCounter.get());
        }

        @Test
        @DisplayName("should execute multi-step workflow sequentially")
        void shouldExecuteMultiStepSequentially() {
            List<String> executionOrder = new ArrayList<>();

            StepDefinition step1 = StepDefinition.of("Validate", ctx -> {
                executionOrder.add("validate");
                return Promise.of(ctx);
            });
            StepDefinition step2 = StepDefinition.of("Process", ctx -> {
                executionOrder.add("process");
                return Promise.of(ctx);
            });
            StepDefinition step3 = StepDefinition.of("Notify", ctx -> {
                executionOrder.add("notify");
                return Promise.of(ctx);
            });

            WorkflowContext result = runWorkflow("multi-step", List.of(step1, step2, step3));

            assertNotNull(result);
            assertEquals(List.of("validate", "process", "notify"), executionOrder);
            assertTrue(lifecycleListener.hasEvent(
                    event -> event.phase() == WorkflowLifecycleEvent.Phase.WORKFLOW_STARTED));
            assertTrue(lifecycleListener.hasEvent(
                    event -> event.phase() == WorkflowLifecycleEvent.Phase.WORKFLOW_COMPLETED));
        }

        @Test
        @DisplayName("should preserve context variables through workflow steps")
        void shouldPreserveContextThroughSteps() {
            StepDefinition step1 = StepDefinition.of("SetValue", ctx -> {
                ctx.setVariable("key1", "value1");
                return Promise.of(ctx);
            });
            StepDefinition step2 = StepDefinition.of("VerifyValue", ctx -> {
                Object value = ctx.getVariable("key1");
                assertTrue(value != null && "value1".equals(value.toString()));
                return Promise.of(ctx);
            });

            WorkflowContext result = runWorkflow("context-preservation", List.of(step1, step2));
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("Error Handling and Compensation")
    class ErrorHandlingCompensation {

        @Test
        @DisplayName("should trigger compensation on failure")
        void shouldTriggerCompensationOnFailure() {
            List<String> history = new ArrayList<>();

            StepDefinition step1 = StepDefinition.of("Reserve", ctx -> {
                history.add("reserve");
                return Promise.of(ctx);
            }).withCompensation(ctx -> {
                history.add("unreserve");
                return null;
            });

            StepDefinition step2 = StepDefinition.of("Charge", ctx -> {
                history.add("charge");
                return Promise.ofException(new RuntimeException("Payment failed"));
            });

            assertThatThrownBy(() -> runWorkflow("compensation-test", List.of(step1, step2)))
                    .isInstanceOf(RuntimeException.class);

            assertTrue(history.contains("unreserve"), "Compensation should have been triggered");
            assertTrue(lifecycleListener.hasEvent(
                    event -> event.phase() == WorkflowLifecycleEvent.Phase.WORKFLOW_COMPENSATING));
        }

        @Test
        @DisplayName("should execute compensation in reverse order")
        void shouldExecuteCompensationInReverseOrder() {
            List<String> history = new ArrayList<>();

            StepDefinition step1 = StepDefinition.of("Step1", ctx -> {
                history.add("step1");
                return Promise.of(ctx);
            }).withCompensation(ctx -> {
                history.add("compensate-step1");
                return null;
            });

            StepDefinition step2 = StepDefinition.of("Step2", ctx -> {
                history.add("step2");
                return Promise.of(ctx);
            }).withCompensation(ctx -> {
                history.add("compensate-step2");
                return null;
            });

            StepDefinition step3 = StepDefinition.of("Step3", ctx -> {
                history.add("step3");
                return Promise.ofException(new RuntimeException("Failed"));
            });

            assertThatThrownBy(() -> runWorkflow("reverse-compensation", List.of(step1, step2, step3)))
                    .isInstanceOf(Exception.class);

            int compensateStep2Index = history.indexOf("compensate-step2");
            int compensateStep1Index = history.indexOf("compensate-step1");
            assertTrue(compensateStep2Index >= 0 && compensateStep1Index >= 0,
                    "Both compensation steps should be present");
            assertTrue(compensateStep2Index < compensateStep1Index,
                    "Compensation should be in reverse order");
        }

        @Test
        @DisplayName("should handle missing context variable gracefully")
        void shouldHandleMissingInputGracefully() {
            StepDefinition step = StepDefinition.of("Process", ctx -> {
                Object missing = ctx.getVariable("nonexistent");
                assertNull(missing);
                return Promise.of(ctx);
            });

            WorkflowContext result = runWorkflow("missing-input", List.of(step));
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("Retry Logic")
    class RetryLogic {

        @Test
        @DisplayName("should retry failed step until success")
        void shouldRetryFailedStep() {
            AtomicInteger attempts = new AtomicInteger(0);

            StepDefinition step = StepDefinition.of("RetryableStep", ctx -> {
                int attempt = attempts.incrementAndGet();
                if (attempt < 3) {
                    return Promise.ofException(new RuntimeException("Temporary failure"));
                }
                return Promise.of(ctx);
            }).withRetries(2, Duration.ofMillis(10));

            WorkflowContext result = runWorkflow("retry-workflow", List.of(step));

            assertNotNull(result);
            assertEquals(3, attempts.get(), "Step should have been retried twice before success");
        }

        @Test
        @DisplayName("should fail after maximum retries are exhausted")
        void shouldRespectMaximumRetries() {
            AtomicInteger attempts = new AtomicInteger(0);

            StepDefinition step = StepDefinition.of("TooManyFailures", ctx -> {
                attempts.incrementAndGet();
                return Promise.ofException(new RuntimeException("Always fails"));
            }).withRetries(2, Duration.ofMillis(10));

            assertThatThrownBy(() -> runWorkflow("max-retry-test", List.of(step)))
                    .isInstanceOf(Exception.class);
            assertEquals(3, attempts.get(), "Should have attempted 1 + 2 retries = 3 total");
        }
    }

    @Nested
    @DisplayName("Timeout Handling")
    class TimeoutHandling {

        @Test
        @DisplayName("should timeout long-running step")
        void shouldTimeoutLongRunningStep() {
            StepDefinition step = StepDefinition.of("SlowStep", ctx -> {
                try {
                    Thread.sleep(500);
                    return Promise.of(ctx);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Promise.ofException(e);
                }
            }).withTimeout(Duration.ofMillis(50));

            assertThatThrownBy(() -> runWorkflow("timeout-test", List.of(step)))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should complete within generous timeout for fast step")
        void shouldCompleteWithinTimeout() {
            StepDefinition step = StepDefinition.of("FastStep", ctx -> Promise.of(ctx))
                    .withTimeout(Duration.ofSeconds(10));

            WorkflowContext result = runWorkflow("no-timeout", List.of(step));
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("State Management and Persistence")
    class StateManagement {

        @Test
        @DisplayName("should save workflow state after execution")
        void shouldSaveWorkflowState() {
            StepDefinition step = StepDefinition.of("SaveState", ctx -> Promise.of(ctx));

            runWorkflow("state-save", List.of(step));

            Optional<WorkflowRun> run = stateStore.load("state-save");
            assertTrue(run.isPresent(), "Workflow state should have been persisted");
        }

        @Test
        @DisplayName("should track step execution")
        void shouldUpdateStepStatus() {
            List<String> statusUpdates = new ArrayList<>();

            StepDefinition step1 = StepDefinition.of("Step1", ctx -> {
                statusUpdates.add("step1-running");
                return Promise.of(ctx);
            });
            StepDefinition step2 = StepDefinition.of("Step2", ctx -> {
                statusUpdates.add("step2-running");
                return Promise.of(ctx);
            });

            runWorkflow("status-update", List.of(step1, step2));
            assertFalse(statusUpdates.isEmpty(), "Steps should have executed");
        }
    }

    @Nested
    @DisplayName("Lifecycle Events")
    class LifecycleEvents {

        @Test
        @DisplayName("should emit workflow start event")
        void shouldEmitStartEvent() {
            StepDefinition step = StepDefinition.of("Test", ctx -> Promise.of(ctx));
            runWorkflow("lifecycle-start", List.of(step));
            assertTrue(lifecycleListener.hasEvent(
                    event -> event.phase() == WorkflowLifecycleEvent.Phase.WORKFLOW_STARTED),
                    "Should emit WORKFLOW_STARTED event");
        }

        @Test
        @DisplayName("should emit step start and completion events")
        void shouldEmitStepEvents() {
            StepDefinition step = StepDefinition.of("TestStep", ctx -> Promise.of(ctx));
            runWorkflow("lifecycle-steps", List.of(step));
            assertTrue(lifecycleListener.hasEvent(
                    event -> event.phase() == WorkflowLifecycleEvent.Phase.STEP_STARTED),
                    "Should emit STEP_STARTED event");
            assertTrue(lifecycleListener.hasEvent(
                    event -> event.phase() == WorkflowLifecycleEvent.Phase.STEP_COMPLETED),
                    "Should emit STEP_COMPLETED event");
        }

        @Test
        @DisplayName("should emit workflow completion event")
        void shouldEmitCompletionEvent() {
            StepDefinition step = StepDefinition.of("Test", ctx -> Promise.of(ctx));
            runWorkflow("lifecycle-complete", List.of(step));
            assertTrue(lifecycleListener.hasEvent(
                    event -> event.phase() == WorkflowLifecycleEvent.Phase.WORKFLOW_COMPLETED),
                    "Should emit WORKFLOW_COMPLETED event");
        }
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private static class TestWorkflowLifecycleListener implements WorkflowLifecycleListener {
        private final List<WorkflowLifecycleEvent> events = new ArrayList<>();

        @Override
        public void onEvent(WorkflowLifecycleEvent event) {
            events.add(event);
        }

        public boolean hasEvent(Predicate<WorkflowLifecycleEvent> predicate) {
            return events.stream().anyMatch(predicate);
        }
    }
}
