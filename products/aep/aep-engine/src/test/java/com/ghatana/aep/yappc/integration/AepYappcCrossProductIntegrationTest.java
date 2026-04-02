package com.ghatana.aep.yappc.integration;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ghatana.aep.core.events.AgentDispatchEvent;
import com.ghatana.aep.core.events.AgentResultEvent;
import com.ghatana.platform.core.async.Promise;
import com.ghatana.platform.core.async.Promises;
import com.ghatana.yappc.domain.agent.AgentOrchestrator;
import com.ghatana.yappc.domain.agent.AgentWorkflow;
import io.activej.eventloop.EventloopTestBase;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * @doc.type class
 * @doc.purpose End-to-end integration tests for AEP-to-YAPPC cross-product agent dispatch
 * @doc.layer platform
 * @doc.pattern Integration Test, E2E Test
 */
@DisplayName("AEP-YAPPC Cross-Product Integration Tests")
class AepYappcCrossProductIntegrationTest extends EventloopTestBase {

    private AepYappcDispatchCoordinator coordinator;
    private AgentOrchestrator yappcOrchestrator;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private AgentRegistryService agentRegistry;

    @Mock
    private MetricsCollector metrics;

    private String testTenantId = "tenant-cross-1";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        yappcOrchestrator = new AgentOrchestrator(metrics, agent -> null);
        coordinator = new AepYappcDispatchCoordinator(
                yappcOrchestrator,
                eventPublisher,
                agentRegistry,
                metrics
        );
    }

    @Nested
    @DisplayName("Agent Dispatch to YAPPC Orchestration")
    class DispatchToOrchestrationTests {

        @Test
        @DisplayName("Should dispatch agent request to YAPPC orchestrator")
        void shouldDispatchAgentRequest() {
            // Setup - create dispatch event
            AgentDispatchEvent dispatchEvent = new AgentDispatchEvent(
                    "dispatch-1",
                    testTenantId,
                    "yappc-agent-shape-validator",
                    new HashMap<>(Map.of(
                            "shapeSpec", "{\"name\": \"MyShape\"}",
                            "validationRules", "strict"
                    )),
                    "shape-validation"
            );

            // Mock agent registry
            mockYappcAgent("yappc-agent-shape-validator", "active");

            // Mock event publisher
            ArgumentCaptor<AgentResultEvent> resultCaptor =
                    ArgumentCaptor.forClass(AgentResultEvent.class);

            // Execute
            runPromise(() ->
                    coordinator.dispatchAndExecute(dispatchEvent)
            );

            // Verify result event published
            verify(eventPublisher).publishEvent(resultCaptor.capture());
            AgentResultEvent resultEvent = resultCaptor.getValue();

            assertThat(resultEvent).isNotNull();
            assertThat(resultEvent.originalDispatchId()).isEqualTo("dispatch-1");
            assertThat(resultEvent.tenantId()).isEqualTo(testTenantId);
            assertThat(resultEvent.success()).isTrue();
        }

        @Test
        @DisplayName("Should propagate agent execution errors back to AEP")
        void shouldPropagateExecutionErrors() {
            // Setup - agent that will fail
            AgentDispatchEvent dispatchEvent = new AgentDispatchEvent(
                    "dispatch-fail-1",
                    testTenantId,
                    "yappc-agent-invalid",
                    new HashMap<>(Map.of("spec", "invalid")),
                    "validation"
            );

            // Mock agent as unavailable
            when(agentRegistry.getAgent("yappc-agent-invalid"))
                    .thenReturn(Optional.empty());

            // Execute
            runPromise(() ->
                    coordinator.dispatchAndExecute(dispatchEvent)
            );

            // Verify error event published
            ArgumentCaptor<AgentResultEvent> resultCaptor =
                    ArgumentCaptor.forClass(AgentResultEvent.class);
            verify(eventPublisher).publishEvent(resultCaptor.capture());

            AgentResultEvent resultEvent = resultCaptor.getValue();
            assertFalse(resultEvent.success());
            assertThat(resultEvent.errorMessage()).isNotEmpty();
        }

        @Test
        @DisplayName("Should maintain correlation ID across dispatch and execution")
        void shouldMaintainCorrelationId() {
            // Setup
            String correlationId = "corr-xyz-789";
            AgentDispatchEvent dispatchEvent = new AgentDispatchEvent(
                    "dispatch-corr-1",
                    testTenantId,
                    "yappc-agent-1",
                    new HashMap<>(),
                    "workflow"
            );
            dispatchEvent.setCorrelationId(correlationId);

            mockYappcAgent("yappc-agent-1", "active");

            // Execute
            runPromise(() ->
                    coordinator.dispatchAndExecute(dispatchEvent)
            );

            // Verify correlation ID preserved in result
            ArgumentCaptor<AgentResultEvent> resultCaptor =
                    ArgumentCaptor.forClass(AgentResultEvent.class);
            verify(eventPublisher).publishEvent(resultCaptor.capture());

            AgentResultEvent resultEvent = resultCaptor.getValue();
            assertThat(resultEvent.correlationId()).isEqualTo(correlationId);
        }
    }

    @Nested
    @DisplayName("Multi-Step Workflow Coordination")
    class WorkflowCoordinationTests {

        @Test
        @DisplayName("Should orchestrate multi-step YAPPC workflow from single AEP dispatch")
        void shouldCoordinateMultiStepWorkflow() {
            // Setup - AEP dispatch requesting multi-step YAPPC workflow
            AgentDispatchEvent dispatchEvent = new AgentDispatchEvent(
                    "dispatch-workflow-1",
                    testTenantId,
                    "yappc-workflow-shape-generation",
                    new HashMap<>(Map.of(
                            "shapeSpec", "MyShape",
                            "steps", "validate,generate,validate_output"
                    )),
                    "shape-workflow"
            );

            mockYappcAgent("yappc-workflow-shape-generation", "active");

            // Execute
            runPromise(() ->
                    coordinator.dispatchAndExecute(dispatchEvent)
            );

            // Verify workflow orchestration metrics
            verify(metrics, atLeast(3)).incrementCounter(
                    argThat(s -> s.contains("step")),
                    anyMap()
            );
        }

        @Test
        @DisplayName("Should handle conditional workflow execution based on results")
        void shouldHandleConditionalWorkflow() {
            // Setup - dispatch with conditional steps
            AgentDispatchEvent dispatchEvent = new AgentDispatchEvent(
                    "dispatch-conditional-1",
                    testTenantId,
                    "yappc-conditional-workflow",
                    new HashMap<>(Map.of(
                            "validation_type", "strict",
                            "onValidationFail", "generate_fixes"
                    )),
                    "conditional-workflow"
            );

            mockYappcAgent("yappc-conditional-workflow", "active");

            // Execute
            runPromise(() ->
                    coordinator.dispatchAndExecute(dispatchEvent)
            );

            // Verify conditional branch taken
            ArgumentCaptor<AgentResultEvent> resultCaptor =
                    ArgumentCaptor.forClass(AgentResultEvent.class);
            verify(eventPublisher).publishEvent(resultCaptor.capture());
            assertThat(resultCaptor.getValue().success()).isTrue();
        }
    }

    @Nested
    @DisplayName("Concurrent Dispatch Handling")
    class ConcurrentDispatchTests {

        @Test
        @DisplayName("Should handle concurrent dispatches to same YAPPC agent")
        void shouldHandleConcurrentDispatches() {
            // Setup - 5 concurrent dispatch events
            int concurrentCount = 5;
            List<AgentDispatchEvent> dispatchEvents = new ArrayList<>();

            for (int i = 0; i < concurrentCount; i++) {
                dispatchEvents.add(new AgentDispatchEvent(
                        "dispatch-" + i,
                        testTenantId,
                        "yappc-agent-1",
                        new HashMap<>(Map.of("index", String.valueOf(i))),
                        "concurrent-test"
                ));
            }

            mockYappcAgent("yappc-agent-1", "active");

            // Execute all concurrently
            List<Promise<Void>> executions = dispatchEvents.stream()
                    .map(e -> coordinator.dispatchAndExecute(e))
                    .toList();

            runPromise(() -> Promises.all(executions));

            // Verify all executed successfully
            ArgumentCaptor<AgentResultEvent> resultCaptor =
                    ArgumentCaptor.forClass(AgentResultEvent.class);
            verify(eventPublisher, times(concurrentCount)).publishEvent(
                    resultCaptor.capture()
            );

            List<AgentResultEvent> results = resultCaptor.getAllValues();
            assertThat(results).hasSize(concurrentCount);
            assertThat(results).allSatisfy(r -> assertTrue(r.success()));
        }

        @Test
        @DisplayName("Should handle dispatches to different YAPPC agents concurrently")
        void shouldHandleMultiAgentConcurrency() {
            // Setup - dispatches to different agents
            List<AgentDispatchEvent> dispatchEvents = List.of(
                    new AgentDispatchEvent("d1", testTenantId, "agent-1", new HashMap<>(), "test"),
                    new AgentDispatchEvent("d2", testTenantId, "agent-2", new HashMap<>(), "test"),
                    new AgentDispatchEvent("d3", testTenantId, "agent-3", new HashMap<>(), "test")
            );

            // Mock all agents
            for (String agentId : List.of("agent-1", "agent-2", "agent-3")) {
                mockYappcAgent(agentId, "active");
            }

            // Execute concurrently
            List<Promise<Void>> executions = dispatchEvents.stream()
                    .map(e -> coordinator.dispatchAndExecute(e))
                    .toList();

            runPromise(() -> Promises.all(executions));

            // Verify all succeeded independently
            verify(eventPublisher, times(3)).publishEvent(any(AgentResultEvent.class));
        }
    }

    @Nested
    @DisplayName("Error Handling and Recovery")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should retry dispatch on transient failures")
        void shouldRetryOnTransientFailure() {
            // Setup
            AgentDispatchEvent dispatchEvent = new AgentDispatchEvent(
                    "dispatch-retry-1",
                    testTenantId,
                    "yappc-agent-1",
                    new HashMap<>(),
                    "test"
            );

            // Mock agent that succeeds on retry
            AtomicInteger callCount = new AtomicInteger(0);
            when(agentRegistry.getAgent("yappc-agent-1"))
                    .thenAnswer(inv -> {
                        if (callCount.incrementAndGet() < 2) {
                            return Optional.empty(); // First call fails
                        }
                        return Optional.of(createMockAgent("yappc-agent-1", "active"));
                    });

            // Execute with retry enabled
            runPromise(() ->
                    coordinator.dispatchWithRetry(dispatchEvent, 3)
            );

            // Verify retry was attempted
            verify(agentRegistry, atLeast(2)).getAgent("yappc-agent-1");
        }

        @Test
        @DisplayName("Should timeout and fail if YAPPC agent unresponsive")
        void shouldTimeoutOnUnresponsiveAgent() {
            // Setup - dispatch with timeout
            AgentDispatchEvent dispatchEvent = new AgentDispatchEvent(
                    "dispatch-timeout-1",
                    testTenantId,
                    "yappc-slow-agent",
                    new HashMap<>(),
                    "test"
            );

            // Mock unresponsive agent
            when(agentRegistry.getAgent("yappc-slow-agent"))
                    .thenReturn(Optional.of(createSlowMockAgent(5000))); // 5 second delay

            // Execute with 1 second timeout
            runPromise(() ->
                    coordinator.dispatchWithTimeout(dispatchEvent, 1000)
            );

            // Verify timeout error published
            ArgumentCaptor<AgentResultEvent> resultCaptor =
                    ArgumentCaptor.forClass(AgentResultEvent.class);
            verify(eventPublisher).publishEvent(resultCaptor.capture());

            AgentResultEvent resultEvent = resultCaptor.getValue();
            assertFalse(resultEvent.success());
            assertThat(resultEvent.errorMessage()).contains("timeout");
        }

        @Test
        @DisplayName("Should handle circuit breaker for failing agents")
        void shouldImplementCircuitBreaker() {
            // Setup - agent that consistently fails
            AgentDispatchEvent dispatchEvent = new AgentDispatchEvent(
                    "dispatch-circuit-1",
                    testTenantId,
                    "yappc-failing-agent",
                    new HashMap<>(),
                    "test"
            );

            // Mock consistently failing agent
            when(agentRegistry.getAgent("yappc-failing-agent"))
                    .thenReturn(Optional.of(createFailingMockAgent("yappc-failing-agent")));

            // Execute multiple times to trip circuit breaker
            for (int i = 0; i < 5; i++) {
                runPromise(() -> coordinator.dispatchAndExecute(dispatchEvent));
            }

            // Verify circuit breaker engaged
            verify(metrics, atLeast(1)).incrementCounter(
                    argThat(s -> s.contains("circuit_breaker")),
                    anyMap()
            );
        }
    }

    @Nested
    @DisplayName("Tenant Isolation Across Products")
    class CrossProductTenantIsolationTests {

        @Test
        @DisplayName("Should enforce tenant isolation between AEP and YAPPC")
        void shouldEnforceTenantIsolation() {
            // Setup - dispatches from different tenants
            AgentDispatchEvent tenant1Dispatch = new AgentDispatchEvent(
                    "d1",
                    "tenant-1",
                    "yappc-agent-1",
                    new HashMap<>(),
                    "test"
            );

            AgentDispatchEvent tenant2Dispatch = new AgentDispatchEvent(
                    "d2",
                    "tenant-2",
                    "yappc-agent-1",
                    new HashMap<>(),
                    "test"
            );

            mockYappcAgent("yappc-agent-1", "active");

            // Execute for both tenants
            runPromise(() -> coordinator.dispatchAndExecute(tenant1Dispatch));
            runPromise(() -> coordinator.dispatchAndExecute(tenant2Dispatch));

            // Verify tenant tags in results
            ArgumentCaptor<AgentResultEvent> resultCaptor =
                    ArgumentCaptor.forClass(AgentResultEvent.class);
            verify(eventPublisher, times(2)).publishEvent(resultCaptor.capture());

            List<AgentResultEvent> results = resultCaptor.getAllValues();
            assertThat(results.get(0).tenantId()).isEqualTo("tenant-1");
            assertThat(results.get(1).tenantId()).isEqualTo("tenant-2");
        }

        @Test
        @DisplayName("Should prevent cross-tenant access to agents")
        void shouldPreventCrossTenantAccess() {
            // Setup - tenant 1 tries to access tenant 2 agent
            AgentDispatchEvent dispatchEvent = new AgentDispatchEvent(
                    "d1",
                    "tenant-1",
                    "yappc-agent-tenant-2", // Agent owned by tenant-2
                    new HashMap<>(),
                    "test"
            );

            // Mock agent validation to reject cross-tenant access
            when(agentRegistry.getAgentForTenant("yappc-agent-tenant-2", "tenant-1"))
                    .thenReturn(Optional.empty()); // Access denied

            // Execute
            runPromise(() -> coordinator.dispatchAndExecute(dispatchEvent));

            // Verify access denied error
            ArgumentCaptor<AgentResultEvent> resultCaptor =
                    ArgumentCaptor.forClass(AgentResultEvent.class);
            verify(eventPublisher).publishEvent(resultCaptor.capture());

            AgentResultEvent resultEvent = resultCaptor.getValue();
            assertFalse(resultEvent.success());
            assertThat(resultEvent.errorMessage()).contains("not authorized");
        }
    }

    @Nested
    @DisplayName("Metrics and Tracing")
    class MetricsAndTracingTests {

        @Test
        @DisplayName("Should record end-to-end latency from AEP dispatch to YAPPC result")
        void shouldRecordEndToEndLatency() {
            // Setup
            AgentDispatchEvent dispatchEvent = new AgentDispatchEvent(
                    "dispatch-latency-1",
                    testTenantId,
                    "yappc-agent-1",
                    new HashMap<>(),
                    "test"
            );

            mockYappcAgent("yappc-agent-1", "active");

            // Execute
            long startTime = System.currentTimeMillis();
            runPromise(() -> coordinator.dispatchAndExecute(dispatchEvent));
            long duration = System.currentTimeMillis() - startTime;

            // Verify latency recorded
            ArgumentCaptor<Long> durationCaptor = ArgumentCaptor.forClass(Long.class);
            verify(metrics).recordTimer(
                    argThat(s -> s.contains("e2e_latency")),
                    durationCaptor.capture(),
                    anyMap()
            );

            assertThat(durationCaptor.getValue()).isLessThan(5000); // Should be fast
        }

        @Test
        @DisplayName("Should propagate trace context from AEP to YAPPC")
        void shouldPropagateTraceContext() {
            // Setup
            AgentDispatchEvent dispatchEvent = new AgentDispatchEvent(
                    "dispatch-trace-1",
                    testTenantId,
                    "yappc-agent-1",
                    new HashMap<>(),
                    "test"
            );
            dispatchEvent.setTraceId("trace-xyz-123");
            dispatchEvent.setSpanId("span-abc-456");

            mockYappcAgent("yappc-agent-1", "active");

            // Execute
            runPromise(() -> coordinator.dispatchAndExecute(dispatchEvent));

            // Verify trace context preserved
            ArgumentCaptor<AgentResultEvent> resultCaptor =
                    ArgumentCaptor.forClass(AgentResultEvent.class);
            verify(eventPublisher).publishEvent(resultCaptor.capture());

            AgentResultEvent resultEvent = resultCaptor.getValue();
            assertThat(resultEvent.traceId()).isEqualTo("trace-xyz-123");
            assertThat(resultEvent.parentSpanId()).isEqualTo("span-abc-456");
        }
    }

    // Helper Methods

    private void mockYappcAgent(String agentId, String status) {
        when(agentRegistry.getAgent(agentId))
                .thenReturn(Optional.of(createMockAgent(agentId, status)));
    }

    private MockAgent createMockAgent(String id, String status) {
        return new MockAgent(id, status, false, null);
    }

    private MockAgent createSlowMockAgent(long delayMs) {
        return new MockAgent("slow-agent", "active", false, delayMs);
    }

    private MockAgent createFailingMockAgent(String id) {
        return new MockAgent(id, "active", true, null);
    }

    // Test Doubles

    static class MockAgent {
        private final String id;
        private final String status;
        private final boolean shouldFail;
        private final Long delayMs;

        MockAgent(String id, String status, boolean shouldFail, Long delayMs) {
            this.id = id;
            this.status = status;
            this.shouldFail = shouldFail;
            this.delayMs = delayMs;
        }

        String id() { return id; }
        String status() { return status; }
        boolean shouldFail() { return shouldFail; }
        Optional<Long> delayMs() { return Optional.ofNullable(delayMs); }
    }

    interface EventPublisher {
        void publishEvent(AgentResultEvent event);
    }

    interface AgentRegistryService {
        Optional<MockAgent> getAgent(String agentId);
        Optional<MockAgent> getAgentForTenant(String agentId, String tenantId);
    }

    interface MetricsCollector {
        void incrementCounter(String name, Map<String, String> tags);
        void recordTimer(String name, long durationMs, Map<String, String> tags);
    }
}
