/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
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
@DisplayName("Composite Agent")
class CompositeAgentTest {

    private AgentContext ctx;

    @BeforeEach
    void setUp() {
        ctx = AgentContext.builder()
                .turnId("turn-1")
                .agentId("composite-test")
                .tenantId("test-tenant")
                .memoryStore(mock(MemoryStore.class))
                .build();
    }

    private <T> T runOnEventloop(java.util.function.Supplier<Promise<T>> supplier) {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> err = new AtomicReference<>();
        Eventloop eventloop = Eventloop.builder().withCurrentThread().build();
        eventloop.post(() -> supplier.get()
                .whenResult(result::set)
                .whenException(err::set));
        eventloop.run();
        if (err.get() != null) throw new RuntimeException(err.get());
        return result.get();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Stub Agent
    // ═══════════════════════════════════════════════════════════════════════════

    static class StubAgent extends AbstractTypedAgent<Map<String, Object>, Map<String, Object>> {
        private final AgentDescriptor desc;
        private final Map<String, Object> output;
        private final double confidence;
        private final boolean shouldFail;

        StubAgent(String id, Map<String, Object> output, double confidence) {
            this(id, output, confidence, false);
        }

        StubAgent(String id, Map<String, Object> output, double confidence, boolean shouldFail) {
            this.desc = AgentDescriptor.builder()
                    .agentId(id).name(id).version("1.0")
                    .type(AgentType.DETERMINISTIC).build();
            this.output = output;
            this.confidence = confidence;
            this.shouldFail = shouldFail;
        }

        @Override public @NotNull AgentDescriptor descriptor() { return desc; }

        @Override protected @NotNull Promise<AgentResult<Map<String, Object>>> doProcess(
                @NotNull AgentContext ctx, @NotNull Map<String, Object> input) {
            if (shouldFail) {
                return Promise.ofException(new RuntimeException("stub fail: " + desc.getAgentId()));
            }
            return Promise.of(AgentResult.<Map<String, Object>>builder()
                    .output(output)
                    .confidence(confidence)
                    .status(AgentResultStatus.SUCCESS)
                    .agentId(desc.getAgentId())
                    .processingTime(Duration.ofMillis(3))
                    .build());
        }
    }

    private CompositeAgent createComposite(String id, CompositeAgentConfig.AggregationStrategy strategy,
                                            List<StubAgent> subs, List<Double> weights) {
        CompositeAgent agent = new CompositeAgent(id);

        var configBuilder = CompositeAgentConfig.builder()
                .agentId(id)
                .type(AgentType.COMPOSITE)
                .aggregationStrategy(strategy);

        for (Double w : weights) {
            configBuilder.weight(w);
        }

        AgentConfig stubConfig = AgentConfig.builder().agentId("s").type(AgentType.DETERMINISTIC).build();
        for (StubAgent s : subs) {
            runOnEventloop(() -> s.initialize(stubConfig));
        }

        agent.setSubAgents(new ArrayList<>(subs));
        runOnEventloop(() -> agent.initialize(configBuilder.build()));
        return agent;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Weighted Average
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("WEIGHTED_AVERAGE")
    class WeightedAverageTests {

        @Test void computesWeightedScore() {
            StubAgent a = new StubAgent("a", Map.of("score", 80.0), 0.9);
            StubAgent b = new StubAgent("b", Map.of("score", 60.0), 0.8);

            CompositeAgent agent = createComposite("wavg", 
                    CompositeAgentConfig.AggregationStrategy.WEIGHTED_AVERAGE,
                    List.of(a, b), List.of(0.7, 0.3));

            var result = runOnEventloop(() -> agent.process(ctx, Map.of()));
            assertThat(result.isSuccess()).isTrue();
            // Weighted: 0.7*80 + 0.3*60 = 56 + 18 = 74
            Object score = result.getOutput().get("score");
            assertThat(((Number) score).doubleValue()).isCloseTo(74.0, within(0.1));
        }

        @Test void handlesEmptyWeightsUniformly() {
            StubAgent a = new StubAgent("a", Map.of("score", 90.0), 0.9);
            StubAgent b = new StubAgent("b", Map.of("score", 70.0), 0.8);

            CompositeAgent agent = createComposite("wavg-uniform",
                    CompositeAgentConfig.AggregationStrategy.WEIGHTED_AVERAGE,
                    List.of(a, b), List.of());

            var result = runOnEventloop(() -> agent.process(ctx, Map.of()));
            assertThat(result.isSuccess()).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Majority Vote
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("MAJORITY_VOTE")
    class MajorityVoteTests {

        @Test void selectsMajority() {
            StubAgent a = new StubAgent("a", Map.of("decision", "ALLOW"), 0.9);
            StubAgent b = new StubAgent("b", Map.of("decision", "BLOCK"), 0.8);
            StubAgent c = new StubAgent("c", Map.of("decision", "ALLOW"), 0.85);

            CompositeAgent agent = createComposite("vote",
                    CompositeAgentConfig.AggregationStrategy.MAJORITY_VOTE,
                    List.of(a, b, c), List.of());

            var result = runOnEventloop(() -> agent.process(ctx, Map.of()));
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("decision", "ALLOW");
        }

        @Test void tieBreaking() {
            StubAgent a = new StubAgent("a", Map.of("decision", "A"), 0.9);
            StubAgent b = new StubAgent("b", Map.of("decision", "B"), 0.8);

            CompositeAgent agent = createComposite("tie",
                    CompositeAgentConfig.AggregationStrategy.MAJORITY_VOTE,
                    List.of(a, b), List.of());

            var result = runOnEventloop(() -> agent.process(ctx, Map.of()));
            assertThat(result.isSuccess()).isTrue();
            // Should pick one, not crash
            assertThat(result.getOutput().get("decision")).isIn("A", "B");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // First Match
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("FIRST_MATCH")
    class FirstMatchTests {

        @Test void takesFirstSuccessful() {
            StubAgent a = new StubAgent("a", Map.of("source", "first"), 0.9);
            StubAgent b = new StubAgent("b", Map.of("source", "second"), 0.85);

            CompositeAgent agent = createComposite("first",
                    CompositeAgentConfig.AggregationStrategy.FIRST_MATCH,
                    List.of(a, b), List.of());

            var result = runOnEventloop(() -> agent.process(ctx, Map.of()));
            assertThat(result.getOutput()).containsEntry("source", "first");
        }

        @Test void skipsFailed() {
            StubAgent a = new StubAgent("fail-a", Map.of(), 0.0, true);
            StubAgent b = new StubAgent("b", Map.of("source", "second"), 0.85);

            CompositeAgent agent = createComposite("first-skip",
                    CompositeAgentConfig.AggregationStrategy.FIRST_MATCH,
                    List.of(a, b), List.of());

            var result = runOnEventloop(() -> agent.process(ctx, Map.of()));
            assertThat(result.getOutput()).containsEntry("source", "second");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Unanimous
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("UNANIMOUS")
    class UnanimousTests {

        @Test void succeedsWhenAllAgree() {
            StubAgent a = new StubAgent("a", Map.of("decision", "ALLOW"), 0.95);
            StubAgent b = new StubAgent("b", Map.of("decision", "ALLOW"), 0.88);

            CompositeAgent agent = createComposite("unan-ok",
                    CompositeAgentConfig.AggregationStrategy.UNANIMOUS,
                    List.of(a, b), List.of());

            var result = runOnEventloop(() -> agent.process(ctx, Map.of()));
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("decision", "ALLOW");
        }

        @Test void failsWhenDisagreement() {
            StubAgent a = new StubAgent("a", Map.of("decision", "ALLOW"), 0.95);
            StubAgent b = new StubAgent("b", Map.of("decision", "BLOCK"), 0.88);

            CompositeAgent agent = createComposite("unan-fail",
                    CompositeAgentConfig.AggregationStrategy.UNANIMOUS,
                    List.of(a, b), List.of());

            var result = runOnEventloop(() -> agent.process(ctx, Map.of()));
            // Should fail or low-confidence since agents disagree
            assertThat(result.getStatus()).isNotEqualTo(AgentResultStatus.SUCCESS);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Sub-agent Failure Isolation
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Failure Isolation")
    class FailureIsolationTests {

        @Test void survivesSubAgentFailure() {
            StubAgent good = new StubAgent("good", Map.of("score", 90.0), 0.95);
            StubAgent bad = new StubAgent("bad", Map.of(), 0.0, true);

            CompositeAgent agent = createComposite("isolate",
                    CompositeAgentConfig.AggregationStrategy.FIRST_MATCH,
                    List.of(bad, good), List.of());

            var result = runOnEventloop(() -> agent.process(ctx, Map.of()));
            assertThat(result.getOutput()).containsEntry("score", 90.0);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Lifecycle")
    class LifecycleTests {

        @Test void metricsTracked() {
            StubAgent a = new StubAgent("a", Map.of("ok", true), 0.9);
            CompositeAgent agent = createComposite("lc",
                    CompositeAgentConfig.AggregationStrategy.FIRST_MATCH,
                    List.of(a), List.of());

            runOnEventloop(() -> agent.process(ctx, Map.of()));
            assertThat(agent.getTotalInvocations()).isEqualTo(1);
        }
    }
}
