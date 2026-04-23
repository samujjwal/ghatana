/*
 * Copyright (c) 2026 Ghatana Technologies // GH-90000
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle.operators;

import com.ghatana.platform.domain.event.Event;
import com.ghatana.platform.domain.event.GEvent;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.workflow.operator.OperatorResult;
import com.ghatana.yappc.agent.YappcAgentSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for the YAPPC agent-orchestration-v1 pipeline operators.
 *
 * <p>All async tests use {@link EventloopTestBase#runPromise(java.util.concurrent.Callable)} // GH-90000
 * to execute promises on the managed ActiveJ Eventloop without blocking.
 *
 * @doc.type class
 * @doc.purpose Tests for AgentDispatchValidatorOperator, BackpressureOperator,
 *              AgentExecutorOperator, ResultAggregatorOperator, MetricsCollectorOperator
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("YAPPC Agent Orchestration Operators")
class YappcAgentOrchestrationOperatorsTest extends EventloopTestBase {

    // ─── shared helpers ───────────────────────────────────────────────────────

    private static Event buildDispatchRequestedEvent( // GH-90000
            String agentId, String fromStage, String toStage, String tenantId) {
        return GEvent.builder() // GH-90000
                .typeTenantVersion(tenantId, AgentDispatchValidatorOperator.EVENT_DISPATCH_REQUESTED, "v1") // GH-90000
                .addPayload("eventId",   "evt-test-001") // GH-90000
                .addPayload("eventType", AgentDispatchValidatorOperator.EVENT_DISPATCH_REQUESTED) // GH-90000
                .addPayload("agentId",   agentId) // GH-90000
                .addPayload("fromStage", fromStage) // GH-90000
                .addPayload("toStage",   toStage) // GH-90000
                .addPayload("tenantId",  tenantId) // GH-90000
                .build(); // GH-90000
    }

    private static Event buildDispatchValidatedEvent( // GH-90000
            String agentId, String fromStage, String toStage,
            String tenantId, String correlationId) {
        return GEvent.builder() // GH-90000
                .typeTenantVersion(tenantId, AgentDispatchValidatorOperator.EVENT_DISPATCH_VALIDATED, "v1") // GH-90000
                .addPayload("agentId",       agentId) // GH-90000
                .addPayload("fromStage",     fromStage) // GH-90000
                .addPayload("toStage",       toStage) // GH-90000
                .addPayload("tenantId",      tenantId) // GH-90000
                .addPayload("correlationId", correlationId) // GH-90000
                .build(); // GH-90000
    }

    private static Event buildResultProducedEvent( // GH-90000
            String agentId, String tenantId, String correlationId, String status) {
        return GEvent.builder() // GH-90000
                .typeTenantVersion(tenantId, AgentExecutorOperator.EVENT_RESULT_PRODUCED, "v1") // GH-90000
                .addPayload("agentId",       agentId) // GH-90000
                .addPayload("status",        status) // GH-90000
                .addPayload("tenantId",      tenantId) // GH-90000
                .addPayload("correlationId", correlationId) // GH-90000
                .build(); // GH-90000
    }

    // =========================================================================
    // AgentDispatchValidatorOperator
    // =========================================================================

    @Nested
    @DisplayName("AgentDispatchValidatorOperator")
    class AgentDispatchValidatorTests {

        private AgentDispatchValidatorOperator operator;

        @BeforeEach
        void setUp() { // GH-90000
            operator = new AgentDispatchValidatorOperator(); // GH-90000
        }

        @Test
        @DisplayName("should emit agent.dispatch.validated for valid dispatch event")
        void shouldValidateCompleteDispatchEvent() { // GH-90000
            // GIVEN
            Event event = buildDispatchRequestedEvent("agent-1", "planning", "execution", "tenant-1"); // GH-90000

            // WHEN
            OperatorResult result = runPromise(() -> operator.process(event)); // GH-90000

            // THEN
            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutputEvents()).hasSize(1); // GH-90000
            Event out = result.getOutputEvents().get(0); // GH-90000
            assertThat(out.getType()).isEqualTo(AgentDispatchValidatorOperator.EVENT_DISPATCH_VALIDATED); // GH-90000
            assertThat(out.getPayload("agentId")).isEqualTo("agent-1");
            assertThat(out.getPayload("fromStage")).isEqualTo("planning");
            assertThat(out.getPayload("toStage")).isEqualTo("execution");
            assertThat(out.getPayload("originalEventType"))
                    .isEqualTo(AgentDispatchValidatorOperator.EVENT_DISPATCH_REQUESTED); // GH-90000
        }

        @Test
        @DisplayName("should fail when agentId is missing")
        void shouldFailWhenAgentIdMissing() { // GH-90000
            // GIVEN — build event without agentId
            Event event = GEvent.builder() // GH-90000
                    .typeTenantVersion("tenant-1", AgentDispatchValidatorOperator.EVENT_DISPATCH_REQUESTED, "v1") // GH-90000
                    .addPayload("eventId",   "evt-bad") // GH-90000
                    .addPayload("eventType", AgentDispatchValidatorOperator.EVENT_DISPATCH_REQUESTED) // GH-90000
                    .addPayload("fromStage", "planning") // GH-90000
                    .addPayload("toStage",   "execution") // GH-90000
                    .build(); // GH-90000

            // WHEN
            OperatorResult result = runPromise(() -> operator.process(event)); // GH-90000

            // THEN
            assertThat(result.isSuccess()).isFalse(); // GH-90000
            assertThat(result.getErrorMessage()).contains("VALIDATION_FAILED");
            assertThat(result.getErrorMessage()).contains("agentId");
        }

        @Test
        @DisplayName("should fail when fromStage is blank")
        void shouldFailWhenFromStageIsBlank() { // GH-90000
            // GIVEN
            Event event = GEvent.builder() // GH-90000
                    .typeTenantVersion("tenant-1", AgentDispatchValidatorOperator.EVENT_DISPATCH_REQUESTED, "v1") // GH-90000
                    .addPayload("eventId",   "evt-bad") // GH-90000
                    .addPayload("eventType", AgentDispatchValidatorOperator.EVENT_DISPATCH_REQUESTED) // GH-90000
                    .addPayload("agentId",   "agent-2") // GH-90000
                    .addPayload("fromStage", "") // GH-90000
                    .addPayload("toStage",   "execution") // GH-90000
                    .build(); // GH-90000

            // WHEN
            OperatorResult result = runPromise(() -> operator.process(event)); // GH-90000

            // THEN
            assertThat(result.isSuccess()).isFalse(); // GH-90000
            assertThat(result.getErrorMessage()).contains("fromStage");
        }

        @Test
        @DisplayName("should fail when all required fields are missing")
        void shouldReportAllMissingFields() { // GH-90000
            // GIVEN — event with no payload at all
            Event event = GEvent.builder() // GH-90000
                    .type(AgentDispatchValidatorOperator.EVENT_DISPATCH_REQUESTED) // GH-90000
                    .build(); // GH-90000

            // WHEN
            OperatorResult result = runPromise(() -> operator.process(event)); // GH-90000

            // THEN
            assertThat(result.isSuccess()).isFalse(); // GH-90000
            assertThat(result.getErrorMessage()) // GH-90000
                    .contains("eventId", "agentId", "fromStage", "toStage"); // GH-90000
        }
    }

    // =========================================================================
    // BackpressureOperator
    // =========================================================================

    @Nested
    @DisplayName("BackpressureOperator")
    class BackpressureOperatorTests {

        private BackpressureOperator operator;

        @BeforeEach
        void setUp() { // GH-90000
            operator = new BackpressureOperator(); // GH-90000
        }

        @Test
        @DisplayName("should pass a single event through the buffer")
        void shouldPassEventThrough() { // GH-90000
            // GIVEN
            Event event = buildDispatchRequestedEvent("agent-1", "plan", "exec", "tenant-1"); // GH-90000

            // WHEN
            OperatorResult result = runPromise(() -> operator.process(event)); // GH-90000

            // THEN
            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutputEvents()).hasSize(1); // GH-90000
            assertThat(result.getOutputEvents().get(0).getType()) // GH-90000
                    .isEqualTo(AgentDispatchValidatorOperator.EVENT_DISPATCH_REQUESTED); // GH-90000
        }

        @Test
        @DisplayName("should maintain correct buffer size after processing")
        void shouldMaintainBufferSizeCorrectly() { // GH-90000
            // GIVEN — two events offered
            Event evt1 = buildDispatchRequestedEvent("agent-1", "plan", "exec", "tenant-1"); // GH-90000
            Event evt2 = buildDispatchRequestedEvent("agent-2", "plan", "exec", "tenant-1"); // GH-90000

            // WHEN — process each one (first drains immediately) // GH-90000
            runPromise(() -> operator.process(evt1)); // GH-90000
            // After first: buffer was offered evt1, then drained -> size=0
            // Offer evt2 then drain
            runPromise(() -> operator.process(evt2)); // GH-90000

            // THEN — buffer should be empty after each synchronized drain
            assertThat(operator.bufferSize()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("should be created with default buffer size constant")
        void shouldHaveCorrectDefaultBufferSize() { // GH-90000
            assertThat(BackpressureOperator.DEFAULT_BUFFER_SIZE).isEqualTo(2048); // GH-90000
        }
    }

    // =========================================================================
    // AgentExecutorOperator
    // =========================================================================

    @Nested
    @DisplayName("AgentExecutorOperator")
    class AgentExecutorOperatorTests {

        @Mock
        private YappcAgentSystem yappcAgentSystem;

        private AgentExecutorOperator operator;

        @BeforeEach
        void setUp() { // GH-90000
            // Use lenient stubbing: isInitialized() is only called in some test cases // GH-90000
            // (tests that reach past the agentId-null guard don't need the stub) // GH-90000
            lenient().when(yappcAgentSystem.isInitialized()).thenReturn(false); // GH-90000
            operator = new AgentExecutorOperator(yappcAgentSystem); // GH-90000
        }

        @Test
        @DisplayName("should emit agent.result.produced with status=error when agent system is not yet initialized")
        void shouldEmitResultProducedEvent() { // GH-90000
            // GIVEN — operator with an uninitialized YappcAgentSystem
            Event event = buildDispatchValidatedEvent("agent-1", "plan", "exec", "tenant-1", "corr-001"); // GH-90000

            // WHEN
            OperatorResult result = runPromise(() -> operator.process(event)); // GH-90000

            // THEN — OperatorResult is a success (carries the result event), // GH-90000
            // but the result event payload has status=error because the agent system is not initialized.
            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutputEvents()).hasSize(1); // GH-90000
            Event out = result.getOutputEvents().get(0); // GH-90000
            assertThat(out.getType()).isEqualTo(AgentExecutorOperator.EVENT_RESULT_PRODUCED); // GH-90000
            assertThat(out.getPayload("status")).isEqualTo("error");
            assertThat(out.getPayload("agentId")).isEqualTo("agent-1");
            assertThat(out.getPayload("correlationId")).isEqualTo("corr-001");
        }

        @Test
        @DisplayName("should fail when agentId is missing from dispatch event")
        void shouldFailWhenAgentIdMissing() { // GH-90000
            // GIVEN — validated event without agentId
            Event event = GEvent.builder() // GH-90000
                    .typeTenantVersion("tenant-1", AgentDispatchValidatorOperator.EVENT_DISPATCH_VALIDATED, "v1") // GH-90000
                    .addPayload("fromStage", "plan") // GH-90000
                    .addPayload("toStage",   "exec") // GH-90000
                    .build(); // GH-90000

            // WHEN
            OperatorResult result = runPromise(() -> operator.process(event)); // GH-90000

            // THEN
            assertThat(result.isSuccess()).isFalse(); // GH-90000
            assertThat(result.getErrorMessage()).contains("agentId");
        }
    }

    // =========================================================================
    // ResultAggregatorOperator
    // =========================================================================

    @Nested
    @DisplayName("ResultAggregatorOperator")
    class ResultAggregatorOperatorTests {

        private ResultAggregatorOperator operator;

        @BeforeEach
        void setUp() { // GH-90000
            // Default threshold = 1 → emit immediately on first result
            operator = new ResultAggregatorOperator(); // GH-90000
        }

        @Test
        @DisplayName("should emit workflow.step.completed immediately when threshold=1")
        void shouldEmitStepCompletedImmediately() { // GH-90000
            // GIVEN
            Event event = buildResultProducedEvent("agent-1", "tenant-1", "corr-001", "success"); // GH-90000

            // WHEN
            OperatorResult result = runPromise(() -> operator.process(event)); // GH-90000

            // THEN
            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutputEvents()).hasSize(1); // GH-90000
            Event out = result.getOutputEvents().get(0); // GH-90000
            assertThat(out.getType()).isEqualTo(ResultAggregatorOperator.EVENT_STEP_COMPLETED); // GH-90000
            assertThat(out.getPayload("correlationId")).isEqualTo("corr-001");
            assertThat(out.getPayload("resultCount")).isEqualTo(1);
        }

        @Test
        @DisplayName("should clear bucket after emitting step.completed")
        void shouldClearBucketAfterEmit() { // GH-90000
            // GIVEN
            Event event = buildResultProducedEvent("agent-1", "tenant-1", "corr-002", "success"); // GH-90000

            // WHEN
            runPromise(() -> operator.process(event)); // GH-90000

            // THEN — bucket for corr-002 should be gone
            assertThat(operator.activeBucketCount()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("should accumulate results until threshold is met with threshold=2")
        void shouldAccumulateUntilThreshold() { // GH-90000
            // GIVEN
            operator = new ResultAggregatorOperator(2); // GH-90000
            Event first  = buildResultProducedEvent("agent-1", "tenant-1", "corr-003", "success"); // GH-90000
            Event second = buildResultProducedEvent("agent-2", "tenant-1", "corr-003", "success"); // GH-90000

            // WHEN — first result: below threshold → no output
            OperatorResult r1 = runPromise(() -> operator.process(first)); // GH-90000
            assertThat(r1.getOutputEvents()).isEmpty(); // GH-90000
            assertThat(operator.activeBucketCount()).isEqualTo(1); // GH-90000

            // WHEN — second result: meets threshold → emit
            OperatorResult r2 = runPromise(() -> operator.process(second)); // GH-90000
            assertThat(r2.isSuccess()).isTrue(); // GH-90000
            assertThat(r2.getOutputEvents()).hasSize(1); // GH-90000
            assertThat(r2.getOutputEvents().get(0).getType()) // GH-90000
                    .isEqualTo(ResultAggregatorOperator.EVENT_STEP_COMPLETED); // GH-90000
            assertThat(operator.activeBucketCount()).isEqualTo(0); // GH-90000
        }
    }

    // =========================================================================
    // MetricsCollectorOperator
    // =========================================================================

    @Nested
    @DisplayName("MetricsCollectorOperator")
    class MetricsCollectorOperatorTests {

        private MetricsCollectorOperator operator;

        @BeforeEach
        void setUp() { // GH-90000
            operator = new MetricsCollectorOperator(); // GH-90000
        }

        @Test
        @DisplayName("should emit agent.metrics.updated for each result event")
        void shouldEmitMetricsUpdatedEvent() { // GH-90000
            // GIVEN
            Event event = buildResultProducedEvent("agent-1", "tenant-1", "corr-001", "success"); // GH-90000

            // WHEN
            OperatorResult result = runPromise(() -> operator.process(event)); // GH-90000

            // THEN
            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutputEvents()).hasSize(1); // GH-90000
            Event out = result.getOutputEvents().get(0); // GH-90000
            assertThat(out.getType()).isEqualTo(MetricsCollectorOperator.EVENT_METRICS_UPDATED); // GH-90000
            assertThat(out.getPayload("agentId")).isEqualTo("agent-1");
            assertThat(out.getPayload("status")).isEqualTo("success");
            assertThat(out.getPayload("agent_executions_total")).isEqualTo(1L);
        }

        @Test
        @DisplayName("should increment totalExecutions counter on each call")
        void shouldIncrementExecutionCounter() { // GH-90000
            // GIVEN
            Event event = buildResultProducedEvent("agent-1", "tenant-1", "corr-001", "success"); // GH-90000

            // WHEN — two calls
            runPromise(() -> operator.process(event)); // GH-90000
            runPromise(() -> operator.process(event)); // GH-90000

            // THEN
            assertThat(operator.getTotalExecutions()).isEqualTo(2L); // GH-90000
        }

        @Test
        @DisplayName("should start with zero total executions")
        void shouldStartAtZero() { // GH-90000
            assertThat(operator.getTotalExecutions()).isEqualTo(0L); // GH-90000
        }
    }
}
