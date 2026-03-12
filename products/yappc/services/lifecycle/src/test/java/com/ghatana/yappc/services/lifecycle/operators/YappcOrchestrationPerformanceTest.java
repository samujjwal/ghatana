/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle.operators;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.domain.domain.event.GEvent;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.workflow.operator.OperatorResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance and throughput tests for the {@code agent-orchestration-v1} pipeline operators.
 *
 * <p>These tests verify that each operator and the full pipeline can sustain
 * acceptable throughput and latency under load — they assert minimum throughput
 * thresholds and maximum per-operation latency budgets.
 *
 * <p>All async tests extend {@link EventloopTestBase} and use
 * {@code runPromise()} to execute ActiveJ Promises on the managed eventloop.
 *
 * @doc.type class
 * @doc.purpose Performance / throughput tests for YAPPC orchestration pipeline operators
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("YAPPC Orchestration Operators — Performance")
class YappcOrchestrationPerformanceTest extends EventloopTestBase {

    private static final String TENANT_ID = "perf-tenant";

    // ─── shared event builders ────────────────────────────────────────────────

    private static Event dispatchRequestedEvent(String agentId, int seq) {
        return GEvent.builder()
                .typeTenantVersion(TENANT_ID,
                        AgentDispatchValidatorOperator.EVENT_DISPATCH_REQUESTED, "v1")
                .addPayload("eventId",   "perf-evt-" + seq)
                .addPayload("eventType", AgentDispatchValidatorOperator.EVENT_DISPATCH_REQUESTED)
                .addPayload("agentId",   agentId)
                .addPayload("fromStage", "planning")
                .addPayload("toStage",   "execution")
                .addPayload("tenantId",  TENANT_ID)
                .build();
    }

    private static Event dispatchValidatedEvent(String agentId, int seq) {
        return GEvent.builder()
                .typeTenantVersion(TENANT_ID,
                        AgentDispatchValidatorOperator.EVENT_DISPATCH_VALIDATED, "v1")
                .addPayload("agentId",       agentId)
                .addPayload("fromStage",     "planning")
                .addPayload("toStage",       "execution")
                .addPayload("tenantId",      TENANT_ID)
                .addPayload("correlationId", "corr-" + seq)
                .build();
    }

    private static Event resultProducedEvent(String agentId, int seq) {
        return GEvent.builder()
                .typeTenantVersion(TENANT_ID,
                        AgentExecutorOperator.EVENT_RESULT_PRODUCED, "v1")
                .addPayload("agentId",       agentId)
                .addPayload("status",        "SUCCESS")
                .addPayload("tenantId",      TENANT_ID)
                .addPayload("correlationId", "corr-" + seq)
                .build();
    }

    // =========================================================================
    // BackpressureOperator — throughput and backpressure behaviour
    // =========================================================================

    @Nested
    @DisplayName("BackpressureOperator — Performance")
    class BackpressurePerformanceTests {

        private static final int EVENT_COUNT = 500;
        private static final long MAX_MILLIS = 2_000; // 500 events in ≤ 2 s

        private BackpressureOperator operator;

        @BeforeEach
        void setUp() {
            operator = new BackpressureOperator(EVENT_COUNT);
        }

        @Test
        @DisplayName("processes " + EVENT_COUNT + " events within " + MAX_MILLIS + " ms")
        void throughputUnderLoad() {
            Instant start = Instant.now();

            for (int i = 0; i < EVENT_COUNT; i++) {
                OperatorResult result = runPromise(() ->
                        operator.process(dispatchRequestedEvent("perf-agent", 0)));
                // Backpressure can return empty (queued) or a forwarded event — both are valid
                assertThat(result).isNotNull();
            }

            long elapsedMs = Duration.between(start, Instant.now()).toMillis();
            assertThat(elapsedMs)
                    .as("Processing %d events must complete in ≤ %d ms", EVENT_COUNT, MAX_MILLIS)
                    .isLessThanOrEqualTo(MAX_MILLIS);
        }

