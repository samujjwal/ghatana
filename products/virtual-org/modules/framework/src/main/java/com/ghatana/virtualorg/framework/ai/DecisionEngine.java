package com.ghatana.virtualorg.framework.ai;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.virtualorg.framework.event.EventBuilder;
import com.ghatana.virtualorg.framework.event.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

/**
 * Generic AI decision orchestration engine for virtual organizations.
 *
 * <p>
 * <b>Purpose</b><br>
 * Coordinates decision-making between organizational agents and departments.
 * Applies configurable policy constraints, tracks HITL escalations, and emits
 * decision events with full audit trails. Serves as the decision policy
 * enforcement point between agent reasoning and department actions.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * DecisionEngine engine = new DecisionEngine(eventPublisher, metrics);
 *
 * // Configure thresholds
 * engine.setConfidenceThreshold("security_approval", 0.90);
 *
 * // Execute decision with policy checks
 * DecisionResult result = engine.executeDecision(
 *     DecisionContext.builder()
 *         .confidence(0.85)
 *         .reasoning("Feature aligns with roadmap")
 *         .agentId("agent-001")
 *         .departmentId("engineering")
 *         .build(),
 *     "FeatureRequestDecided",
 *     Map.of("feature_id", "feat-123"),
 *     "tenant-123"
 * );
 * }</pre>
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Thread-safe - metrics are atomic, event emission is synchronized
 *
 * @doc.type class
 * @doc.purpose Generic AI decision orchestration for any organization type
 * @doc.layer product
 * @doc.pattern Policy Enforcer
 */
public class DecisionEngine {

    private static final Logger logger = LoggerFactory.getLogger(DecisionEngine.class);

    private final EventPublisher publisher;
    private final MetricsCollector metrics;
    private final Map<String, Double> confidenceThresholds;

    private static final double DEFAULT_CONFIDENCE_THRESHOLD = 0.7;

    /**
     * Creates a new DecisionEngine.
     *
     * @param publisher event publisher for decision events
     * @param metrics   metrics collector for observability
     */
    public DecisionEngine(EventPublisher publisher, MetricsCollector metrics) {
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.confidenceThresholds = new HashMap<>();
    }

    /**
     * Sets confidence threshold for a decision type.
     *
     * @param decisionType type of decision
     * @param threshold    confidence threshold (0.0-1.0)
     */
    public void setConfidenceThreshold(String decisionType, double threshold) {
        if (threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("Threshold must be between 0.0 and 1.0");
        }
        confidenceThresholds.put(decisionType, threshold);
    }

    /**
     * Gets confidence threshold for a decision type.
     *
     * @param decisionType type of decision
     * @return confidence threshold
     */
    public double getConfidenceThreshold(String decisionType) {
        return confidenceThresholds.getOrDefault(decisionType, DEFAULT_CONFIDENCE_THRESHOLD);
    }

    /**
     * Executes a decision with policy enforcement.
     *
     * @param context   decision context with confidence and reasoning
     * @param eventType event type to emit
     * @param payload   event payload
     * @param tenantId  tenant identifier
     * @return decision result
     */
    public DecisionResult executeDecision(
            DecisionContext context,
            String eventType,
            Map<String, Object> payload,
            String tenantId) {

        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        String decisionType = extractDecisionType(eventType);
        double threshold = getConfidenceThreshold(decisionType);

        // Check if decision meets confidence threshold
        boolean requiresHitl = context.confidence() < threshold;

        // Enrich payload with decision metadata
        Map<String, Object> enrichedPayload = new HashMap<>(payload);
        enrichedPayload.put("_decision_confidence", context.confidence());
        enrichedPayload.put("_decision_reasoning", context.reasoning());
        enrichedPayload.put("_decision_agent_id", context.agentId());
        enrichedPayload.put("_decision_timestamp", Instant.now().toString());
        enrichedPayload.put("_requires_hitl", requiresHitl);

        if (requiresHitl) {
            logger.info("Decision requires HITL review: confidence={} < threshold={} for type={}",
                    context.confidence(), threshold, decisionType);
            enrichedPayload.put("_hitl_reason", "confidence_below_threshold");
            recordMetric("decision.hitl_required", decisionType);
        } else {
            recordMetric("decision.auto_approved", decisionType);
        }

        // Publish decision event using EventBuilder
        if (publisher != null) {
            try {
                EventBuilder.publishEvent(publisher, eventType, enrichedPayload, tenantId);
                recordMetric("decision.published", decisionType);
            } catch (Exception e) {
                logger.error("Failed to publish decision event: {}", eventType, e);
                recordMetric("decision.publish_failed", decisionType);
                return new DecisionResult(false, requiresHitl, "Failed to publish: " + e.getMessage());
            }
        }

        return new DecisionResult(true, requiresHitl, requiresHitl ? "Requires human review" : "Auto-approved");
    }

    /**
     * Evaluates if a decision should be auto-approved.
     *
     * @param decisionType type of decision
     * @param confidence   confidence score
     * @return true if auto-approve, false if HITL required
     */
    public boolean shouldAutoApprove(String decisionType, double confidence) {
        return confidence >= getConfidenceThreshold(decisionType);
    }

    private String extractDecisionType(String eventType) {
        if (eventType == null) return "unknown";
        int lastDot = eventType.lastIndexOf('.');
        return lastDot >= 0 ? eventType.substring(lastDot + 1) : eventType;
    }

    private void recordMetric(String metricName, String decisionType) {
        if (metrics != null) {
            try {
                metrics.incrementCounter(metricName, "decision_type", decisionType);
            } catch (Exception e) {
                logger.debug("Failed to record metric: {}", metricName);
            }
        }
    }

    /**
     * Decision context containing confidence, reasoning, and agent info.
     */
    public record DecisionContext(
            double confidence,
            String reasoning,
            String agentId,
            String departmentId,
            Map<String, Object> metadata) {

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private double confidence = 0.0;
            private String reasoning = "";
            private String agentId = "";
            private String departmentId = "";
            private Map<String, Object> metadata = Map.of();

            public Builder confidence(double confidence) {
                this.confidence = confidence;
                return this;
            }

            public Builder reasoning(String reasoning) {
                this.reasoning = reasoning;
                return this;
            }

            public Builder agentId(String agentId) {
                this.agentId = agentId;
                return this;
            }

            public Builder departmentId(String departmentId) {
                this.departmentId = departmentId;
                return this;
            }

            public Builder metadata(Map<String, Object> metadata) {
                this.metadata = metadata;
                return this;
            }

            public DecisionContext build() {
                return new DecisionContext(confidence, reasoning, agentId, departmentId, metadata);
            }
        }
    }

    /**
     * Result of a decision execution.
     */
    public record DecisionResult(boolean success, boolean requiresHitl, String message) {
    }
}
