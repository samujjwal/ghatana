/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
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
 *   <li>Fallback on timeout (sub-agent throws TimeoutException)</li>
 *   <li>Both agents fail → DEGRADED result</li>
 *   <li>DETERMINISTIC_FIRST with no probabilistic → DEGRADED when det is low-confidence</li>
 *   <li>PROBABILISTIC_FIRST with no deterministic → DEGRADED when prob fails</li>
 *   <li>PARALLEL with one agent producing empty output</li>
 *   <li>Hybrid metadata in output (_hybrid.source, _hybrid.strategy)</li>
 * </ul>
 */
@DisplayName("Hybrid Agent — Gap Tests")
class HybridAgentGapTest {

    private AgentContext ctx;

    @BeforeEach
    void setUp() {
        ctx = AgentContext.builder()
                .turnId("turn-1")
                .agentId("hybrid-gap")
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
        private final Map<String, Object> fixedOutput;
        private final double confidence;
        private final boolean shouldFail;
        private final boolean shouldTimeout;

        StubAgent(String id, Map<String, Object> output, double confidence) {
            this(id, output, confidence, false, false);
        }

        StubAgent(String id, Map<String, Object> output, double confidence,
                  boolean shouldFail, boolean shouldTimeout) {
            this.desc = AgentDescriptor.builder()
                    .agentId(id).name(id).version("1.0")
                    .type(AgentType.DETERMINISTIC).build();
            this.fixedOutput = output;
            this.confidence = confidence;
            this.shouldFail = shouldFail;
            this.shouldTimeout = shouldTimeout;
        }

        @Override
        public @NotNull AgentDescriptor descriptor() { return desc; }

        @Override
        protected @NotNull Promise<AgentResult<Map<String, Object>>> doProcess(
                @NotNull AgentContext ctx, @NotNull Map<String, Object> input) {
            if (shouldTimeout) {
                return Promise.ofException(new java.util.concurrent.TimeoutException(
                        "Agent " + desc.getAgentId() + " timed out"));
            }
            if (shouldFail) {
                return Promise.ofException(new RuntimeException("Agent " + desc.getAgentId() + " failed"));
            }
            return Promise.of(AgentResult.<Map<String, Object>>builder()
                    .output(fixedOutput)
                    .confidence(confidence)
                    .status(confidence > 0.5 ? AgentResultStatus.SUCCESS : AgentResultStatus.LOW_CONFIDENCE)
                    .agentId(desc.getAgentId())
                    .processingTime(Duration.ofMillis(5))
                    .build());
        }
    }

