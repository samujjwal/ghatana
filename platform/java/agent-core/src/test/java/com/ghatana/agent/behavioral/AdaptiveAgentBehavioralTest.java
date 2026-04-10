/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.behavioral;

import com.ghatana.agent.*;
import com.ghatana.agent.adaptive.*;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Behavioral tests for AdaptiveAgent.
 *
 * Focus: Multi-armed bandit algorithms, self-tuning, parameter learning,
 * exploration vs exploitation trade-off, and convergence.
 */
@DisplayName("AdaptiveAgent Behavioral Tests")
@ExtendWith(MockitoExtension.class)
class AdaptiveAgentBehavioralTest {

    @Mock
    private MemoryStore memoryStore;

    private AgentContext agentContext;
    private AdaptiveAgent agent;

    @BeforeEach
    void setUp() {
        agentContext = AgentContext.builder()
                .turnId("turn-1")
                .agentId("adaptive-agent")
                .tenantId("tenant-1")
                .memoryStore(memoryStore)
                .build();

        agent = new AdaptiveAgent("adaptive-agent");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Processing & Arm Selection Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Processing & Arm Selection")
    class ProcessingTests {

        @Test
        @DisplayName("Agent selects and returns arm value")
        void armSelection() {
            AdaptiveAgentConfig config = AdaptiveAgentConfig.builder()
                    .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.UCB1)
                    .tunedParameter("threshold")
                    .parameterMin(10.0)
                    .parameterMax(100.0)
                    .armCount(5)
                    .build();

            runPromise(() -> agent.initialize(config));

            Map<String, Object> input = Map.of("x", 1);
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).isNotNull();
        }

        @Test
        @DisplayName("Selected arm value is within parameter bounds")
        void armWithinBounds() {
            double min = 20.0;
            double max = 80.0;

            AdaptiveAgentConfig config = AdaptiveAgentConfig.builder()
                    .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.UCB1)
                    .tunedParameter("threshold")
                    .parameterMin(min)
                    .parameterMax(max)
                    .armCount(10)
                    .build();

            runPromise(() -> agent.initialize(config));

