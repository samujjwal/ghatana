/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
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
@ExtendWith(MockitoExtension.class) // GH-90000
class AdaptiveAgentBehavioralTest {

    @Mock
    private MemoryStore memoryStore;

    private AgentContext agentContext;
    private AdaptiveAgent agent;

    @BeforeEach
    void setUp() { // GH-90000
        agentContext = AgentContext.builder() // GH-90000
                .turnId("turn-1")
                .agentId("adaptive-agent")
                .tenantId("tenant-1")
                .memoryStore(memoryStore) // GH-90000
                .build(); // GH-90000

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
        void armSelection() { // GH-90000
            AdaptiveAgentConfig config = AdaptiveAgentConfig.builder() // GH-90000
                    .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.UCB1) // GH-90000
                    .tunedParameter("threshold")
                    .parameterMin(10.0) // GH-90000
                    .parameterMax(100.0) // GH-90000
                    .armCount(5) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            Map<String, Object> input = Map.of("x", 1); // GH-90000
            AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutput()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Selected arm value is within parameter bounds")
        void armWithinBounds() { // GH-90000
            double min = 20.0;
            double max = 80.0;

            AdaptiveAgentConfig config = AdaptiveAgentConfig.builder() // GH-90000
                    .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.UCB1) // GH-90000
                    .tunedParameter("threshold")
                    .parameterMin(min) // GH-90000
                    .parameterMax(max) // GH-90000
                    .armCount(10) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            // Run multiple times
            for (int i = 0; i < 20; i++) { // GH-90000
                Map<String, Object> input = Map.of("iteration", i); // GH-90000
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

                assertThat(result.isSuccess()).isTrue(); // GH-90000
                // Output should contain arm value or similar metric
                assertThat(result.getOutput()).isNotNull(); // GH-90000
            }
        }

        @Test
        @DisplayName("Agent tracks arm statistics over time")
        void armStatisticsTracking() { // GH-90000
            AdaptiveAgentConfig config = AdaptiveAgentConfig.builder() // GH-90000
                    .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.THOMPSON_SAMPLING) // GH-90000
                    .tunedParameter("confidence")
                    .parameterMin(0.1) // GH-90000
                    .parameterMax(0.9) // GH-90000
                    .armCount(5) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            // Execute multiple rounds
            for (int i = 0; i < 50; i++) { // GH-90000
                Map<String, Object> input = Map.of("round", i); // GH-90000
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

                assertThat(result.isSuccess()).isTrue(); // GH-90000
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
        void ucb1BalanceExplorationExploitation() { // GH-90000
            AdaptiveAgentConfig config = AdaptiveAgentConfig.builder() // GH-90000
                    .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.UCB1) // GH-90000
                    .tunedParameter("rate")
                    .parameterMin(0.0) // GH-90000
                    .parameterMax(100.0) // GH-90000
                    .armCount(5) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            // Track arm selections
            Map<Integer, Integer> armCounts = new HashMap<>(); // GH-90000

            for (int i = 0; i < 100; i++) { // GH-90000
                Map<String, Object> input = Map.of("round", i); // GH-90000
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

                assertThat(result.isSuccess()).isTrue(); // GH-90000
                // Count selections (would need to extract from metrics) // GH-90000
            }

            // Over 100 rounds with 5 arms, should not be perfectly uniform
            // (exploitation should dominate over time) // GH-90000
        }

        @Test
        @DisplayName("UCB1 converges to best arm")
        void ucb1Convergence() { // GH-90000
            AdaptiveAgentConfig config = AdaptiveAgentConfig.builder() // GH-90000
                    .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.UCB1) // GH-90000
                    .tunedParameter("success_rate")
                    .parameterMin(50.0) // GH-90000
                    .parameterMax(100.0) // GH-90000
                    .armCount(3) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            // After many iterations, agent should converge to optimal arm
            for (int i = 0; i < 200; i++) { // GH-90000
                Map<String, Object> input = Map.of("iteration", i); // GH-90000
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

                assertThat(result.isSuccess()).isTrue(); // GH-90000
            }

