package com.ghatana.products.finance.bff.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.*;

/**
 * @doc.type class
 * @doc.purpose Trade data composition DTO for frontend consumption
 * @doc.layer product
 * @doc.pattern DTO
 */
public final class TradeDataComposition {
    @JsonProperty("trade_id")
    private final String tradeId;

    @JsonProperty("user_id")
    private final String userId;

    @JsonProperty("status")
    private final String status;

    @JsonProperty("trade_type")
    private final String tradeType;

    @JsonProperty("quantity")
    private final double quantity;

    @JsonProperty("price")
    private final double price;

    @JsonProperty("total_value")
    private final double totalValue;

    @JsonProperty("created_at")
    private final Instant createdAt;

    @JsonProperty("updated_at")
    private final Instant updatedAt;

    @JsonProperty("risk_assessment")
    private final RiskAssessment riskAssessment;

    @JsonProperty("compliance_status")
    private final ComplianceStatus complianceStatus;

    @JsonProperty("audit_trail")
    private final List<AuditEntry> auditTrail;

    private TradeDataComposition(Builder builder) {
        this.tradeId = builder.tradeId;
        this.userId = builder.userId;
        this.status = builder.status;
        this.tradeType = builder.tradeType;
        this.quantity = builder.quantity;
        this.price = builder.price;
        this.totalValue = builder.totalValue;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
        this.riskAssessment = builder.riskAssessment;
        this.complianceStatus = builder.complianceStatus;
        this.auditTrail = Collections.unmodifiableList(builder.auditTrail);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getTradeId() { return tradeId; }
    public String getUserId() { return userId; }
    public String getStatus() { return status; }
    public String getTradeType() { return tradeType; }
    public double getQuantity() { return quantity; }
    public double getPrice() { return price; }
    public double getTotalValue() { return totalValue; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public RiskAssessment getRiskAssessment() { return riskAssessment; }
    public ComplianceStatus getComplianceStatus() { return complianceStatus; }
    public List<AuditEntry> getAuditTrail() { return auditTrail; }

    public static final class Builder {
        private String tradeId;
        private String userId;
        private String status;
        private String tradeType;
        private double quantity;
        private double price;
        private double totalValue;
        private Instant createdAt;
        private Instant updatedAt;
        private RiskAssessment riskAssessment;
        private ComplianceStatus complianceStatus;
        private List<AuditEntry> auditTrail = new ArrayList<>();

        public Builder tradeId(String tradeId) { this.tradeId = tradeId; return this; }
        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder tradeType(String tradeType) { this.tradeType = tradeType; return this; }
        public Builder quantity(double quantity) { this.quantity = quantity; return this; }
        public Builder price(double price) { this.price = price; return this; }
        public Builder totalValue(double totalValue) { this.totalValue = totalValue; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }
        public Builder riskAssessment(RiskAssessment riskAssessment) { this.riskAssessment = riskAssessment; return this; }
        public Builder complianceStatus(ComplianceStatus complianceStatus) { this.complianceStatus = complianceStatus; return this; }
        public Builder auditTrail(List<AuditEntry> auditTrail) { this.auditTrail = new ArrayList<>(auditTrail); return this; }

        public TradeDataComposition build() {
            return new TradeDataComposition(this);
        }
    }

    /**
     * Risk assessment embedded in trade composition.
     */
    public static final class RiskAssessment {
        @JsonProperty("risk_level")
        public final String riskLevel;

        @JsonProperty("risk_score")
        public final double riskScore;

        @JsonProperty("confidence")
        public final double confidence;

        @JsonProperty("review_required")
        public final boolean reviewRequired;

        public RiskAssessment(String riskLevel, double riskScore, double confidence, boolean reviewRequired) {
            this.riskLevel = riskLevel;
            this.riskScore = riskScore;
            this.confidence = confidence;
            this.reviewRequired = reviewRequired;
        }
    }

    /**
     * Compliance status embedded in trade composition.
     */
    public static final class ComplianceStatus {
        @JsonProperty("compliant")
        public final boolean compliant;

        @JsonProperty("checks_passed")
        public final int checksPassed;

        @JsonProperty("checks_failed")
        public final int checksFailed;

        @JsonProperty("remarks")
        public final String remarks;

        public ComplianceStatus(boolean compliant, int checksPassed, int checksFailed, String remarks) {
            this.compliant = compliant;
            this.checksPassed = checksPassed;
            this.checksFailed = checksFailed;
            this.remarks = remarks;
        }
    }

    /**
     * Audit entry in trade composition.
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
