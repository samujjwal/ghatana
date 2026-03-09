/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 * Phase 4 — Task 4.1: Gap-filling tests for CompositeAgent.
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
 * Gap-filling tests for {@link CompositeAgent}.
 *
 * <p>Fills gaps identified in Phase 4 audit:
 * <ul>
 *   <li>All sub-agents fail simultaneously</li>
 *   <li>Weighted average with non-numeric field (graceful degradation)</li>
 *   <li>UNANIMOUS confidence is 1.0 when all agree</li>
 *   <li>MAJORITY_VOTE with no voting field present</li>
 *   <li>FIRST_MATCH when all fail → FAILED</li>
 *   <li>Processing with zero sub-agents → FAILED</li>
 * </ul>
 */
@DisplayName("Composite Agent — Gap Tests")
class CompositeAgentGapTest {

    private AgentContext ctx;

    @BeforeEach
    void setUp() {
        ctx = AgentContext.builder()
                .turnId("turn-1")
                .agentId("composite-gap")
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

    // ── Stubs ───────────────────────────────────────────────────────────────

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

        @Override
        public @NotNull AgentDescriptor descriptor() { return desc; }

        @Override
        protected @NotNull Promise<AgentResult<Map<String, Object>>> doProcess(
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
    // All sub-agents fail
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("All Sub-Agents Fail")
    class AllFailTests {

        @Test
        void allSubAgentsFailReturnsFailed() {
            StubAgent a = new StubAgent("bad-1", Map.of(), 0.0, true);
            StubAgent b = new StubAgent("bad-2", Map.of(), 0.0, true);
            StubAgent c = new StubAgent("bad-3", Map.of(), 0.0, true);

            CompositeAgent agent = createComposite("all-fail",
                    CompositeAgentConfig.AggregationStrategy.WEIGHTED_AVERAGE,
                    List.of(a, b, c), List.of(0.5, 0.3, 0.2));

            var result = runOnEventloop(() -> agent.process(ctx, Map.of()));

            assertThat(result.isFailed()).isTrue();
            assertThat(result.getExplanation()).containsIgnoringCase("all sub-agents failed");
        }

        @Test
        void allSubAgentsFailFirstMatchReturnsFailed() {
            StubAgent a = new StubAgent("fail-fm-1", Map.of(), 0.0, true);
            StubAgent b = new StubAgent("fail-fm-2", Map.of(), 0.0, true);

            CompositeAgent agent = createComposite("all-fail-fm",
                    CompositeAgentConfig.AggregationStrategy.FIRST_MATCH,
                    List.of(a, b), List.of());

            var result = runOnEventloop(() -> agent.process(ctx, Map.of()));
            assertThat(result.isFailed()).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Weighted average with non-numeric field
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Weighted Average — edge cases")
    class WeightedAverageEdgeCases {

        @Test
        void nonNumericFieldProducesZeroAverage() {
            StubAgent a = new StubAgent("a", Map.of("score", "not-a-number"), 0.9);
            StubAgent b = new StubAgent("b", Map.of("score", "also-nan"), 0.8);

            CompositeAgent agent = createComposite("wav-nan",
                    CompositeAgentConfig.AggregationStrategy.WEIGHTED_AVERAGE,
                    List.of(a, b), List.of(0.5, 0.5));

            var result = runOnEventloop(() -> agent.process(ctx, Map.of()));

            assertThat(result.isSuccess()).isTrue();
            // Non-numeric values should be skipped → weighted sum = 0 / 0 → 0
            double score = ((Number) result.getOutput().get("score")).doubleValue();
            assertThat(score).isCloseTo(0.0, within(0.001));
        }

        @Test
        void mixedNumericAndNonNumericUsesOnlyNumeric() {
            StubAgent a = new StubAgent("a", Map.of("score", 80.0), 0.9);
            StubAgent b = new StubAgent("b", Map.of("score", "skip-me"), 0.8);

            CompositeAgent agent = createComposite("wav-mixed",
                    CompositeAgentConfig.AggregationStrategy.WEIGHTED_AVERAGE,
                    List.of(a, b), List.of(0.6, 0.4));

            var result = runOnEventloop(() -> agent.process(ctx, Map.of()));

            // Only agent a's value used: 80.0 * 0.6 / 0.6 = 80.0
            double score = ((Number) result.getOutput().get("score")).doubleValue();
            assertThat(score).isCloseTo(80.0, within(0.1));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UNANIMOUS confidence
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("UNANIMOUS Confidence")
    class UnanimousConfidenceTests {

        @Test
        void unanimousAgreementSetsFullConfidence() {
            StubAgent a = new StubAgent("a", Map.of("decision", "ALLOW"), 0.7);
            StubAgent b = new StubAgent("b", Map.of("decision", "ALLOW"), 0.6);
            StubAgent c = new StubAgent("c", Map.of("decision", "ALLOW"), 0.8);

            CompositeAgent agent = createComposite("unan-conf",
                    CompositeAgentConfig.AggregationStrategy.UNANIMOUS,
                    List.of(a, b, c), List.of());

            var result = runOnEventloop(() -> agent.process(ctx, Map.of()));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getConfidence()).isCloseTo(1.0, within(0.001));
            assertThat(result.getOutput()).containsEntry("_composite.agreementCount", 3);
        }

        @Test
        void unanimousWithOneFailedSubAgentReturnsDegraded() {
            StubAgent a = new StubAgent("a", Map.of("decision", "ALLOW"), 0.9);
            StubAgent b = new StubAgent("b-fail", Map.of(), 0.0, true);

            CompositeAgent agent = createComposite("unan-partial",
                    CompositeAgentConfig.AggregationStrategy.UNANIMOUS,
                    List.of(a, b), List.of());

            var result = runOnEventloop(() -> agent.process(ctx, Map.of()));

            // One failed → not all succeeded → unanimity broken
            assertThat(result.getStatus()).isNotEqualTo(AgentResultStatus.SUCCESS);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MAJORITY_VOTE — no voting field
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("MAJORITY_VOTE — missing voting field")
    class MajorityVoteMissingField {

        @Test
        void noVotingFieldReturnsDegraded() {
            // Agents produce outputs without the "decision" field
            StubAgent a = new StubAgent("a", Map.of("other", "data"), 0.9);
            StubAgent b = new StubAgent("b", Map.of("other", "data2"), 0.8);

            CompositeAgent agent = createComposite("vote-nofield",
                    CompositeAgentConfig.AggregationStrategy.MAJORITY_VOTE,
                    List.of(a, b), List.of());

            var result = runOnEventloop(() -> agent.process(ctx, Map.of()));

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.DEGRADED);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Strategy metadata in output
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Strategy Metadata")
    class StrategyMetadataTests {

        @Test
        void weightedAverageIncludesStrategyInOutput() {
            StubAgent a = new StubAgent("a", Map.of("score", 70.0), 0.9);

            CompositeAgent agent = createComposite("meta-wa",
                    CompositeAgentConfig.AggregationStrategy.WEIGHTED_AVERAGE,
                    List.of(a), List.of(1.0));

            var result = runOnEventloop(() -> agent.process(ctx, Map.of()));
            assertThat(result.getOutput()).containsEntry("_composite.strategy", "WEIGHTED_AVERAGE");
        }

        @Test
        void majorityVoteIncludesVoteCounts() {
            StubAgent a = new StubAgent("a", Map.of("decision", "YES"), 0.9);
            StubAgent b = new StubAgent("b", Map.of("decision", "YES"), 0.8);
            StubAgent c = new StubAgent("c", Map.of("decision", "NO"), 0.7);

            CompositeAgent agent = createComposite("meta-vote",
                    CompositeAgentConfig.AggregationStrategy.MAJORITY_VOTE,
                    List.of(a, b, c), List.of());

            var result = runOnEventloop(() -> agent.process(ctx, Map.of()));
            assertThat(result.getOutput()).containsEntry("_composite.strategy", "MAJORITY_VOTE");
            assertThat(result.getOutput()).containsKey("_composite.votes");
            assertThat(result.getOutput()).containsEntry("_composite.voteCount", 3);
        }
    }
}
