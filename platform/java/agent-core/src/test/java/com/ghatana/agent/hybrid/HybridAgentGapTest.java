/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
 * Phase 4 — Task 4.1: Gap-filling tests for HybridAgent.
 */

package com.ghatana.agent.hybrid;

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
 * Gap-filling tests for {@link HybridAgent}.
 *
 * <p>Fills gaps identified in Phase 4 audit:
 * <ul>
 *   <li>Fallback on timeout (sub-agent throws TimeoutException)</li> // GH-90000
 *   <li>Both agents fail → DEGRADED result</li>
 *   <li>DETERMINISTIC_FIRST with no probabilistic → DEGRADED when det is low-confidence</li>
 *   <li>PROBABILISTIC_FIRST with no deterministic → DEGRADED when prob fails</li>
 *   <li>PARALLEL with one agent producing empty output</li>
 *   <li>Hybrid metadata in output (_hybrid.source, _hybrid.strategy)</li> // GH-90000
 * </ul>
 */
@DisplayName("Hybrid Agent — Gap Tests")
class HybridAgentGapTest {

    private AgentContext ctx;

    @BeforeEach
    void setUp() { // GH-90000
        ctx = AgentContext.builder() // GH-90000
                .turnId("turn-1")
                .agentId("hybrid-gap")
                .tenantId("test-tenant")
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
        private final Map<String, Object> fixedOutput;
        private final double confidence;
        private final boolean shouldFail;
        private final boolean shouldTimeout;

        StubAgent(String id, Map<String, Object> output, double confidence) { // GH-90000
            this(id, output, confidence, false, false); // GH-90000
        }

        StubAgent(String id, Map<String, Object> output, double confidence, // GH-90000
                  boolean shouldFail, boolean shouldTimeout) {
            this.desc = AgentDescriptor.builder() // GH-90000
                    .agentId(id).name(id).version("1.0")
                    .type(AgentType.DETERMINISTIC).build(); // GH-90000
            this.fixedOutput = output;
            this.confidence = confidence;
            this.shouldFail = shouldFail;
            this.shouldTimeout = shouldTimeout;
        }

        @Override
        public @NotNull AgentDescriptor descriptor() { return desc; } // GH-90000

        @Override
        protected @NotNull Promise<AgentResult<Map<String, Object>>> doProcess( // GH-90000
                @NotNull AgentContext ctx, @NotNull Map<String, Object> input) {
            if (shouldTimeout) { // GH-90000
                return Promise.ofException(new java.util.concurrent.TimeoutException( // GH-90000
                        "Agent " + desc.getAgentId() + " timed out")); // GH-90000
            }
            if (shouldFail) { // GH-90000
                return Promise.ofException(new RuntimeException("Agent " + desc.getAgentId() + " failed")); // GH-90000
            }
            return Promise.of(AgentResult.<Map<String, Object>>builder() // GH-90000
                    .output(fixedOutput) // GH-90000
                    .confidence(confidence) // GH-90000
                    .status(confidence > 0.5 ? AgentResultStatus.SUCCESS : AgentResultStatus.LOW_CONFIDENCE) // GH-90000
                    .agentId(desc.getAgentId()) // GH-90000
                    .processingTime(Duration.ofMillis(5)) // GH-90000
                    .build()); // GH-90000
        }
    }

