/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
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
 *   <li>Weighted average with non-numeric field (graceful degradation)</li> // GH-90000
 *   <li>UNANIMOUS confidence is 1.0 when all agree</li>
 *   <li>MAJORITY_VOTE with no voting field present</li>
 *   <li>FIRST_MATCH when all fail → FAILED</li>
 *   <li>Processing with zero sub-agents → FAILED</li>
 * </ul>
 */
@DisplayName("Composite Agent — Gap Tests [GH-90000]")
class CompositeAgentGapTest {

    private AgentContext ctx;

    @BeforeEach
    void setUp() { // GH-90000
        ctx = AgentContext.builder() // GH-90000
                .turnId("turn-1 [GH-90000]")
                .agentId("composite-gap [GH-90000]")
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

    // ── Stubs ───────────────────────────────────────────────────────────────

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

        @Override
        public @NotNull AgentDescriptor descriptor() { return desc; } // GH-90000

        @Override
        protected @NotNull Promise<AgentResult<Map<String, Object>>> doProcess( // GH-90000
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
    // All sub-agents fail
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("All Sub-Agents Fail [GH-90000]")
    class AllFailTests {

        @Test
        void allSubAgentsFailReturnsFailed() { // GH-90000
            StubAgent a = new StubAgent("bad-1", Map.of(), 0.0, true); // GH-90000
            StubAgent b = new StubAgent("bad-2", Map.of(), 0.0, true); // GH-90000
            StubAgent c = new StubAgent("bad-3", Map.of(), 0.0, true); // GH-90000

            CompositeAgent agent = createComposite("all-fail", // GH-90000
                    CompositeAgentConfig.AggregationStrategy.WEIGHTED_AVERAGE,
                    List.of(a, b, c), List.of(0.5, 0.3, 0.2)); // GH-90000

            var result = runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000

            assertThat(result.isFailed()).isTrue(); // GH-90000
            assertThat(result.getExplanation()).containsIgnoringCase("all sub-agents failed [GH-90000]");
        }

        @Test
        void allSubAgentsFailFirstMatchReturnsFailed() { // GH-90000
            StubAgent a = new StubAgent("fail-fm-1", Map.of(), 0.0, true); // GH-90000
            StubAgent b = new StubAgent("fail-fm-2", Map.of(), 0.0, true); // GH-90000

            CompositeAgent agent = createComposite("all-fail-fm", // GH-90000
                    CompositeAgentConfig.AggregationStrategy.FIRST_MATCH,
                    List.of(a, b), List.of()); // GH-90000

            var result = runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000
            assertThat(result.isFailed()).isTrue(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Weighted average with non-numeric field
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Weighted Average — edge cases [GH-90000]")
    class WeightedAverageEdgeCases {

        @Test
        void nonNumericFieldProducesZeroAverage() { // GH-90000
            StubAgent a = new StubAgent("a", Map.of("score", "not-a-number"), 0.9); // GH-90000
            StubAgent b = new StubAgent("b", Map.of("score", "also-nan"), 0.8); // GH-90000

            CompositeAgent agent = createComposite("wav-nan", // GH-90000
                    CompositeAgentConfig.AggregationStrategy.WEIGHTED_AVERAGE,
                    List.of(a, b), List.of(0.5, 0.5)); // GH-90000

            var result = runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            // Non-numeric values should be skipped → weighted sum = 0 / 0 → 0
            double score = ((Number) result.getOutput().get("score [GH-90000]")).doubleValue();
            assertThat(score).isCloseTo(0.0, within(0.001)); // GH-90000
        }

        @Test
        void mixedNumericAndNonNumericUsesOnlyNumeric() { // GH-90000
            StubAgent a = new StubAgent("a", Map.of("score", 80.0), 0.9); // GH-90000
            StubAgent b = new StubAgent("b", Map.of("score", "skip-me"), 0.8); // GH-90000

            CompositeAgent agent = createComposite("wav-mixed", // GH-90000
                    CompositeAgentConfig.AggregationStrategy.WEIGHTED_AVERAGE,
                    List.of(a, b), List.of(0.6, 0.4)); // GH-90000

            var result = runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000

            // Only agent a's value used: 80.0 * 0.6 / 0.6 = 80.0
            double score = ((Number) result.getOutput().get("score [GH-90000]")).doubleValue();
            assertThat(score).isCloseTo(80.0, within(0.1)); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UNANIMOUS confidence
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("UNANIMOUS Confidence [GH-90000]")
    class UnanimousConfidenceTests {

        @Test
        void unanimousAgreementSetsFullConfidence() { // GH-90000
            StubAgent a = new StubAgent("a", Map.of("decision", "ALLOW"), 0.7); // GH-90000
            StubAgent b = new StubAgent("b", Map.of("decision", "ALLOW"), 0.6); // GH-90000
            StubAgent c = new StubAgent("c", Map.of("decision", "ALLOW"), 0.8); // GH-90000

            CompositeAgent agent = createComposite("unan-conf", // GH-90000
                    CompositeAgentConfig.AggregationStrategy.UNANIMOUS,
                    List.of(a, b, c), List.of()); // GH-90000

            var result = runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getConfidence()).isCloseTo(1.0, within(0.001)); // GH-90000
            assertThat(result.getOutput()).containsEntry("_composite.agreementCount", 3); // GH-90000
        }

        @Test
        void unanimousWithOneFailedSubAgentReturnsDegraded() { // GH-90000
            StubAgent a = new StubAgent("a", Map.of("decision", "ALLOW"), 0.9); // GH-90000
            StubAgent b = new StubAgent("b-fail", Map.of(), 0.0, true); // GH-90000

            CompositeAgent agent = createComposite("unan-partial", // GH-90000
                    CompositeAgentConfig.AggregationStrategy.UNANIMOUS,
                    List.of(a, b), List.of()); // GH-90000

            var result = runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000

            // One failed → not all succeeded → unanimity broken
            assertThat(result.getStatus()).isNotEqualTo(AgentResultStatus.SUCCESS); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MAJORITY_VOTE — no voting field
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("MAJORITY_VOTE — missing voting field [GH-90000]")
    class MajorityVoteMissingField {

        @Test
        void noVotingFieldReturnsDegraded() { // GH-90000
            // Agents produce outputs without the "decision" field
            StubAgent a = new StubAgent("a", Map.of("other", "data"), 0.9); // GH-90000
            StubAgent b = new StubAgent("b", Map.of("other", "data2"), 0.8); // GH-90000

            CompositeAgent agent = createComposite("vote-nofield", // GH-90000
                    CompositeAgentConfig.AggregationStrategy.MAJORITY_VOTE,
                    List.of(a, b), List.of()); // GH-90000

            var result = runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000

            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.DEGRADED); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Strategy metadata in output
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Strategy Metadata [GH-90000]")
    class StrategyMetadataTests {

        @Test
        void weightedAverageIncludesStrategyInOutput() { // GH-90000
            StubAgent a = new StubAgent("a", Map.of("score", 70.0), 0.9); // GH-90000

            CompositeAgent agent = createComposite("meta-wa", // GH-90000
                    CompositeAgentConfig.AggregationStrategy.WEIGHTED_AVERAGE,
                    List.of(a), List.of(1.0)); // GH-90000

            var result = runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000
            assertThat(result.getOutput()).containsEntry("_composite.strategy", "WEIGHTED_AVERAGE"); // GH-90000
        }

        @Test
        void majorityVoteIncludesVoteCounts() { // GH-90000
            StubAgent a = new StubAgent("a", Map.of("decision", "YES"), 0.9); // GH-90000
            StubAgent b = new StubAgent("b", Map.of("decision", "YES"), 0.8); // GH-90000
            StubAgent c = new StubAgent("c", Map.of("decision", "NO"), 0.7); // GH-90000

            CompositeAgent agent = createComposite("meta-vote", // GH-90000
                    CompositeAgentConfig.AggregationStrategy.MAJORITY_VOTE,
                    List.of(a, b, c), List.of()); // GH-90000

            var result = runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000
            assertThat(result.getOutput()).containsEntry("_composite.strategy", "MAJORITY_VOTE"); // GH-90000
            assertThat(result.getOutput()).containsKey("_composite.votes [GH-90000]");
            assertThat(result.getOutput()).containsEntry("_composite.voteCount", 3); // GH-90000
        }
    }
}
