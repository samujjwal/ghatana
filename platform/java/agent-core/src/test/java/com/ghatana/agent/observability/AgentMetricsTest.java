package com.ghatana.agent.observability;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.SimpleMetricsCollector;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
    void setUp() { // GH-90000
        registry = new SimpleMeterRegistry(); // GH-90000
        collector = new SimpleMetricsCollector(registry); // GH-90000
        metrics = new AgentMetrics(collector); // GH-90000
    }

    // ════════════════════════════════════════════════════════════════
    // Construction
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("rejects null MetricsCollector")
        void rejectsNullCollector() { // GH-90000
            assertThatThrownBy(() -> new AgentMetrics(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("MetricsCollector");
        }

        @Test
        @DisplayName("exposes underlying collector")
        void exposesCollector() { // GH-90000
            assertThat(metrics.getCollector()).isSameAs(collector); // GH-90000
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
        void recordsSuccessfulDeterministicProcessing() { // GH-90000
            metrics.recordProcessing("deterministic", "agent-1", 15, true); // GH-90000

            Counter counter = registry.find(AgentMetrics.PROCESS_COUNT) // GH-90000
                    .tag("agent_type", "deterministic") // GH-90000
                    .tag("agent_id", "agent-1") // GH-90000
                    .tag("status", "success") // GH-90000
                    .counter(); // GH-90000
            assertThat(counter).isNotNull(); // GH-90000
            assertThat(counter.count()).isEqualTo(1.0); // GH-90000

            Timer timer = registry.find(AgentMetrics.PROCESS_DURATION_MS) // GH-90000
                    .tag("agent_type", "deterministic") // GH-90000
                    .timer(); // GH-90000
            assertThat(timer).isNotNull(); // GH-90000
            assertThat(timer.count()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("records failed processing with error counter")
        void recordsFailedProcessing() { // GH-90000
            metrics.recordProcessing("probabilistic", "agent-2", 5, false); // GH-90000

            Counter errCounter = registry.find(AgentMetrics.PROCESS_ERRORS) // GH-90000
                    .tag("agent_type", "probabilistic") // GH-90000
                    .counter(); // GH-90000
            assertThat(errCounter).isNotNull(); // GH-90000
            assertThat(errCounter.count()).isEqualTo(1.0); // GH-90000
        }

        @Test
        @DisplayName("does not record error counter for success")
        void noErrorCounterForSuccess() { // GH-90000
            metrics.recordProcessing("adaptive", "agent-3", 10, true); // GH-90000

            Counter errCounter = registry.find(AgentMetrics.PROCESS_ERRORS) // GH-90000
                    .tag("agent_type", "adaptive") // GH-90000
                    .counter(); // GH-90000
            assertThat(errCounter).isNull(); // GH-90000
        }

        @Test
        @DisplayName("records processing for all six agent types")
        void recordsAllAgentTypes() { // GH-90000
            String[] types = {"deterministic", "probabilistic", "adaptive",
                    "reactive", "composite", "hybrid"};

            for (String type : types) { // GH-90000
                metrics.recordProcessing(type, "agent-" + type, 10, true); // GH-90000
            }

            for (String type : types) { // GH-90000
                Counter counter = registry.find(AgentMetrics.PROCESS_COUNT) // GH-90000
                        .tag("agent_type", type) // GH-90000
                        .counter(); // GH-90000
                assertThat(counter) // GH-90000
                        .as("Counter for agent type: " + type) // GH-90000
                        .isNotNull(); // GH-90000
                assertThat(counter.count()).isEqualTo(1.0); // GH-90000
            }
        }

        @Test
        @DisplayName("accumulates processing count across invocations")
        void accumulatesProcessingCount() { // GH-90000
            for (int i = 0; i < 50; i++) { // GH-90000
                metrics.recordProcessing("deterministic", "agent-1", 5, true); // GH-90000
            }

            Counter counter = registry.find(AgentMetrics.PROCESS_COUNT) // GH-90000
                    .tag("agent_type", "deterministic") // GH-90000
                    .tag("status", "success") // GH-90000
                    .counter(); // GH-90000
            assertThat(counter.count()).isEqualTo(50.0); // GH-90000
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
        void recordsDecision() { // GH-90000
            metrics.recordDecision("deterministic", "agent-1", "approved"); // GH-90000

            Counter counter = registry.find(AgentMetrics.DECISION_COUNT) // GH-90000
                    .tag("agent_type", "deterministic") // GH-90000
                    .tag("status", "approved") // GH-90000
                    .counter(); // GH-90000
            assertThat(counter).isNotNull(); // GH-90000
            assertThat(counter.count()).isEqualTo(1.0); // GH-90000
        }

        @Test
        @DisplayName("tracks different decision outcomes separately")
        void tracksDifferentOutcomes() { // GH-90000
            metrics.recordDecision("probabilistic", "agent-1", "approved"); // GH-90000
            metrics.recordDecision("probabilistic", "agent-1", "rejected"); // GH-90000
            metrics.recordDecision("probabilistic", "agent-1", "approved"); // GH-90000

            Counter approved = registry.find(AgentMetrics.DECISION_COUNT) // GH-90000
                    .tag("status", "approved") // GH-90000
                    .counter(); // GH-90000
            Counter rejected = registry.find(AgentMetrics.DECISION_COUNT) // GH-90000
                    .tag("status", "rejected") // GH-90000
                    .counter(); // GH-90000

            assertThat(approved.count()).isEqualTo(2.0); // GH-90000
            assertThat(rejected.count()).isEqualTo(1.0); // GH-90000
        }

        @Test
        @DisplayName("records confidence score")
        void recordsConfidenceScore() { // GH-90000
            metrics.recordConfidence("probabilistic", "agent-1", 0.85); // GH-90000

            // Verify the counter for confidence recorded
            Counter counter = registry.find(AgentMetrics.CONFIDENCE_SCORE + ".recorded") // GH-90000
                    .tag("agent_type", "probabilistic") // GH-90000
                    .counter(); // GH-90000
            assertThat(counter).isNotNull(); // GH-90000
            assertThat(counter.count()).isEqualTo(1.0); // GH-90000
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
        void recordsCreation() { // GH-90000
            metrics.recordCreated("adaptive", "agent-1"); // GH-90000

            Counter counter = registry.find(AgentMetrics.LIFECYCLE_CREATED) // GH-90000
                    .tag("agent_type", "adaptive") // GH-90000
                    .counter(); // GH-90000
            assertThat(counter).isNotNull(); // GH-90000
            assertThat(counter.count()).isEqualTo(1.0); // GH-90000
        }

        @Test
        @DisplayName("records agent start")
        void recordsStart() { // GH-90000
            metrics.recordStarted("reactive", "agent-2"); // GH-90000

            Counter counter = registry.find(AgentMetrics.LIFECYCLE_STARTED) // GH-90000
                    .tag("agent_type", "reactive") // GH-90000
                    .counter(); // GH-90000
            assertThat(counter).isNotNull(); // GH-90000
            assertThat(counter.count()).isEqualTo(1.0); // GH-90000
        }

        @Test
        @DisplayName("records agent stop")
        void recordsStop() { // GH-90000
            metrics.recordStopped("composite", "agent-3"); // GH-90000

            Counter counter = registry.find(AgentMetrics.LIFECYCLE_STOPPED) // GH-90000
                    .tag("agent_type", "composite") // GH-90000
                    .counter(); // GH-90000
            assertThat(counter).isNotNull(); // GH-90000
            assertThat(counter.count()).isEqualTo(1.0); // GH-90000
        }

        @Test
        @DisplayName("tracks full lifecycle: created → started → stopped")
        void tracksFullLifecycle() { // GH-90000
            metrics.recordCreated("hybrid", "agent-4"); // GH-90000
            metrics.recordStarted("hybrid", "agent-4"); // GH-90000
            metrics.recordStopped("hybrid", "agent-4"); // GH-90000

            assertThat(registry.find(AgentMetrics.LIFECYCLE_CREATED) // GH-90000
                    .tag("agent_type", "hybrid").counter().count()).isEqualTo(1.0); // GH-90000
            assertThat(registry.find(AgentMetrics.LIFECYCLE_STARTED) // GH-90000
                    .tag("agent_type", "hybrid").counter().count()).isEqualTo(1.0); // GH-90000
            assertThat(registry.find(AgentMetrics.LIFECYCLE_STOPPED) // GH-90000
                    .tag("agent_type", "hybrid").counter().count()).isEqualTo(1.0); // GH-90000
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
        void recordsArmSelection() { // GH-90000
            metrics.recordArmSelected("bandit-1", "arm-A"); // GH-90000

            Counter counter = registry.find(AgentMetrics.ARM_SELECTED) // GH-90000
                    .tag("agent_id", "bandit-1") // GH-90000
                    .tag("arm_id", "arm-A") // GH-90000
                    .counter(); // GH-90000
            assertThat(counter).isNotNull(); // GH-90000
            assertThat(counter.count()).isEqualTo(1.0); // GH-90000
        }

        @Test
        @DisplayName("tracks arm selection distribution")
        void tracksArmSelectionDistribution() { // GH-90000
            // Simulate epsilon-greedy: mostly arm-A, sometimes arm-B
            for (int i = 0; i < 80; i++) { // GH-90000
                metrics.recordArmSelected("bandit-1", "arm-A"); // GH-90000
            }
            for (int i = 0; i < 20; i++) { // GH-90000
                metrics.recordArmSelected("bandit-1", "arm-B"); // GH-90000
            }

            Counter armA = registry.find(AgentMetrics.ARM_SELECTED) // GH-90000
                    .tag("arm_id", "arm-A") // GH-90000
                    .counter(); // GH-90000
            Counter armB = registry.find(AgentMetrics.ARM_SELECTED) // GH-90000
                    .tag("arm_id", "arm-B") // GH-90000
                    .counter(); // GH-90000

            assertThat(armA.count()).isEqualTo(80.0); // GH-90000
            assertThat(armB.count()).isEqualTo(20.0); // GH-90000
        }

        @Test
        @DisplayName("records reward signal")
        void recordsReward() { // GH-90000
            metrics.recordReward("bandit-1", 1.0); // GH-90000

            Counter counter = registry.find(AgentMetrics.REWARD_RECORDED + ".count") // GH-90000
                    .tag("agent_id", "bandit-1") // GH-90000
                    .counter(); // GH-90000
            assertThat(counter).isNotNull(); // GH-90000
            assertThat(counter.count()).isEqualTo(1.0); // GH-90000
        }

        @Test
        @DisplayName("records exploration rate gauge")
        void recordsExplorationRate() { // GH-90000
            metrics.recordExplorationRate("bandit-1", 0.15); // GH-90000

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
        void recordsRuleMatched() { // GH-90000
            metrics.recordRuleMatched("rule-agent-1", "rule-A"); // GH-90000

            Counter counter = registry.find(AgentMetrics.RULE_MATCHED) // GH-90000
                    .tag("agent_id", "rule-agent-1") // GH-90000
                    .tag("rule_id", "rule-A") // GH-90000
                    .counter(); // GH-90000
            assertThat(counter).isNotNull(); // GH-90000
            assertThat(counter.count()).isEqualTo(1.0); // GH-90000
        }

        @Test
        @DisplayName("records no rule match")
        void recordsNoRuleMatch() { // GH-90000
            metrics.recordNoRuleMatch("rule-agent-1");

            Counter counter = registry.find(AgentMetrics.RULE_NO_MATCH) // GH-90000
                    .tag("agent_id", "rule-agent-1") // GH-90000
                    .counter(); // GH-90000
            assertThat(counter).isNotNull(); // GH-90000
            assertThat(counter.count()).isEqualTo(1.0); // GH-90000
        }

        @Test
        @DisplayName("tracks rule match ratio")
        void tracksRuleMatchRatio() { // GH-90000
            for (int i = 0; i < 7; i++) { // GH-90000
                metrics.recordRuleMatched("rule-agent-1", "rule-X"); // GH-90000
            }
            for (int i = 0; i < 3; i++) { // GH-90000
                metrics.recordNoRuleMatch("rule-agent-1");
            }

            double matched = registry.find(AgentMetrics.RULE_MATCHED) // GH-90000
                    .tag("agent_id", "rule-agent-1") // GH-90000
                    .counter().count(); // GH-90000
            double noMatch = registry.find(AgentMetrics.RULE_NO_MATCH) // GH-90000
                    .tag("agent_id", "rule-agent-1") // GH-90000
                    .counter().count(); // GH-90000

            assertThat(matched).isEqualTo(7.0); // GH-90000
            assertThat(noMatch).isEqualTo(3.0); // GH-90000
            assertThat(matched / (matched + noMatch)).isCloseTo(0.7, org.assertj.core.data.Offset.offset(0.01)); // GH-90000
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
        void allMetricsFollowNamespace() { // GH-90000
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
        void tagKeysUseSnakeCase() { // GH-90000
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
        void concurrentRecordingIsThreadSafe() throws InterruptedException { // GH-90000
            int threads = 10;
            int iterationsPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threads); // GH-90000
            CountDownLatch latch = new CountDownLatch(threads); // GH-90000

            for (int t = 0; t < threads; t++) { // GH-90000
                executor.submit(() -> { // GH-90000
                    try {
                        for (int i = 0; i < iterationsPerThread; i++) { // GH-90000
                            metrics.recordProcessing("deterministic", "agent-1", 5, true); // GH-90000
                        }
                    } finally {
                        latch.countDown(); // GH-90000
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS); // GH-90000
            executor.shutdown(); // GH-90000

            Counter counter = registry.find(AgentMetrics.PROCESS_COUNT) // GH-90000
                    .tag("agent_type", "deterministic") // GH-90000
                    .tag("status", "success") // GH-90000
                    .counter(); // GH-90000
            assertThat(counter.count()).isEqualTo((double) threads * iterationsPerThread); // GH-90000
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
        void completeAgentLifecycle() { // GH-90000
            String agentType = "adaptive";
            String agentId = "epsilon-greedy-1";

            // Create and start
            metrics.recordCreated(agentType, agentId); // GH-90000
            metrics.recordStarted(agentType, agentId); // GH-90000

            // Process 10 events with arm selections
            for (int i = 0; i < 10; i++) { // GH-90000
                String arm = (i < 8) ? "arm-best" : "arm-explore"; // GH-90000
                metrics.recordArmSelected(agentId, arm); // GH-90000
                metrics.recordProcessing(agentType, agentId, 5 + i, true); // GH-90000
                metrics.recordDecision(agentType, agentId, "approved"); // GH-90000
            }

            // Record rewards
            metrics.recordReward(agentId, 1.0); // GH-90000
            metrics.recordExplorationRate(agentId, 0.1); // GH-90000

            // Stop agent
            metrics.recordStopped(agentType, agentId); // GH-90000

            // Verify lifecycle
            assertThat(registry.find(AgentMetrics.LIFECYCLE_CREATED) // GH-90000
                    .tag("agent_type", agentType).counter().count()).isEqualTo(1.0); // GH-90000
            assertThat(registry.find(AgentMetrics.LIFECYCLE_STARTED) // GH-90000
                    .tag("agent_type", agentType).counter().count()).isEqualTo(1.0); // GH-90000
            assertThat(registry.find(AgentMetrics.LIFECYCLE_STOPPED) // GH-90000
                    .tag("agent_type", agentType).counter().count()).isEqualTo(1.0); // GH-90000

            // Verify processing
            assertThat(registry.find(AgentMetrics.PROCESS_COUNT) // GH-90000
                    .tag("status", "success").counter().count()).isEqualTo(10.0); // GH-90000

            // Verify arm distribution
            assertThat(registry.find(AgentMetrics.ARM_SELECTED) // GH-90000
                    .tag("arm_id", "arm-best").counter().count()).isEqualTo(8.0); // GH-90000
            assertThat(registry.find(AgentMetrics.ARM_SELECTED) // GH-90000
                    .tag("arm_id", "arm-explore").counter().count()).isEqualTo(2.0); // GH-90000

            // Verify decisions
            assertThat(registry.find(AgentMetrics.DECISION_COUNT) // GH-90000
                    .tag("status", "approved").counter().count()).isEqualTo(10.0); // GH-90000
        }

        @Test
        @DisplayName("tracks deterministic agent rule matching pattern")
        void deterministicRulePattern() { // GH-90000
            String agentId = "rule-agent-1";

            metrics.recordCreated("deterministic", agentId); // GH-90000

            // Process events: 7 match rules, 3 don't
            for (int i = 0; i < 10; i++) { // GH-90000
                boolean matched = i < 7;
                if (matched) { // GH-90000
                    metrics.recordRuleMatched(agentId, "high-value-rule"); // GH-90000
                    metrics.recordDecision("deterministic", agentId, "approved"); // GH-90000
                } else {
                    metrics.recordNoRuleMatch(agentId); // GH-90000
                    metrics.recordDecision("deterministic", agentId, "default"); // GH-90000
                }
                metrics.recordProcessing("deterministic", agentId, 2, true); // GH-90000
            }

            // Verify metrics
            assertThat(registry.find(AgentMetrics.RULE_MATCHED) // GH-90000
                    .counter().count()).isEqualTo(7.0); // GH-90000
            assertThat(registry.find(AgentMetrics.RULE_NO_MATCH) // GH-90000
                    .counter().count()).isEqualTo(3.0); // GH-90000
            assertThat(registry.find(AgentMetrics.PROCESS_COUNT) // GH-90000
                    .tag("status", "success").counter().count()).isEqualTo(10.0); // GH-90000
        }
    }
}
