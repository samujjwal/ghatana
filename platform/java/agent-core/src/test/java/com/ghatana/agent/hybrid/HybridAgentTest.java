/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
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
 * Comprehensive tests for HybridAgent — all routing strategies.
 */
@DisplayName("Hybrid Agent")
class HybridAgentTest {

    private AgentContext ctx;

    @BeforeEach
    void setUp() {
        ctx = AgentContext.builder()
                .turnId("turn-1")
                .agentId("hybrid-test")
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
    // Stub Agents
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * A simple agent that returns a fixed result.
     */
    static class StubAgent extends AbstractTypedAgent<Map<String, Object>, Map<String, Object>> {
        private final AgentDescriptor desc;
        private final Map<String, Object> fixedOutput;
        private final double confidence;
        private final boolean shouldFail;

        StubAgent(String id, Map<String, Object> output, double confidence) {
            this(id, output, confidence, false);
        }

        StubAgent(String id, Map<String, Object> output, double confidence, boolean shouldFail) {
            this.desc = AgentDescriptor.builder()
                    .agentId(id).name(id).version("1.0")
                    .type(AgentType.DETERMINISTIC)
                    .build();
            this.fixedOutput = output;
            this.confidence = confidence;
            this.shouldFail = shouldFail;
        }

        @Override public @NotNull AgentDescriptor descriptor() { return desc; }

        @Override protected @NotNull Promise<AgentResult<Map<String, Object>>> doProcess(
                @NotNull AgentContext ctx, @NotNull Map<String, Object> input) {
            if (shouldFail) {
                return Promise.ofException(new RuntimeException("Stub failure"));
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

        // Initialize both stubs
        AgentConfig stubConfig = AgentConfig.builder().agentId("stub").type(AgentType.DETERMINISTIC).build();
        runOnEventloop(() -> det.initialize(stubConfig));
        runOnEventloop(() -> prob.initialize(stubConfig));
        runOnEventloop(() -> agent.initialize(config));
        return agent;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Deterministic-First Strategy
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DETERMINISTIC_FIRST")
    class DeterministicFirst {

        @Test void usesDetResultWhenHighConfidence() {
            StubAgent det = new StubAgent("det", Map.of("decision", "BLOCK"), 0.95);
            StubAgent prob = new StubAgent("prob", Map.of("decision", "ALLOW"), 0.8);

            HybridAgent agent = createHybrid("h1", det, prob,
                    HybridAgentConfig.RoutingStrategy.DETERMINISTIC_FIRST);

            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1)));
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("decision", "BLOCK");
        }

        @Test void escalatesToProbOnLowConfidence() {
            StubAgent det = new StubAgent("det", Map.of("decision", "UNSURE"), 0.3);
            StubAgent prob = new StubAgent("prob", Map.of("decision", "ALLOW"), 0.9);

            HybridAgent agent = createHybrid("h2", det, prob,
                    HybridAgentConfig.RoutingStrategy.DETERMINISTIC_FIRST);

            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1)));
            // Should escalate to prob since det confidence 0.3 < threshold 0.7
            assertThat(result.getOutput()).containsEntry("decision", "ALLOW");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Probabilistic-First Strategy
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PROBABILISTIC_FIRST")
    class ProbabilisticFirst {

        @Test void usesProbWhenHighConfidence() {
            StubAgent det = new StubAgent("det", Map.of("decision", "BLOCK"), 0.99);
            StubAgent prob = new StubAgent("prob", Map.of("decision", "ML_ALLOW"), 0.92);

            HybridAgent agent = createHybrid("h3", det, prob,
                    HybridAgentConfig.RoutingStrategy.PROBABILISTIC_FIRST);

            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1)));
            assertThat(result.getOutput()).containsEntry("decision", "ML_ALLOW");
        }

        @Test void fallsToDeterministicOnProbFailure() {
            StubAgent det = new StubAgent("det", Map.of("decision", "RULE_OK"), 0.95);
            StubAgent prob = new StubAgent("prob", Map.of(), 0.0, true);

            HybridAgent agent = createHybrid("h4", det, prob,
                    HybridAgentConfig.RoutingStrategy.PROBABILISTIC_FIRST);

            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1)));
            assertThat(result.getOutput()).containsEntry("decision", "RULE_OK");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Parallel Strategy
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PARALLEL")
    class ParallelStrategy {

        @Test void mergesBothResults() {
            StubAgent det = new StubAgent("det", Map.of("rule_decision", "BLOCK"), 0.95);
            StubAgent prob = new StubAgent("prob", Map.of("ml_score", 0.87), 0.87);

            HybridAgent agent = createHybrid("h5", det, prob,
                    HybridAgentConfig.RoutingStrategy.PARALLEL);

            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1)));
            assertThat(result.isSuccess()).isTrue();
            // Parallel should merge outputs
            assertThat(result.getOutput()).containsKey("rule_decision");
            assertThat(result.getOutput()).containsKey("ml_score");
        }

        @Test void detOverridesProbOnConflict() {
            StubAgent det = new StubAgent("det", Map.of("decision", "DET_WINS"), 0.95);
            StubAgent prob = new StubAgent("prob", Map.of("decision", "PROB_VALUE"), 0.87);

            HybridAgent agent = createHybrid("h6", det, prob,
                    HybridAgentConfig.RoutingStrategy.PARALLEL);

            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1)));
            assertThat(result.getOutput()).containsEntry("decision", "DET_WINS");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Lifecycle")
    class LifecycleTests {

        @Test void tracksMetrics() {
            StubAgent det = new StubAgent("det", Map.of("ok", true), 0.9);
            StubAgent prob = new StubAgent("prob", Map.of("ok", true), 0.9);
            HybridAgent agent = createHybrid("h-metrics", det, prob,
                    HybridAgentConfig.RoutingStrategy.DETERMINISTIC_FIRST);

            runOnEventloop(() -> agent.process(ctx, Map.of("x", 1)));
            runOnEventloop(() -> agent.process(ctx, Map.of("x", 2)));

            assertThat(agent.getTotalInvocations()).isEqualTo(2);
        }
    }
}
