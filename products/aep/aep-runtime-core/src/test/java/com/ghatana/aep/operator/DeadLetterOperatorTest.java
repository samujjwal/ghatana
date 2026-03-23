/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.aep.operator;

import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.agent.framework.memory.Episode;
import com.ghatana.agent.framework.memory.Fact;
import com.ghatana.agent.framework.memory.Policy;
import com.ghatana.agent.framework.memory.Preference;
import com.ghatana.agent.framework.runtime.AbstractTypedAgent;
import com.ghatana.platform.resilience.DeadLetterQueue;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DeadLetterOperator}.
 *
 * <p>Covers:
 * <ul>
 *   <li>Successful event pass-through to delegate</li>
 *   <li>Failed events are routed to DLQ instead of propagating the error</li>
 *   <li>DLQ receipt fields (_status, _dlqId, _error, _operator) are populated</li>
 *   <li>Metrics (totalProcessed, totalDeadLettered, dlqSize) are accurate</li>
 *   <li>Builder rejects null delegate / null DLQ</li>
 * </ul>
 *
 * @doc.type test
 * @doc.purpose Unit tests for DeadLetterOperator
 * @doc.layer product-aep
 * @doc.pattern Unit Test
 */
@DisplayName("DeadLetterOperator")
class DeadLetterOperatorTest extends EventloopTestBase {

    private static final String TENANT = "test-tenant";

    private DeadLetterQueue dlq;
    private AgentContext ctx;

