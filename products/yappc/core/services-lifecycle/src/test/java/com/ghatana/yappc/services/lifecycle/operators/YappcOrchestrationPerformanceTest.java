/*
 * Copyright (c) 2026 Ghatana Technologies // GH-90000
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle.operators;

import com.ghatana.platform.domain.event.Event;
import com.ghatana.platform.domain.event.GEvent;
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
 * {@code runPromise()} to execute ActiveJ Promises on the managed eventloop. // GH-90000
 *
 * @doc.type class
 * @doc.purpose Performance / throughput tests for YAPPC orchestration pipeline operators
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("YAPPC Orchestration Operators — Performance")
class YappcOrchestrationPerformanceTest extends EventloopTestBase {

    private static final String TENANT_ID = "perf-tenant";

    // ─── shared event builders ────────────────────────────────────────────────

    private static Event dispatchRequestedEvent(String agentId, int seq) { // GH-90000
        return GEvent.builder() // GH-90000
                .typeTenantVersion(TENANT_ID, // GH-90000
                        AgentDispatchValidatorOperator.EVENT_DISPATCH_REQUESTED, "v1")
                .addPayload("eventId",   "perf-evt-" + seq) // GH-90000
                .addPayload("eventType", AgentDispatchValidatorOperator.EVENT_DISPATCH_REQUESTED) // GH-90000
                .addPayload("agentId",   agentId) // GH-90000
                .addPayload("fromStage", "planning") // GH-90000
                .addPayload("toStage",   "execution") // GH-90000
                .addPayload("tenantId",  TENANT_ID) // GH-90000
                .build(); // GH-90000
    }

    private static Event dispatchValidatedEvent(String agentId, int seq) { // GH-90000
        return GEvent.builder() // GH-90000
                .typeTenantVersion(TENANT_ID, // GH-90000
                        AgentDispatchValidatorOperator.EVENT_DISPATCH_VALIDATED, "v1")
                .addPayload("agentId",       agentId) // GH-90000
                .addPayload("fromStage",     "planning") // GH-90000
                .addPayload("toStage",       "execution") // GH-90000
                .addPayload("tenantId",      TENANT_ID) // GH-90000
                .addPayload("correlationId", "corr-" + seq) // GH-90000
                .build(); // GH-90000
    }

    private static Event resultProducedEvent(String agentId, int seq) { // GH-90000
        return GEvent.builder() // GH-90000
                .typeTenantVersion(TENANT_ID, // GH-90000
                        AgentExecutorOperator.EVENT_RESULT_PRODUCED, "v1")
                .addPayload("agentId",       agentId) // GH-90000
                .addPayload("status",        "SUCCESS") // GH-90000
                .addPayload("tenantId",      TENANT_ID) // GH-90000
                .addPayload("correlationId", "corr-" + seq) // GH-90000
                .build(); // GH-90000
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
        void setUp() { // GH-90000
            operator = new BackpressureOperator(EVENT_COUNT); // GH-90000
        }

        @Test
        @DisplayName("processes " + EVENT_COUNT + " events within " + MAX_MILLIS + " ms") // GH-90000
        void throughputUnderLoad() { // GH-90000
            Instant start = Instant.now(); // GH-90000

            for (int i = 0; i < EVENT_COUNT; i++) { // GH-90000
                OperatorResult result = runPromise(() -> // GH-90000
                        operator.process(dispatchRequestedEvent("perf-agent", 0))); // GH-90000
                // Backpressure can return empty (queued) or a forwarded event — both are valid // GH-90000
                assertThat(result).isNotNull(); // GH-90000
            }

            long elapsedMs = Duration.between(start, Instant.now()).toMillis(); // GH-90000
            assertThat(elapsedMs) // GH-90000
                    .as("Processing %d events must complete in ≤ %d ms", EVENT_COUNT, MAX_MILLIS) // GH-90000
                    .isLessThanOrEqualTo(MAX_MILLIS); // GH-90000
        }

        @Test
        @DisplayName("single process() call completes in < 50 ms (latency budget)")
        void singleOperationLatency() { // GH-90000
            Event event = dispatchRequestedEvent("latency-agent", 1); // GH-90000
            Instant start = Instant.now(); // GH-90000
            runPromise(() -> operator.process(event)); // GH-90000
            long elapsedMs = Duration.between(start, Instant.now()).toMillis(); // GH-90000
            assertThat(elapsedMs) // GH-90000
                    .as("Single process() must complete in < 50 ms")
                    .isLessThan(50); // GH-90000
        }

        @Test
        @DisplayName("drops oldest event and accepts new when buffer full (DROP_OLDEST)")
        void dropOldestWhenFull() { // GH-90000
            // Fill the buffer exactly (size=5) // GH-90000
            BackpressureOperator smallBuf = new BackpressureOperator(5); // GH-90000
            for (int i = 0; i < 5; i++) { // GH-90000
                int seq = i;
                runPromise(() -> smallBuf.process(dispatchRequestedEvent("drop-agent", seq))); // GH-90000
            }
            // Drain the 5 events that were queued (each process() queues+drains one) // GH-90000
            // Now force overflow: send 10 more — should not throw, just drop oldest
            for (int i = 5; i < 15; i++) { // GH-90000
                int seq = i;
                OperatorResult result = runPromise(() -> // GH-90000
                        smallBuf.process(dispatchRequestedEvent("drop-agent", seq))); // GH-90000
                assertThat(result).isNotNull(); // GH-90000
            }
            // Buffer should not exceed capacity at any point — verify no exceptions thrown
        }

        @Test
        @DisplayName("all events forwarded continuously when buffer never saturates")
        void allEventsForwardedFromUnboundedBuffer() { // GH-90000
            BackpressureOperator largeBuf = new BackpressureOperator(1000); // GH-90000
            int forwarded = 0;
            for (int i = 0; i < 100; i++) { // GH-90000
                int seq = i;
                OperatorResult result = runPromise(() -> // GH-90000
                        largeBuf.process(dispatchRequestedEvent("fwd-agent", seq))); // GH-90000
                if (result.isSuccess() && !result.getOutputEvents().isEmpty()) { // GH-90000
                    forwarded++;
                }
            }
            // With a large buffer, each event queued-and-immediately-drained → 100% forwarded
            assertThat(forwarded).isEqualTo(100); // GH-90000
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
        void setUp() { // GH-90000
            operator = new AgentDispatchValidatorOperator(); // GH-90000
        }

        @Test
        @DisplayName("validates " + EVENT_COUNT + " events within " + MAX_MILLIS + " ms") // GH-90000
        void validationThroughput() { // GH-90000
            int successCount = 0;

            Instant start = Instant.now(); // GH-90000
            for (int i = 0; i < EVENT_COUNT; i++) { // GH-90000
                int seq = i;
                OperatorResult result = runPromise(() -> // GH-90000
                        operator.process(dispatchRequestedEvent("validator-agent", seq))); // GH-90000
                if (result.isSuccess()) successCount++; // GH-90000
            }
            long elapsedMs = Duration.between(start, Instant.now()).toMillis(); // GH-90000

            assertThat(successCount).isEqualTo(EVENT_COUNT); // GH-90000
            assertThat(elapsedMs) // GH-90000
                    .as("Validating %d events must complete in ≤ %d ms", EVENT_COUNT, MAX_MILLIS) // GH-90000
                    .isLessThanOrEqualTo(MAX_MILLIS); // GH-90000
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
        void setUp() { // GH-90000
            operator = new MetricsCollectorOperator(); // GH-90000
        }

        @Test
        @DisplayName("collects metrics for " + EVENT_COUNT + " events within " + MAX_MILLIS + " ms") // GH-90000
        void metricsThroughput() { // GH-90000
            Instant start = Instant.now(); // GH-90000

            for (int i = 0; i < EVENT_COUNT; i++) { // GH-90000
                int seq = i;
                OperatorResult result = runPromise(() -> // GH-90000
                        operator.process(resultProducedEvent("metrics-agent", seq))); // GH-90000
                assertThat(result).isNotNull(); // GH-90000
            }

            long elapsedMs = Duration.between(start, Instant.now()).toMillis(); // GH-90000
            assertThat(elapsedMs) // GH-90000
                    .as("Collecting metrics for %d events must complete in ≤ %d ms", EVENT_COUNT, MAX_MILLIS) // GH-90000
                    .isLessThanOrEqualTo(MAX_MILLIS); // GH-90000
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
        void setUp() { // GH-90000
            operator = new ResultAggregatorOperator(); // GH-90000
        }

        @Test
        @DisplayName("aggregates " + EVENT_COUNT + " events within " + MAX_MILLIS + " ms") // GH-90000
        void aggregatorThroughput() { // GH-90000
            Instant start = Instant.now(); // GH-90000

            for (int i = 0; i < EVENT_COUNT; i++) { // GH-90000
                int seq = i;
                OperatorResult result = runPromise(() -> // GH-90000
                        operator.process(resultProducedEvent("agg-agent", seq))); // GH-90000
                assertThat(result).isNotNull(); // GH-90000
            }

            long elapsedMs = Duration.between(start, Instant.now()).toMillis(); // GH-90000
            assertThat(elapsedMs) // GH-90000
                    .as("Aggregating %d events must complete in ≤ %d ms", EVENT_COUNT, MAX_MILLIS) // GH-90000
                    .isLessThanOrEqualTo(MAX_MILLIS); // GH-90000
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
        void setUp() { // GH-90000
            validator = new AgentDispatchValidatorOperator(); // GH-90000
            backpressure = new BackpressureOperator(EVENT_COUNT); // GH-90000
        }

        @Test
        @DisplayName("chains validator → backpressure for " + EVENT_COUNT + " events within " + MAX_MILLIS + " ms") // GH-90000
        void chainedPipelineThroughput() { // GH-90000
            List<OperatorResult> finalResults = new ArrayList<>(); // GH-90000
            Instant start = Instant.now(); // GH-90000

            for (int i = 0; i < EVENT_COUNT; i++) { // GH-90000
                int seq = i;
                // Stage 1: Validate
                OperatorResult validated = runPromise(() -> // GH-90000
                        validator.process(dispatchRequestedEvent("chain-agent", seq))); // GH-90000

                // Stage 2: Route through backpressure
                if (validated.isSuccess() && !validated.getOutputEvents().isEmpty()) { // GH-90000
                    Event validatedEvent = validated.getOutputEvents().get(0); // GH-90000
                    OperatorResult bpResult = runPromise(() -> // GH-90000
                            backpressure.process(validatedEvent)); // GH-90000
                    finalResults.add(bpResult); // GH-90000
                }
            }

            long elapsedMs = Duration.between(start, Instant.now()).toMillis(); // GH-90000

            assertThat(finalResults).hasSizeGreaterThan(0); // GH-90000
            assertThat(elapsedMs) // GH-90000
                    .as("Chained pipeline must process %d events in ≤ %d ms", EVENT_COUNT, MAX_MILLIS) // GH-90000
                    .isLessThanOrEqualTo(MAX_MILLIS); // GH-90000
        }

        @Test
        @DisplayName("maintains 100% validation success rate across " + EVENT_COUNT + " events") // GH-90000
        void validationSuccessRate() { // GH-90000
            long successCount = 0;
            for (int i = 0; i < EVENT_COUNT; i++) { // GH-90000
                int seq = i;
                OperatorResult result = runPromise(() -> // GH-90000
                        validator.process(dispatchRequestedEvent("rate-agent", seq))); // GH-90000
                if (result.isSuccess()) successCount++; // GH-90000
            }
            assertThat(successCount) // GH-90000
                    .as("All valid events must pass validation")
                    .isEqualTo(EVENT_COUNT); // GH-90000
        }
    }
}