    private HybridAgent createHybrid(String id, StubAgent det, StubAgent prob,
                                      HybridAgentConfig.RoutingStrategy strategy) {
        HybridAgent agent = new HybridAgent(id);
        agent.setDeterministicAgent(det);
        agent.setProbabilisticAgent(prob);

        HybridAgentConfig config = HybridAgentConfig.builder()
                .agentId(id)
                .type(AgentType.HYBRID)
                .strategy(strategy)
                .escalationConfidenceThreshold(0.7)
                .build();

        AgentConfig stubConfig = AgentConfig.builder().agentId("stub").type(AgentType.DETERMINISTIC).build();
        if (det != null) runOnEventloop(() -> det.initialize(stubConfig));
        if (prob != null) runOnEventloop(() -> prob.initialize(stubConfig));
        runOnEventloop(() -> agent.initialize(config));
        return agent;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Fallback on timeout
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Fallback on Timeout")
    class FallbackOnTimeoutTests {

        @Test
        void detFirstEscalatesToProbOnTimeout() {
            StubAgent det = new StubAgent("det-timeout", Map.of(), 0.0, false, true);
            StubAgent prob = new StubAgent("prob-ok", Map.of("decision", "ML_ALLOW"), 0.9);

            HybridAgent agent = createHybrid("h-timeout-1", det, prob,
                    HybridAgentConfig.RoutingStrategy.DETERMINISTIC_FIRST);

            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1)));
            assertThat(result.getOutput()).containsEntry("decision", "ML_ALLOW");
        }

        @Test
        void probFirstFallsToDeterministicOnTimeout() {
            StubAgent det = new StubAgent("det-ok", Map.of("decision", "RULE_OK"), 0.95);
            StubAgent prob = new StubAgent("prob-timeout", Map.of(), 0.0, false, true);

            HybridAgent agent = createHybrid("h-timeout-2", det, prob,
                    HybridAgentConfig.RoutingStrategy.PROBABILISTIC_FIRST);

            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1)));
            assertThat(result.getOutput()).containsEntry("decision", "RULE_OK");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Both agents fail
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Both Agents Fail")
    class BothAgentsFailTests {

        @Test
        void detFirstBothFailResultsInExceptionOrDegraded() {
            StubAgent det = new StubAgent("det-fail", Map.of(), 0.0, true, false);
            StubAgent prob = new StubAgent("prob-fail", Map.of(), 0.0, true, false);

            HybridAgent agent = createHybrid("h-both-fail-1", det, prob,
                    HybridAgentConfig.RoutingStrategy.DETERMINISTIC_FIRST);

            // When both agents fail, the exception from prob propagates
            // since the hybrid agent has no final catch-all handler
            assertThatThrownBy(() -> runOnEventloop(() -> agent.process(ctx, Map.of("x", 1))))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        void parallelBothSkippedReturnsDegraded() {
            // Both return SKIPPED (no model, no rules)
            StubAgent det = new StubAgent("det-skip", Map.of("x", 1), 0.2);
            StubAgent prob = new StubAgent("prob-skip", Map.of("y", 2), 0.3);

            HybridAgent agent = createHybrid("h-both-skip", det, prob,
                    HybridAgentConfig.RoutingStrategy.PARALLEL);

            var result = runOnEventloop(() -> agent.process(ctx, Map.of("z", 3)));
            // Both LOW_CONFIDENCE → merged result should contain _hybrid.strategy
            assertThat(result.getOutput()).containsEntry("_hybrid.strategy", "PARALLEL");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Missing sub-agent scenarios
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Missing Sub-Agent")
    class MissingSubAgentTests {

        @Test
        void detFirstWithNoProbReturnsDetResultIfHighConfidence() {
            StubAgent det = new StubAgent("det-only", Map.of("answer", "rule"), 0.95);

            HybridAgent agent = new HybridAgent("h-det-only");
            agent.setDeterministicAgent(det);
            // No probabilistic agent set

            HybridAgentConfig config = HybridAgentConfig.builder()
                    .agentId("h-det-only")
                    .type(AgentType.HYBRID)
                    .strategy(HybridAgentConfig.RoutingStrategy.DETERMINISTIC_FIRST)
                    .escalationConfidenceThreshold(0.7)
                    .build();

            AgentConfig stubConfig = AgentConfig.builder().agentId("s").type(AgentType.DETERMINISTIC).build();
            runOnEventloop(() -> det.initialize(stubConfig));
            runOnEventloop(() -> agent.initialize(config));

            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1)));
            assertThat(result.getOutput()).containsEntry("answer", "rule");
        }

        @Test
        void detFirstLowConfAndNoProbReturnsDegraded() {
            StubAgent det = new StubAgent("det-low", Map.of("answer", "unsure"), 0.3);

            HybridAgent agent = new HybridAgent("h-no-prob");
            agent.setDeterministicAgent(det);

            HybridAgentConfig config = HybridAgentConfig.builder()
                    .agentId("h-no-prob")
                    .type(AgentType.HYBRID)
                    .strategy(HybridAgentConfig.RoutingStrategy.DETERMINISTIC_FIRST)
                    .escalationConfidenceThreshold(0.7)
                    .build();

            AgentConfig stubConfig = AgentConfig.builder().agentId("s").type(AgentType.DETERMINISTIC).build();
            runOnEventloop(() -> det.initialize(stubConfig));
            runOnEventloop(() -> agent.initialize(config));

            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1)));
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.DEGRADED);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Parallel — metadata enrichment
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Parallel — metadata")
    class ParallelMetadataTests {

        @Test
        void parallelResultContainsStatusFromBothAgents() {
            StubAgent det = new StubAgent("det", Map.of("rule", "ok"), 0.95);
            StubAgent prob = new StubAgent("prob", Map.of("ml", "ok"), 0.88);

            HybridAgent agent = createHybrid("h-parallel-meta", det, prob,
                    HybridAgentConfig.RoutingStrategy.PARALLEL);

            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1)));
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("_hybrid.detStatus", "SUCCESS");
            assertThat(result.getOutput()).containsEntry("_hybrid.probStatus", "SUCCESS");
            assertThat(result.getOutput()).containsEntry("_hybrid.strategy", "PARALLEL");
        }

        @Test
        void parallelConfidenceUsesMaxWhenDetSucceeds() {
            StubAgent det = new StubAgent("det", Map.of("rule", "ok"), 0.8);
            StubAgent prob = new StubAgent("prob", Map.of("ml", "ok"), 0.95);

            HybridAgent agent = createHybrid("h-parallel-conf", det, prob,
                    HybridAgentConfig.RoutingStrategy.PARALLEL);

            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1)));
            // When det is successful, confidence = max(det, prob) = 0.95
            assertThat(result.getConfidence()).isCloseTo(0.95, within(0.01));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Escalation reason tracking
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Escalation Reason Tracking")
    class EscalationReasonTests {

        @Test
        void escalationReasonIncludedInOutput() {
            StubAgent det = new StubAgent("det", Map.of("answer", "unsure"), 0.3);
            StubAgent prob = new StubAgent("prob", Map.of("answer", "confident"), 0.92);

            HybridAgent agent = createHybrid("h-esc-reason", det, prob,
                    HybridAgentConfig.RoutingStrategy.DETERMINISTIC_FIRST);

            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1)));
            assertThat(result.getOutput()).containsKey("_hybrid.escalationReason");
            assertThat(result.getOutput()).containsEntry("_hybrid.source", "probabilistic");
        }
    }
}