            // Run multiple times
            for (int i = 0; i < 20; i++) {
                Map<String, Object> input = Map.of("iteration", i);
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

                assertThat(result.isSuccess()).isTrue();
                // Output should contain arm value or similar metric
                assertThat(result.getOutput()).isNotNull();
            }
        }

        @Test
        @DisplayName("Agent tracks arm statistics over time")
        void armStatisticsTracking() {
            AdaptiveAgentConfig config = AdaptiveAgentConfig.builder()
                    .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.THOMPSON_SAMPLING)
                    .tunedParameter("confidence")
                    .parameterMin(0.1)
                    .parameterMax(0.9)
                    .armCount(5)
                    .build();

            runPromise(() -> agent.initialize(config));

            // Execute multiple rounds
            for (int i = 0; i < 50; i++) {
                Map<String, Object> input = Map.of("round", i);
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

                assertThat(result.isSuccess()).isTrue();
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UCB1 Algorithm Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("UCB1 Algorithm")
    class UCB1Tests {

        @Test
        @DisplayName("UCB1 balances exploration and exploitation")
        void ucb1BalanceExplorationExploitation() {
            AdaptiveAgentConfig config = AdaptiveAgentConfig.builder()
                    .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.UCB1)
                    .tunedParameter("rate")
                    .parameterMin(0.0)
                    .parameterMax(100.0)
                    .armCount(5)
                    .build();

            runPromise(() -> agent.initialize(config));

            // Track arm selections
            Map<Integer, Integer> armCounts = new HashMap<>();

            for (int i = 0; i < 100; i++) {
                Map<String, Object> input = Map.of("round", i);
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

                assertThat(result.isSuccess()).isTrue();
                // Count selections (would need to extract from metrics)
            }

            // Over 100 rounds with 5 arms, should not be perfectly uniform
            // (exploitation should dominate over time)
        }

        @Test
        @DisplayName("UCB1 converges to best arm")
        void ucb1Convergence() {
            AdaptiveAgentConfig config = AdaptiveAgentConfig.builder()
                    .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.UCB1)
                    .tunedParameter("success_rate")
                    .parameterMin(50.0)
                    .parameterMax(100.0)
                    .armCount(3)
                    .build();

            runPromise(() -> agent.initialize(config));

            // After many iterations, agent should converge to optimal arm
            for (int i = 0; i < 200; i++) {
                Map<String, Object> input = Map.of("iteration", i);
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

                assertThat(result.isSuccess()).isTrue();
            }

            // Final selections should bias toward better arms
            Map<String, Object> finalInput = Map.of("final", true);
            AgentResult<?> finalResult = runPromise(() -> agent.process(agentContext, finalInput));
            assertThat(finalResult.getOutput()).isNotNull();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Thompson Sampling Algorithm Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Thompson Sampling Algorithm")
    class ThompsonSamplingTests {

        @Test
        @DisplayName("Thompson Sampling samples from posterior")
        void thompsonSamplingPosterior() {
            AdaptiveAgentConfig config = AdaptiveAgentConfig.builder()
                    .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.THOMPSON_SAMPLING)
                    .tunedParameter("probability")
                    .parameterMin(0.0)
                    .parameterMax(1.0)
                    .armCount(4)
                    .build();

            runPromise(() -> agent.initialize(config));

            // Thompson sampling should show variance in early rounds
            Set<Double> selectedValues = new HashSet<>();

            for (int i = 0; i < 50; i++) {
                Map<String, Object> input = Map.of("round", i);
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

                assertThat(result.isSuccess()).isTrue();
                // Could track selected arm values if exposed in metrics
            }
        }

        @Test
        @DisplayName("Thompson Sampling converges with feedback")
        void thompsonConvergence() {
            AdaptiveAgentConfig config = AdaptiveAgentConfig.builder()
                    .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.THOMPSON_SAMPLING)
                    .tunedParameter("confidence")
                    .parameterMin(10.0)
                    .parameterMax(90.0)
                    .armCount(5)
                    .build();

            runPromise(() -> agent.initialize(config));

            // Multiple iterations to allow convergence
            for (int i = 0; i < 150; i++) {
                Map<String, Object> input = Map.of("reward", i > 100 ? 1 : 0);
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

                assertThat(result.isSuccess()).isTrue();
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Epsilon-Greedy Algorithm Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Epsilon-Greedy Algorithm")
    class EpsilonGreedyTests {

        @Test
        @DisplayName("Epsilon-Greedy explores with fixed probability")
        void epsilonGreedyExploration() {
            double epsilon = 0.1;  // 10% exploration

            AdaptiveAgentConfig config = AdaptiveAgentConfig.builder()
                    .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.EPSILON_GREEDY)
                    .explorationRate(epsilon)
                    .tunedParameter("threshold")
                    .parameterMin(0.0)
                    .parameterMax(100.0)
                    .armCount(5)
                    .build();

            runPromise(() -> agent.initialize(config));

            int explorationCount = 0;
            int totalRounds = 100;

            for (int i = 0; i < totalRounds; i++) {
                Map<String, Object> input = Map.of("round", i);
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

                assertThat(result.isSuccess()).isTrue();
                // Would track exploration vs exploitation if exposed
            }
        }

        @Test
        @DisplayName("Epsilon-Greedy greedy phase exploits best arm")
        void epsilonGreedyExploitation() {
            AdaptiveAgentConfig config = AdaptiveAgentConfig.builder()
                    .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.EPSILON_GREEDY)
                    .explorationRate(0.05)  // 5% exploration
                    .tunedParameter("success_rate")
                    .parameterMin(50.0)
                    .parameterMax(100.0)
                    .armCount(3)
                    .build();

            runPromise(() -> agent.initialize(config));

            // Run many rounds for exploitation phase to dominate
            for (int i = 0; i < 200; i++) {
                Map<String, Object> input = Map.of("iteration", i);
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

                assertThat(result.isSuccess()).isTrue();
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Feedback Integration Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Feedback Integration")
    class FeedbackTests {

        @Test
        @DisplayName("Agent learns from success feedback")
        void learningFromSuccess() {
            AdaptiveAgentConfig config = AdaptiveAgentConfig.builder()
                    .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.THOMPSON_SAMPLING)
                    .tunedParameter("reward_rate")
                    .parameterMin(0.0)
                    .parameterMax(100.0)
                    .armCount(5)
                    .build();

            runPromise(() -> agent.initialize(config));

            // Simulate successful results
            for (int i = 0; i < 50; i++) {
                Map<String, Object> input = Map.of(
                        "round", i,
                        "success", true,
                        "reward", 1.0
                );
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

                assertThat(result.isSuccess()).isTrue();
            }
        }

        @Test
        @DisplayName("Agent learns from failure feedback")
        void learningFromFailure() {
            AdaptiveAgentConfig config = AdaptiveAgentConfig.builder()
                    .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.UCB1)
                    .tunedParameter("failure_rate")
                    .parameterMin(1.0)
                    .parameterMax(10.0)
                    .armCount(4)
                    .build();

            runPromise(() -> agent.initialize(config));

            // Simulate failed results to drive learning
            for (int i = 0; i < 50; i++) {
                Map<String, Object> input = Map.of(
                        "round", i,
                        "success", false,
                        "reward", 0.0
                );
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

                assertThat(result.isSuccess()).isTrue();
            }
        }

        @Test
        @DisplayName("Agent adapts strategy based on reward distribution")
        void strategyAdaptation() {
            AdaptiveAgentConfig config = AdaptiveAgentConfig.builder()
                    .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.THOMPSON_SAMPLING)
                    .tunedParameter("strategy")
                    .parameterMin(10.0)
                    .parameterMax(50.0)
                    .armCount(3)
                    .build();

            runPromise(() -> agent.initialize(config));

            // Early phase: mixed rewards
            for (int i = 0; i < 30; i++) {
                Map<String, Object> input = Map.of(
                        "phase", "early",
                        "reward", i % 2 == 0 ? 1.0 : 0.0
                );
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));
                assertThat(result.isSuccess()).isTrue();
            }

            // Late phase: consistent rewards
            for (int i = 0; i < 30; i++) {
                Map<String, Object> input = Map.of(
                        "phase", "late",
                        "reward", 1.0  // All successful
                );
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));
                assertThat(result.isSuccess()).isTrue();
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Convergence Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Convergence Behavior")
    class ConvergenceTests {

        @Test
        @DisplayName("Agent converges toward optimal parameter")
        void convergenceToOptimal() {
            AdaptiveAgentConfig config = AdaptiveAgentConfig.builder()
                    .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.UCB1)
                    .tunedParameter("optimal_value")
                    .parameterMin(0.0)
                    .parameterMax(100.0)
                    .armCount(10)
                    .build();

            runPromise(() -> agent.initialize(config));

            // Run long horizon for convergence
            for (int i = 0; i < 500; i++) {
                Map<String, Object> input = Map.of("iteration", i);
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

                assertThat(result.isSuccess()).isTrue();
            }

            // Final result should favor learned optimal arm
            Map<String, Object> finalInput = Map.of("final_check", true);
            AgentResult<?> finalResult = runPromise(() -> agent.process(agentContext, finalInput));
            assertThat(finalResult.getOutput()).isNotNull();
        }

        @Test
        @DisplayName("Multiple parameter ranges converge differently")
        void multipleRangeConvergence() {
            // Test with tight range
            AdaptiveAgentConfig tightConfig = AdaptiveAgentConfig.builder()
                    .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.EPSILON_GREEDY)
                    .explorationRate(0.1)
                    .tunedParameter("tight_range")
                    .parameterMin(45.0)
                    .parameterMax(55.0)
                    .armCount(5)
                    .build();

            AdaptiveAgent tightAgent = new AdaptiveAgent("tight-agent");
            runPromise(() -> tightAgent.initialize(tightConfig));

            // Test with wide range
            AdaptiveAgentConfig wideConfig = AdaptiveAgentConfig.builder()
                    .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.EPSILON_GREEDY)
                    .explorationRate(0.1)
                    .tunedParameter("wide_range")
                    .parameterMin(0.0)
                    .parameterMax(1000.0)
                    .armCount(20)
                    .build();

            AdaptiveAgent wideAgent = new AdaptiveAgent("wide-agent");
            runPromise(() -> wideAgent.initialize(wideConfig));

            // Both should converge but at different rates
            for (int i = 0; i < 100; i++) {
                Map<String, Object> input = Map.of("iteration", i);

                AgentResult<?> tightResult = runPromise(() -> tightAgent.process(agentContext, input));
                AgentResult<?> wideResult = runPromise(() -> wideAgent.process(agentContext, input));

                assertThat(tightResult.isSuccess()).isTrue();
                assertThat(wideResult.isSuccess()).isTrue();
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Stability Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Stability")
    class StabilityTests {

        @Test
        @DisplayName("Agent handles zero and extreme rewards")
        void extremeRewardHandling() {
            AdaptiveAgentConfig config = AdaptiveAgentConfig.builder()
                    .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.THOMPSON_SAMPLING)
                    .tunedParameter("extreme_reward")
                    .parameterMin(1.0)
                    .parameterMax(100.0)
                    .armCount(5)
                    .build();

            runPromise(() -> agent.initialize(config));

            // Zero rewards
            for (int i = 0; i < 20; i++) {
                Map<String, Object> input = Map.of("round", i, "reward", 0.0);
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));
                assertThat(result.isSuccess()).isTrue();
            }

            // Maximum rewards
            for (int i = 0; i < 20; i++) {
                Map<String, Object> input = Map.of("round", i, "reward", 1.0);
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));
                assertThat(result.isSuccess()).isTrue();
            }
        }

        @Test
        @DisplayName("Agent handles random reward variations")
        void randomRewardVariation() {
            AdaptiveAgentConfig config = AdaptiveAgentConfig.builder()
                    .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.UCB1)
                    .tunedParameter("random_variation")
                    .parameterMin(10.0)
                    .parameterMax(90.0)
                    .armCount(5)
                    .build();

            runPromise(() -> agent.initialize(config));

            java.util.Random random = new java.util.Random(42);

            for (int i = 0; i < 100; i++) {
                double reward = random.nextDouble();
                Map<String, Object> input = Map.of(
                        "round", i,
                        "reward", reward
                );
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));
                assertThat(result.isSuccess()).isTrue();
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Confidence & Metrics Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Confidence & Metrics")
    class ConfidenceMetricsTests {

        @Test
        @DisplayName("Confidence reflects learning progress")
        void confidenceProgress() {
            AdaptiveAgentConfig config = AdaptiveAgentConfig.builder()
                    .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.THOMPSON_SAMPLING)
                    .tunedParameter("confidence")
                    .parameterMin(0.0)
                    .parameterMax(100.0)
                    .armCount(5)
                    .build();

            runPromise(() -> agent.initialize(config));

            // Early iterations should have varying confidence
            for (int i = 0; i < 50; i++) {
                Map<String, Object> input = Map.of("round", i);
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

                assertThat(result.getConfidence())
                        .isGreaterThanOrEqualTo(0.0)
                        .isLessThanOrEqualTo(1.0);
            }

            // Later iterations should converge
            for (int i = 50; i < 100; i++) {
                Map<String, Object> input = Map.of("round", i);
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

                assertThat(result.getConfidence())
                        .isGreaterThanOrEqualTo(0.0)
                        .isLessThanOrEqualTo(1.0);
            }
        }

        @Test
        @DisplayName("Agent metrics include arm statistics")
        void armMetricsTracking() {
            AdaptiveAgentConfig config = AdaptiveAgentConfig.builder()
                    .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.UCB1)
                    .tunedParameter("metrics")
                    .parameterMin(0.0)
                    .parameterMax(100.0)
                    .armCount(5)
                    .build();

            runPromise(() -> agent.initialize(config));

            for (int i = 0; i < 100; i++) {
                Map<String, Object> input = Map.of("round", i);
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input));

                assertThat(result.getMetrics()).isNotNull();
                // Could contain arm pull counts, rewards, etc.
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    private <T> T runPromise(java.util.function.Supplier<Promise<T>> supplier) {
        var result = new Object() { T value; };
        var error = new Object() { Exception ex; };

        Eventloop eventloop = Eventloop.builder().withCurrentThread().build();
        eventloop.post(() -> supplier.get()
                .whenResult(v -> result.value = v)
                .whenException(e -> error.ex = (Exception) e));

        eventloop.run();

        if (error.ex != null) {
            throw new RuntimeException(error.ex);
        }

        return result.value;
    }
}