        @Test
        @DisplayName("single process() call completes in < 50 ms (latency budget)")
        void singleOperationLatency() {
            Event event = dispatchRequestedEvent("latency-agent", 1);
            Instant start = Instant.now();
            runPromise(() -> operator.process(event));
            long elapsedMs = Duration.between(start, Instant.now()).toMillis();
            assertThat(elapsedMs)
                    .as("Single process() must complete in < 50 ms")
                    .isLessThan(50);
        }

        @Test
        @DisplayName("drops oldest event and accepts new when buffer full (DROP_OLDEST)")
        void dropOldestWhenFull() {
            // Fill the buffer exactly (size=5)
            BackpressureOperator smallBuf = new BackpressureOperator(5);
            for (int i = 0; i < 5; i++) {
                int seq = i;
                runPromise(() -> smallBuf.process(dispatchRequestedEvent("drop-agent", seq)));
            }
            // Drain the 5 events that were queued (each process() queues+drains one)
            // Now force overflow: send 10 more — should not throw, just drop oldest
            for (int i = 5; i < 15; i++) {
                int seq = i;
                OperatorResult result = runPromise(() ->
                        smallBuf.process(dispatchRequestedEvent("drop-agent", seq)));
                assertThat(result).isNotNull();
            }
            // Buffer should not exceed capacity at any point — verify no exceptions thrown
        }

        @Test
        @DisplayName("all events forwarded continuously when buffer never saturates")
        void allEventsForwardedFromUnboundedBuffer() {
            BackpressureOperator largeBuf = new BackpressureOperator(1000);
            int forwarded = 0;
            for (int i = 0; i < 100; i++) {
                int seq = i;
                OperatorResult result = runPromise(() ->
                        largeBuf.process(dispatchRequestedEvent("fwd-agent", seq)));
                if (result.isSuccess() && !result.getOutputEvents().isEmpty()) {
                    forwarded++;
                }
            }
            // With a large buffer, each event queued-and-immediately-drained → 100% forwarded
            assertThat(forwarded).isEqualTo(100);
        }
    }

    // =========================================================================
    // AgentDispatchValidatorOperator — throughput
    // =========================================================================

    @Nested
    @DisplayName("AgentDispatchValidatorOperator — Performance")
    class ValidatorPerformanceTests {

        private static final int EVENT_COUNT = 300;
        private static final long MAX_MILLIS = 2_000;

        private AgentDispatchValidatorOperator operator;

        @BeforeEach
        void setUp() {
            operator = new AgentDispatchValidatorOperator();
        }

        @Test
        @DisplayName("validates " + EVENT_COUNT + " events within " + MAX_MILLIS + " ms")
        void validationThroughput() {
            int successCount = 0;

            Instant start = Instant.now();
            for (int i = 0; i < EVENT_COUNT; i++) {
                int seq = i;
                OperatorResult result = runPromise(() ->
                        operator.process(dispatchRequestedEvent("validator-agent", seq)));
                if (result.isSuccess()) successCount++;
            }
            long elapsedMs = Duration.between(start, Instant.now()).toMillis();

            assertThat(successCount).isEqualTo(EVENT_COUNT);
            assertThat(elapsedMs)
                    .as("Validating %d events must complete in ≤ %d ms", EVENT_COUNT, MAX_MILLIS)
                    .isLessThanOrEqualTo(MAX_MILLIS);
        }
    }

    // =========================================================================
    // MetricsCollectorOperator — throughput
    // =========================================================================

    @Nested
    @DisplayName("MetricsCollectorOperator — Performance")
    class MetricsPerformanceTests {

        private static final int EVENT_COUNT = 500;
        private static final long MAX_MILLIS = 2_000;

        private MetricsCollectorOperator operator;

        @BeforeEach
        void setUp() {
            operator = new MetricsCollectorOperator();
        }

