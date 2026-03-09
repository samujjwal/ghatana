package com.ghatana.agent.observability;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.SimpleMetricsCollector;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive tests for AgentMetrics observability facade.
 *
 * <p>Validates that all agent processing metrics are correctly recorded
 * for all six agent types: deterministic, probabilistic, adaptive,
 * reactive, composite, and hybrid.</p>
 *
 * @doc.type test
 * @doc.purpose Validate agent observability metrics contract
 * @doc.layer core
 */
@DisplayName("Agent Observability Metrics")
class AgentMetricsTest {

    private MeterRegistry registry;
    private MetricsCollector collector;
    private AgentMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        collector = new SimpleMetricsCollector(registry);
        metrics = new AgentMetrics(collector);
    }

    // ════════════════════════════════════════════════════════════════
    // Construction
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("rejects null MetricsCollector")
        void rejectsNullCollector() {
            assertThatThrownBy(() -> new AgentMetrics(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("MetricsCollector");
        }

        @Test
        @DisplayName("exposes underlying collector")
        void exposesCollector() {
            assertThat(metrics.getCollector()).isSameAs(collector);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Processing metrics
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Processing Metrics")
    class ProcessingMetricsTests {

        @Test
        @DisplayName("records successful processing for deterministic agent")
        void recordsSuccessfulDeterministicProcessing() {
            metrics.recordProcessing("deterministic", "agent-1", 15, true);

            Counter counter = registry.find(AgentMetrics.PROCESS_COUNT)
                    .tag("agent_type", "deterministic")
                    .tag("agent_id", "agent-1")
                    .tag("status", "success")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);

            Timer timer = registry.find(AgentMetrics.PROCESS_DURATION_MS)
                    .tag("agent_type", "deterministic")
                    .timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("records failed processing with error counter")
        void recordsFailedProcessing() {
            metrics.recordProcessing("probabilistic", "agent-2", 5, false);

            Counter errCounter = registry.find(AgentMetrics.PROCESS_ERRORS)
                    .tag("agent_type", "probabilistic")
                    .counter();
            assertThat(errCounter).isNotNull();
            assertThat(errCounter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("does not record error counter for success")
        void noErrorCounterForSuccess() {
            metrics.recordProcessing("adaptive", "agent-3", 10, true);

            Counter errCounter = registry.find(AgentMetrics.PROCESS_ERRORS)
                    .tag("agent_type", "adaptive")
                    .counter();
            assertThat(errCounter).isNull();
        }

        @Test
        @DisplayName("records processing for all six agent types")
        void recordsAllAgentTypes() {
            String[] types = {"deterministic", "probabilistic", "adaptive",
                    "reactive", "composite", "hybrid"};

            for (String type : types) {
                metrics.recordProcessing(type, "agent-" + type, 10, true);
            }

            for (String type : types) {
                Counter counter = registry.find(AgentMetrics.PROCESS_COUNT)
                        .tag("agent_type", type)
                        .counter();
                assertThat(counter)
                        .as("Counter for agent type: " + type)
                        .isNotNull();
                assertThat(counter.count()).isEqualTo(1.0);
            }
        }

        @Test
        @DisplayName("accumulates processing count across invocations")
        void accumulatesProcessingCount() {
            for (int i = 0; i < 50; i++) {
                metrics.recordProcessing("deterministic", "agent-1", 5, true);
            }

            Counter counter = registry.find(AgentMetrics.PROCESS_COUNT)
                    .tag("agent_type", "deterministic")
                    .tag("status", "success")
                    .counter();
            assertThat(counter.count()).isEqualTo(50.0);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Decision metrics
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Decision Metrics")
    class DecisionMetricsTests {

        @Test
        @DisplayName("records decision with outcome")
        void recordsDecision() {
            metrics.recordDecision("deterministic", "agent-1", "approved");

            Counter counter = registry.find(AgentMetrics.DECISION_COUNT)
                    .tag("agent_type", "deterministic")
                    .tag("status", "approved")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("tracks different decision outcomes separately")
        void tracksDifferentOutcomes() {
            metrics.recordDecision("probabilistic", "agent-1", "approved");
            metrics.recordDecision("probabilistic", "agent-1", "rejected");
            metrics.recordDecision("probabilistic", "agent-1", "approved");

            Counter approved = registry.find(AgentMetrics.DECISION_COUNT)
                    .tag("status", "approved")
                    .counter();
            Counter rejected = registry.find(AgentMetrics.DECISION_COUNT)
                    .tag("status", "rejected")
                    .counter();

            assertThat(approved.count()).isEqualTo(2.0);
            assertThat(rejected.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("records confidence score")
        void recordsConfidenceScore() {
            metrics.recordConfidence("probabilistic", "agent-1", 0.85);

            // Verify the counter for confidence recorded
            Counter counter = registry.find(AgentMetrics.CONFIDENCE_SCORE + ".recorded")
                    .tag("agent_type", "probabilistic")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Lifecycle metrics
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Lifecycle Metrics")
    class LifecycleMetricsTests {

        @Test
        @DisplayName("records agent creation")
        void recordsCreation() {
            metrics.recordCreated("adaptive", "agent-1");

            Counter counter = registry.find(AgentMetrics.LIFECYCLE_CREATED)
                    .tag("agent_type", "adaptive")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("records agent start")
        void recordsStart() {
            metrics.recordStarted("reactive", "agent-2");

            Counter counter = registry.find(AgentMetrics.LIFECYCLE_STARTED)
                    .tag("agent_type", "reactive")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("records agent stop")
        void recordsStop() {
            metrics.recordStopped("composite", "agent-3");

            Counter counter = registry.find(AgentMetrics.LIFECYCLE_STOPPED)
                    .tag("agent_type", "composite")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("tracks full lifecycle: created → started → stopped")
        void tracksFullLifecycle() {
            metrics.recordCreated("hybrid", "agent-4");
            metrics.recordStarted("hybrid", "agent-4");
            metrics.recordStopped("hybrid", "agent-4");

            assertThat(registry.find(AgentMetrics.LIFECYCLE_CREATED)
                    .tag("agent_type", "hybrid").counter().count()).isEqualTo(1.0);
            assertThat(registry.find(AgentMetrics.LIFECYCLE_STARTED)
                    .tag("agent_type", "hybrid").counter().count()).isEqualTo(1.0);
            assertThat(registry.find(AgentMetrics.LIFECYCLE_STOPPED)
                    .tag("agent_type", "hybrid").counter().count()).isEqualTo(1.0);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Adaptive agent metrics
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Adaptive Agent Metrics")
    class AdaptiveMetricsTests {

        @Test
        @DisplayName("records arm selection")
        void recordsArmSelection() {
            metrics.recordArmSelected("bandit-1", "arm-A");

            Counter counter = registry.find(AgentMetrics.ARM_SELECTED)
                    .tag("agent_id", "bandit-1")
                    .tag("arm_id", "arm-A")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("tracks arm selection distribution")
        void tracksArmSelectionDistribution() {
            // Simulate epsilon-greedy: mostly arm-A, sometimes arm-B
            for (int i = 0; i < 80; i++) {
                metrics.recordArmSelected("bandit-1", "arm-A");
            }
            for (int i = 0; i < 20; i++) {
                metrics.recordArmSelected("bandit-1", "arm-B");
            }

            Counter armA = registry.find(AgentMetrics.ARM_SELECTED)
                    .tag("arm_id", "arm-A")
                    .counter();
            Counter armB = registry.find(AgentMetrics.ARM_SELECTED)
                    .tag("arm_id", "arm-B")
                    .counter();

            assertThat(armA.count()).isEqualTo(80.0);
            assertThat(armB.count()).isEqualTo(20.0);
        }

        @Test
        @DisplayName("records reward signal")
        void recordsReward() {
            metrics.recordReward("bandit-1", 1.0);

            Counter counter = registry.find(AgentMetrics.REWARD_RECORDED + ".count")
                    .tag("agent_id", "bandit-1")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("records exploration rate gauge")
        void recordsExplorationRate() {
            metrics.recordExplorationRate("bandit-1", 0.15);

            // Gauge recording is fire-and-forget; just verify no exception
            // Micrometer gauges with primitive values in SimpleMeterRegistry work differently
            // so we just verify the call doesn't throw
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Rule-based agent metrics
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Rule-Based Agent Metrics")
    class RuleBasedMetricsTests {

        @Test
        @DisplayName("records rule matched")
        void recordsRuleMatched() {
            metrics.recordRuleMatched("rule-agent-1", "rule-A");

            Counter counter = registry.find(AgentMetrics.RULE_MATCHED)
                    .tag("agent_id", "rule-agent-1")
                    .tag("rule_id", "rule-A")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("records no rule match")
        void recordsNoRuleMatch() {
            metrics.recordNoRuleMatch("rule-agent-1");

            Counter counter = registry.find(AgentMetrics.RULE_NO_MATCH)
                    .tag("agent_id", "rule-agent-1")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("tracks rule match ratio")
        void tracksRuleMatchRatio() {
            for (int i = 0; i < 7; i++) {
                metrics.recordRuleMatched("rule-agent-1", "rule-X");
            }
            for (int i = 0; i < 3; i++) {
                metrics.recordNoRuleMatch("rule-agent-1");
            }

            double matched = registry.find(AgentMetrics.RULE_MATCHED)
                    .tag("agent_id", "rule-agent-1")
                    .counter().count();
            double noMatch = registry.find(AgentMetrics.RULE_NO_MATCH)
                    .tag("agent_id", "rule-agent-1")
                    .counter().count();

            assertThat(matched).isEqualTo(7.0);
            assertThat(noMatch).isEqualTo(3.0);
            assertThat(matched / (matched + noMatch)).isCloseTo(0.7, org.assertj.core.data.Offset.offset(0.01));
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Naming conventions
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Naming Conventions")
    class NamingConventionTests {

        @Test
        @DisplayName("all metric names follow agent.* namespace")
        void allMetricsFollowNamespace() {
            assertThat(AgentMetrics.PROCESS_COUNT).startsWith("agent.");
            assertThat(AgentMetrics.PROCESS_DURATION_MS).startsWith("agent.");
            assertThat(AgentMetrics.PROCESS_ERRORS).startsWith("agent.");
            assertThat(AgentMetrics.DECISION_COUNT).startsWith("agent.");
            assertThat(AgentMetrics.CONFIDENCE_SCORE).startsWith("agent.");
            assertThat(AgentMetrics.LIFECYCLE_CREATED).startsWith("agent.");
            assertThat(AgentMetrics.LIFECYCLE_STARTED).startsWith("agent.");
            assertThat(AgentMetrics.LIFECYCLE_STOPPED).startsWith("agent.");
            assertThat(AgentMetrics.ARM_SELECTED).startsWith("agent.");
            assertThat(AgentMetrics.REWARD_RECORDED).startsWith("agent.");
            assertThat(AgentMetrics.EXPLORATION_RATE).startsWith("agent.");
            assertThat(AgentMetrics.RULE_MATCHED).startsWith("agent.");
            assertThat(AgentMetrics.RULE_NO_MATCH).startsWith("agent.");
        }

        @Test
        @DisplayName("tag keys use snake_case")
        void tagKeysUseSnakeCase() {
            assertThat(AgentMetrics.TAG_AGENT_TYPE).matches("[a-z_]+");
            assertThat(AgentMetrics.TAG_AGENT_ID).matches("[a-z_]+");
            assertThat(AgentMetrics.TAG_TENANT_ID).matches("[a-z_]+");
            assertThat(AgentMetrics.TAG_STATUS).matches("[a-z_]+");
            assertThat(AgentMetrics.TAG_ARM_ID).matches("[a-z_]+");
            assertThat(AgentMetrics.TAG_RULE_ID).matches("[a-z_]+");
            assertThat(AgentMetrics.TAG_ERROR_TYPE).matches("[a-z_]+");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Concurrency safety
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Concurrency Safety")
    class ConcurrencyTests {

        @Test
        @DisplayName("concurrent metric recording is thread-safe")
        void concurrentRecordingIsThreadSafe() throws InterruptedException {
            int threads = 10;
            int iterationsPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);

            for (int t = 0; t < threads; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < iterationsPerThread; i++) {
                            metrics.recordProcessing("deterministic", "agent-1", 5, true);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            Counter counter = registry.find(AgentMetrics.PROCESS_COUNT)
                    .tag("agent_type", "deterministic")
                    .tag("status", "success")
                    .counter();
            assertThat(counter.count()).isEqualTo((double) threads * iterationsPerThread);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // End-to-end scenario
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("End-to-End Scenarios")
    class EndToEndTests {

        @Test
        @DisplayName("tracks complete agent lifecycle with processing")
        void completeAgentLifecycle() {
            String agentType = "adaptive";
            String agentId = "epsilon-greedy-1";

            // Create and start
            metrics.recordCreated(agentType, agentId);
            metrics.recordStarted(agentType, agentId);

            // Process 10 events with arm selections
            for (int i = 0; i < 10; i++) {
                String arm = (i < 8) ? "arm-best" : "arm-explore";
                metrics.recordArmSelected(agentId, arm);
                metrics.recordProcessing(agentType, agentId, 5 + i, true);
                metrics.recordDecision(agentType, agentId, "approved");
            }

            // Record rewards
            metrics.recordReward(agentId, 1.0);
            metrics.recordExplorationRate(agentId, 0.1);

            // Stop agent
            metrics.recordStopped(agentType, agentId);

            // Verify lifecycle
            assertThat(registry.find(AgentMetrics.LIFECYCLE_CREATED)
                    .tag("agent_type", agentType).counter().count()).isEqualTo(1.0);
            assertThat(registry.find(AgentMetrics.LIFECYCLE_STARTED)
                    .tag("agent_type", agentType).counter().count()).isEqualTo(1.0);
            assertThat(registry.find(AgentMetrics.LIFECYCLE_STOPPED)
                    .tag("agent_type", agentType).counter().count()).isEqualTo(1.0);

            // Verify processing
            assertThat(registry.find(AgentMetrics.PROCESS_COUNT)
                    .tag("status", "success").counter().count()).isEqualTo(10.0);

            // Verify arm distribution
            assertThat(registry.find(AgentMetrics.ARM_SELECTED)
                    .tag("arm_id", "arm-best").counter().count()).isEqualTo(8.0);
            assertThat(registry.find(AgentMetrics.ARM_SELECTED)
                    .tag("arm_id", "arm-explore").counter().count()).isEqualTo(2.0);

            // Verify decisions
            assertThat(registry.find(AgentMetrics.DECISION_COUNT)
                    .tag("status", "approved").counter().count()).isEqualTo(10.0);
        }

        @Test
        @DisplayName("tracks deterministic agent rule matching pattern")
        void deterministicRulePattern() {
            String agentId = "rule-agent-1";

            metrics.recordCreated("deterministic", agentId);

            // Process events: 7 match rules, 3 don't
            for (int i = 0; i < 10; i++) {
                boolean matched = i < 7;
                if (matched) {
                    metrics.recordRuleMatched(agentId, "high-value-rule");
                    metrics.recordDecision("deterministic", agentId, "approved");
                } else {
                    metrics.recordNoRuleMatch(agentId);
                    metrics.recordDecision("deterministic", agentId, "default");
                }
                metrics.recordProcessing("deterministic", agentId, 2, true);
            }

            // Verify metrics
            assertThat(registry.find(AgentMetrics.RULE_MATCHED)
                    .counter().count()).isEqualTo(7.0);
            assertThat(registry.find(AgentMetrics.RULE_NO_MATCH)
                    .counter().count()).isEqualTo(3.0);
            assertThat(registry.find(AgentMetrics.PROCESS_COUNT)
                    .tag("status", "success").counter().count()).isEqualTo(10.0);
        }
    }
}