    @BeforeEach
    void setUp() {
        dlq = DeadLetterQueue.builder()
                .maxSize(1000)
                .ttl(Duration.ofHours(1))
                .enableReplay(false)
                .build();

        ctx = AgentContext.builder()
                .turnId("turn-1")
                .agentId("test-operator-agent")
                .tenantId(TENANT)
                .memoryStore(MemoryStore.noOp())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Happy path
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Successful processing")
    class SuccessPath {

        @Test
        @DisplayName("passes result through when delegate succeeds")
        void shouldPassThroughOnSuccess() {
            Map<String, Object> expectedOutput = Map.of("result", "processed", "score", 0.99);

            AgentEventOperator delegate = operatorThatReturns(expectedOutput);
            DeadLetterOperator op = DeadLetterOperator.builder()
                    .delegate(delegate)
                    .deadLetterQueue(dlq)
                    .build();

            Map<String, Object> event = Map.of("id", "evt-1", "type", "sensor.data");
            Map<String, Object> result = runPromise(() -> op.submit(ctx, event));

            assertThat(result).isEqualTo(expectedOutput);
            assertThat(op.getTotalProcessed()).isEqualTo(1);
            assertThat(op.getTotalDeadLettered()).isEqualTo(0);
            assertThat(op.getDlqSize()).isEqualTo(0);
        }

        @Test
        @DisplayName("processes multiple events without DLQ entries when all succeed")
        void shouldProcessMultipleEventsWithoutDlq() {
            AgentEventOperator delegate = operatorThatReturns(Map.of("ok", true));
            DeadLetterOperator op = DeadLetterOperator.builder()
                    .delegate(delegate)
                    .deadLetterQueue(dlq)
                    .build();

            for (int i = 0; i < 10; i++) {
                Map<String, Object> event = Map.of("i", (Object) i);
                runPromise(() -> op.submit(ctx, event));
            }

            assertThat(op.getTotalProcessed()).isEqualTo(10);
            assertThat(op.getTotalDeadLettered()).isEqualTo(0);
            assertThat(op.getDlqSize()).isEqualTo(0);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Failure routing
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Failure routing to DLQ")
    class FailurePath {

        @Test
        @DisplayName("routes failed event to DLQ and returns DLQ receipt instead of error")
        void shouldRouteToDlqOnFailure() {
            RuntimeException cause = new RuntimeException("upstream-timeout");
            AgentEventOperator delegate = operatorThatThrows(cause);
            DeadLetterOperator op = DeadLetterOperator.builder()
                    .delegate(delegate)
                    .deadLetterQueue(dlq)
                    .build();

            Map<String, Object> event = Map.of("id", "evt-fail", "type", "bad.event");

            // Must NOT throw — failure is absorbed and a receipt returned
            Map<String, Object> receipt = runPromise(() -> op.submit(ctx, event));

            assertThat(receipt).containsKey("_dlqId");
            assertThat(receipt.get("_status")).isEqualTo("DEAD_LETTERED");
            assertThat(receipt.get("_error")).asString().contains("upstream-timeout");
            assertThat(receipt.get("_operator")).asString().contains("test-fail-agent");
        }

        @Test
        @DisplayName("increments dead-letter counter and DLQ size on failure")
        void shouldIncrementMetricsOnFailure() {
            AgentEventOperator delegate = operatorThatThrows(new RuntimeException("boom"));
            DeadLetterOperator op = DeadLetterOperator.builder()
                    .delegate(delegate)
                    .deadLetterQueue(dlq)
                    .build();

            runPromise(() -> op.submit(ctx, Map.of("id", "e1")));
            runPromise(() -> op.submit(ctx, Map.of("id", "e2")));
            runPromise(() -> op.submit(ctx, Map.of("id", "e3")));

            assertThat(op.getTotalProcessed()).isEqualTo(3);
            assertThat(op.getTotalDeadLettered()).isEqualTo(3);
            assertThat(op.getDlqSize()).isEqualTo(3);
        }

        @Test
        @DisplayName("mixed success/failure: only failed events go to DLQ")
        void shouldOnlyDeadLetterFailedEvents() {
            // Alternate success / failure based on event "fail" key
            AgentEventOperator delegate = operatorThatFailsIfFlagSet();
            DeadLetterOperator op = DeadLetterOperator.builder()
                    .delegate(delegate)
                    .deadLetterQueue(dlq)
                    .build();

            runPromise(() -> op.submit(ctx, Map.of("fail", false)));
            runPromise(() -> op.submit(ctx, Map.of("fail", true)));
            runPromise(() -> op.submit(ctx, Map.of("fail", false)));
            runPromise(() -> op.submit(ctx, Map.of("fail", true)));

            assertThat(op.getTotalProcessed()).isEqualTo(4);
            assertThat(op.getTotalDeadLettered()).isEqualTo(2);
            assertThat(op.getDlqSize()).isEqualTo(2);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Builder validation
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Builder")
    class BuilderValidation {

        @Test
        @DisplayName("rejects null delegate")
        void shouldRejectNullDelegate() {
            assertThatThrownBy(() ->
                    DeadLetterOperator.builder()
                            .deadLetterQueue(dlq)
                            .build()
            ).isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("delegate");
        }

        @Test
        @DisplayName("rejects null dead-letter queue")
        void shouldRejectNullDlq() {
            AgentEventOperator delegate = operatorThatReturns(Map.of());
            assertThatThrownBy(() ->
                    DeadLetterOperator.builder()
                            .delegate(delegate)
                            .build()
            ).isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("deadLetterQueue");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Returns an AgentEventOperator backed by an agent that always returns the given output. */
    private AgentEventOperator operatorThatReturns(Map<String, Object> output) {
        TypedAgent<Map<String, Object>, Map<String, Object>> agent =
                new StubTypedAgent("test-success-agent") {
                    @Override
                    protected Promise<AgentResult<Map<String, Object>>> doProcess(
                            @NotNull AgentContext ctx, @NotNull Map<String, Object> input) {
                        return Promise.of(AgentResult.success(output, descriptor().getAgentId(),
                                Duration.ofMillis(1)));
                    }
                };
        // Initialize the agent to READY state
        agent.initialize(AgentConfig.builder().agentId("test-success-agent").build());
        return new AgentEventOperator(agent);
    }

    /** Returns an AgentEventOperator backed by an agent that always throws. */
    private AgentEventOperator operatorThatThrows(RuntimeException ex) {
        TypedAgent<Map<String, Object>, Map<String, Object>> agent =
                new StubTypedAgent("test-fail-agent") {
                    @Override
                    protected Promise<AgentResult<Map<String, Object>>> doProcess(
                            @NotNull AgentContext ctx, @NotNull Map<String, Object> input) {
                        return Promise.ofException(ex);
                    }
                };
        // Initialize the agent to READY state
        agent.initialize(AgentConfig.builder().agentId("test-fail-agent").build());
        return new AgentEventOperator(agent);
    }

    /** Returns an AgentEventOperator that fails when event has "fail"=true. */
    private AgentEventOperator operatorThatFailsIfFlagSet() {
        TypedAgent<Map<String, Object>, Map<String, Object>> agent =
                new StubTypedAgent("test-conditional-agent") {
                    @Override
                    protected Promise<AgentResult<Map<String, Object>>> doProcess(
                            @NotNull AgentContext ctx, @NotNull Map<String, Object> input) {
                        boolean shouldFail = Boolean.TRUE.equals(input.get("fail"));
                        if (shouldFail) {
                            return Promise.ofException(new RuntimeException("conditional-failure"));
                        }
                        return Promise.of(AgentResult.success(
                                Map.of("ok", true), descriptor().getAgentId(), Duration.ofMillis(1)));
                    }
                };
        // Initialize the agent to READY state
        agent.initialize(AgentConfig.builder().agentId("test-conditional-agent").build());
        return new AgentEventOperator(agent);
    }

    /**
     * Minimal AbstractTypedAgent stub for test use.
     * Subclasses only need to implement doProcess.
     */
    private abstract static class StubTypedAgent
            extends AbstractTypedAgent<Map<String, Object>, Map<String, Object>> {

        private final String agentId;

        StubTypedAgent(String agentId) {
            this.agentId = agentId;
        }

        @Override
        public AgentDescriptor descriptor() {
            return AgentDescriptor.builder()
                    .agentId(agentId)
                    .type(AgentType.DETERMINISTIC)
                    .version("1.0.0")
                    .build();
        }
    }
}
