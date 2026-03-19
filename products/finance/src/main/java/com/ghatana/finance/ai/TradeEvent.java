package com.ghatana.finance.ai;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Trade event data for fraud detection.
 */
public class TradeEvent {
    private final String tradeId;
    private final String accountId;
    private final String symbol;
    private final double quantity;
    private final double price;
    private final double marketPrice;
    private final Instant timestamp;
    private final String market;
    private final String eventType;
    private final Map<String, Object> features;

    private TradeEvent(Builder builder) {
        this.tradeId = builder.tradeId;
        this.accountId = builder.accountId;
        this.symbol = builder.symbol;
        this.quantity = builder.quantity;
        this.price = builder.price;
        this.marketPrice = builder.marketPrice;
        this.timestamp = builder.timestamp;
        this.market = builder.market;
        this.eventType = builder.eventType;
        this.features = builder.features;
    }

    public String getTradeId() { return tradeId; }
    public String getAccountId() { return accountId; }
    public String getSymbol() { return symbol; }
    public double getQuantity() { return quantity; }
    public double getPrice() { return price; }
    public double getMarketPrice() { return marketPrice; }
    public Instant getTimestamp() { return timestamp; }
    public String getMarket() { return market; }
    public String getEventType() { return eventType; }
    public Map<String, Object> getFeatures() { return features; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String tradeId;
        private String accountId;
        private String symbol;
        private double quantity;
        private double price;
        private double marketPrice;
        private Instant timestamp;
        private String market;
        private String eventType;
        private Map<String, Object> features;

        public Builder tradeId(String tradeId) {
            this.tradeId = tradeId;
            return this;
        }

        public Builder accountId(String accountId) {
            this.accountId = accountId;
            return this;
        }

        public Builder symbol(String symbol) {
            this.symbol = symbol;
            return this;
        }

        public Builder quantity(double quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder price(double price) {
            this.price = price;
            return this;
        }

        public Builder marketPrice(double marketPrice) {
            this.marketPrice = marketPrice;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder market(String market) {
            this.market = market;
            return this;
        }

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder features(Map<String, Object> features) {
            this.features = features;
            return this;
        }

        public TradeEvent build() {
            return new TradeEvent(this);
        }
    }
}

/**
 * Fraud detection result.
 */
class FraudDetectionResult {
    private final String tradeId;
    private final String accountId;
    private final boolean suspicious;
    private final String fraudType;
    private final double confidence;
    private final boolean skipped;

    private FraudDetectionResult(String tradeId, String accountId, boolean suspicious,
                                 String fraudType, double confidence, boolean skipped) {
        this.tradeId = tradeId;
        this.accountId = accountId;
        this.suspicious = suspicious;
        this.fraudType = fraudType;
        this.confidence = confidence;
        this.skipped = skipped;
    }

    public static FraudDetectionResult skip() {
        return new FraudDetectionResult(null, null, false, null, 0.0, true);
    }

    public static FraudDetectionResult clean(String tradeId, String accountId) {
        return new FraudDetectionResult(tradeId, accountId, false, null, 1.0, false);
    }

    public static FraudDetectionResult suspicious(String tradeId, String accountId,
                                                  String fraudType, double confidence) {
        return new FraudDetectionResult(tradeId, accountId, true, fraudType, confidence, false);
    }

    public String getTradeId() { return tradeId; }
    public String getAccountId() { return accountId; }
    public boolean isSuspicious() { return suspicious; }
    public String getFraudType() { return fraudType; }
    public double getConfidence() { return confidence; }
    public boolean isSkipped() { return skipped; }
}

/**
 * Fraud alert.
 */
class Alert {
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

/**
 * Learning episode for fraud detection.
 */
class Episode {
    private final String agentId;
    private final String tradeId;
    private final Map<String, Object> inputFeatures;
    private final FraudDetectionResult outputResult;
    private final Instant timestamp;

    private Episode(Builder builder) {
        this.agentId = builder.agentId;
        this.tradeId = builder.tradeId;
        this.inputFeatures = builder.inputFeatures;
        this.outputResult = builder.outputResult;
        this.timestamp = builder.timestamp;
    }

    public String getAgentId() { return agentId; }
    public String getTradeId() { return tradeId; }
    public Map<String, Object> getInputFeatures() { return inputFeatures; }
    public FraudDetectionResult getOutputResult() { return outputResult; }
    public Instant getTimestamp() { return timestamp; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String agentId;
        private String tradeId;
        private Map<String, Object> inputFeatures;
        private FraudDetectionResult outputResult;
        private Instant timestamp;

        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public Builder tradeId(String tradeId) {
            this.tradeId = tradeId;
            return this;
        }

        public Builder inputFeatures(Map<String, Object> inputFeatures) {
            this.inputFeatures = inputFeatures;
            return this;
        }

        public Builder outputResult(FraudDetectionResult outputResult) {
            this.outputResult = outputResult;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Episode build() {
            return new Episode(this);
        }
    }
}

/**
 * Extracted fraud patterns.
 */
class Patterns {
    private final double confidence;

    public Patterns(double confidence) {
        this.confidence = confidence;
    }

    public double getConfidence() {
        return confidence;
    }
}
