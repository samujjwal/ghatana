package com.ghatana.agent.observability;

import com.ghatana.platform.observability.MetricsCollector;

import java.util.Map;
import java.util.Objects;

/**
 * Observability facade for agent processing metrics.
 *
 * <p>Provides domain-specific metrics methods for agent operations across all
 * six agent types (deterministic, probabilistic, adaptive, reactive, composite,
 * hybrid). All agent metrics follow the {@code agent.*} namespace convention.</p>
 *
 * <h2>Metric Categories</h2>
 * <ul>
 *   <li><b>Processing metrics:</b> agent.process.count, agent.process.duration, agent.process.errors</li>
 *   <li><b>Decision metrics:</b> agent.decision.count, agent.confidence.score</li>
 *   <li><b>Lifecycle metrics:</b> agent.lifecycle.created, agent.lifecycle.started, agent.lifecycle.stopped</li>
 *   <li><b>Adaptive metrics:</b> agent.arm.selected, agent.reward.recorded</li>
 * </ul>
 *
 * <h2>Standard Tags</h2>
 * <ul>
 *   <li>{@code agent_type} — deterministic, probabilistic, adaptive, reactive, composite, hybrid</li>
 *   <li>{@code agent_id} — Agent instance identifier</li>
 *   <li>{@code tenant_id} — Tenant for multi-tenant isolation</li>
 *   <li>{@code status} — success/failure/skipped</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Observability facade for agent processing metrics
 * @doc.layer core
 * @doc.pattern Facade
 */
public class AgentMetrics {

    // ════════════════════════════════════════════════════════════════
    // Metric name constants
    // ════════════════════════════════════════════════════════════════

    public static final String PROCESS_COUNT = "agent.process.count";
    public static final String PROCESS_DURATION_MS = "agent.process.duration";
    public static final String PROCESS_ERRORS = "agent.process.errors";

    public static final String DECISION_COUNT = "agent.decision.count";
    public static final String CONFIDENCE_SCORE = "agent.confidence.score";

    public static final String LIFECYCLE_CREATED = "agent.lifecycle.created";
    public static final String LIFECYCLE_STARTED = "agent.lifecycle.started";
    public static final String LIFECYCLE_STOPPED = "agent.lifecycle.stopped";

    public static final String ARM_SELECTED = "agent.arm.selected";
    public static final String REWARD_RECORDED = "agent.reward.recorded";
    public static final String EXPLORATION_RATE = "agent.exploration.rate";

    public static final String RULE_MATCHED = "agent.rule.matched";
    public static final String RULE_NO_MATCH = "agent.rule.no_match";

    // ════════════════════════════════════════════════════════════════
    // Tag key constants
    // ════════════════════════════════════════════════════════════════

    public static final String TAG_AGENT_TYPE = "agent_type";
    public static final String TAG_AGENT_ID = "agent_id";
    public static final String TAG_TENANT_ID = "tenant_id";
    public static final String TAG_STATUS = "status";
    public static final String TAG_ARM_ID = "arm_id";
    public static final String TAG_RULE_ID = "rule_id";
    public static final String TAG_ERROR_TYPE = "error_type";

    // ════════════════════════════════════════════════════════════════
    // Fields
    // ════════════════════════════════════════════════════════════════

    private final MetricsCollector collector;

    /**
     * Creates an AgentMetrics facade wrapping the given MetricsCollector.
     *
     * @param collector the underlying metrics collector (must not be null)
     */
    public AgentMetrics(MetricsCollector collector) {
        this.collector = Objects.requireNonNull(collector, "MetricsCollector must not be null");
    }

    // ════════════════════════════════════════════════════════════════
    // Processing metrics
    // ════════════════════════════════════════════════════════════════

