package com.ghatana.ai.llm;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Configuration for LLM (Large Language Model) services.
 * 
 * @doc.type class
 * @doc.purpose Holds configuration parameters for LLM services including API keys and model settings.
 * @doc.layer configuration
 * @doc.pattern Builder (via static factory methods)
 */
public final class LLMConfiguration {
    private final String apiKey;
    private final String baseUrl;
    private final String modelName;
    private final String organization;
    private final int maxTokens;
    private final double temperature;
    private final int timeoutSeconds;
    private final int maxRetries;

    @JsonCreator
    private LLMConfiguration(
            @JsonProperty("apiKey") String apiKey,
            @JsonProperty("baseUrl") String baseUrl,
            @JsonProperty("modelName") String modelName,
            @JsonProperty("organization") String organization,
            @JsonProperty("maxTokens") int maxTokens,
            @JsonProperty("temperature") double temperature,
            @JsonProperty("timeoutSeconds") int timeoutSeconds,
            @JsonProperty("maxRetries") int maxRetries) {
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey cannot be null");
        this.baseUrl = baseUrl; // Can be null (default to provider default)
        this.modelName = Objects.requireNonNull(modelName, "modelName cannot be null");
        this.organization = organization; // Can be null
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.timeoutSeconds = timeoutSeconds;
        this.maxRetries = maxRetries;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getModelName() {
        return modelName;
    }

    public String getOrganization() {
        return organization;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public double getTemperature() {
        return temperature;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String apiKey;
        private String baseUrl;
        private String modelName = "gpt-3.5-turbo";
        private String organization;
        private int maxTokens = 2048;
        private double temperature = 0.7;
        private int timeoutSeconds = 30;
        private int maxRetries = 3;

        private Builder() {
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder organization(String organization) {
            this.organization = organization;
            return this;
        }

        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder timeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public LLMConfiguration build() {
            return new LLMConfiguration(apiKey, baseUrl, modelName, organization, maxTokens, temperature, timeoutSeconds, maxRetries);
        }
    }
}
