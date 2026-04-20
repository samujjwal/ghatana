package com.ghatana.products.finance.shell.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.*;

/**
 * @doc.type class
 * @doc.purpose Trade execution result DTO with decision context and audit trail
 * @doc.layer product
 * @doc.pattern DTO
 */
public final class TradeExecutionResult {
    @JsonProperty("execution_id")
    private final String executionId;

    @JsonProperty("trade_id")
    private final String tradeId;

    @JsonProperty("user_id")
    private final String userId;

    @JsonProperty("status")
    private final String status;

    @JsonProperty("filled_quantity")
    private final double filledQuantity;

    @JsonProperty("execution_price")
    private final double executionPrice;

    @JsonProperty("execution_cost")
    private final double executionCost;

    @JsonProperty("timestamp")
    private final Instant timestamp;

    @JsonProperty("decision_context")
    private final DecisionContext decisionContext;

    @JsonProperty("risk_validation")
    private final RiskValidation riskValidation;

    @JsonProperty("audit_trail")
    private final List<AuditEntry> auditTrail;

    private TradeExecutionResult(Builder builder) {
        this.executionId = builder.executionId;
        this.tradeId = builder.tradeId;
        this.userId = builder.userId;
        this.status = builder.status;
        this.filledQuantity = builder.filledQuantity;
        this.executionPrice = builder.executionPrice;
        this.executionCost = builder.executionCost;
        this.timestamp = builder.timestamp;
        this.decisionContext = builder.decisionContext;
        this.riskValidation = builder.riskValidation;
        this.auditTrail = Collections.unmodifiableList(builder.auditTrail);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getExecutionId() { return executionId; }
    public String getTradeId() { return tradeId; }
    public String getUserId() { return userId; }
    public String getStatus() { return status; }
    public double getFilledQuantity() { return filledQuantity; }
    public double getExecutionPrice() { return executionPrice; }
    public double getExecutionCost() { return executionCost; }
    public Instant getTimestamp() { return timestamp; }
    public DecisionContext getDecisionContext() { return decisionContext; }
    public RiskValidation getRiskValidation() { return riskValidation; }
    public List<AuditEntry> getAuditTrail() { return auditTrail; }

    public static final class Builder {
        private String executionId;
        private String tradeId;
        private String userId;
        private String status;
        private double filledQuantity;
        private double executionPrice;
        private double executionCost;
        private Instant timestamp;
        private DecisionContext decisionContext;
        private RiskValidation riskValidation;
        private List<AuditEntry> auditTrail = new ArrayList<>();

        public Builder executionId(String executionId) { this.executionId = executionId; return this; }
        public Builder tradeId(String tradeId) { this.tradeId = tradeId; return this; }
        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder filledQuantity(double filledQuantity) { this.filledQuantity = filledQuantity; return this; }
        public Builder executionPrice(double executionPrice) { this.executionPrice = executionPrice; return this; }
        public Builder executionCost(double executionCost) { this.executionCost = executionCost; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder decisionContext(DecisionContext decisionContext) { this.decisionContext = decisionContext; return this; }
        public Builder riskValidation(RiskValidation riskValidation) { this.riskValidation = riskValidation; return this; }
        public Builder auditTrail(List<AuditEntry> auditTrail) { this.auditTrail = new ArrayList<>(auditTrail); return this; }

        public TradeExecutionResult build() {
            return new TradeExecutionResult(this);
        }
    }

    /**
     * Decision context for execution (autonomy, approval, flags).
     */
    public static final class DecisionContext {
        @JsonProperty("decision_type")
        public final String decisionType; // AUTO, HUMAN_APPROVED, HUMAN_REVIEWED

        @JsonProperty("approver_id")
        public final String approverId;

        @JsonProperty("confidence_score")
        public final double confidenceScore;

        @JsonProperty("rationale")
        public final String rationale;

        @JsonProperty("decision_timestamp")
        public final Instant decisionTimestamp;

        public DecisionContext(String decisionType, String approverId, double confidenceScore, String rationale, Instant decisionTimestamp) {
            this.decisionType = decisionType;
            this.approverId = approverId;
            this.confidenceScore = confidenceScore;
            this.rationale = rationale;
            this.decisionTimestamp = decisionTimestamp;
        }
    }

    /**
     * Risk validation results.
     */
    public static final class RiskValidation {
        @JsonProperty("passed")
        public final boolean passed;

        @JsonProperty("risk_level")
        public final String riskLevel;

        @JsonProperty("checks_performed")
        public final List<String> checksPerformed;

        @JsonProperty("violations")
        public final List<String> violations;

        public RiskValidation(boolean passed, String riskLevel, List<String> checksPerformed, List<String> violations) {
            this.passed = passed;
            this.riskLevel = riskLevel;
            this.checksPerformed = Collections.unmodifiableList(checksPerformed);
            this.violations = Collections.unmodifiableList(violations);
        }
    }

    /**
     * Audit entry for execution.
     */
    public static final class AuditEntry {
        @JsonProperty("timestamp")
        public final Instant timestamp;

        @JsonProperty("action")
        public final String action;

        @JsonProperty("actor")
        public final String actor;

        @JsonProperty("details")
        public final String details;

        public AuditEntry(Instant timestamp, String action, String actor, String details) {
            this.timestamp = timestamp;
            this.action = action;
            this.actor = actor;
            this.details = details;
        }
    }
}
