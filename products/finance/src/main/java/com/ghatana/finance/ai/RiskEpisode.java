package com.ghatana.finance.ai;

import java.time.Instant;
import java.util.Map;

/**
 * Risk learning episode.
 *
 * @doc.type class
 * @doc.purpose Data transfer object for risk assessment episodes
 * @doc.layer product
 * @doc.pattern Data Transfer Object
 */
public class RiskEpisode {
    private final String agentId;
    private final String portfolioId;
    private final Map<String, Object> inputFeatures;
    private final RiskAssessmentResult outputResult;
    private final Object marketConditions;
    private final Instant timestamp;

    private RiskEpisode(Builder builder) {
        this.agentId = builder.agentId;
        this.portfolioId = builder.portfolioId;
        this.inputFeatures = builder.inputFeatures;
        this.outputResult = builder.outputResult;
        this.marketConditions = builder.marketConditions;
        this.timestamp = builder.timestamp;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String agentId;
        private String portfolioId;
        private Map<String, Object> inputFeatures;
        private RiskAssessmentResult outputResult;
        private Object marketConditions;
        private Instant timestamp;

        public Builder agentId(String agentId) { this.agentId = agentId; return this; }
        public Builder portfolioId(String portfolioId) { this.portfolioId = portfolioId; return this; }
        public Builder inputFeatures(Map<String, Object> inputFeatures) { this.inputFeatures = inputFeatures; return this; }
        public Builder outputResult(RiskAssessmentResult outputResult) { this.outputResult = outputResult; return this; }
        public Builder marketConditions(Object marketConditions) { this.marketConditions = marketConditions; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }

        public RiskEpisode build() {
            return new RiskEpisode(this);
        }
    }
}