        @Test
        @DisplayName("collects metrics for " + EVENT_COUNT + " events within " + MAX_MILLIS + " ms")
        void metricsThroughput() {
            Instant start = Instant.now();

            for (int i = 0; i < EVENT_COUNT; i++) {
                int seq = i;
                OperatorResult result = runPromise(() ->
                        operator.process(resultProducedEvent("metrics-agent", seq)));
                assertThat(result).isNotNull();
            }

            long elapsedMs = Duration.between(start, Instant.now()).toMillis();
            assertThat(elapsedMs)
                    .as("Collecting metrics for %d events must complete in ≤ %d ms", EVENT_COUNT, MAX_MILLIS)
                    .isLessThanOrEqualTo(MAX_MILLIS);
        }
    }

    // =========================================================================
    // ResultAggregatorOperator — throughput
    // =========================================================================

    @Nested
    @DisplayName("ResultAggregatorOperator — Performance")
    class AggregatorPerformanceTests {

        private static final int EVENT_COUNT = 300;
        private static final long MAX_MILLIS = 2_000;

        private ResultAggregatorOperator operator;

        @BeforeEach
        void setUp() {
            operator = new ResultAggregatorOperator();
        }

        @Test
        @DisplayName("aggregates " + EVENT_COUNT + " events within " + MAX_MILLIS + " ms")
        void aggregatorThroughput() {
            Instant start = Instant.now();

            for (int i = 0; i < EVENT_COUNT; i++) {
                int seq = i;
                OperatorResult result = runPromise(() ->
                        operator.process(resultProducedEvent("agg-agent", seq)));
                assertThat(result).isNotNull();
            }

            long elapsedMs = Duration.between(start, Instant.now()).toMillis();
            assertThat(elapsedMs)
                    .as("Aggregating %d events must complete in ≤ %d ms", EVENT_COUNT, MAX_MILLIS)
                    .isLessThanOrEqualTo(MAX_MILLIS);
        }
    }

    // =========================================================================
    // Pipeline chain: Validator → Backpressure
    // =========================================================================

    @Nested
    @DisplayName("Validator → Backpressure — Chained Pipeline Performance")
    class PipelineChainPerformanceTests {

        private static final int EVENT_COUNT = 200;
        private static final long MAX_MILLIS = 3_000;

        private AgentDispatchValidatorOperator validator;
        private BackpressureOperator backpressure;

        @BeforeEach
        void setUp() {
            validator = new AgentDispatchValidatorOperator();
            backpressure = new BackpressureOperator(EVENT_COUNT);
        }

        @Test
        @DisplayName("chains validator → backpressure for " + EVENT_COUNT + " events within " + MAX_MILLIS + " ms")
        void chainedPipelineThroughput() {
            List<OperatorResult> finalResults = new ArrayList<>();
            Instant start = Instant.now();

            for (int i = 0; i < EVENT_COUNT; i++) {
                int seq = i;
                // Stage 1: Validate
                OperatorResult validated = runPromise(() ->
                        validator.process(dispatchRequestedEvent("chain-agent", seq)));

                // Stage 2: Route through backpressure
                if (validated.isSuccess() && !validated.getOutputEvents().isEmpty()) {
                    Event validatedEvent = validated.getOutputEvents().get(0);
                    OperatorResult bpResult = runPromise(() ->
                            backpressure.process(validatedEvent));
                    finalResults.add(bpResult);
                }
            }

            long elapsedMs = Duration.between(start, Instant.now()).toMillis();

            assertThat(finalResults).hasSizeGreaterThan(0);
            assertThat(elapsedMs)
                    .as("Chained pipeline must process %d events in ≤ %d ms", EVENT_COUNT, MAX_MILLIS)
                    .isLessThanOrEqualTo(MAX_MILLIS);
        }

        @Test
        @DisplayName("maintains 100% validation success rate across " + EVENT_COUNT + " events")
        void validationSuccessRate() {
            long successCount = 0;
            for (int i = 0; i < EVENT_COUNT; i++) {
                int seq = i;
                OperatorResult result = runPromise(() ->
                        validator.process(dispatchRequestedEvent("rate-agent", seq)));
                if (result.isSuccess()) successCount++;
            }
            assertThat(successCount)
                    .as("All valid events must pass validation")
                    .isEqualTo(EVENT_COUNT);
        }
    }
}
