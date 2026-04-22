/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
 * Phase 4 — Task 4.1: Gap-filling tests for AdaptiveAgent.
 */

package com.ghatana.agent.adaptive;

import com.ghatana.agent.*;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Gap-filling tests for {@link AdaptiveAgent}.
 *
 * <p>Fills gaps identified in Phase 4 audit:
 * <ul>
 *   <li>Negative reward / reward clamping edge cases</li>
 *   <li>Invalid arm index feedback rejection</li>
 *   <li>Exploitation ratio convergence</li>
 *   <li>Arm statistics completeness</li>
 *   <li>Custom parameter range (non [0,1])</li> // GH-90000
 *   <li>Single arm edge case</li>
 *   <li>All algorithms with many iterations — verifying statistical properties</li>
 * </ul>
 */
@DisplayName("Adaptive Agent — Gap Tests [GH-90000]")
class AdaptiveAgentGapTest {

    private AgentContext ctx;

    @BeforeEach
    void setUp() { // GH-90000
        ctx = AgentContext.builder() // GH-90000
                .turnId("turn-1 [GH-90000]")
                .agentId("adaptive-gap [GH-90000]")
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

    private AdaptiveAgent createAgent(String id, AdaptiveAgentConfig.BanditAlgorithm algo, // GH-90000
                                       int arms, double min, double max, double explorationRate) {
        AdaptiveAgent agent = new AdaptiveAgent(id); // GH-90000
        AdaptiveAgentConfig config = AdaptiveAgentConfig.builder() // GH-90000
                .agentId(id) // GH-90000
                .type(AgentType.ADAPTIVE) // GH-90000
                .banditAlgorithm(algo) // GH-90000
                .tunedParameter("threshold [GH-90000]")
                .parameterMin(min) // GH-90000
                .parameterMax(max) // GH-90000
                .armCount(arms) // GH-90000
                .explorationRate(explorationRate) // GH-90000
                .build(); // GH-90000
        runOnEventloop(() -> agent.initialize(config)); // GH-90000
        return agent;
    }

    private AdaptiveAgent createAgent(String id, AdaptiveAgentConfig.BanditAlgorithm algo, int arms) { // GH-90000
        return createAgent(id, algo, arms, 0.0, 1.0, 0.1); // GH-90000
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Negative reward / reward clamping
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Negative Reward [GH-90000]")
    class NegativeRewardTests {

        @Test
        void negativeRewardAcceptedAndTracked() { // GH-90000
            AdaptiveAgent agent = createAgent("neg-reward", // GH-90000
                    AdaptiveAgentConfig.BanditAlgorithm.UCB1, 3);

            // Process to explore all 3 arms first (pullCount must be > 0) // GH-90000
            for (int i = 0; i < 3; i++) { // GH-90000
                runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000
            }

            // Give arm 0 negative rewards (penalize it) // GH-90000
            for (int i = 0; i < 20; i++) { // GH-90000
                agent.recordFeedback(0, -1.0); // GH-90000
                agent.recordFeedback(1, 0.8); // GH-90000
                agent.recordFeedback(2, 0.5); // GH-90000
            }

            // Arm 0 should NOT be the best arm
            assertThat(agent.getBestArm()).isNotEqualTo(0); // GH-90000
            // Arm 1 (highest reward) should be best // GH-90000
            assertThat(agent.getBestArm()).isEqualTo(1); // GH-90000
        }

        @Test
        void zeroRewardCountsAsFailureForThompson() { // GH-90000
            AdaptiveAgent agent = createAgent("zero-reward", // GH-90000
                    AdaptiveAgentConfig.BanditAlgorithm.THOMPSON_SAMPLING, 3);

            // Process enough times to explore all 3 arms (Thompson Sampling is // GH-90000
            // stochastic so 3 calls may not cover every arm; 30 virtually guarantees it).
            for (int i = 0; i < 30; i++) { // GH-90000
                runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000
            }

            // Give arm 2 all successes (>0.5), others all failures (<=0.5) // GH-90000
            // Enough rounds to dominate any noise from the initial exploration.
            for (int i = 0; i < 300; i++) { // GH-90000
                agent.recordFeedback(0, 0.0);  // failure // GH-90000
                agent.recordFeedback(1, 0.1);  // failure // GH-90000
                agent.recordFeedback(2, 1.0);  // success // GH-90000
            }

            assertThat(agent.getBestArm()).isEqualTo(2); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Invalid arm index
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Invalid Arm Index [GH-90000]")
    class InvalidArmTests {

        @Test
        void feedbackWithNegativeIndexThrows() { // GH-90000
            AdaptiveAgent agent = createAgent("invalid-neg", // GH-90000
                    AdaptiveAgentConfig.BanditAlgorithm.UCB1, 3);

            assertThatThrownBy(() -> agent.recordFeedback(-1, 0.5)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        void feedbackWithIndexBeyondRangeThrows() { // GH-90000
            AdaptiveAgent agent = createAgent("invalid-high", // GH-90000
                    AdaptiveAgentConfig.BanditAlgorithm.UCB1, 3);

            assertThatThrownBy(() -> agent.recordFeedback(3, 0.5)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Exploitation ratio convergence
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Exploitation Ratio [GH-90000]")
    class ExploitationRatioTests {

        @Test
        void exploitationRatioIncreasesWithTraining() { // GH-90000
            AdaptiveAgent agent = createAgent("expl-ratio", // GH-90000
                    AdaptiveAgentConfig.BanditAlgorithm.UCB1, 5);

            // Initial pulls — low exploitation ratio (exploring) // GH-90000
            for (int i = 0; i < 5; i++) { // GH-90000
                runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000
            }
            var earlyResult = runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000
            double earlyConfidence = earlyResult.getConfidence(); // GH-90000

            // Train arm 2 heavily
            for (int i = 0; i < 200; i++) { // GH-90000
                agent.recordFeedback(2, 1.0); // GH-90000
                for (int j = 0; j < 5; j++) { // GH-90000
                    if (j != 2) agent.recordFeedback(j, 0.1); // GH-90000
                }
            }

            // Many more pulls — exploitation ratio should increase
            for (int i = 0; i < 50; i++) { // GH-90000
                runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000
            }

            var lateResult = runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000
            double lateConfidence = lateResult.getConfidence(); // GH-90000

            assertThat(lateConfidence).isGreaterThanOrEqualTo(earlyConfidence); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Arm statistics
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Arm Statistics [GH-90000]")
    class ArmStatisticsTests {

        @Test
        void statisticsContainAllExpectedFields() { // GH-90000
            AdaptiveAgent agent = createAgent("stats", // GH-90000
                    AdaptiveAgentConfig.BanditAlgorithm.UCB1, 4);

            // Pull some arms and give feedback
            runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000
            agent.recordFeedback(0, 0.7); // GH-90000
            runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000

            Map<String, Object> stats = agent.getArmStatistics(); // GH-90000

            assertThat(stats).containsKey("totalPulls [GH-90000]");
            assertThat(stats).containsKey("bestArm [GH-90000]");
            assertThat(stats).containsKey("arm_0 [GH-90000]");
            assertThat(stats).containsKey("arm_3 [GH-90000]");

            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> arm0 = (Map<String, Object>) stats.get("arm_0 [GH-90000]");
            assertThat(arm0).containsKeys("value", "pulls", "totalReward", "avgReward"); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Custom parameter range
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Custom Parameter Range [GH-90000]")
    class CustomRangeTests {

        @Test
        void armValuesDistributedAcrossCustomRange() { // GH-90000
            AdaptiveAgent agent = createAgent("custom-range", // GH-90000
                    AdaptiveAgentConfig.BanditAlgorithm.EPSILON_GREEDY,
                    5, 100.0, 500.0, 0.1);

            var result = runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000
            double param = ((Number) result.getOutput().get("threshold [GH-90000]")).doubleValue();

            assertThat(param).isBetween(100.0, 500.0); // GH-90000
        }

        @Test
        void bestParameterValueWithinCustomRange() { // GH-90000
            AdaptiveAgent agent = createAgent("range-check", // GH-90000
                    AdaptiveAgentConfig.BanditAlgorithm.UCB1,
                    3, 10.0, 30.0, 0.1);

            // Pull each arm
            for (int i = 0; i < 3; i++) { // GH-90000
                runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000
            }

            double best = agent.getBestParameterValue(); // GH-90000
            assertThat(best).isBetween(10.0, 30.0); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Single arm edge case
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Single Arm [GH-90000]")
    class SingleArmTests {

        @Test
        void singleArmAlwaysSelected() { // GH-90000
            AdaptiveAgent agent = createAgent("single-arm", // GH-90000
                    AdaptiveAgentConfig.BanditAlgorithm.UCB1, 1);

            for (int i = 0; i < 10; i++) { // GH-90000
                var result = runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000
                int arm = ((Number) result.getOutput().get("_adaptive.selectedArm [GH-90000]")).intValue();
                assertThat(arm).isEqualTo(0); // GH-90000
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Output metadata
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Output Metadata [GH-90000]")
    class OutputMetadataTests {

        @Test
        void outputContainsAlgorithmName() { // GH-90000
            AdaptiveAgent agent = createAgent("meta-algo", // GH-90000
                    AdaptiveAgentConfig.BanditAlgorithm.THOMPSON_SAMPLING, 3);

            var result = runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000

            assertThat(result.getOutput().get("_adaptive.algorithm [GH-90000]"))
                    .isEqualTo("THOMPSON_SAMPLING [GH-90000]");
        }

        @Test
        void outputContainsPullCounts() { // GH-90000
            AdaptiveAgent agent = createAgent("meta-pulls", // GH-90000
                    AdaptiveAgentConfig.BanditAlgorithm.UCB1, 3);

            runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000
            var result = runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000

            long totalPulls = ((Number) result.getOutput().get("_adaptive.totalPulls [GH-90000]")).longValue();
            assertThat(totalPulls).isEqualTo(2); // GH-90000
        }
    }
}