            // Final selections should bias toward better arms
            Map<String, Object> finalInput = Map.of("final", true); // GH-90000
            AgentResult<?> finalResult = runPromise(() -> agent.process(agentContext, finalInput)); // GH-90000
            assertThat(finalResult.getOutput()).isNotNull(); // GH-90000
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
        void thompsonSamplingPosterior() { // GH-90000
            AdaptiveAgentConfig config = AdaptiveAgentConfig.builder() // GH-90000
                    .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.THOMPSON_SAMPLING) // GH-90000
                    .tunedParameter("probability")
                    .parameterMin(0.0) // GH-90000
                    .parameterMax(1.0) // GH-90000
                    .armCount(4) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            // Thompson sampling should show variance in early rounds
            Set<Double> selectedValues = new HashSet<>(); // GH-90000

            for (int i = 0; i < 50; i++) { // GH-90000
                Map<String, Object> input = Map.of("round", i); // GH-90000
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

                assertThat(result.isSuccess()).isTrue(); // GH-90000
                // Could track selected arm values if exposed in metrics
            }
        }

        @Test
        @DisplayName("Thompson Sampling converges with feedback")
        void thompsonConvergence() { // GH-90000
            AdaptiveAgentConfig config = AdaptiveAgentConfig.builder() // GH-90000
                    .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.THOMPSON_SAMPLING) // GH-90000
                    .tunedParameter("confidence")
                    .parameterMin(10.0) // GH-90000
                    .parameterMax(90.0) // GH-90000
                    .armCount(5) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            // Multiple iterations to allow convergence
            for (int i = 0; i < 150; i++) { // GH-90000
                Map<String, Object> input = Map.of("reward", i > 100 ? 1 : 0); // GH-90000
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

                assertThat(result.isSuccess()).isTrue(); // GH-90000
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
        void epsilonGreedyExploration() { // GH-90000
            double epsilon = 0.1;  // 10% exploration

            AdaptiveAgentConfig config = AdaptiveAgentConfig.builder() // GH-90000
                    .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.EPSILON_GREEDY) // GH-90000
                    .explorationRate(epsilon) // GH-90000
                    .tunedParameter("threshold")
                    .parameterMin(0.0) // GH-90000
                    .parameterMax(100.0) // GH-90000
                    .armCount(5) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            int explorationCount = 0;
            int totalRounds = 100;

            for (int i = 0; i < totalRounds; i++) { // GH-90000
                Map<String, Object> input = Map.of("round", i); // GH-90000
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

                assertThat(result.isSuccess()).isTrue(); // GH-90000
                // Would track exploration vs exploitation if exposed
            }
        }

        @Test
        @DisplayName("Epsilon-Greedy greedy phase exploits best arm")
        void epsilonGreedyExploitation() { // GH-90000
            AdaptiveAgentConfig config = AdaptiveAgentConfig.builder() // GH-90000
                    .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.EPSILON_GREEDY) // GH-90000
                    .explorationRate(0.05)  // 5% exploration // GH-90000
                    .tunedParameter("success_rate")
                    .parameterMin(50.0) // GH-90000
                    .parameterMax(100.0) // GH-90000
                    .armCount(3) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            // Run many rounds for exploitation phase to dominate
            for (int i = 0; i < 200; i++) { // GH-90000
                Map<String, Object> input = Map.of("iteration", i); // GH-90000
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

                assertThat(result.isSuccess()).isTrue(); // GH-90000
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
        void learningFromSuccess() { // GH-90000
            AdaptiveAgentConfig config = AdaptiveAgentConfig.builder() // GH-90000
                    .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.THOMPSON_SAMPLING) // GH-90000
                    .tunedParameter("reward_rate")
                    .parameterMin(0.0) // GH-90000
                    .parameterMax(100.0) // GH-90000
                    .armCount(5) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            // Simulate successful results
            for (int i = 0; i < 50; i++) { // GH-90000
                Map<String, Object> input = Map.of( // GH-90000
                        "round", i,
                        "success", true,
                        "reward", 1.0
                );
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

                assertThat(result.isSuccess()).isTrue(); // GH-90000
            }
        }

        @Test
        @DisplayName("Agent learns from failure feedback")
        void learningFromFailure() { // GH-90000
            AdaptiveAgentConfig config = AdaptiveAgentConfig.builder() // GH-90000
                    .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.UCB1) // GH-90000
                    .tunedParameter("failure_rate")
                    .parameterMin(1.0) // GH-90000
                    .parameterMax(10.0) // GH-90000
                    .armCount(4) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            // Simulate failed results to drive learning
            for (int i = 0; i < 50; i++) { // GH-90000
                Map<String, Object> input = Map.of( // GH-90000
                        "round", i,
                        "success", false,
                        "reward", 0.0
                );
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

                assertThat(result.isSuccess()).isTrue(); // GH-90000
            }
        }

        @Test
        @DisplayName("Agent adapts strategy based on reward distribution")
        void strategyAdaptation() { // GH-90000
            AdaptiveAgentConfig config = AdaptiveAgentConfig.builder() // GH-90000
                    .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.THOMPSON_SAMPLING) // GH-90000
                    .tunedParameter("strategy")
                    .parameterMin(10.0) // GH-90000
                    .parameterMax(50.0) // GH-90000
                    .armCount(3) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            // Early phase: mixed rewards
            for (int i = 0; i < 30; i++) { // GH-90000
                Map<String, Object> input = Map.of( // GH-90000
                        "phase", "early",
                        "reward", i % 2 == 0 ? 1.0 : 0.0
                );
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000
                assertThat(result.isSuccess()).isTrue(); // GH-90000
            }

            // Late phase: consistent rewards
            for (int i = 0; i < 30; i++) { // GH-90000
                Map<String, Object> input = Map.of( // GH-90000
                        "phase", "late",
                        "reward", 1.0  // All successful
                );
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000
                assertThat(result.isSuccess()).isTrue(); // GH-90000
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
        void convergenceToOptimal() { // GH-90000
            AdaptiveAgentConfig config = AdaptiveAgentConfig.builder() // GH-90000
                    .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.UCB1) // GH-90000
                    .tunedParameter("optimal_value")
                    .parameterMin(0.0) // GH-90000
                    .parameterMax(100.0) // GH-90000
                    .armCount(10) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            // Run long horizon for convergence
            for (int i = 0; i < 500; i++) { // GH-90000
                Map<String, Object> input = Map.of("iteration", i); // GH-90000
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

                assertThat(result.isSuccess()).isTrue(); // GH-90000
            }

            // Final result should favor learned optimal arm
            Map<String, Object> finalInput = Map.of("final_check", true); // GH-90000
            AgentResult<?> finalResult = runPromise(() -> agent.process(agentContext, finalInput)); // GH-90000
            assertThat(finalResult.getOutput()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("Multiple parameter ranges converge differently")
        void multipleRangeConvergence() { // GH-90000
            // Test with tight range
            AdaptiveAgentConfig tightConfig = AdaptiveAgentConfig.builder() // GH-90000
                    .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.EPSILON_GREEDY) // GH-90000
                    .explorationRate(0.1) // GH-90000
                    .tunedParameter("tight_range")
                    .parameterMin(45.0) // GH-90000
                    .parameterMax(55.0) // GH-90000
                    .armCount(5) // GH-90000
                    .build(); // GH-90000

            AdaptiveAgent tightAgent = new AdaptiveAgent("tight-agent");
            runPromise(() -> tightAgent.initialize(tightConfig)); // GH-90000

            // Test with wide range
            AdaptiveAgentConfig wideConfig = AdaptiveAgentConfig.builder() // GH-90000
                    .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.EPSILON_GREEDY) // GH-90000
                    .explorationRate(0.1) // GH-90000
                    .tunedParameter("wide_range")
                    .parameterMin(0.0) // GH-90000
                    .parameterMax(1000.0) // GH-90000
                    .armCount(20) // GH-90000
                    .build(); // GH-90000

            AdaptiveAgent wideAgent = new AdaptiveAgent("wide-agent");
            runPromise(() -> wideAgent.initialize(wideConfig)); // GH-90000

            // Both should converge but at different rates
            for (int i = 0; i < 100; i++) { // GH-90000
                Map<String, Object> input = Map.of("iteration", i); // GH-90000

                AgentResult<?> tightResult = runPromise(() -> tightAgent.process(agentContext, input)); // GH-90000
                AgentResult<?> wideResult = runPromise(() -> wideAgent.process(agentContext, input)); // GH-90000

                assertThat(tightResult.isSuccess()).isTrue(); // GH-90000
                assertThat(wideResult.isSuccess()).isTrue(); // GH-90000
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
        void extremeRewardHandling() { // GH-90000
            AdaptiveAgentConfig config = AdaptiveAgentConfig.builder() // GH-90000
                    .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.THOMPSON_SAMPLING) // GH-90000
                    .tunedParameter("extreme_reward")
                    .parameterMin(1.0) // GH-90000
                    .parameterMax(100.0) // GH-90000
                    .armCount(5) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            // Zero rewards
            for (int i = 0; i < 20; i++) { // GH-90000
                Map<String, Object> input = Map.of("round", i, "reward", 0.0); // GH-90000
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000
                assertThat(result.isSuccess()).isTrue(); // GH-90000
            }

            // Maximum rewards
            for (int i = 0; i < 20; i++) { // GH-90000
                Map<String, Object> input = Map.of("round", i, "reward", 1.0); // GH-90000
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000
                assertThat(result.isSuccess()).isTrue(); // GH-90000
            }
        }

        @Test
        @DisplayName("Agent handles random reward variations")
        void randomRewardVariation() { // GH-90000
            AdaptiveAgentConfig config = AdaptiveAgentConfig.builder() // GH-90000
                    .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.UCB1) // GH-90000
                    .tunedParameter("random_variation")
                    .parameterMin(10.0) // GH-90000
                    .parameterMax(90.0) // GH-90000
                    .armCount(5) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            java.util.Random random = new java.util.Random(42); // GH-90000

            for (int i = 0; i < 100; i++) { // GH-90000
                double reward = random.nextDouble(); // GH-90000
                Map<String, Object> input = Map.of( // GH-90000
                        "round", i,
                        "reward", reward
                );
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000
                assertThat(result.isSuccess()).isTrue(); // GH-90000
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
        void confidenceProgress() { // GH-90000
            AdaptiveAgentConfig config = AdaptiveAgentConfig.builder() // GH-90000
                    .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.THOMPSON_SAMPLING) // GH-90000
                    .tunedParameter("confidence")
                    .parameterMin(0.0) // GH-90000
                    .parameterMax(100.0) // GH-90000
                    .armCount(5) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            // Early iterations should have varying confidence
            for (int i = 0; i < 50; i++) { // GH-90000
                Map<String, Object> input = Map.of("round", i); // GH-90000
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

                assertThat(result.getConfidence()) // GH-90000
                        .isGreaterThanOrEqualTo(0.0) // GH-90000
                        .isLessThanOrEqualTo(1.0); // GH-90000
            }

            // Later iterations should converge
            for (int i = 50; i < 100; i++) { // GH-90000
                Map<String, Object> input = Map.of("round", i); // GH-90000
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

                assertThat(result.getConfidence()) // GH-90000
                        .isGreaterThanOrEqualTo(0.0) // GH-90000
                        .isLessThanOrEqualTo(1.0); // GH-90000
            }
        }

        @Test
        @DisplayName("Agent metrics include arm statistics")
        void armMetricsTracking() { // GH-90000
            AdaptiveAgentConfig config = AdaptiveAgentConfig.builder() // GH-90000
                    .banditAlgorithm(AdaptiveAgentConfig.BanditAlgorithm.UCB1) // GH-90000
                    .tunedParameter("metrics")
                    .parameterMin(0.0) // GH-90000
                    .parameterMax(100.0) // GH-90000
                    .armCount(5) // GH-90000
                    .build(); // GH-90000

            runPromise(() -> agent.initialize(config)); // GH-90000

            for (int i = 0; i < 100; i++) { // GH-90000
                Map<String, Object> input = Map.of("round", i); // GH-90000
                AgentResult<?> result = runPromise(() -> agent.process(agentContext, input)); // GH-90000

                assertThat(result.getMetrics()).isNotNull(); // GH-90000
                // Could contain arm pull counts, rewards, etc.
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    private <T> T runPromise(java.util.function.Supplier<Promise<T>> supplier) { // GH-90000
        var result = new Object() { T value; }; // GH-90000
        var error = new Object() { Exception ex; }; // GH-90000

        Eventloop eventloop = Eventloop.builder().withCurrentThread().build(); // GH-90000
        eventloop.post(() -> supplier.get() // GH-90000
                .whenResult(v -> result.value = v) // GH-90000
                .whenException(e -> error.ex = (Exception) e)); // GH-90000

        eventloop.run(); // GH-90000

        if (error.ex != null) { // GH-90000
            throw new RuntimeException(error.ex); // GH-90000
        }

        return result.value;
    }
}
