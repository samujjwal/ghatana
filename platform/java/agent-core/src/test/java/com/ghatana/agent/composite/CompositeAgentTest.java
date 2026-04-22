/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
 */

package com.ghatana.agent.composite;

import com.ghatana.agent.*;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.agent.framework.runtime.AbstractTypedAgent;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Comprehensive tests for CompositeAgent — all aggregation strategies.
 */
@DisplayName("Composite Agent [GH-90000]")
class CompositeAgentTest {

    private AgentContext ctx;

    @BeforeEach
    void setUp() { // GH-90000
        ctx = AgentContext.builder() // GH-90000
                .turnId("turn-1 [GH-90000]")
                .agentId("composite-test [GH-90000]")
                .tenantId("test-tenant [GH-90000]")
                .memoryStore(mock(MemoryStore.class)) // GH-90000
                .build(); // GH-90000
    }

    private <T> T runOnEventloop(java.util.function.Supplier<Promise<T>> supplier) { // GH-90000
        AtomicReference<T> result = new AtomicReference<>(); // GH-90000
        AtomicReference<Exception> err = new AtomicReference<>(); // GH-90000
        Eventloop eventloop = Eventloop.builder().withCurrentThread().build(); // GH-90000
        eventloop.post(() -> supplier.get() // GH-90000
                .whenResult(result::set) // GH-90000
                .whenException(err::set)); // GH-90000
        eventloop.run(); // GH-90000
        if (err.get() != null) throw new RuntimeException(err.get()); // GH-90000
        return result.get(); // GH-90000
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Stub Agent
    // ═══════════════════════════════════════════════════════════════════════════

    static class StubAgent extends AbstractTypedAgent<Map<String, Object>, Map<String, Object>> {
        private final AgentDescriptor desc;
        private final Map<String, Object> output;
        private final double confidence;
        private final boolean shouldFail;

        StubAgent(String id, Map<String, Object> output, double confidence) { // GH-90000
            this(id, output, confidence, false); // GH-90000
        }

        StubAgent(String id, Map<String, Object> output, double confidence, boolean shouldFail) { // GH-90000
            this.desc = AgentDescriptor.builder() // GH-90000
                    .agentId(id).name(id).version("1.0 [GH-90000]")
                    .type(AgentType.DETERMINISTIC).build(); // GH-90000
            this.output = output;
            this.confidence = confidence;
            this.shouldFail = shouldFail;
        }

        @Override public @NotNull AgentDescriptor descriptor() { return desc; } // GH-90000

        @Override protected @NotNull Promise<AgentResult<Map<String, Object>>> doProcess( // GH-90000
                @NotNull AgentContext ctx, @NotNull Map<String, Object> input) {
            if (shouldFail) { // GH-90000
                return Promise.ofException(new RuntimeException("stub fail: " + desc.getAgentId())); // GH-90000
            }
            return Promise.of(AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(output) // GH-90000
                    .confidence(confidence) // GH-90000
                    .status(AgentResultStatus.SUCCESS) // GH-90000
                    .agentId(desc.getAgentId()) // GH-90000
                    .processingTime(Duration.ofMillis(3)) // GH-90000
                    .build()); // GH-90000
        }
    }

    private CompositeAgent createComposite(String id, CompositeAgentConfig.AggregationStrategy strategy, // GH-90000
                                            List<StubAgent> subs, List<Double> weights) {
        CompositeAgent agent = new CompositeAgent(id); // GH-90000

        var configBuilder = CompositeAgentConfig.builder() // GH-90000
                .agentId(id) // GH-90000
                .type(AgentType.COMPOSITE) // GH-90000
                .aggregationStrategy(strategy); // GH-90000

        for (Double w : weights) { // GH-90000
            configBuilder.weight(w); // GH-90000
        }

        AgentConfig stubConfig = AgentConfig.builder().agentId("s [GH-90000]").type(AgentType.DETERMINISTIC).build();
        for (StubAgent s : subs) { // GH-90000
            runOnEventloop(() -> s.initialize(stubConfig)); // GH-90000
        }

        agent.setSubAgents(new ArrayList<>(subs)); // GH-90000
        runOnEventloop(() -> agent.initialize(configBuilder.build())); // GH-90000
        return agent;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Weighted Average
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("WEIGHTED_AVERAGE [GH-90000]")
    class WeightedAverageTests {

        @Test void computesWeightedScore() { // GH-90000
            StubAgent a = new StubAgent("a", Map.of("score", 80.0), 0.9); // GH-90000
            StubAgent b = new StubAgent("b", Map.of("score", 60.0), 0.8); // GH-90000

            CompositeAgent agent = createComposite("wavg", // GH-90000
                    CompositeAgentConfig.AggregationStrategy.WEIGHTED_AVERAGE,
                    List.of(a, b), List.of(0.7, 0.3)); // GH-90000

            var result = runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000
            assertThat(result.isSuccess()).isTrue(); // GH-90000
            // Weighted: 0.7*80 + 0.3*60 = 56 + 18 = 74
            Object score = result.getOutput().get("score [GH-90000]");
            assertThat(((Number) score).doubleValue()).isCloseTo(74.0, within(0.1)); // GH-90000
        }

        @Test void handlesEmptyWeightsUniformly() { // GH-90000
            StubAgent a = new StubAgent("a", Map.of("score", 90.0), 0.9); // GH-90000
            StubAgent b = new StubAgent("b", Map.of("score", 70.0), 0.8); // GH-90000

            CompositeAgent agent = createComposite("wavg-uniform", // GH-90000
                    CompositeAgentConfig.AggregationStrategy.WEIGHTED_AVERAGE,
                    List.of(a, b), List.of()); // GH-90000

            var result = runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000
            assertThat(result.isSuccess()).isTrue(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Majority Vote
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("MAJORITY_VOTE [GH-90000]")
    class MajorityVoteTests {

        @Test void selectsMajority() { // GH-90000
            StubAgent a = new StubAgent("a", Map.of("decision", "ALLOW"), 0.9); // GH-90000
            StubAgent b = new StubAgent("b", Map.of("decision", "BLOCK"), 0.8); // GH-90000
            StubAgent c = new StubAgent("c", Map.of("decision", "ALLOW"), 0.85); // GH-90000

            CompositeAgent agent = createComposite("vote", // GH-90000
                    CompositeAgentConfig.AggregationStrategy.MAJORITY_VOTE,
                    List.of(a, b, c), List.of()); // GH-90000

            var result = runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000
            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutput()).containsEntry("decision", "ALLOW"); // GH-90000
        }

        @Test void tieBreaking() { // GH-90000
            StubAgent a = new StubAgent("a", Map.of("decision", "A"), 0.9); // GH-90000
            StubAgent b = new StubAgent("b", Map.of("decision", "B"), 0.8); // GH-90000

            CompositeAgent agent = createComposite("tie", // GH-90000
                    CompositeAgentConfig.AggregationStrategy.MAJORITY_VOTE,
                    List.of(a, b), List.of()); // GH-90000

            var result = runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000
            assertThat(result.isSuccess()).isTrue(); // GH-90000
            // Should pick one, not crash
            assertThat(result.getOutput().get("decision [GH-90000]")).isIn("A", "B");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // First Match
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("FIRST_MATCH [GH-90000]")
    class FirstMatchTests {

        @Test void takesFirstSuccessful() { // GH-90000
            StubAgent a = new StubAgent("a", Map.of("source", "first"), 0.9); // GH-90000
            StubAgent b = new StubAgent("b", Map.of("source", "second"), 0.85); // GH-90000

            CompositeAgent agent = createComposite("first", // GH-90000
                    CompositeAgentConfig.AggregationStrategy.FIRST_MATCH,
                    List.of(a, b), List.of()); // GH-90000

            var result = runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000
            assertThat(result.getOutput()).containsEntry("source", "first"); // GH-90000
        }

        @Test void skipsFailed() { // GH-90000
            StubAgent a = new StubAgent("fail-a", Map.of(), 0.0, true); // GH-90000
            StubAgent b = new StubAgent("b", Map.of("source", "second"), 0.85); // GH-90000

            CompositeAgent agent = createComposite("first-skip", // GH-90000
                    CompositeAgentConfig.AggregationStrategy.FIRST_MATCH,
                    List.of(a, b), List.of()); // GH-90000

            var result = runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000
            assertThat(result.getOutput()).containsEntry("source", "second"); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Unanimous
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("UNANIMOUS [GH-90000]")
    class UnanimousTests {

        @Test void succeedsWhenAllAgree() { // GH-90000
            StubAgent a = new StubAgent("a", Map.of("decision", "ALLOW"), 0.95); // GH-90000
            StubAgent b = new StubAgent("b", Map.of("decision", "ALLOW"), 0.88); // GH-90000

            CompositeAgent agent = createComposite("unan-ok", // GH-90000
                    CompositeAgentConfig.AggregationStrategy.UNANIMOUS,
                    List.of(a, b), List.of()); // GH-90000

            var result = runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000
            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutput()).containsEntry("decision", "ALLOW"); // GH-90000
        }

        @Test void failsWhenDisagreement() { // GH-90000
            StubAgent a = new StubAgent("a", Map.of("decision", "ALLOW"), 0.95); // GH-90000
            StubAgent b = new StubAgent("b", Map.of("decision", "BLOCK"), 0.88); // GH-90000

            CompositeAgent agent = createComposite("unan-fail", // GH-90000
                    CompositeAgentConfig.AggregationStrategy.UNANIMOUS,
                    List.of(a, b), List.of()); // GH-90000

            var result = runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000
            // Should fail or low-confidence since agents disagree
            assertThat(result.getStatus()).isNotEqualTo(AgentResultStatus.SUCCESS); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Sub-agent Failure Isolation
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Failure Isolation [GH-90000]")
    class FailureIsolationTests {

        @Test void survivesSubAgentFailure() { // GH-90000
            StubAgent good = new StubAgent("good", Map.of("score", 90.0), 0.95); // GH-90000
            StubAgent bad = new StubAgent("bad", Map.of(), 0.0, true); // GH-90000

            CompositeAgent agent = createComposite("isolate", // GH-90000
                    CompositeAgentConfig.AggregationStrategy.FIRST_MATCH,
                    List.of(bad, good), List.of()); // GH-90000

            var result = runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000
            assertThat(result.getOutput()).containsEntry("score", 90.0); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Lifecycle [GH-90000]")
    class LifecycleTests {

        @Test void metricsTracked() { // GH-90000
            StubAgent a = new StubAgent("a", Map.of("ok", true), 0.9); // GH-90000
            CompositeAgent agent = createComposite("lc", // GH-90000
                    CompositeAgentConfig.AggregationStrategy.FIRST_MATCH,
                    List.of(a), List.of()); // GH-90000

            runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000
            assertThat(agent.getTotalInvocations()).isEqualTo(1); // GH-90000
        }
    }
}
