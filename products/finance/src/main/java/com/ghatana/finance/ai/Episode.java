package com.ghatana.finance.ai;

import java.time.Instant;
import java.util.Map;

/**
 * Learning episode for fraud detection.
 *
 * @doc.type class
 * @doc.purpose Data transfer object for fraud detection episodes
 * @doc.layer product
 * @doc.pattern Data Transfer Object
 */
public class Episode {
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
