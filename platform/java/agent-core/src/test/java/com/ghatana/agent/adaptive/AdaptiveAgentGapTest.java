/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
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
 *   <li>Custom parameter range (non [0,1])</li>
 *   <li>Single arm edge case</li>
 *   <li>All algorithms with many iterations — verifying statistical properties</li>
 * </ul>
 */
@DisplayName("Adaptive Agent — Gap Tests")
class AdaptiveAgentGapTest {

    private AgentContext ctx;

    @BeforeEach
    void setUp() {
        ctx = AgentContext.builder()
                .turnId("turn-1")
                .agentId("adaptive-gap")
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

    private AdaptiveAgent createAgent(String id, AdaptiveAgentConfig.BanditAlgorithm algo,
                                       int arms, double min, double max, double explorationRate) {
        AdaptiveAgent agent = new AdaptiveAgent(id);
        AdaptiveAgentConfig config = AdaptiveAgentConfig.builder()
                .agentId(id)
                .type(AgentType.ADAPTIVE)
                .banditAlgorithm(algo)
                .tunedParameter("threshold")
                .parameterMin(min)
                .parameterMax(max)
                .armCount(arms)
                .explorationRate(explorationRate)
                .build();
        runOnEventloop(() -> agent.initialize(config));
        return agent;
    }

    private AdaptiveAgent createAgent(String id, AdaptiveAgentConfig.BanditAlgorithm algo, int arms) {
        return createAgent(id, algo, arms, 0.0, 1.0, 0.1);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Negative reward / reward clamping
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Negative Reward")
    class NegativeRewardTests {

        @Test
        void negativeRewardAcceptedAndTracked() {
            AdaptiveAgent agent = createAgent("neg-reward",
                    AdaptiveAgentConfig.BanditAlgorithm.UCB1, 3);

            // Process to explore all 3 arms first (pullCount must be > 0)
            for (int i = 0; i < 3; i++) {
                runOnEventloop(() -> agent.process(ctx, Map.of()));
            }

            // Give arm 0 negative rewards (penalize it)
            for (int i = 0; i < 20; i++) {
                agent.recordFeedback(0, -1.0);
                agent.recordFeedback(1, 0.8);
                agent.recordFeedback(2, 0.5);
            }

            // Arm 0 should NOT be the best arm
            assertThat(agent.getBestArm()).isNotEqualTo(0);
            // Arm 1 (highest reward) should be best
            assertThat(agent.getBestArm()).isEqualTo(1);
        }

        @Test
        void zeroRewardCountsAsFailureForThompson() {
            AdaptiveAgent agent = createAgent("zero-reward",
                    AdaptiveAgentConfig.BanditAlgorithm.THOMPSON_SAMPLING, 3);

            // Process enough times to explore all 3 arms (Thompson Sampling is
            // stochastic so 3 calls may not cover every arm; 30 virtually guarantees it).
            for (int i = 0; i < 30; i++) {
                runOnEventloop(() -> agent.process(ctx, Map.of()));
            }

            // Give arm 2 all successes (>0.5), others all failures (<=0.5)
            // Enough rounds to dominate any noise from the initial exploration.
            for (int i = 0; i < 300; i++) {
                agent.recordFeedback(0, 0.0);  // failure
                agent.recordFeedback(1, 0.1);  // failure  
                agent.recordFeedback(2, 1.0);  // success
            }

            assertThat(agent.getBestArm()).isEqualTo(2);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Invalid arm index
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Invalid Arm Index")
    class InvalidArmTests {

        @Test
        void feedbackWithNegativeIndexThrows() {
            AdaptiveAgent agent = createAgent("invalid-neg",
                    AdaptiveAgentConfig.BanditAlgorithm.UCB1, 3);

            assertThatThrownBy(() -> agent.recordFeedback(-1, 0.5))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void feedbackWithIndexBeyondRangeThrows() {
            AdaptiveAgent agent = createAgent("invalid-high",
                    AdaptiveAgentConfig.BanditAlgorithm.UCB1, 3);

            assertThatThrownBy(() -> agent.recordFeedback(3, 0.5))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Exploitation ratio convergence
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Exploitation Ratio")
    class ExploitationRatioTests {

        @Test
        void exploitationRatioIncreasesWithTraining() {
            AdaptiveAgent agent = createAgent("expl-ratio",
                    AdaptiveAgentConfig.BanditAlgorithm.UCB1, 5);

            // Initial pulls — low exploitation ratio (exploring)
            for (int i = 0; i < 5; i++) {
                runOnEventloop(() -> agent.process(ctx, Map.of()));
            }
            var earlyResult = runOnEventloop(() -> agent.process(ctx, Map.of()));
            double earlyConfidence = earlyResult.getConfidence();

            // Train arm 2 heavily
            for (int i = 0; i < 200; i++) {
                agent.recordFeedback(2, 1.0);
                for (int j = 0; j < 5; j++) {
                    if (j != 2) agent.recordFeedback(j, 0.1);
                }
            }

            // Many more pulls — exploitation ratio should increase
            for (int i = 0; i < 50; i++) {
                runOnEventloop(() -> agent.process(ctx, Map.of()));
            }

            var lateResult = runOnEventloop(() -> agent.process(ctx, Map.of()));
            double lateConfidence = lateResult.getConfidence();

            assertThat(lateConfidence).isGreaterThanOrEqualTo(earlyConfidence);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Arm statistics
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Arm Statistics")
    class ArmStatisticsTests {

        @Test
        void statisticsContainAllExpectedFields() {
            AdaptiveAgent agent = createAgent("stats",
                    AdaptiveAgentConfig.BanditAlgorithm.UCB1, 4);

            // Pull some arms and give feedback
            runOnEventloop(() -> agent.process(ctx, Map.of()));
            agent.recordFeedback(0, 0.7);
            runOnEventloop(() -> agent.process(ctx, Map.of()));

            Map<String, Object> stats = agent.getArmStatistics();

            assertThat(stats).containsKey("totalPulls");
            assertThat(stats).containsKey("bestArm");
            assertThat(stats).containsKey("arm_0");
            assertThat(stats).containsKey("arm_3");

            @SuppressWarnings("unchecked")
            Map<String, Object> arm0 = (Map<String, Object>) stats.get("arm_0");
            assertThat(arm0).containsKeys("value", "pulls", "totalReward", "avgReward");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Custom parameter range
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Custom Parameter Range")
    class CustomRangeTests {

        @Test
        void armValuesDistributedAcrossCustomRange() {
            AdaptiveAgent agent = createAgent("custom-range",
                    AdaptiveAgentConfig.BanditAlgorithm.EPSILON_GREEDY,
                    5, 100.0, 500.0, 0.1);

            var result = runOnEventloop(() -> agent.process(ctx, Map.of()));
            double param = ((Number) result.getOutput().get("threshold")).doubleValue();

            assertThat(param).isBetween(100.0, 500.0);
        }

        @Test
        void bestParameterValueWithinCustomRange() {
            AdaptiveAgent agent = createAgent("range-check",
                    AdaptiveAgentConfig.BanditAlgorithm.UCB1,
                    3, 10.0, 30.0, 0.1);

            // Pull each arm
            for (int i = 0; i < 3; i++) {
                runOnEventloop(() -> agent.process(ctx, Map.of()));
            }

            double best = agent.getBestParameterValue();
            assertThat(best).isBetween(10.0, 30.0);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Single arm edge case
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Single Arm")
    class SingleArmTests {

        @Test
        void singleArmAlwaysSelected() {
            AdaptiveAgent agent = createAgent("single-arm",
                    AdaptiveAgentConfig.BanditAlgorithm.UCB1, 1);

            for (int i = 0; i < 10; i++) {
                var result = runOnEventloop(() -> agent.process(ctx, Map.of()));
                int arm = ((Number) result.getOutput().get("_adaptive.selectedArm")).intValue();
                assertThat(arm).isEqualTo(0);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Output metadata
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Output Metadata")
    class OutputMetadataTests {

        @Test
        void outputContainsAlgorithmName() {
            AdaptiveAgent agent = createAgent("meta-algo",
                    AdaptiveAgentConfig.BanditAlgorithm.THOMPSON_SAMPLING, 3);

            var result = runOnEventloop(() -> agent.process(ctx, Map.of()));

            assertThat(result.getOutput().get("_adaptive.algorithm"))
                    .isEqualTo("THOMPSON_SAMPLING");
        }

        @Test
        void outputContainsPullCounts() {
            AdaptiveAgent agent = createAgent("meta-pulls",
                    AdaptiveAgentConfig.BanditAlgorithm.UCB1, 3);

            runOnEventloop(() -> agent.process(ctx, Map.of()));
            var result = runOnEventloop(() -> agent.process(ctx, Map.of()));

            long totalPulls = ((Number) result.getOutput().get("_adaptive.totalPulls")).longValue();
            assertThat(totalPulls).isEqualTo(2);
        }
    }
}