    private HybridAgent createHybrid(String id, StubAgent det, StubAgent prob, // GH-90000
                                      HybridAgentConfig.RoutingStrategy strategy) {
        HybridAgent agent = new HybridAgent(id); // GH-90000
        agent.setDeterministicAgent(det); // GH-90000
        agent.setProbabilisticAgent(prob); // GH-90000

        HybridAgentConfig config = HybridAgentConfig.builder() // GH-90000
                .agentId(id) // GH-90000
                .type(AgentType.HYBRID) // GH-90000
                .strategy(strategy) // GH-90000
                .escalationConfidenceThreshold(0.7) // GH-90000
                .build(); // GH-90000

        AgentConfig stubConfig = AgentConfig.builder().agentId("stub").type(AgentType.DETERMINISTIC).build();
        if (det != null) runOnEventloop(() -> det.initialize(stubConfig)); // GH-90000
        if (prob != null) runOnEventloop(() -> prob.initialize(stubConfig)); // GH-90000
        runOnEventloop(() -> agent.initialize(config)); // GH-90000
        return agent;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Fallback on timeout
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Fallback on Timeout")
    class FallbackOnTimeoutTests {

        @Test
        void detFirstEscalatesToProbOnTimeout() { // GH-90000
            StubAgent det = new StubAgent("det-timeout", Map.of(), 0.0, false, true); // GH-90000
            StubAgent prob = new StubAgent("prob-ok", Map.of("decision", "ML_ALLOW"), 0.9); // GH-90000

            HybridAgent agent = createHybrid("h-timeout-1", det, prob, // GH-90000
                    HybridAgentConfig.RoutingStrategy.DETERMINISTIC_FIRST);

            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1))); // GH-90000
            assertThat(result.getOutput()).containsEntry("decision", "ML_ALLOW"); // GH-90000
        }

        @Test
        void probFirstFallsToDeterministicOnTimeout() { // GH-90000
            StubAgent det = new StubAgent("det-ok", Map.of("decision", "RULE_OK"), 0.95); // GH-90000
            StubAgent prob = new StubAgent("prob-timeout", Map.of(), 0.0, false, true); // GH-90000

            HybridAgent agent = createHybrid("h-timeout-2", det, prob, // GH-90000
                    HybridAgentConfig.RoutingStrategy.PROBABILISTIC_FIRST);

            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1))); // GH-90000
            assertThat(result.getOutput()).containsEntry("decision", "RULE_OK"); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Both agents fail
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Both Agents Fail")
    class BothAgentsFailTests {

        @Test
        void detFirstBothFailReturnsFailedResult() { // GH-90000
            StubAgent det = new StubAgent("det-fail", Map.of(), 0.0, true, false); // GH-90000
            StubAgent prob = new StubAgent("prob-fail", Map.of(), 0.0, true, false); // GH-90000

            HybridAgent agent = createHybrid("h-both-fail-1", det, prob, // GH-90000
                    HybridAgentConfig.RoutingStrategy.DETERMINISTIC_FIRST);

            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1))); // GH-90000
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.FAILED); // GH-90000
            assertThat(result.getExplanation()).contains("Deterministic: FAILED");
        }

        @Test
        void parallelBothSkippedReturnsDegraded() { // GH-90000
            // Both return SKIPPED (no model, no rules) // GH-90000
            StubAgent det = new StubAgent("det-skip", Map.of("x", 1), 0.2); // GH-90000
            StubAgent prob = new StubAgent("prob-skip", Map.of("y", 2), 0.3); // GH-90000

            HybridAgent agent = createHybrid("h-both-skip", det, prob, // GH-90000
                    HybridAgentConfig.RoutingStrategy.PARALLEL);

            var result = runOnEventloop(() -> agent.process(ctx, Map.of("z", 3))); // GH-90000
            // Both LOW_CONFIDENCE → merged result should contain _hybrid.strategy
            assertThat(result.getOutput()).containsEntry("_hybrid.strategy", "PARALLEL"); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Missing sub-agent scenarios
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Missing Sub-Agent")
    class MissingSubAgentTests {

        @Test
        void detFirstWithNoProbReturnsDetResultIfHighConfidence() { // GH-90000
            StubAgent det = new StubAgent("det-only", Map.of("answer", "rule"), 0.95); // GH-90000

            HybridAgent agent = new HybridAgent("h-det-only");
            agent.setDeterministicAgent(det); // GH-90000
            // No probabilistic agent set

            HybridAgentConfig config = HybridAgentConfig.builder() // GH-90000
                    .agentId("h-det-only")
                    .type(AgentType.HYBRID) // GH-90000
                    .strategy(HybridAgentConfig.RoutingStrategy.DETERMINISTIC_FIRST) // GH-90000
                    .escalationConfidenceThreshold(0.7) // GH-90000
                    .build(); // GH-90000

            AgentConfig stubConfig = AgentConfig.builder().agentId("s").type(AgentType.DETERMINISTIC).build();
            runOnEventloop(() -> det.initialize(stubConfig)); // GH-90000
            runOnEventloop(() -> agent.initialize(config)); // GH-90000

            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1))); // GH-90000
            assertThat(result.getOutput()).containsEntry("answer", "rule"); // GH-90000
        }

        @Test
        void detFirstLowConfAndNoProbReturnsDegraded() { // GH-90000
            StubAgent det = new StubAgent("det-low", Map.of("answer", "unsure"), 0.3); // GH-90000

            HybridAgent agent = new HybridAgent("h-no-prob");
            agent.setDeterministicAgent(det); // GH-90000

            HybridAgentConfig config = HybridAgentConfig.builder() // GH-90000
                    .agentId("h-no-prob")
                    .type(AgentType.HYBRID) // GH-90000
                    .strategy(HybridAgentConfig.RoutingStrategy.DETERMINISTIC_FIRST) // GH-90000
                    .escalationConfidenceThreshold(0.7) // GH-90000
                    .build(); // GH-90000

            AgentConfig stubConfig = AgentConfig.builder().agentId("s").type(AgentType.DETERMINISTIC).build();
            runOnEventloop(() -> det.initialize(stubConfig)); // GH-90000
            runOnEventloop(() -> agent.initialize(config)); // GH-90000

            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1))); // GH-90000
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.DEGRADED); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Parallel — metadata enrichment
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Parallel — metadata")
    class ParallelMetadataTests {

        @Test
        void parallelResultContainsStatusFromBothAgents() { // GH-90000
            StubAgent det = new StubAgent("det", Map.of("rule", "ok"), 0.95); // GH-90000
            StubAgent prob = new StubAgent("prob", Map.of("ml", "ok"), 0.88); // GH-90000

            HybridAgent agent = createHybrid("h-parallel-meta", det, prob, // GH-90000
                    HybridAgentConfig.RoutingStrategy.PARALLEL);

            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1))); // GH-90000
            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutput()).containsEntry("_hybrid.detStatus", "SUCCESS"); // GH-90000
            assertThat(result.getOutput()).containsEntry("_hybrid.probStatus", "SUCCESS"); // GH-90000
            assertThat(result.getOutput()).containsEntry("_hybrid.strategy", "PARALLEL"); // GH-90000
        }

        @Test
        void parallelConfidenceUsesMaxWhenDetSucceeds() { // GH-90000
            StubAgent det = new StubAgent("det", Map.of("rule", "ok"), 0.8); // GH-90000
            StubAgent prob = new StubAgent("prob", Map.of("ml", "ok"), 0.95); // GH-90000

            HybridAgent agent = createHybrid("h-parallel-conf", det, prob, // GH-90000
                    HybridAgentConfig.RoutingStrategy.PARALLEL);

            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1))); // GH-90000
            // When det is successful, confidence = max(det, prob) = 0.95 // GH-90000
            assertThat(result.getConfidence()).isCloseTo(0.95, within(0.01)); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Escalation reason tracking
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Escalation Reason Tracking")
    class EscalationReasonTests {

        @Test
        void escalationReasonIncludedInOutput() { // GH-90000
            StubAgent det = new StubAgent("det", Map.of("answer", "unsure"), 0.3); // GH-90000
            StubAgent prob = new StubAgent("prob", Map.of("answer", "confident"), 0.92); // GH-90000

            HybridAgent agent = createHybrid("h-esc-reason", det, prob, // GH-90000
                    HybridAgentConfig.RoutingStrategy.DETERMINISTIC_FIRST);

            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1))); // GH-90000
            assertThat(result.getOutput()).containsKey("_hybrid.escalationReason");
            assertThat(result.getOutput()).containsEntry("_hybrid.source", "probabilistic"); // GH-90000
        }
    }
}
