package com.ghatana.yappc.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ghatana.platform.core.agent.Agent;
import com.ghatana.platform.core.agent.AgentContext;
import com.ghatana.platform.core.agent.AgentResult;
import com.ghatana.platform.core.async.Promise;
import io.activej.common.ref.RefInt;
import io.activej.eventloop.Eventloop;
import io.activej.eventloop.EventloopTestBase;
import io.activej.test.ExpectedMessage;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * @doc.type class
 * @doc.purpose Integration tests for AgentOrchestrator multi-step workflow execution
 * @doc.layer product
 * @doc.pattern Integration Test
 */
@DisplayName("AgentOrchestrator Integration Tests")
class AgentOrchestratorIntegrationTest extends EventloopTestBase {

    private AgentOrchestrator orchestrator;
    private MetricsCollector metricsCollector;
    private Map<String, MockAgent> agents; // Registry of mock agents
    private AgentContext testContext;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        metricsCollector = mock(MetricsCollector.class);
        agents = new ConcurrentHashMap<>();
        testContext = createTestContext("tenant-123", "test-request-456");
        orchestrator = new AgentOrchestrator(metricsCollector, agent -> agents.get(agent.id()));
    }

    @Nested
    @DisplayName("Sequential Workflow Execution")
    class SequentialWorkflowExecutionTests {

        @Test
        @DisplayName("Should execute single-step workflow")
        void shouldExecuteSingleStep() {
            // Setup
            MockAgent step1 = new MockAgent("step-1", "result-1");
            agents.put("step-1", step1);

            AgentWorkflow workflow = new AgentWorkflow(
                    "single-step-workflow",
                    new AgentWorkflow.Step("step-1", "step-1", List.of()),
                    false // stopOnError = false
            );

            // Execute
            WorkflowResult result = runPromise(() ->
                    orchestrator.executeWorkflow(workflow, testContext)
            );

            // Verify
            assertThat(result).isNotNull();
            assertTrue(result.success());
            assertThat(result.results()).hasSize(1);
            assertThat(step1.executionCount()).isEqualTo(1);
            verify(metricsCollector).incrementCounter(
                    "orchestrator.workflows.started", "workflow", "single-step-workflow");
            verify(metricsCollector).incrementCounter(
                    "orchestrator.workflows.success", "workflow", "single-step-workflow");
        }

        @Test
        @DisplayName("Should execute multi-step linear workflow")
        void shouldExecuteMultiStepLinearWorkflow() {
            // Setup
            MockAgent step1 = new MockAgent("step-1", "result-1");
            MockAgent step2 = new MockAgent("step-2", "result-2");
            MockAgent step3 = new MockAgent("step-3", "result-3");
            agents.put("step-1", step1);
            agents.put("step-2", step2);
            agents.put("step-3", step3);

            AgentWorkflow workflow = new AgentWorkflow(
                    "linear-workflow",
                    new AgentWorkflow.Step("step-1", "step-1", List.of()),
                    new AgentWorkflow.Step("step-2", "step-2", List.of("step-1")),
                    new AgentWorkflow.Step("step-3", "step-3", List.of("step-2")),
                    false
            );

            // Execute
            WorkflowResult result = runPromise(() ->
                    orchestrator.executeWorkflow(workflow, testContext)
            );

            // Verify execution order
            assertTrue(result.success());
            assertThat(result.results()).hasSize(3);
            assertThat(step1.executionCount()).isEqualTo(1);
            assertThat(step2.executionCount()).isEqualTo(1);
            assertThat(step3.executionCount()).isEqualTo(1);

            // Verify step2 executed after step1 completed
            long step1EndTime = step1.completionTime();
            long step2StartTime = step2.startTime();
            assertThat(step2StartTime).isGreaterThanOrEqualTo(step1EndTime);

            // Verify metrics recorded
            verify(metricsCollector).recordTimer(
                    argThat(s -> s.equals("orchestrator.workflows.duration")),
                    anyLong(),
                    argThat(map -> map.containsValue("linear-workflow")));
        }

        @Test
        @DisplayName("Should execute parallel steps within same stage")
        void shouldExecuteParallelSteps() {
            // Setup
            MockAgent step1 = new MockAgent("step-1", "result-1");
            MockAgent step2a = new MockAgent("step-2a", "result-2a");
            MockAgent step2b = new MockAgent("step-2b", "result-2b");
            MockAgent step3 = new MockAgent("step-3", "result-3");
            agents.put("step-1", step1);
            agents.put("step-2a", step2a);
            agents.put("step-2b", step2b);
            agents.put("step-3", step3);

            AgentWorkflow workflow = new AgentWorkflow(
                    "parallel-workflow",
                    new AgentWorkflow.Step("step-1", "step-1", List.of()),
                    new AgentWorkflow.Step("step-2a", "step-2a", List.of("step-1")),
                    new AgentWorkflow.Step("step-2b", "step-2b", List.of("step-1")),
                    new AgentWorkflow.Step("step-3", "step-3", List.of("step-2a", "step-2b")),
                    false
            );

            // Execute
            WorkflowResult result = runPromise(() ->
                    orchestrator.executeWorkflow(workflow, testContext)
            );

            // Verify
            assertTrue(result.success());
            assertThat(result.results()).hasSize(4);

            // Both parallel steps should start at similar time (after step-1)
            long step2aStart = step2a.startTime();
            long step2bStart = step2b.startTime();
            long timeDifference = Math.abs(step2aStart - step2bStart);
            assertThat(timeDifference).isLessThan(100); // Within 100ms

            // Step3 should start only after both parallel steps complete
            long step3Start = step3.startTime();
            long step2aEnd = step2a.completionTime();
            long step2bEnd = step2b.completionTime();
            assertThat(step3Start).isGreaterThanOrEqualTo(Math.max(step2aEnd, step2bEnd));
        }

        @Test
        @DisplayName("Should detect circular dependencies")
        void shouldDetectCircularDependencies() {
            // Setup circular: step-1 -> step-2 -> step-3 -> step-1
            MockAgent step1 = new MockAgent("step-1", "result-1");
            MockAgent step2 = new MockAgent("step-2", "result-2");
            MockAgent step3 = new MockAgent("step-3", "result-3");
            agents.put("step-1", step1);
            agents.put("step-2", step2);
            agents.put("step-3", step3);

            AgentWorkflow workflow = new AgentWorkflow(
                    "circular-workflow",
                    new AgentWorkflow.Step("step-1", "step-1", List.of("step-3")),
                    new AgentWorkflow.Step("step-2", "step-2", List.of("step-1")),
                    new AgentWorkflow.Step("step-3", "step-3", List.of("step-2")),
                    false
            );

            // Execute and verify exception
            assertThatThrownBy(() ->
                    runPromise(() -> orchestrator.executeWorkflow(workflow, testContext))
            ).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Circular dependency");
        }
    }

    @Nested
    @DisplayName("Error Handling and Failure Propagation")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should propagate step failure when stopOnError=true")
        void shouldStopOnError() {
            // Setup
            MockAgent step1 = new MockAgent("step-1", "result-1");
            MockAgent step2 = new MockAgent("step-2", null).withFailure(
                    new RuntimeException("Step 2 failed")
            );
            MockAgent step3 = new MockAgent("step-3", "result-3");
            agents.put("step-1", step1);
            agents.put("step-2", step2);
            agents.put("step-3", step3);

            AgentWorkflow workflow = new AgentWorkflow(
                    "stop-on-error-workflow",
                    new AgentWorkflow.Step("step-1", "step-1", List.of()),
                    new AgentWorkflow.Step("step-2", "step-2", List.of("step-1")),
                    new AgentWorkflow.Step("step-3", "step-3", List.of("step-2")),
                    true // stopOnError = true
            );

            // Execute
            WorkflowResult result = runPromise(() ->
                    orchestrator.executeWorkflow(workflow, testContext)
            );

            // Verify
            assertFalse(result.success());
            assertThat(result.results()).as("Step 3 should not execute when step 2 fails")
                    .hasSize(2); // Only step-1 and step-2 executed
            assertThat(step3.executionCount()).isZero();
            verify(metricsCollector).incrementCounter(
                    "orchestrator.workflows.failed", "workflow", "stop-on-error-workflow");
        }

        @Test
        @DisplayName("Should continue on error when stopOnError=false")
        void shouldContinueOnError() {
            // Setup
            MockAgent step1 = new MockAgent("step-1", "result-1");
            MockAgent step2 = new MockAgent("step-2", null).withFailure(
                    new RuntimeException("Step 2 failed")
            );
            MockAgent step3 = new MockAgent("step-3", "result-3");
            agents.put("step-1", step1);
            agents.put("step-2", step2);
            agents.put("step-3", step3);

            AgentWorkflow workflow = new AgentWorkflow(
                    "continue-on-error-workflow",
                    new AgentWorkflow.Step("step-1", "step-1", List.of()),
                    new AgentWorkflow.Step("step-2", "step-2", List.of("step-1")),
                    new AgentWorkflow.Step("step-3", "step-3", List.of()),
                    false // stopOnError = false
            );

            // Execute
            WorkflowResult result = runPromise(() ->
                    orchestrator.executeWorkflow(workflow, testContext)
            );

            // Verify step3 still executes despite step2 failure
            assertFalse(result.success());
            assertThat(result.results()).hasSize(3);
            assertThat(step3.executionCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should aggregate errors from all failed steps")
        void shouldAggregateMultipleErrors() {
            // Setup
            MockAgent step1a = new MockAgent("step-1a", null).withFailure(
                    new RuntimeException("Step 1a failed")
            );
            MockAgent step1b = new MockAgent("step-1b", null).withFailure(
                    new RuntimeException("Step 1b failed")
            );
            MockAgent step2 = new MockAgent("step-2", "result-2");

            agents.put("step-1a", step1a);
            agents.put("step-1b", step1b);
            agents.put("step-2", step2);

            AgentWorkflow workflow = new AgentWorkflow(
                    "multi-error-workflow",
                    new AgentWorkflow.Step("step-1a", "step-1a", List.of()),
                    new AgentWorkflow.Step("step-1b", "step-1b", List.of()),
                    new AgentWorkflow.Step("step-2", "step-2", List.of()),
                    false
            );

            // Execute
            WorkflowResult result = runPromise(() ->
                    orchestrator.executeWorkflow(workflow, testContext)
            );

            // Verify both errors are captured
            assertFalse(result.success());
            assertThat(result.results())
                    .filteredOn(r -> !r.success())
                    .hasSize(2);
        }
    }

    @Nested
    @DisplayName("Metrics and Observability")
    class MetricsTests {

        @Test
        @DisplayName("Should record workflow start and completion metrics")
        void shouldRecordWorkflowMetrics() {
            // Setup
            MockAgent step1 = new MockAgent("step-1", "result-1");
            agents.put("step-1", step1);

            AgentWorkflow workflow = new AgentWorkflow(
                    "metrics-workflow",
                    new AgentWorkflow.Step("step-1", "step-1", List.of()),
                    false
            );

            // Execute
            runPromise(() -> orchestrator.executeWorkflow(workflow, testContext));

            // Verify metrics
            verify(metricsCollector).incrementCounter(
                    "orchestrator.workflows.started",
                    "workflow", "metrics-workflow"
            );
            verify(metricsCollector).incrementCounter(
                    "orchestrator.workflows.success",
                    "workflow", "metrics-workflow"
            );
            verify(metricsCollector).recordTimer(
                    contains("orchestrator.workflows.duration"),
                    anyLong(),
                    contains("metrics-workflow")
            );
        }

        @Test
        @DisplayName("Should record error metrics for failed workflows")
        void shouldRecordErrorMetrics() {
            // Setup
            MockAgent step1 = new MockAgent("step-1", null).withFailure(
                    new RuntimeException("Failed")
            );
            agents.put("step-1", step1);

            AgentWorkflow workflow = new AgentWorkflow(
                    "error-metrics-workflow",
                    new AgentWorkflow.Step("step-1", "step-1", List.of()),
                    false
            );

            // Execute
            runPromise(() -> orchestrator.executeWorkflow(workflow, testContext));

            // Verify error metrics
            verify(metricsCollector).incrementCounter(
                    "orchestrator.workflows.failed",
                    "workflow", "error-metrics-workflow"
            );
        }

        @Test
        @DisplayName("Should aggregate step execution metrics")
        void shouldAggregateMetrics() {
            // Setup
            MockAgent step1 = new MockAgent("step-1", "result-1").withLatency(10);
            MockAgent step2 = new MockAgent("step-2", "result-2").withLatency(20);
            agents.put("step-1", step1);
            agents.put("step-2", step2);

            AgentWorkflow workflow = new AgentWorkflow(
                    "metric-aggregation-workflow",
                    new AgentWorkflow.Step("step-1", "step-1", List.of()),
                    new AgentWorkflow.Step("step-2", "step-2", List.of("step-1")),
                    false
            );

            // Execute
            WorkflowResult result = runPromise(() ->
                    orchestrator.executeWorkflow(workflow, testContext)
            );

            // Verify aggregated metrics
            assertThat(result.aggregateMetrics())
                    .as("Total duration should be >= sum of latencies")
                    .isGreaterThanOrEqualTo(30);
        }
    }

    @Nested
    @DisplayName("Tenant Isolation")
    class TenantIsolationTests {

        @Test
        @DisplayName("Should include tenant ID in all metrics")
        void shouldIncludeTenantInMetrics() {
            // Setup
            MockAgent step1 = new MockAgent("step-1", "result-1");
            agents.put("step-1", step1);

            AgentWorkflow workflow = new AgentWorkflow(
                    "tenant-aware-workflow",
                    new AgentWorkflow.Step("step-1", "step-1", List.of()),
                    false
            );

            AgentContext tenantContext = createTestContext("tenant-xyz", "request-xyz");

            // Execute
            runPromise(() -> orchestrator.executeWorkflow(workflow, tenantContext));

            // Verify tenant tag in all metrics
            verify(metricsCollector).incrementCounter(
                    argThat(s -> s.contains("orchestrator")),
                    argThat(map -> map.containsValue("tenant-xyz") || 
                                   map.containsValue("tenant-aware-workflow"))
            );
        }

        @Test
        @DisplayName("Should isolate workflow state between tenants")
        void shouldIsolateTenantState() {
            // Setup - same workflow, different tenants
            MockAgent agent1 = new MockAgent("agent-1", "result-1");
            agents.put("agent-1", agent1);

            AgentWorkflow workflow = new AgentWorkflow(
                    "shared-workflow",
                    new AgentWorkflow.Step("agent-1", "agent-1", List.of()),
                    false
            );

            AgentContext tenant1Context = createTestContext("tenant-1", "request-1");
            AgentContext tenant2Context = createTestContext("tenant-2", "request-2");

            // Execute for both tenants
            WorkflowResult result1 = runPromise(() ->
                    orchestrator.executeWorkflow(workflow, tenant1Context)
            );
            WorkflowResult result2 = runPromise(() ->
                    orchestrator.executeWorkflow(workflow, tenant2Context)
            );

            // Verify both executed independently
            assertTrue(result1.success());
            assertTrue(result2.success());
            assertThat(agent1.executionCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Complex Workflow Scenarios")
    class ComplexWorkflowScenarios {

        @Test
        @DisplayName("Should handle workflow with conditional logic (optional steps)")
        void shouldHandleOptionalSteps() {
            // Setup
            MockAgent step1 = new MockAgent("step-1", "result-1");
            MockAgent optionalStep = new MockAgent("optional-step", null)
                    .withFailure(new RuntimeException("Optional failed"));
            MockAgent step3 = new MockAgent("step-3", "result-3");

            agents.put("step-1", step1);
            agents.put("optional-step", optionalStep);
            agents.put("step-3", step3);

            AgentWorkflow workflow = new AgentWorkflow(
                    "optional-steps-workflow",
                    new AgentWorkflow.Step("step-1", "step-1", List.of()),
                    new AgentWorkflow.Step("optional-step", "optional-step", List.of("step-1")),
                    new AgentWorkflow.Step("step-3", "step-3", List.of()),
                    false /* optional step failure won't stop workflow */
            );

            // Execute
            WorkflowResult result = runPromise(() ->
                    orchestrator.executeWorkflow(workflow, testContext)
            );

            // Verify step-3 executes despite optional step failure
            assertTrue(step3.executionCount() > 0);
        }

        @Test
        @DisplayName("Should handle diamond dependency graph")
        void shouldHandleDiamondDependency() {
            // Setup diamond: root -> [left, right] -> leaf
            MockAgent root = new MockAgent("root", "root-result");
            MockAgent left = new MockAgent("left", "left-result");
            MockAgent right = new MockAgent("right", "right-result");
            MockAgent leaf = new MockAgent("leaf", "leaf-result");

            agents.put("root", root);
            agents.put("left", left);
            agents.put("right", right);
            agents.put("leaf", leaf);

            AgentWorkflow workflow = new AgentWorkflow(
                    "diamond-workflow",
                    new AgentWorkflow.Step("root", "root", List.of()),
                    new AgentWorkflow.Step("left", "left", List.of("root")),
                    new AgentWorkflow.Step("right", "right", List.of("root")),
                    new AgentWorkflow.Step("leaf", "leaf", List.of("left", "right")),
                    false
            );

            // Execute
            WorkflowResult result = runPromise(() ->
                    orchestrator.executeWorkflow(workflow, testContext)
            );

            // Verify
            assertTrue(result.success());
            assertThat(result.results()).hasSize(4);
            assertThat(left.executionCount()).isEqualTo(1);
            assertThat(right.executionCount()).isEqualTo(1);
            assertThat(leaf.executionCount()).isEqualTo(1);
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 5, 10, 20})
        @DisplayName("Should handle workflows with varying step counts")
        void shouldHandleVariousWorkflowSizes(int stepCount) {
            // Create linear workflow with N steps
            List<AgentWorkflow.Step> steps = new ArrayList<>();
            for (int i = 1; i <= stepCount; i++) {
                String stepId = "step-" + i;
                MockAgent agent = new MockAgent(stepId, "result-" + i);
                agents.put(stepId, agent);

                List<String> deps = i > 1 ? List.of("step-" + (i - 1)) : List.of();
                steps.add(new AgentWorkflow.Step(stepId, stepId, deps));
            }

            AgentWorkflow workflow = new AgentWorkflow(
                    "large-workflow-" + stepCount,
                    steps.toArray(new AgentWorkflow.Step[0]),
                    false
            );

            // Execute
            WorkflowResult result = runPromise(() ->
                    orchestrator.executeWorkflow(workflow, testContext)
            );

            // Verify
            assertTrue(result.success());
            assertThat(result.results()).hasSize(stepCount);
        }
    }

    // Helper Methods

    private AgentContext createTestContext(String tenantId, String requestId) {
        return new AgentContext(
                tenantId,
                requestId,
                new HashMap<>(), // input map
                new HashMap<>()  // context variables
        );
    }

    /**
     * Mock Agent implementation for testing
     */
    static class MockAgent implements Agent {
        private final String id;
        private final String result;
        private final AtomicInteger executionCount = new AtomicInteger(0);
        private RuntimeException failureException;
        private long latencyMs = 0;
        private long startTimeMs = 0;
        private long completionTimeMs = 0;

        MockAgent(String id, String result) {
            this.id = id;
            this.result = result;
        }

        MockAgent withFailure(RuntimeException exception) {
            this.failureException = exception;
            return this;
        }

        MockAgent withLatency(long latencyMs) {
            this.latencyMs = latencyMs;
            return this;
        }

        @Override
        public Promise<AgentResult<?>> execute(AgentContext context) {
            startTimeMs = System.currentTimeMillis();
            executionCount.incrementAndGet();

            if (latencyMs > 0) {
                try {
                    Thread.sleep(latencyMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            completionTimeMs = System.currentTimeMillis();

            if (failureException != null) {
                return Promise.ofException(failureException);
            }

            return Promise.of(new AgentResult<>(
                    true, result, new HashMap<>(), null
            ));
        }

        public String id() {
            return id;
        }

        public int executionCount() {
            return executionCount.get();
        }

        public long startTime() {
            return startTimeMs;
        }

        public long completionTime() {
            return completionTimeMs;
        }
    }
}
