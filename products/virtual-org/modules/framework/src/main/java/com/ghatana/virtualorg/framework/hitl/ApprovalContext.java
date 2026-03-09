package com.ghatana.virtualorg.framework.hitl;

import java.util.Map;
import java.util.Objects;

/**
 * Context for an approval request.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides detailed context to human approvers, including: - What action is
 * being proposed - Why the agent decided to take this action - What the
 * expected outcome is - Any relevant data for making a decision
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * ApprovalContext context = ApprovalContext.builder()
 *     .reason("Need to deploy to resolve critical bug")
 *     .expectedOutcome("Service will restart, ~30s downtime")
 *     .riskLevel(RiskLevel.HIGH)
 *     .data(Map.of("service", "payments", "version", "2.1.0"))
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Approval context for human reviewers
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class ApprovalContext {

    private final String reason;
    private final String expectedOutcome;
    private final RiskLevel riskLevel;
    private final Map<String, Object> data;
    private final String agentReasoning;
    private final double confidenceScore;

    private ApprovalContext(Builder builder) {
        this.reason = builder.reason != null ? builder.reason : "";
        this.expectedOutcome = builder.expectedOutcome != null ? builder.expectedOutcome : "";
        this.riskLevel = builder.riskLevel != null ? builder.riskLevel : RiskLevel.MEDIUM;
        this.data = builder.data != null ? Map.copyOf(builder.data) : Map.of();
        this.agentReasoning = builder.agentReasoning != null ? builder.agentReasoning : "";
        this.confidenceScore = builder.confidenceScore;
    }

    public static ApprovalContext empty() {
        return new Builder().build();
    }

    public static ApprovalContext of(String reason) {
        return new Builder().reason(reason).build();
    }

    // ========== Getters ==========
    public String getReason() {
        return reason;
    }

    public String getExpectedOutcome() {
        return expectedOutcome;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public String getAgentReasoning() {
        return agentReasoning;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

    /**
     * Gets a specific data value.
     */
    @SuppressWarnings("unchecked")
    public <T> T getData(String key, Class<T> type) {
        Object value = data.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    // ========== Builder ==========
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String reason;
        private String expectedOutcome;
        private RiskLevel riskLevel;
        private Map<String, Object> data;
        private String agentReasoning;
        private double confidenceScore;

        private Builder() {
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder expectedOutcome(String expectedOutcome) {
            this.expectedOutcome = expectedOutcome;
            return this;
        }

        public Builder riskLevel(RiskLevel riskLevel) {
            this.riskLevel = riskLevel;
            return this;
        }

        public Builder data(Map<String, Object> data) {
            this.data = data;
            return this;
        }

        public Builder agentReasoning(String agentReasoning) {
            this.agentReasoning = agentReasoning;
            return this;
        }

        public Builder confidenceScore(double confidenceScore) {
            this.confidenceScore = confidenceScore;
            return this;
        }

        public ApprovalContext build() {
            return new ApprovalContext(this);
        }
    }

    @Override
    public String toString() {
        return "ApprovalContext{"
                + "reason='" + reason + '\''
                + ", riskLevel=" + riskLevel
                + ", confidenceScore=" + confidenceScore
                + '}';
    }

    // ========== Enums ==========
    public enum RiskLevel {
        LOW("Low risk, minimal impact"),
        MEDIUM("Medium risk, some impact possible"),
        HIGH("High risk, significant impact"),
        CRITICAL("Critical risk, major impact");

        private final String description;

        RiskLevel(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
