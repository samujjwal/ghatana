package com.ghatana.finance.ai;

import java.time.Instant;
import java.util.Map;

/**
 * Portfolio update event.
 *
 * @doc.type class
 * @doc.purpose Data transfer object for portfolio update information
 * @doc.layer product
 * @doc.pattern Data Transfer Object
 */
public class PortfolioUpdate {
    private final String portfolioId;
    private final String accountId;
    private final Instant timestamp;
    private final Map<String, Double> positions;
    private final Map<String, Double> marketValues;
    private final Map<String, Object> riskFeatures;
    private final String eventType;

    private PortfolioUpdate(Builder builder) {
        this.portfolioId = builder.portfolioId;
        this.accountId = builder.accountId;
        this.timestamp = builder.timestamp;
        this.positions = builder.positions;
        this.marketValues = builder.marketValues;
        this.riskFeatures = builder.riskFeatures;
        this.eventType = builder.eventType;
    }

    public String getPortfolioId() { return portfolioId; }
    public String getAccountId() { return accountId; }
    public Instant getTimestamp() { return timestamp; }
    public Map<String, Double> getPositions() { return positions; }
    public Map<String, Double> getMarketValues() { return marketValues; }
    public Map<String, Object> getRiskFeatures() { return riskFeatures; }
    public String getEventType() { return eventType; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String portfolioId;
        private String accountId;
        private Instant timestamp;
        private Map<String, Double> positions;
        private Map<String, Double> marketValues;
        private Map<String, Object> riskFeatures;
        private String eventType;

        public Builder portfolioId(String portfolioId) {
            this.portfolioId = portfolioId;
            return this;
        }

        public Builder accountId(String accountId) {
            this.accountId = accountId;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder positions(Map<String, Double> positions) {
            this.positions = positions;
            return this;
        }

        public Builder marketValues(Map<String, Double> marketValues) {
            this.marketValues = marketValues;
            return this;
        }

        public Builder riskFeatures(Map<String, Object> riskFeatures) {
            this.riskFeatures = riskFeatures;
            return this;
        }

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public PortfolioUpdate build() {
            return new PortfolioUpdate(this);
        }
    }
}
