package com.ghatana.products.finance.bff.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.*;

/**
 * @doc.type class
 * @doc.purpose Portfolio data composition DTO for frontend consumption
 * @doc.layer product
 * @doc.pattern DTO
 */
public final class PortfolioDataComposition {
    @JsonProperty("portfolio_id")
    private final String portfolioId;

    @JsonProperty("user_id")
    private final String userId;

    @JsonProperty("portfolio_name")
    private final String portfolioName;

    @JsonProperty("total_value")
    private final double totalValue;

    @JsonProperty("unrealized_pnl")
    private final double unrealizedPnl;

    @JsonProperty("realized_pnl")
    private final double realizedPnl;

    @JsonProperty("allocation")
    private final AllocationData allocation;

    @JsonProperty("positions")
    private final List<PositionData> positions;

    @JsonProperty("risk_metrics")
    private final RiskMetrics riskMetrics;

    @JsonProperty("performance")
    private final PerformanceData performance;

    @JsonProperty("updated_at")
    private final Instant updatedAt;

    @JsonProperty("audit_trail")
    private final List<AuditEntry> auditTrail;

    private PortfolioDataComposition(Builder builder) {
        this.portfolioId = builder.portfolioId;
        this.userId = builder.userId;
        this.portfolioName = builder.portfolioName;
        this.totalValue = builder.totalValue;
        this.unrealizedPnl = builder.unrealizedPnl;
        this.realizedPnl = builder.realizedPnl;
        this.allocation = builder.allocation;
        this.positions = Collections.unmodifiableList(builder.positions);
        this.riskMetrics = builder.riskMetrics;
        this.performance = builder.performance;
        this.updatedAt = builder.updatedAt;
        this.auditTrail = Collections.unmodifiableList(builder.auditTrail);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getPortfolioId() { return portfolioId; }
    public String getUserId() { return userId; }
    public String getPortfolioName() { return portfolioName; }
    public double getTotalValue() { return totalValue; }
    public double getUnrealizedPnl() { return unrealizedPnl; }
    public double getRealizedPnl() { return realizedPnl; }
    public AllocationData getAllocation() { return allocation; }
    public List<PositionData> getPositions() { return positions; }
    public RiskMetrics getRiskMetrics() { return riskMetrics; }
    public PerformanceData getPerformance() { return performance; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<AuditEntry> getAuditTrail() { return auditTrail; }

    public static final class Builder {
        private String portfolioId;
        private String userId;
        private String portfolioName;
        private double totalValue;
        private double unrealizedPnl;
        private double realizedPnl;
        private AllocationData allocation;
        private List<PositionData> positions = new ArrayList<>();
        private RiskMetrics riskMetrics;
        private PerformanceData performance;
        private Instant updatedAt;
        private List<AuditEntry> auditTrail = new ArrayList<>();

        public Builder portfolioId(String portfolioId) { this.portfolioId = portfolioId; return this; }
        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder portfolioName(String portfolioName) { this.portfolioName = portfolioName; return this; }
        public Builder totalValue(double totalValue) { this.totalValue = totalValue; return this; }
        public Builder unrealizedPnl(double unrealizedPnl) { this.unrealizedPnl = unrealizedPnl; return this; }
        public Builder realizedPnl(double realizedPnl) { this.realizedPnl = realizedPnl; return this; }
        public Builder allocation(AllocationData allocation) { this.allocation = allocation; return this; }
        public Builder positions(List<PositionData> positions) { this.positions = new ArrayList<>(positions); return this; }
        public Builder riskMetrics(RiskMetrics riskMetrics) { this.riskMetrics = riskMetrics; return this; }
        public Builder performance(PerformanceData performance) { this.performance = performance; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }
        public Builder auditTrail(List<AuditEntry> auditTrail) { this.auditTrail = new ArrayList<>(auditTrail); return this; }

        public PortfolioDataComposition build() {
            return new PortfolioDataComposition(this);
        }
    }

    /**
     * Allocation breakdown in portfolio.
     */
    public static final class AllocationData {
        @JsonProperty("by_asset_class")
        public final Map<String, Double> byAssetClass;

        @JsonProperty("by_sector")
        public final Map<String, Double> bySector;

        @JsonProperty("by_geography")
        public final Map<String, Double> byGeography;

        public AllocationData(Map<String, Double> byAssetClass, Map<String, Double> bySector, Map<String, Double> byGeography) {
            this.byAssetClass = Collections.unmodifiableMap(new HashMap<>(byAssetClass));
            this.bySector = Collections.unmodifiableMap(new HashMap<>(bySector));
            this.byGeography = Collections.unmodifiableMap(new HashMap<>(byGeography));
        }
    }

    /**
     * Position data in portfolio.
     */
    public static final class PositionData {
        @JsonProperty("instrument_id")
        public final String instrumentId;

        @JsonProperty("quantity")
        public final double quantity;

        @JsonProperty("current_price")
        public final double currentPrice;

        @JsonProperty("cost_basis")
        public final double costBasis;

        @JsonProperty("unrealized_pnl")
        public final double unrealizedPnl;

        @JsonProperty("allocation_percent")
        public final double allocationPercent;

        public PositionData(String instrumentId, double quantity, double currentPrice, double costBasis, double unrealizedPnl, double allocationPercent) {
            this.instrumentId = instrumentId;
            this.quantity = quantity;
            this.currentPrice = currentPrice;
            this.costBasis = costBasis;
            this.unrealizedPnl = unrealizedPnl;
            this.allocationPercent = allocationPercent;
        }
    }

    /**
     * Risk metrics in portfolio.
     */
    public static final class RiskMetrics {
        @JsonProperty("var_95")
        public final double var95;

        @JsonProperty("var_99")
        public final double var99;

        @JsonProperty("sharpe_ratio")
        public final double sharpeRatio;

        @JsonProperty("beta")
        public final double beta;

        @JsonProperty("max_drawdown")
        public final double maxDrawdown;

        public RiskMetrics(double var95, double var99, double sharpeRatio, double beta, double maxDrawdown) {
            this.var95 = var95;
            this.var99 = var99;
            this.sharpeRatio = sharpeRatio;
            this.beta = beta;
            this.maxDrawdown = maxDrawdown;
        }
    }

    /**
     * Performance data in portfolio.
     */
    public static final class PerformanceData {
        @JsonProperty("ytd_return")
        public final double ytdReturn;

        @JsonProperty("one_year_return")
        public final double oneYearReturn;

        @JsonProperty("three_year_return")
        public final double threeYearReturn;

        @JsonProperty("benchmark_comparison")
        public final double benchmarkComparison;

        public PerformanceData(double ytdReturn, double oneYearReturn, double threeYearReturn, double benchmarkComparison) {
            this.ytdReturn = ytdReturn;
            this.oneYearReturn = oneYearReturn;
            this.threeYearReturn = threeYearReturn;
            this.benchmarkComparison = benchmarkComparison;
        }
    }

    /**
     * Audit entry in portfolio composition.
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
