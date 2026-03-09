/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
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
 * Comprehensive tests for AdaptiveAgent — bandit algorithms and feedback.
 */
@DisplayName("Adaptive Agent")
class AdaptiveAgentTest {

    private AgentContext ctx;

    @BeforeEach
    void setUp() {
        ctx = AgentContext.builder()
                .turnId("turn-1")
                .agentId("adaptive-test")
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

    private AdaptiveAgent createAgent(String id, AdaptiveAgentConfig.BanditAlgorithm algo, int arms) {
        AdaptiveAgent agent = new AdaptiveAgent(id);
        AdaptiveAgentConfig config = AdaptiveAgentConfig.builder()
                .agentId(id)
                .type(AgentType.ADAPTIVE)
                .banditAlgorithm(algo)
                .tunedParameter("threshold")
                .parameterMin(0.0)
                .parameterMax(1.0)
                .armCount(arms)
                .explorationRate(0.1)
                .build();
        runOnEventloop(() -> agent.initialize(config));
        return agent;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UCB1
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("UCB1")
    class UCB1Tests {

        @Test void selectsAllArmsInitially() {
            AdaptiveAgent agent = createAgent("ucb1", AdaptiveAgentConfig.BanditAlgorithm.UCB1, 5);

            Set<Integer> selectedArms = new HashSet<>();
            for (int i = 0; i < 20; i++) {
                var result = runOnEventloop(() -> agent.process(ctx, Map.of()));
                Object armObj = result.getOutput().get("_adaptive.selectedArm");
                selectedArms.add(((Number) armObj).intValue());
            }
            // UCB1 explores all arms before exploiting
            assertThat(selectedArms).hasSize(5);
        }

        @Test void convergesAfterFeedback() {
            AdaptiveAgent agent = createAgent("ucb1-conv", AdaptiveAgentConfig.BanditAlgorithm.UCB1, 3);

            // Pull each arm once to get past exploration phase
            for (int i = 0; i < 3; i++) {
                runOnEventloop(() -> agent.process(ctx, Map.of()));
            }

            // Give arm 1 consistently high reward
            for (int i = 0; i < 50; i++) {
                agent.recordFeedback(1, 1.0);
                agent.recordFeedback(0, 0.1);
                agent.recordFeedback(2, 0.2);
            }

            assertThat(agent.getBestArm()).isEqualTo(1);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Thompson Sampling
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Thompson Sampling")
    class ThompsonTests {

        @Test void producesValidOutput() {
            AdaptiveAgent agent = createAgent("ts",
                    AdaptiveAgentConfig.BanditAlgorithm.THOMPSON_SAMPLING, 4);

            var result = runOnEventloop(() -> agent.process(ctx, Map.of()));
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsKey("_adaptive.selectedArm");
            assertThat(result.getOutput()).containsKey("threshold");

            double pv = ((Number) result.getOutput().get("threshold")).doubleValue();
            assertThat(pv).isBetween(0.0, 1.0);
        }

        @Test void convergesToBestArm() {
            AdaptiveAgent agent = createAgent("ts-conv",
                    AdaptiveAgentConfig.BanditAlgorithm.THOMPSON_SAMPLING, 3);

            // Process → get selected arm → give feedback.
            // Arm 2 always gets reward 1.0, others always 0.0.
            // Over many iterations, getBestArm should converge to arm 2.
            for (int i = 0; i < 150; i++) {
                var result = runOnEventloop(() -> agent.process(ctx, Map.of()));
                int arm = ((Number) result.getOutput().get("_adaptive.selectedArm")).intValue();
                // Reward: 1.0 for arm 2, 0.0 for others
                agent.recordFeedback(arm, arm == 2 ? 1.0 : 0.0);
            }

            assertThat(agent.getBestArm()).isEqualTo(2);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Epsilon-Greedy
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Epsilon-Greedy")
    class EpsilonGreedyTests {

        @Test void producesValidOutput() {
            AdaptiveAgent agent = createAgent("eg",
                    AdaptiveAgentConfig.BanditAlgorithm.EPSILON_GREEDY, 5);

            var result = runOnEventloop(() -> agent.process(ctx, Map.of()));
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsKey("_adaptive.selectedArm");
        }

        @Test void mostlyExploitsBestArm() {
            AdaptiveAgent agent = createAgent("eg-exploit",
                    AdaptiveAgentConfig.BanditAlgorithm.EPSILON_GREEDY, 3);

            // Train: process → feedback loop to properly build pullCounts and reward stats.
            // recordFeedback alone does NOT increment pullCount, so selectEpsilonGreedy
            // would see avg=0 for all arms. We must call process() to populate pullCount.
            double[] armRewards = {1.0, 0.2, 0.1};
            for (int i = 0; i < 200; i++) {
                var result = runOnEventloop(() -> agent.process(ctx, Map.of()));
                int arm = ((Number) result.getOutput().get("_adaptive.selectedArm")).intValue();
                agent.recordFeedback(arm, armRewards[arm]);
            }

            // Pull many times — majority should select arm 0 (exploitation with eps=0.1)
            Map<Integer, Integer> counts = new HashMap<>();
            for (int i = 0; i < 100; i++) {
                var result = runOnEventloop(() -> agent.process(ctx, Map.of()));
                int arm = ((Number) result.getOutput().get("_adaptive.selectedArm")).intValue();
                counts.merge(arm, 1, Integer::sum);
            }

            // With eps=0.1 and arm 0 having highest avg reward, expect ~90% arm 0.
            // Use threshold of 50 to account for random exploration variance.
            assertThat(counts.getOrDefault(0, 0)).isGreaterThan(50);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Feedback & Statistics
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Feedback & Statistics")
    class FeedbackTests {

        @Test void recordFeedbackUpdatesStats() {
            AdaptiveAgent agent = createAgent("fb",
                    AdaptiveAgentConfig.BanditAlgorithm.UCB1, 3);

            agent.recordFeedback(0, 0.8);
            agent.recordFeedback(0, 0.6);
            agent.recordFeedback(1, 0.9);

            Map<String, Object> stats = agent.getArmStatistics();
            assertThat(stats).isNotEmpty();
        }

        @Test void bestParameterValueInRange() {
            AdaptiveAgent agent = createAgent("param",
                    AdaptiveAgentConfig.BanditAlgorithm.UCB1, 5);

            for (int i = 0; i < 5; i++) {
                agent.recordFeedback(i, i * 0.2);
            }

            double value = agent.getBestParameterValue();
            assertThat(value).isBetween(0.0, 1.0);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Lifecycle")
    class LifecycleTests {

        @Test void metricsTracked() {
            AdaptiveAgent agent = createAgent("metrics",
                    AdaptiveAgentConfig.BanditAlgorithm.UCB1, 3);

            runOnEventloop(() -> agent.process(ctx, Map.of()));
            runOnEventloop(() -> agent.process(ctx, Map.of()));

            assertThat(agent.getTotalInvocations()).isEqualTo(2);
        }

        @Test void healthCheckReturnsHealthy() {
            AdaptiveAgent agent = createAgent("health",
                    AdaptiveAgentConfig.BanditAlgorithm.UCB1, 3);

            HealthStatus status = runOnEventloop(agent::healthCheck);
            assertThat(status).isEqualTo(HealthStatus.HEALTHY);
        }
    }
}
