/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
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
    void setUp() { // GH-90000
        ctx = AgentContext.builder() // GH-90000
                .turnId("turn-1")
                .agentId("hybrid-test")
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

        StubAgent(String id, Map<String, Object> output, double confidence) { // GH-90000
            this(id, output, confidence, false); // GH-90000
        }

        StubAgent(String id, Map<String, Object> output, double confidence, boolean shouldFail) { // GH-90000
            this.desc = AgentDescriptor.builder() // GH-90000
                    .agentId(id).name(id).version("1.0")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .build(); // GH-90000
            this.fixedOutput = output;
            this.confidence = confidence;
            this.shouldFail = shouldFail;
        }

        @Override public @NotNull AgentDescriptor descriptor() { return desc; } // GH-90000

        @Override protected @NotNull Promise<AgentResult<Map<String, Object>>> doProcess( // GH-90000
                @NotNull AgentContext ctx, @NotNull Map<String, Object> input) {
            if (shouldFail) { // GH-90000
                return Promise.ofException(new RuntimeException("Stub failure"));
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

        // Initialize both stubs
        AgentConfig stubConfig = AgentConfig.builder().agentId("stub").type(AgentType.DETERMINISTIC).build();
        runOnEventloop(() -> det.initialize(stubConfig)); // GH-90000
        runOnEventloop(() -> prob.initialize(stubConfig)); // GH-90000
        runOnEventloop(() -> agent.initialize(config)); // GH-90000
        return agent;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Deterministic-First Strategy
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DETERMINISTIC_FIRST")
    class DeterministicFirst {

        @Test void usesDetResultWhenHighConfidence() { // GH-90000
            StubAgent det = new StubAgent("det", Map.of("decision", "BLOCK"), 0.95); // GH-90000
            StubAgent prob = new StubAgent("prob", Map.of("decision", "ALLOW"), 0.8); // GH-90000

            HybridAgent agent = createHybrid("h1", det, prob, // GH-90000
                    HybridAgentConfig.RoutingStrategy.DETERMINISTIC_FIRST);

            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1))); // GH-90000
            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutput()).containsEntry("decision", "BLOCK"); // GH-90000
        }

        @Test void escalatesToProbOnLowConfidence() { // GH-90000
            StubAgent det = new StubAgent("det", Map.of("decision", "UNSURE"), 0.3); // GH-90000
            StubAgent prob = new StubAgent("prob", Map.of("decision", "ALLOW"), 0.9); // GH-90000

            HybridAgent agent = createHybrid("h2", det, prob, // GH-90000
                    HybridAgentConfig.RoutingStrategy.DETERMINISTIC_FIRST);

            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1))); // GH-90000
            // Should escalate to prob since det confidence 0.3 < threshold 0.7
            assertThat(result.getOutput()).containsEntry("decision", "ALLOW"); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Probabilistic-First Strategy
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PROBABILISTIC_FIRST")
    class ProbabilisticFirst {

        @Test void usesProbWhenHighConfidence() { // GH-90000
            StubAgent det = new StubAgent("det", Map.of("decision", "BLOCK"), 0.99); // GH-90000
            StubAgent prob = new StubAgent("prob", Map.of("decision", "ML_ALLOW"), 0.92); // GH-90000

            HybridAgent agent = createHybrid("h3", det, prob, // GH-90000
                    HybridAgentConfig.RoutingStrategy.PROBABILISTIC_FIRST);

            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1))); // GH-90000
            assertThat(result.getOutput()).containsEntry("decision", "ML_ALLOW"); // GH-90000
        }

        @Test void fallsToDeterministicOnProbFailure() { // GH-90000
            StubAgent det = new StubAgent("det", Map.of("decision", "RULE_OK"), 0.95); // GH-90000
            StubAgent prob = new StubAgent("prob", Map.of(), 0.0, true); // GH-90000

            HybridAgent agent = createHybrid("h4", det, prob, // GH-90000
                    HybridAgentConfig.RoutingStrategy.PROBABILISTIC_FIRST);

            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1))); // GH-90000
            assertThat(result.getOutput()).containsEntry("decision", "RULE_OK"); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Parallel Strategy
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PARALLEL")
    class ParallelStrategy {

        @Test void mergesBothResults() { // GH-90000
            StubAgent det = new StubAgent("det", Map.of("rule_decision", "BLOCK"), 0.95); // GH-90000
            StubAgent prob = new StubAgent("prob", Map.of("ml_score", 0.87), 0.87); // GH-90000

            HybridAgent agent = createHybrid("h5", det, prob, // GH-90000
                    HybridAgentConfig.RoutingStrategy.PARALLEL);

            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1))); // GH-90000
            assertThat(result.isSuccess()).isTrue(); // GH-90000
            // Parallel should merge outputs
            assertThat(result.getOutput()).containsKey("rule_decision");
            assertThat(result.getOutput()).containsKey("ml_score");
        }

        @Test void detOverridesProbOnConflict() { // GH-90000
            StubAgent det = new StubAgent("det", Map.of("decision", "DET_WINS"), 0.95); // GH-90000
            StubAgent prob = new StubAgent("prob", Map.of("decision", "PROB_VALUE"), 0.87); // GH-90000

            HybridAgent agent = createHybrid("h6", det, prob, // GH-90000
                    HybridAgentConfig.RoutingStrategy.PARALLEL);

            var result = runOnEventloop(() -> agent.process(ctx, Map.of("x", 1))); // GH-90000
            assertThat(result.getOutput()).containsEntry("decision", "DET_WINS"); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Lifecycle")
    class LifecycleTests {

        @Test void tracksMetrics() { // GH-90000
            StubAgent det = new StubAgent("det", Map.of("ok", true), 0.9); // GH-90000
            StubAgent prob = new StubAgent("prob", Map.of("ok", true), 0.9); // GH-90000
            HybridAgent agent = createHybrid("h-metrics", det, prob, // GH-90000
                    HybridAgentConfig.RoutingStrategy.DETERMINISTIC_FIRST);

            runOnEventloop(() -> agent.process(ctx, Map.of("x", 1))); // GH-90000
            runOnEventloop(() -> agent.process(ctx, Map.of("x", 2))); // GH-90000

            assertThat(agent.getTotalInvocations()).isEqualTo(2); // GH-90000
        }
    }
}
