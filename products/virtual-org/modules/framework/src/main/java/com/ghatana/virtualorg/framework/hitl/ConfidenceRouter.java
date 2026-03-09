package com.ghatana.virtualorg.framework.hitl;

import com.ghatana.virtualorg.framework.runtime.AgentDecision;

import java.util.Objects;

/**
 * Routes agent decisions based on confidence level.
 *
 * <p>
 * <b>Purpose</b><br>
 * Implements risk-based automation control by routing actions to: -
 * Auto-approve for high-confidence decisions - Human review for
 * medium-confidence decisions - Escalation for low-confidence decisions -
 * Rejection for very low-confidence decisions
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * ConfidenceRouter router = ConfidenceRouter.builder()
 *     .autoApproveThreshold(0.9)
 *     .humanReviewThreshold(0.7)
 *     .escalateThreshold(0.4)
 *     .rejectThreshold(0.2)
 *     .build();
 *
 * RoutingDecision decision = router.route(agentDecision);
 * switch (decision.getAction()) {
 *     case AUTO_APPROVE -> executeAction();
 *     case HUMAN_REVIEW -> requestApproval();
 *     case ESCALATE -> escalateToSupervisor();
 *     case REJECT -> logRejection();
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Confidence-based action routing
 * @doc.layer product
 * @doc.pattern Strategy
 */
public class ConfidenceRouter {

    private final double autoApproveThreshold;
    private final double humanReviewThreshold;
    private final double escalateThreshold;
    private final double rejectThreshold;

    private ConfidenceRouter(Builder builder) {
        this.autoApproveThreshold = builder.autoApproveThreshold;
        this.humanReviewThreshold = builder.humanReviewThreshold;
        this.escalateThreshold = builder.escalateThreshold;
        this.rejectThreshold = builder.rejectThreshold;

        // Validate thresholds are in descending order
        if (autoApproveThreshold < humanReviewThreshold) {
            throw new IllegalArgumentException(
                    "autoApproveThreshold must be >= humanReviewThreshold");
        }
        if (humanReviewThreshold < escalateThreshold) {
            throw new IllegalArgumentException(
                    "humanReviewThreshold must be >= escalateThreshold");
        }
        if (escalateThreshold < rejectThreshold) {
            throw new IllegalArgumentException(
                    "escalateThreshold must be >= rejectThreshold");
        }
    }

    /**
     * Routes an agent decision based on its confidence level.
     *
     * @param decision The agent's decision
     * @return The routing decision
     */
    public RoutingDecision route(AgentDecision decision) {
        double confidence = decision.getConfidence();

        if (confidence >= autoApproveThreshold) {
            return RoutingDecision.autoApprove(
                    "Confidence " + formatPercent(confidence) + " >= auto-approve threshold");
        }

        if (confidence >= humanReviewThreshold) {
            return RoutingDecision.humanReview(
                    "Confidence " + formatPercent(confidence) + " requires human review");
        }

        if (confidence >= escalateThreshold) {
            return RoutingDecision.escalate(
                    "Confidence " + formatPercent(confidence) + " requires escalation");
        }

        if (confidence >= rejectThreshold) {
            return RoutingDecision.reject(
                    "Confidence " + formatPercent(confidence) + " is below acceptable threshold");
        }

        return RoutingDecision.reject(
                "Confidence " + formatPercent(confidence) + " is critically low");
    }

    /**
     * Routes a decision with additional context factors.
     *
     * @param decision The agent's decision
     * @param riskLevel The risk level of the action
     * @param isReversible Whether the action is reversible
     * @return The routing decision
     */
    public RoutingDecision routeWithContext(
            AgentDecision decision,
            ApprovalContext.RiskLevel riskLevel,
            boolean isReversible) {

        RoutingDecision baseDecision = route(decision);

        // Adjust based on risk level
        if (riskLevel == ApprovalContext.RiskLevel.CRITICAL) {
            // Critical actions always need human review
            if (baseDecision.getAction() == RoutingDecision.Action.AUTO_APPROVE) {
                return RoutingDecision.humanReview(
                        "Critical risk level requires human review");
            }
        }

        if (riskLevel == ApprovalContext.RiskLevel.HIGH && !isReversible) {
            // High-risk irreversible actions need extra scrutiny
            if (baseDecision.getAction() == RoutingDecision.Action.AUTO_APPROVE) {
                return RoutingDecision.humanReview(
                        "High-risk irreversible action requires human review");
            }
        }

        return baseDecision;
    }

    private String formatPercent(double value) {
        return String.format("%.0f%%", value * 100);
    }

    // ========== Getters ==========
    public double getAutoApproveThreshold() {
        return autoApproveThreshold;
    }

    public double getHumanReviewThreshold() {
        return humanReviewThreshold;
    }

    public double getEscalateThreshold() {
        return escalateThreshold;
    }

    public double getRejectThreshold() {
        return rejectThreshold;
    }

    // ========== Builder ==========
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a router with default thresholds.
     */
    public static ConfidenceRouter defaults() {
        return new Builder().build();
    }

    public static final class Builder {

        private double autoApproveThreshold = 0.9;
        private double humanReviewThreshold = 0.7;
        private double escalateThreshold = 0.4;
        private double rejectThreshold = 0.2;

        private Builder() {
        }

        public Builder autoApproveThreshold(double threshold) {
            this.autoApproveThreshold = threshold;
            return this;
        }

        public Builder humanReviewThreshold(double threshold) {
            this.humanReviewThreshold = threshold;
            return this;
        }

        public Builder escalateThreshold(double threshold) {
            this.escalateThreshold = threshold;
            return this;
        }

        public Builder rejectThreshold(double threshold) {
            this.rejectThreshold = threshold;
            return this;
        }

        public ConfidenceRouter build() {
            return new ConfidenceRouter(this);
        }
    }

    // ========== Routing Decision ==========
    /**
     * Result of confidence-based routing.
     */
    public static final class RoutingDecision {

        public enum Action {
            AUTO_APPROVE,
            HUMAN_REVIEW,
            ESCALATE,
            REJECT
        }

        private final Action action;
        private final String reason;

        private RoutingDecision(Action action, String reason) {
            this.action = Objects.requireNonNull(action);
            this.reason = reason != null ? reason : "";
        }

        public static RoutingDecision autoApprove(String reason) {
            return new RoutingDecision(Action.AUTO_APPROVE, reason);
        }

        public static RoutingDecision humanReview(String reason) {
            return new RoutingDecision(Action.HUMAN_REVIEW, reason);
        }

        public static RoutingDecision escalate(String reason) {
            return new RoutingDecision(Action.ESCALATE, reason);
        }

        public static RoutingDecision reject(String reason) {
            return new RoutingDecision(Action.REJECT, reason);
        }

        public Action getAction() {
            return action;
        }

        public String getReason() {
            return reason;
        }

        public boolean isAutoApprove() {
            return action == Action.AUTO_APPROVE;
        }

        public boolean needsHumanReview() {
            return action == Action.HUMAN_REVIEW;
        }

        public boolean needsEscalation() {
            return action == Action.ESCALATE;
        }

        public boolean isRejected() {
            return action == Action.REJECT;
        }

        @Override
        public String toString() {
            return "RoutingDecision{"
                    + "action=" + action
                    + ", reason='" + reason + '\''
                    + '}';
        }
    }
}
