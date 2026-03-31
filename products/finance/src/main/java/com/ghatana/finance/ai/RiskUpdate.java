package com.ghatana.finance.ai;

import java.time.Instant;

/**
 * Risk update for alerts.
 *
 * @doc.type class
 * @doc.purpose Data transfer object for risk update information
 * @doc.layer product
 * @doc.pattern Data Transfer Object
 */
public class RiskUpdate {
    private final String updateId;
    private final String portfolioId;
    private final double var95;
    private final double var99;
    private final double expectedShortfall;
    private final double beta;
    private final double concentrationRisk;
    private final double liquidityRisk;
    private final double creditRisk;
    private final double overallRiskScore;
    private final Instant timestamp;

    private RiskUpdate(Builder builder) {
        this.updateId = builder.updateId;
        this.portfolioId = builder.portfolioId;
        this.var95 = builder.var95;
        this.var99 = builder.var99;
        this.expectedShortfall = builder.expectedShortfall;
        this.beta = builder.beta;
        this.concentrationRisk = builder.concentrationRisk;
        this.liquidityRisk = builder.liquidityRisk;
        this.creditRisk = builder.creditRisk;
        this.overallRiskScore = builder.overallRiskScore;
        this.timestamp = builder.timestamp;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String updateId;
        private String portfolioId;
        private double var95;
        private double var99;
        private double expectedShortfall;
        private double beta;
        private double concentrationRisk;
        private double liquidityRisk;
        private double creditRisk;
        private double overallRiskScore;
        private Instant timestamp;

        public Builder updateId(String updateId) { this.updateId = updateId; return this; }
        public Builder portfolioId(String portfolioId) { this.portfolioId = portfolioId; return this; }
        public Builder var95(double var95) { this.var95 = var95; return this; }
        public Builder var99(double var99) { this.var99 = var99; return this; }
        public Builder expectedShortfall(double expectedShortfall) { this.expectedShortfall = expectedShortfall; return this; }
        public Builder beta(double beta) { this.beta = beta; return this; }
        public Builder concentrationRisk(double concentrationRisk) { this.concentrationRisk = concentrationRisk; return this; }
        public Builder liquidityRisk(double liquidityRisk) { this.liquidityRisk = liquidityRisk; return this; }
        public Builder creditRisk(double creditRisk) { this.creditRisk = creditRisk; return this; }
        public Builder overallRiskScore(double overallRiskScore) { this.overallRiskScore = overallRiskScore; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }

        public RiskUpdate build() {
            return new RiskUpdate(this);
        }
    }
}
