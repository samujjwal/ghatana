/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
 */

package com.ghatana.agent.adaptive;

import com.ghatana.agent.AgentType;
import com.ghatana.platform.health.HealthStatus;
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
 * Comprehensive tests for AdaptiveAgent — bandit algorithms and feedback.
 */
@DisplayName("Adaptive Agent [GH-90000]")
class AdaptiveAgentTest {

    private AgentContext ctx;

    @BeforeEach
    void setUp() { // GH-90000
        ctx = AgentContext.builder() // GH-90000
                .turnId("turn-1 [GH-90000]")
                .agentId("adaptive-test [GH-90000]")
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

    private AdaptiveAgent createAgent(String id, AdaptiveAgentConfig.BanditAlgorithm algo, int arms) { // GH-90000
        AdaptiveAgent agent = new AdaptiveAgent(id); // GH-90000
        AdaptiveAgentConfig config = AdaptiveAgentConfig.builder() // GH-90000
                .agentId(id) // GH-90000
                .type(AgentType.ADAPTIVE) // GH-90000
                .banditAlgorithm(algo) // GH-90000
                .tunedParameter("threshold [GH-90000]")
                .parameterMin(0.0) // GH-90000
                .parameterMax(1.0) // GH-90000
                .armCount(arms) // GH-90000
                .explorationRate(0.1) // GH-90000
                .build(); // GH-90000
        runOnEventloop(() -> agent.initialize(config)); // GH-90000
        return agent;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UCB1
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("UCB1 [GH-90000]")
    class UCB1Tests {

        @Test void selectsAllArmsInitially() { // GH-90000
            AdaptiveAgent agent = createAgent("ucb1", AdaptiveAgentConfig.BanditAlgorithm.UCB1, 5); // GH-90000

            Set<Integer> selectedArms = new HashSet<>(); // GH-90000
            for (int i = 0; i < 20; i++) { // GH-90000
                var result = runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000
                Object armObj = result.getOutput().get("_adaptive.selectedArm [GH-90000]");
                selectedArms.add(((Number) armObj).intValue()); // GH-90000
            }
            // UCB1 explores all arms before exploiting
            assertThat(selectedArms).hasSize(5); // GH-90000
        }

        @Test void convergesAfterFeedback() { // GH-90000
            AdaptiveAgent agent = createAgent("ucb1-conv", AdaptiveAgentConfig.BanditAlgorithm.UCB1, 3); // GH-90000

            // Pull each arm once to get past exploration phase
            for (int i = 0; i < 3; i++) { // GH-90000
                runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000
            }

            // Give arm 1 consistently high reward
            for (int i = 0; i < 50; i++) { // GH-90000
                agent.recordFeedback(1, 1.0); // GH-90000
                agent.recordFeedback(0, 0.1); // GH-90000
                agent.recordFeedback(2, 0.2); // GH-90000
            }

            assertThat(agent.getBestArm()).isEqualTo(1); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Thompson Sampling
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Thompson Sampling [GH-90000]")
    class ThompsonTests {

        @Test void producesValidOutput() { // GH-90000
            AdaptiveAgent agent = createAgent("ts", // GH-90000
                    AdaptiveAgentConfig.BanditAlgorithm.THOMPSON_SAMPLING, 4);

            var result = runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000
            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutput()).containsKey("_adaptive.selectedArm [GH-90000]");
            assertThat(result.getOutput()).containsKey("threshold [GH-90000]");

            double pv = ((Number) result.getOutput().get("threshold [GH-90000]")).doubleValue();
            assertThat(pv).isBetween(0.0, 1.0); // GH-90000
        }

        @Test void convergesToBestArm() { // GH-90000
            AdaptiveAgent agent = createAgent("ts-conv", // GH-90000
                    AdaptiveAgentConfig.BanditAlgorithm.THOMPSON_SAMPLING, 3);

            // Process → get selected arm → give feedback.
            // Arm 2 always gets reward 1.0, others always 0.0.
            // Over many iterations, getBestArm should converge to arm 2.
            for (int i = 0; i < 150; i++) { // GH-90000
                var result = runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000
                int arm = ((Number) result.getOutput().get("_adaptive.selectedArm [GH-90000]")).intValue();
                // Reward: 1.0 for arm 2, 0.0 for others
                agent.recordFeedback(arm, arm == 2 ? 1.0 : 0.0); // GH-90000
            }

            assertThat(agent.getBestArm()).isEqualTo(2); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Epsilon-Greedy
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Epsilon-Greedy [GH-90000]")
    class EpsilonGreedyTests {

        @Test void producesValidOutput() { // GH-90000
            AdaptiveAgent agent = createAgent("eg", // GH-90000
                    AdaptiveAgentConfig.BanditAlgorithm.EPSILON_GREEDY, 5);

            var result = runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000
            assertThat(result.isSuccess()).isTrue(); // GH-90000
            assertThat(result.getOutput()).containsKey("_adaptive.selectedArm [GH-90000]");
        }

        @Test void mostlyExploitsBestArm() { // GH-90000
            AdaptiveAgent agent = createAgent("eg-exploit", // GH-90000
                    AdaptiveAgentConfig.BanditAlgorithm.EPSILON_GREEDY, 3);

            // Train: process → feedback loop to properly build pullCounts and reward stats.
            // recordFeedback alone does NOT increment pullCount, so selectEpsilonGreedy
            // would see avg=0 for all arms. We must call process() to populate pullCount. // GH-90000
            double[] armRewards = {1.0, 0.2, 0.1};
            for (int i = 0; i < 200; i++) { // GH-90000
                var result = runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000
                int arm = ((Number) result.getOutput().get("_adaptive.selectedArm [GH-90000]")).intValue();
                agent.recordFeedback(arm, armRewards[arm]); // GH-90000
            }

            // Pull many times — majority should select arm 0 (exploitation with eps=0.1) // GH-90000
            Map<Integer, Integer> counts = new HashMap<>(); // GH-90000
            for (int i = 0; i < 100; i++) { // GH-90000
                var result = runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000
                int arm = ((Number) result.getOutput().get("_adaptive.selectedArm [GH-90000]")).intValue();
                counts.merge(arm, 1, Integer::sum); // GH-90000
            }

            // With eps=0.1 and arm 0 having highest avg reward, expect ~90% arm 0.
            // Use threshold of 50 to account for random exploration variance.
            assertThat(counts.getOrDefault(0, 0)).isGreaterThan(50); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Feedback & Statistics
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Feedback & Statistics [GH-90000]")
    class FeedbackTests {

        @Test void recordFeedbackUpdatesStats() { // GH-90000
            AdaptiveAgent agent = createAgent("fb", // GH-90000
                    AdaptiveAgentConfig.BanditAlgorithm.UCB1, 3);

            agent.recordFeedback(0, 0.8); // GH-90000
            agent.recordFeedback(0, 0.6); // GH-90000
            agent.recordFeedback(1, 0.9); // GH-90000

            Map<String, Object> stats = agent.getArmStatistics(); // GH-90000
            assertThat(stats).isNotEmpty(); // GH-90000
        }

        @Test void bestParameterValueInRange() { // GH-90000
            AdaptiveAgent agent = createAgent("param", // GH-90000
                    AdaptiveAgentConfig.BanditAlgorithm.UCB1, 5);

            for (int i = 0; i < 5; i++) { // GH-90000
                agent.recordFeedback(i, i * 0.2); // GH-90000
            }

            double value = agent.getBestParameterValue(); // GH-90000
            assertThat(value).isBetween(0.0, 1.0); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Lifecycle [GH-90000]")
    class LifecycleTests {

        @Test void metricsTracked() { // GH-90000
            AdaptiveAgent agent = createAgent("metrics", // GH-90000
                    AdaptiveAgentConfig.BanditAlgorithm.UCB1, 3);

            runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000
            runOnEventloop(() -> agent.process(ctx, Map.of())); // GH-90000

            assertThat(agent.getTotalInvocations()).isEqualTo(2); // GH-90000
        }

        @Test void healthCheckReturnsHealthy() { // GH-90000
            AdaptiveAgent agent = createAgent("health", // GH-90000
                    AdaptiveAgentConfig.BanditAlgorithm.UCB1, 3);

            HealthStatus status = runOnEventloop(agent::healthCheck); // GH-90000
            assertThat(status.getStatus()).isEqualTo(HealthStatus.Status.HEALTHY); // GH-90000
        }
    }
}