    /**
     * Records an agent processing event.
     *
     * @param agentType  the type of agent (e.g., "deterministic", "probabilistic")
     * @param agentId    the agent ID
     * @param durationMs processing duration in milliseconds
     * @param success    whether processing succeeded
     */
    public void recordProcessing(String agentType, String agentId,
                                  long durationMs, boolean success) {
        String status = success ? "success" : "failure";
        collector.incrementCounter(PROCESS_COUNT,
                TAG_AGENT_TYPE, agentType,
                TAG_AGENT_ID, agentId,
                TAG_STATUS, status);
        collector.recordTimer(PROCESS_DURATION_MS, durationMs,
                TAG_AGENT_TYPE, agentType,
                TAG_AGENT_ID, agentId);
        if (!success) {
            collector.incrementCounter(PROCESS_ERRORS,
                    TAG_AGENT_TYPE, agentType,
                    TAG_AGENT_ID, agentId);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Decision metrics
    // ════════════════════════════════════════════════════════════════

    /**
     * Records a decision made by an agent.
     *
     * @param agentType the agent type
     * @param agentId   the agent ID
     * @param outcome   the decision outcome (e.g., "approved", "rejected", "escalated")
     */
    public void recordDecision(String agentType, String agentId, String outcome) {
        collector.incrementCounter(DECISION_COUNT,
                TAG_AGENT_TYPE, agentType,
                TAG_AGENT_ID, agentId,
                TAG_STATUS, outcome);
    }

    /**
     * Records a confidence score from probabilistic/hybrid agents.
     *
     * @param agentType       the agent type
     * @param agentId         the agent ID
     * @param confidenceScore the confidence score (0.0-1.0)
     */
    public void recordConfidence(String agentType, String agentId, double confidenceScore) {
        collector.recordConfidenceScore(CONFIDENCE_SCORE, confidenceScore);
        collector.incrementCounter(CONFIDENCE_SCORE + ".recorded",
                TAG_AGENT_TYPE, agentType,
                TAG_AGENT_ID, agentId);
    }

    // ════════════════════════════════════════════════════════════════
    // Lifecycle metrics
    // ════════════════════════════════════════════════════════════════

    /**
     * Records agent creation.
     *
     * @param agentType the agent type
     * @param agentId   the agent ID
     */
    public void recordCreated(String agentType, String agentId) {
        collector.incrementCounter(LIFECYCLE_CREATED,
                TAG_AGENT_TYPE, agentType,
                TAG_AGENT_ID, agentId);
    }

    /**
     * Records agent start.
     *
     * @param agentType the agent type
     * @param agentId   the agent ID
     */
    public void recordStarted(String agentType, String agentId) {
        collector.incrementCounter(LIFECYCLE_STARTED,
                TAG_AGENT_TYPE, agentType,
                TAG_AGENT_ID, agentId);
    }

    /**
     * Records agent stop.
     *
     * @param agentType the agent type
     * @param agentId   the agent ID
     */
    public void recordStopped(String agentType, String agentId) {
        collector.incrementCounter(LIFECYCLE_STOPPED,
                TAG_AGENT_TYPE, agentType,
                TAG_AGENT_ID, agentId);
    }

    // ════════════════════════════════════════════════════════════════
    // Adaptive agent metrics (bandit algorithms)
    // ════════════════════════════════════════════════════════════════

    /**
     * Records an arm selection in a multi-armed bandit agent.
     *
     * @param agentId the agent ID
     * @param armId   the selected arm ID
     */
    public void recordArmSelected(String agentId, String armId) {
        collector.incrementCounter(ARM_SELECTED,
                TAG_AGENT_ID, agentId,
                TAG_ARM_ID, armId);
    }

    /**
     * Records a reward signal for an adaptive agent.
     *
     * @param agentId the agent ID
     * @param reward  the reward value
     */
    public void recordReward(String agentId, double reward) {
        collector.recordConfidenceScore(REWARD_RECORDED, reward);
        collector.incrementCounter(REWARD_RECORDED + ".count",
                TAG_AGENT_ID, agentId);
    }

    /**
     * Records the current exploration rate for an adaptive agent.
     *
     * @param agentId         the agent ID
     * @param explorationRate the current exploration rate (0.0-1.0)
     */
    public void recordExplorationRate(String agentId, double explorationRate) {
        collector.recordGauge(EXPLORATION_RATE, explorationRate);
    }

    // ════════════════════════════════════════════════════════════════
    // Rule-based agent metrics
    // ════════════════════════════════════════════════════════════════

    /**
     * Records a rule match in a deterministic agent.
     *
     * @param agentId the agent ID
     * @param ruleId  the matched rule ID
     */
    public void recordRuleMatched(String agentId, String ruleId) {
        collector.incrementCounter(RULE_MATCHED,
                TAG_AGENT_ID, agentId,
                TAG_RULE_ID, ruleId);
    }

    /**
     * Records when no rules match for a deterministic agent.
     *
     * @param agentId the agent ID
     */
    public void recordNoRuleMatch(String agentId) {
        collector.incrementCounter(RULE_NO_MATCH,
                TAG_AGENT_ID, agentId);
    }

    /**
     * Returns the underlying MetricsCollector.
     *
     * @return the metrics collector
     */
    public MetricsCollector getCollector() {
        return collector;
    }
}
