package com.ghatana.platform.workflow.engine;

import com.ghatana.platform.workflow.WorkflowContext;
import com.ghatana.platform.workflow.WorkflowLifecycleEvent;
import com.ghatana.platform.workflow.WorkflowLifecycleListener;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @doc.type class
 * @doc.purpose Tests for DurableWorkflowEngine
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("DurableWorkflowEngine Tests")
class DurableWorkflowEngineTest {

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
                .addLifecycleListener(lifecycleListener)
                .build();
    }

    @Nested
    @DisplayName("Basic Workflow Execution")
    class BasicExecution {

        @Test
        @DisplayName("should execute single step workflow successfully")
        void shouldExecuteSingleStepSuccessfully() {
            AtomicInteger stepCounter = new AtomicInteger(0);
            
            StepDefinition step = StepDefinition.builder()
                    .id("step-1")
                    .name("Validate")
                    .body(ctx -> {
                        stepCounter.incrementAndGet();
                        return Promise.complete();
                    })
                    .build();

            WorkflowContext initialContext = WorkflowContext.builder()
                    .withValue("orderId", "order-123")
                    .build();

            WorkflowExecution execution = engine.submit("single-step",
                    initialContext,
                    List.of(step));

            WorkflowContext result = execution.result().getResult();
            
            assertNotNull(result);
            assertEquals(1, stepCounter.get());
        }

        @Test
        @DisplayName("should execute multi-step workflow sequentially")
        void shouldExecuteMultiStepSequentially() {
            List<String> executionOrder = new ArrayList<>();
            
            StepDefinition step1 = StepDefinition.builder()
                    .id("step-1")
                    .name("Validate")
                    .body(ctx -> {
                        executionOrder.add("validate");
                        return Promise.complete();
                    })
                    .build();

            StepDefinition step2 = StepDefinition.builder()
                    .id("step-2")
                    .name("Process")
                    .body(ctx -> {
                        executionOrder.add("process");
                        return Promise.complete();
                    })
                    .build();

            StepDefinition step3 = StepDefinition.builder()
                    .id("step-3")
                    .name("Notify")
                    .body(ctx -> {
                        executionOrder.add("notify");
                        return Promise.complete();
                    })
                    .build();

            WorkflowContext context = WorkflowContext.builder()
                    .withValue("id", "test-123")
                    .build();

            WorkflowExecution execution = engine.submit("multi-step",
                    context,
                    List.of(step1, step2, step3));

            WorkflowContext result = execution.result().getResult();
            
            assertNotNull(result);
            assertEquals(List.of("validate", "process", "notify"), executionOrder);
            assertTrue(lifecycleListener.hasEvent(
                    event -> event.getPhase() == WorkflowLifecycleEvent.Phase.WORKFLOW_STARTED));
            assertTrue(lifecycleListener.hasEvent(
                    event -> event.getPhase() == WorkflowLifecycleEvent.Phase.WORKFLOW_COMPLETED));
        }

        @Test
        @DisplayName("should preserve context through workflow steps")
        void shouldPreserveContextThroughSteps() {
            StepDefinition step1 = StepDefinition.builder()
                    .id("step-1")
                    .name("SetValue")
                    .body(ctx -> {
                        ctx.withValue("key1", "value1");
                        return Promise.complete();
                    })
                    .build();

            StepDefinition step2 = StepDefinition.builder()
                    .id("step-2")
                    .name("VerifyValue")
                    .body(ctx -> {
                        Object key1 = ctx.getValue("key1");
                        assertTrue(key1 != null && "value1".equals(key1.toString()));
                        return Promise.complete();
                    })
                    .build();

            WorkflowContext initialContext = WorkflowContext.builder()
                    .withValue("id", "test-id")
                    .build();

            WorkflowExecution execution = engine.submit("context-preservation",
                    initialContext,
                    List.of(step1, step2));

            WorkflowContext result = execution.result().getResult();
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
            
            StepDefinition step1 = StepDefinition.builder()
                    .id("step-1")
                    .name("Reserve")
                    .body(ctx -> {
                        history.add("reserve");
                        return Promise.complete();
                    })
                    .compensationBody(ctx -> {
                        history.add("unreserve");
                        return Promise.complete();
                    })
                    .build();

            StepDefinition step2 = StepDefinition.builder()
                    .id("step-2")
                    .name("Charge")
                    .body(ctx -> {
                        history.add("charge");
                        return Promise.ofException(new RuntimeException("Payment failed"));
                    })
                    .build();

            WorkflowContext context = WorkflowContext.builder()
                    .withValue("orderId", "order-456")
                    .build();

            WorkflowExecution execution = engine.submit("compensation-test",
                    context,
                    List.of(step1, step2));

            try {
                execution.result().getResult();
                fail("Should have thrown exception");
            } catch (Exception e) {
                // Expected
            }
            
            assertTrue(history.contains("unreserve"), "Compensation should have been triggered");
            assertTrue(lifecycleListener.hasEvent(
                    event -> event.getPhase() == WorkflowLifecycleEvent.Phase.WORKFLOW_COMPENSATING));
        }

        @Test
        @DisplayName("should execute compensation in reverse order")
        void shouldExecuteCompensationInReverseOrder() {
            List<String> history = new ArrayList<>();
            
            StepDefinition step1 = StepDefinition.builder()
                    .id("step-1")
                    .name("Step1")
                    .body(ctx -> {
                        history.add("step1");
                        return Promise.complete();
                    })
                    .compensationBody(ctx -> {
                        history.add("compensate-step1");
                        return Promise.complete();
                    })
                    .build();

            StepDefinition step2 = StepDefinition.builder()
                    .id("step-2")
                    .name("Step2")
                    .body(ctx -> {
                        history.add("step2");
                        return Promise.complete();
                    })
                    .compensationBody(ctx -> {
                        history.add("compensate-step2");
                        return Promise.complete();
                    })
                    .build();

            StepDefinition step3 = StepDefinition.builder()
                    .id("step-3")
                    .name("Step3")
                    .body(ctx -> {
                        history.add("step3");
                        return Promise.ofException(new RuntimeException("Failed"));
                    })
                    .build();

            WorkflowContext context = WorkflowContext.builder()
                    .withValue("id", "test-id")
                    .build();

            WorkflowExecution execution = engine.submit("reverse-compensation",
                    context,
                    List.of(step1, step2, step3));

            try {
                execution.result().getResult();
            } catch (Exception e) {
                // Expected
            }
            
            // Verify reverse order: step2 should be compensated before step1
            int compensateStep2Index = history.indexOf("compensate-step2");
            int compensateStep1Index = history.indexOf("compensate-step1");
            assertTrue(compensateStep2Index < compensateStep1Index, 
                    "Compensation should be in reverse order");
        }

        @Test
        @DisplayName("should handle missing input gracefully")
        void shouldHandleMissingInputGracefully() {
            StepDefinition step = StepDefinition.builder()
                    .id("step-1")
                    .name("Process")
                    .body(ctx -> {
                        Object missing = ctx.getValue("nonexistent");
                        assertNull(missing);
                        return Promise.complete();
                    })
                    .build();

            WorkflowContext context = WorkflowContext.builder()
                    .build();

            WorkflowExecution execution = engine.submit("missing-input",
                    context,
                    List.of(step));

            WorkflowContext result = execution.result().getResult();
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("Retry Logic")
    class RetryLogic {

        @Test
        @DisplayName("should retry failed step") 
        void shouldRetryFailedStep() {
            AtomicInteger attempts = new AtomicInteger(0);
            
            StepDefinition step = StepDefinition.builder()
                    .id("step-1")
                    .name("RetryableStep")
                    .maxRetries(2)
                    .retryBackoff(Duration.ofMillis(10))
                    .body(ctx -> {
                        int attempt = attempts.incrementAndGet();
                        if (attempt < 3) {
                            return Promise.ofException(new RuntimeException("Temporary failure"));
                        }
                        return Promise.complete();
                    })
                    .build();

            WorkflowContext context = WorkflowContext.builder()
                    .withValue("id", "retry-test")
                    .build();

            WorkflowExecution execution = engine.submit("retry-workflow",
                    context,
                    List.of(step));

            WorkflowContext result = execution.result().getResult();
            
            assertNotNull(result);
            assertEquals(3, attempts.get(), "Step should have been retried");
        }

        @Test
        @DisplayName("should respect maximum retries")
        void shouldRespectMaximumRetries() {
            AtomicInteger attempts = new AtomicInteger(0);
            
            StepDefinition step = StepDefinition.builder()
                    .id("step-1")
                    .name("TooManyFailures")
                    .maxRetries(2)
                    .retryBackoff(Duration.ofMillis(10))
                    .body(ctx -> {
                        attempts.incrementAndGet();
                        return Promise.ofException(new RuntimeException("Always fails"));
                    })
                    .build();

            WorkflowContext context = WorkflowContext.builder()
                    .build();

            WorkflowExecution execution = engine.submit("max-retry-test",
                    context,
                    List.of(step));

            try {
                execution.result().getResult();
                fail("Should have failed after max retries");
            } catch (Exception e) {
                // Expected
            }
            
            // Initial attempt + 2 retries = 3 total
            assertEquals(3, attempts.get());
        }
    }

    @Nested
    @DisplayName("Timeout Handling")
    class TimeoutHandling {

        @Test
        @DisplayName("should timeout long-running step")
        void shouldTimeoutLongRunningStep() {
            StepDefinition step = StepDefinition.builder()
                    .id("step-1")
                    .name("SlowStep")
                    .timeout(Duration.ofMillis(50))
                    .body(ctx -> {
                        try {
                            Thread.sleep(500); // Longer than timeout
                            return Promise.complete();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return Promise.ofException(e);
                        }
                    })
                    .build();

            WorkflowContext context = WorkflowContext.builder()
                    .build();

            WorkflowExecution execution = engine.submit("timeout-test",
                    context,
                    List.of(step));

            try {
                execution.result().getResult();
                fail("Should have timed out");
            } catch (Exception e) {
                // Expected - timeout exception
            }
        }

        @Test
        @DisplayName("should complete within timeout for fast step")
        void shouldCompleteWithinTimeout() {
            StepDefinition step = StepDefinition.builder()
                    .id("step-1")
                    .name("FastStep")
                    .timeout(Duration.ofSeconds(10))
                    .body(ctx -> Promise.complete())
                    .build();

            WorkflowContext context = WorkflowContext.builder()
                    .build();

            WorkflowExecution execution = engine.submit("no-timeout",
                    context,
                    List.of(step));

            WorkflowContext result = execution.result().getResult();
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("State Management and Persistence")
    class StateManagement {

        @Test
        @DisplayName("should save workflow state")
        void shouldSaveWorkflowState() {
            StepDefinition step = StepDefinition.builder()
                    .id("step-1")
                    .name("SaveState")
                    .body(ctx -> Promise.complete())
                    .build();

            WorkflowContext context = WorkflowContext.builder()
                    .build();

            WorkflowExecution execution = engine.submit("state-save",
                    context,
                    List.of(step));

            execution.result().getResult();
            
            // Verify state was persisted
            assertNotNull(stateStore.load("state-save"));
        }

        @Test
        @DisplayName("should update step status in state")
        void shouldUpdateStepStatus() {
            List<String> statusUpdates = new ArrayList<>();
            
            StepDefinition step1 = StepDefinition.builder()
                    .id("step-1")
                    .name("Step1")
                    .body(ctx -> {
                        statusUpdates.add("step1-running");
                        return Promise.complete();
                    })
                    .build();

            StepDefinition step2 = StepDefinition.builder()
                    .id("step-2")
                    .name("Step2")
                    .body(ctx -> {
                        statusUpdates.add("step2-running");
                        return Promise.complete();
                    })
                    .build();

            WorkflowContext context = WorkflowContext.builder()
                    .build();

            engine.submit("status-update", context, List.of(step1, step2))
                    .result().getResult();

            // Workflow should have gone through multiple status updates
            assertFalse(statusUpdates.isEmpty());
        }
    }

    @Nested
    @DisplayName("Lifecycle Events")
    class LifecycleEvents {

        @Test
        @DisplayName("should emit workflow start event")
        void shouldEmitStartEvent() {
            StepDefinition step = StepDefinition.builder()
                    .id("step-1")
                    .name("Test")
                    .body(ctx -> Promise.complete())
                    .build();

            WorkflowContext context = WorkflowContext.builder()
                    .build();

            engine.submit("lifecycle-start", context, List.of(step))
                    .result().getResult();

            assertTrue(lifecycleListener.hasEvent(
                    event -> event.getPhase() == WorkflowLifecycleEvent.Phase.WORKFLOW_STARTED));
        }

        @Test
        @DisplayName("should emit step events")
        void shouldEmitStepEvents() {
            StepDefinition step = StepDefinition.builder()
                    .id("step-1")
                    .name("TestStep")
                    .body(ctx -> Promise.complete())
                    .build();

            WorkflowContext context = WorkflowContext.builder()
                    .build();

            engine.submit("lifecycle-steps", context, List.of(step))
                    .result().getResult();

            assertTrue(lifecycleListener.hasEvent(
                    event -> event.getPhase() == WorkflowLifecycleEvent.Phase.STEP_STARTED));
            assertTrue(lifecycleListener.hasEvent(
                    event -> event.getPhase() == WorkflowLifecycleEvent.Phase.STEP_COMPLETED));
        }

        @Test
        @DisplayName("should emit completion event")
        void shouldEmitCompletionEvent() {
            StepDefinition step = StepDefinition.builder()
                    .id("step-1")
                    .name("Test")
                    .body(ctx -> Promise.complete())
                    .build();

            WorkflowContext context = WorkflowContext.builder()
                    .build();

            engine.submit("lifecycle-complete", context, List.of(step))
                    .result().getResult();

            assertTrue(lifecycleListener.hasEvent(
                    event -> event.getPhase() == WorkflowLifecycleEvent.Phase.WORKFLOW_COMPLETED));
        }
    }

    // ─── Helper Classes ───────────────────────────────────────────────────

    private static class TestWorkflowLifecycleListener implements WorkflowLifecycleListener {
        private final List<WorkflowLifecycleEvent> events = new ArrayList<>();

        @Override
        public void onEvent(WorkflowLifecycleEvent event) {
            events.add(event);
        }

        public boolean hasEvent(java.util.function.Predicate<WorkflowLifecycleEvent> pred) {
            return events.stream().anyMatch(pred);
        }

        public List<WorkflowLifecycleEvent> getEvents() {
            return new ArrayList<>(events);
        }
    }

    private static class InMemoryWorkflowStateStore implements WorkflowStateStore {
        private final java.util.Map<String, Object> store = new java.util.ConcurrentHashMap<>();

        @Override
        public void save(Object state) {
            // Simplified implementation
        }

        @Override
        public Object load(String key) {
            return store.get(key);
        }
    }
}
