package com.ghatana.products.finance.shell.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.*;

/**
 * @doc.type class
 * @doc.purpose Portfolio management result DTO with decision context and updates
 * @doc.layer product
 * @doc.pattern DTO
 */
public final class PortfolioManagementResult {
    @JsonProperty("operation_id")
    private final String operationId;

    @JsonProperty("portfolio_id")
    private final String portfolioId;

    @JsonProperty("user_id")
    private final String userId;

    @JsonProperty("operation_type")
    private final String operationType; // REBALANCE, ALLOCATION_UPDATE, ADJUSTMENT, etc.

    @JsonProperty("status")
    private final String status; // PENDING, COMPLETED, FAILED

    @JsonProperty("timestamp")
    private final Instant timestamp;

    @JsonProperty("changes")
    private final List<PortfolioChange> changes;

    @JsonProperty("decision_context")
    private final DecisionContext decisionContext;

    @JsonProperty("performance_impact")
    private final PerformanceImpact performanceImpact;

    @JsonProperty("audit_trail")
    private final List<AuditEntry> auditTrail;

    private PortfolioManagementResult(Builder builder) {
        this.operationId = builder.operationId;
        this.portfolioId = builder.portfolioId;
        this.userId = builder.userId;
        this.operationType = builder.operationType;
        this.status = builder.status;
        this.timestamp = builder.timestamp;
        this.changes = Collections.unmodifiableList(builder.changes);
        this.decisionContext = builder.decisionContext;
        this.performanceImpact = builder.performanceImpact;
        this.auditTrail = Collections.unmodifiableList(builder.auditTrail);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getOperationId() { return operationId; }
    public String getPortfolioId() { return portfolioId; }
    public String getUserId() { return userId; }
    public String getOperationType() { return operationType; }
    public String getStatus() { return status; }
    public Instant getTimestamp() { return timestamp; }
    public List<PortfolioChange> getChanges() { return changes; }
    public DecisionContext getDecisionContext() { return decisionContext; }
    public PerformanceImpact getPerformanceImpact() { return performanceImpact; }
    public List<AuditEntry> getAuditTrail() { return auditTrail; }

    public static final class Builder {
        private String operationId;
        private String portfolioId;
        private String userId;
        private String operationType;
        private String status;
        private Instant timestamp;
        private List<PortfolioChange> changes = new ArrayList<>();
        private DecisionContext decisionContext;
        private PerformanceImpact performanceImpact;
        private List<AuditEntry> auditTrail = new ArrayList<>();

        public Builder operationId(String operationId) { this.operationId = operationId; return this; }
        public Builder portfolioId(String portfolioId) { this.portfolioId = portfolioId; return this; }
        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder operationType(String operationType) { this.operationType = operationType; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder changes(List<PortfolioChange> changes) { this.changes = new ArrayList<>(changes); return this; }
        public Builder decisionContext(DecisionContext decisionContext) { this.decisionContext = decisionContext; return this; }
        public Builder performanceImpact(PerformanceImpact performanceImpact) { this.performanceImpact = performanceImpact; return this; }
        public Builder auditTrail(List<AuditEntry> auditTrail) { this.auditTrail = new ArrayList<>(auditTrail); return this; }

        public PortfolioManagementResult build() {
            return new PortfolioManagementResult(this);
        }
    }

    /**
     * Individual position change in portfolio.
     */
    public static final class PortfolioChange {
        @JsonProperty("instrument_id")
        public final String instrumentId;

        @JsonProperty("action")
        public final String action; // ADD, REMOVE, INCREASE, DECREASE

        @JsonProperty("quantity_change")
        public final double quantityChange;

        @JsonProperty("estimated_cost")
        public final double estimatedCost;

        @JsonProperty("reason")
        public final String reason;

        public PortfolioChange(String instrumentId, String action, double quantityChange, double estimatedCost, String reason) {
            this.instrumentId = instrumentId;
            this.action = action;
            this.quantityChange = quantityChange;
            this.estimatedCost = estimatedCost;
            this.reason = reason;
        }
    }

    /**
     * Decision context for portfolio operation.
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
     * Performance impact estimates for portfolio operation.
     */
    public static final class PerformanceImpact {
        @JsonProperty("expected_return_impact")
        public final double expectedReturnImpact;

        @JsonProperty("risk_reduction")
        public final double riskReduction;

        @JsonProperty("concentration_improvement")
        public final double concentrationImprovement;

        @JsonProperty("estimated_rebalancing_cost")
        public final double estimatedRebalancingCost;

        public PerformanceImpact(double expectedReturnImpact, double riskReduction, double concentrationImprovement, double estimatedRebalancingCost) {
            this.expectedReturnImpact = expectedReturnImpact;
            this.riskReduction = riskReduction;
            this.concentrationImprovement = concentrationImprovement;
            this.estimatedRebalancingCost = estimatedRebalancingCost;
        }
    }

    /**
     * Audit entry for portfolio management.
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
