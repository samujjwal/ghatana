package com.ghatana.finance.ai;

import java.time.Instant;

/**
 * Fraud alert.
 *
 * @doc.type class
 * @doc.purpose Data transfer object for fraud alerts
 * @doc.layer product
 * @doc.pattern Data Transfer Object
 */
public class Alert {
    private final String alertId;
    private final String tradeId;
    private final String accountId;
    private final String fraudType;
    private final double confidence;
    private final Instant timestamp;

    private Alert(Builder builder) {
        this.alertId = builder.alertId;
        this.tradeId = builder.tradeId;
        this.accountId = builder.accountId;
        this.fraudType = builder.fraudType;
        this.confidence = builder.confidence;
        this.timestamp = builder.timestamp;
    }

    public String getAlertId() { return alertId; }
    public String getTradeId() { return tradeId; }
    public String getAccountId() { return accountId; }
    public String getFraudType() { return fraudType; }
    public double getConfidence() { return confidence; }
    public Instant getTimestamp() { return timestamp; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String alertId;
        private String tradeId;
        private String accountId;
        private String fraudType;
        private double confidence;
        private Instant timestamp;

        public Builder alertId(String alertId) {
            this.alertId = alertId;
            return this;
        }

        public Builder tradeId(String tradeId) {
            this.tradeId = tradeId;
            return this;
        }

        public Builder accountId(String accountId) {
            this.accountId = accountId;
            return this;
        }

        public Builder fraudType(String fraudType) {
            this.fraudType = fraudType;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Alert build() {
            return new Alert(this);
        }
    }
}
