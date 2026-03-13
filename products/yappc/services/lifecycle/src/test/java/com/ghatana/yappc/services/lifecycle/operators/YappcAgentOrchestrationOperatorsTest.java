/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle.operators;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.domain.domain.event.GEvent;
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
import static org.mockito.Mockito.when;

/**
 * Unit tests for the YAPPC agent-orchestration-v1 pipeline operators.
 *
 * <p>All async tests use {@link EventloopTestBase#runPromise(java.util.concurrent.Callable)}
 * to execute promises on the managed ActiveJ Eventloop without blocking.
 *
 * @doc.type class
 * @doc.purpose Tests for AgentDispatchValidatorOperator, BackpressureOperator,
 *              AgentExecutorOperator, ResultAggregatorOperator, MetricsCollectorOperator
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("YAPPC Agent Orchestration Operators")
class YappcAgentOrchestrationOperatorsTest extends EventloopTestBase {

    // ─── shared helpers ───────────────────────────────────────────────────────

    private static Event buildDispatchRequestedEvent(
            String agentId, String fromStage, String toStage, String tenantId) {
        return GEvent.builder()
                .typeTenantVersion(tenantId, AgentDispatchValidatorOperator.EVENT_DISPATCH_REQUESTED, "v1")
                .addPayload("eventId",   "evt-test-001")
                .addPayload("eventType", AgentDispatchValidatorOperator.EVENT_DISPATCH_REQUESTED)
                .addPayload("agentId",   agentId)
                .addPayload("fromStage", fromStage)
                .addPayload("toStage",   toStage)
                .addPayload("tenantId",  tenantId)
                .build();
    }

    private static Event buildDispatchValidatedEvent(
            String agentId, String fromStage, String toStage,
            String tenantId, String correlationId) {
        return GEvent.builder()
                .typeTenantVersion(tenantId, AgentDispatchValidatorOperator.EVENT_DISPATCH_VALIDATED, "v1")
                .addPayload("agentId",       agentId)
                .addPayload("fromStage",     fromStage)
                .addPayload("toStage",       toStage)
                .addPayload("tenantId",      tenantId)
                .addPayload("correlationId", correlationId)
                .build();
    }

    private static Event buildResultProducedEvent(
            String agentId, String tenantId, String correlationId, String status) {
        return GEvent.builder()
                .typeTenantVersion(tenantId, AgentExecutorOperator.EVENT_RESULT_PRODUCED, "v1")
                .addPayload("agentId",       agentId)
                .addPayload("status",        status)
                .addPayload("tenantId",      tenantId)
                .addPayload("correlationId", correlationId)
                .build();
    }

    // =========================================================================
    // AgentDispatchValidatorOperator
    // =========================================================================

    @Nested
    @DisplayName("AgentDispatchValidatorOperator")
    class AgentDispatchValidatorTests {

        private AgentDispatchValidatorOperator operator;

        @BeforeEach
        void setUp() {
            operator = new AgentDispatchValidatorOperator();
        }

        @Test
        @DisplayName("should emit agent.dispatch.validated for valid dispatch event")
        void shouldValidateCompleteDispatchEvent() {
            // GIVEN
            Event event = buildDispatchRequestedEvent("agent-1", "planning", "execution", "tenant-1");

            // WHEN
            OperatorResult result = runPromise(() -> operator.process(event));

            // THEN
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutputEvents()).hasSize(1);
            Event out = result.getOutputEvents().get(0);
            assertThat(out.getType()).isEqualTo(AgentDispatchValidatorOperator.EVENT_DISPATCH_VALIDATED);
            assertThat(out.getPayload("agentId")).isEqualTo("agent-1");
            assertThat(out.getPayload("fromStage")).isEqualTo("planning");
            assertThat(out.getPayload("toStage")).isEqualTo("execution");
            assertThat(out.getPayload("originalEventType"))
                    .isEqualTo(AgentDispatchValidatorOperator.EVENT_DISPATCH_REQUESTED);
        }

        @Test
        @DisplayName("should fail when agentId is missing")
        void shouldFailWhenAgentIdMissing() {
            // GIVEN — build event without agentId
            Event event = GEvent.builder()
                    .typeTenantVersion("tenant-1", AgentDispatchValidatorOperator.EVENT_DISPATCH_REQUESTED, "v1")
                    .addPayload("eventId",   "evt-bad")
                    .addPayload("eventType", AgentDispatchValidatorOperator.EVENT_DISPATCH_REQUESTED)
                    .addPayload("fromStage", "planning")
                    .addPayload("toStage",   "execution")
                    .build();

            // WHEN
            OperatorResult result = runPromise(() -> operator.process(event));

            // THEN
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("VALIDATION_FAILED");
            assertThat(result.getErrorMessage()).contains("agentId");
        }

        @Test
        @DisplayName("should fail when fromStage is blank")
        void shouldFailWhenFromStageIsBlank() {
            // GIVEN
            Event event = GEvent.builder()
                    .typeTenantVersion("tenant-1", AgentDispatchValidatorOperator.EVENT_DISPATCH_REQUESTED, "v1")
                    .addPayload("eventId",   "evt-bad")
                    .addPayload("eventType", AgentDispatchValidatorOperator.EVENT_DISPATCH_REQUESTED)
                    .addPayload("agentId",   "agent-2")
                    .addPayload("fromStage", "")
                    .addPayload("toStage",   "execution")
                    .build();

            // WHEN
            OperatorResult result = runPromise(() -> operator.process(event));

            // THEN
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("fromStage");
        }

        @Test
        @DisplayName("should fail when all required fields are missing")
        void shouldReportAllMissingFields() {
            // GIVEN — event with no payload at all
            Event event = GEvent.builder()
                    .type(AgentDispatchValidatorOperator.EVENT_DISPATCH_REQUESTED)
                    .build();

            // WHEN
            OperatorResult result = runPromise(() -> operator.process(event));

            // THEN
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage())
                    .contains("eventId", "agentId", "fromStage", "toStage");
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
        void setUp() {
            operator = new BackpressureOperator();
        }

        @Test
        @DisplayName("should pass a single event through the buffer")
        void shouldPassEventThrough() {
            // GIVEN
            Event event = buildDispatchRequestedEvent("agent-1", "plan", "exec", "tenant-1");

            // WHEN
            OperatorResult result = runPromise(() -> operator.process(event));

            // THEN
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutputEvents()).hasSize(1);
            assertThat(result.getOutputEvents().get(0).getType())
                    .isEqualTo(AgentDispatchValidatorOperator.EVENT_DISPATCH_REQUESTED);
        }

        @Test
        @DisplayName("should maintain correct buffer size after processing")
        void shouldMaintainBufferSizeCorrectly() {
            // GIVEN — two events offered
            Event evt1 = buildDispatchRequestedEvent("agent-1", "plan", "exec", "tenant-1");
            Event evt2 = buildDispatchRequestedEvent("agent-2", "plan", "exec", "tenant-1");

            // WHEN — process each one (first drains immediately)
            runPromise(() -> operator.process(evt1));
            // After first: buffer was offered evt1, then drained -> size=0
            // Offer evt2 then drain
            runPromise(() -> operator.process(evt2));

            // THEN — buffer should be empty after each synchronized drain
            assertThat(operator.bufferSize()).isEqualTo(0);
        }

        @Test
        @DisplayName("should be created with default buffer size constant")
        void shouldHaveCorrectDefaultBufferSize() {
            assertThat(BackpressureOperator.DEFAULT_BUFFER_SIZE).isEqualTo(2048);
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
        void setUp() {
            // Use lenient stubbing: isInitialized() is only called in some test cases
            // (tests that reach past the agentId-null guard don't need the stub)
            lenient().when(yappcAgentSystem.isInitialized()).thenReturn(false);
            operator = new AgentExecutorOperator(yappcAgentSystem);
        }

        @Test
        @DisplayName("should emit agent.result.produced with status=error when agent system is not yet initialized")
        void shouldEmitResultProducedEvent() {
            // GIVEN — operator with an uninitialized YappcAgentSystem
            Event event = buildDispatchValidatedEvent("agent-1", "plan", "exec", "tenant-1", "corr-001");

            // WHEN
            OperatorResult result = runPromise(() -> operator.process(event));

            // THEN — OperatorResult is a success (carries the result event),
            // but the result event payload has status=error because the agent system is not initialized.
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutputEvents()).hasSize(1);
            Event out = result.getOutputEvents().get(0);
            assertThat(out.getType()).isEqualTo(AgentExecutorOperator.EVENT_RESULT_PRODUCED);
            assertThat(out.getPayload("status")).isEqualTo("error");
            assertThat(out.getPayload("agentId")).isEqualTo("agent-1");
            assertThat(out.getPayload("correlationId")).isEqualTo("corr-001");
        }

        @Test
        @DisplayName("should fail when agentId is missing from dispatch event")
        void shouldFailWhenAgentIdMissing() {
            // GIVEN — validated event without agentId
            Event event = GEvent.builder()
                    .typeTenantVersion("tenant-1", AgentDispatchValidatorOperator.EVENT_DISPATCH_VALIDATED, "v1")
                    .addPayload("fromStage", "plan")
                    .addPayload("toStage",   "exec")
                    .build();

            // WHEN
            OperatorResult result = runPromise(() -> operator.process(event));

            // THEN
            assertThat(result.isSuccess()).isFalse();
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
        void setUp() {
            // Default threshold = 1 → emit immediately on first result
            operator = new ResultAggregatorOperator();
        }

        @Test
        @DisplayName("should emit workflow.step.completed immediately when threshold=1")
        void shouldEmitStepCompletedImmediately() {
            // GIVEN
            Event event = buildResultProducedEvent("agent-1", "tenant-1", "corr-001", "success");

            // WHEN
            OperatorResult result = runPromise(() -> operator.process(event));

            // THEN
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutputEvents()).hasSize(1);
            Event out = result.getOutputEvents().get(0);
            assertThat(out.getType()).isEqualTo(ResultAggregatorOperator.EVENT_STEP_COMPLETED);
            assertThat(out.getPayload("correlationId")).isEqualTo("corr-001");
            assertThat(out.getPayload("resultCount")).isEqualTo(1);
        }

        @Test
        @DisplayName("should clear bucket after emitting step.completed")
        void shouldClearBucketAfterEmit() {
            // GIVEN
            Event event = buildResultProducedEvent("agent-1", "tenant-1", "corr-002", "success");

            // WHEN
            runPromise(() -> operator.process(event));

            // THEN — bucket for corr-002 should be gone
            assertThat(operator.activeBucketCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("should accumulate results until threshold is met with threshold=2")
        void shouldAccumulateUntilThreshold() {
            // GIVEN
            operator = new ResultAggregatorOperator(2);
            Event first  = buildResultProducedEvent("agent-1", "tenant-1", "corr-003", "success");
            Event second = buildResultProducedEvent("agent-2", "tenant-1", "corr-003", "success");

            // WHEN — first result: below threshold → no output
            OperatorResult r1 = runPromise(() -> operator.process(first));
            assertThat(r1.getOutputEvents()).isEmpty();
            assertThat(operator.activeBucketCount()).isEqualTo(1);

            // WHEN — second result: meets threshold → emit
            OperatorResult r2 = runPromise(() -> operator.process(second));
            assertThat(r2.isSuccess()).isTrue();
            assertThat(r2.getOutputEvents()).hasSize(1);
            assertThat(r2.getOutputEvents().get(0).getType())
                    .isEqualTo(ResultAggregatorOperator.EVENT_STEP_COMPLETED);
            assertThat(operator.activeBucketCount()).isEqualTo(0);
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
        void setUp() {
            operator = new MetricsCollectorOperator();
        }

        @Test
        @DisplayName("should emit agent.metrics.updated for each result event")
        void shouldEmitMetricsUpdatedEvent() {
            // GIVEN
            Event event = buildResultProducedEvent("agent-1", "tenant-1", "corr-001", "success");

            // WHEN
            OperatorResult result = runPromise(() -> operator.process(event));

            // THEN
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutputEvents()).hasSize(1);
            Event out = result.getOutputEvents().get(0);
            assertThat(out.getType()).isEqualTo(MetricsCollectorOperator.EVENT_METRICS_UPDATED);
            assertThat(out.getPayload("agentId")).isEqualTo("agent-1");
            assertThat(out.getPayload("status")).isEqualTo("success");
            assertThat(out.getPayload("agent_executions_total")).isEqualTo(1L);
        }

        @Test
        @DisplayName("should increment totalExecutions counter on each call")
        void shouldIncrementExecutionCounter() {
            // GIVEN
            Event event = buildResultProducedEvent("agent-1", "tenant-1", "corr-001", "success");

            // WHEN — two calls
            runPromise(() -> operator.process(event));
            runPromise(() -> operator.process(event));

            // THEN
            assertThat(operator.getTotalExecutions()).isEqualTo(2L);
        }

        @Test
        @DisplayName("should start with zero total executions")
        void shouldStartAtZero() {
            assertThat(operator.getTotalExecutions()).isEqualTo(0L);
        }
    }
}
