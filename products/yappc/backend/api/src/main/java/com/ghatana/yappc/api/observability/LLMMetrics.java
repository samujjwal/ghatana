/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.observability;

import java.time.Instant;
import java.util.Map;

/**
 * LLM request/response metrics for observability.
 *
 * <p>Tracks cost, latency, token usage, and quality metrics for all LLM calls.
 * Used for monitoring, alerting, and cost optimization.
 *
 * @doc.type class
 * @doc.purpose LLM observability
 * @doc.layer infrastructure
 * @doc.pattern Value Object
 */
public class LLMMetrics {

    private final String requestId;
    private final String model;
    private final String provider;
    private final Instant timestamp;
    private final long latencyMs;
    private final int promptTokens;
    private final int completionTokens;
    private final int totalTokens;
    private final double estimatedCost;
    private final String tenantId;
    private final String userId;
    private final String feature;
    private final boolean cached;
    private final String errorCode;
    private final Map<String, Object> metadata;

    private LLMMetrics(Builder builder) {
        this.requestId = builder.requestId;
        this.model = builder.model;
        this.provider = builder.provider;
        this.timestamp = builder.timestamp;
        this.latencyMs = builder.latencyMs;
        this.promptTokens = builder.promptTokens;
        this.completionTokens = builder.completionTokens;
        this.totalTokens = builder.totalTokens;
        this.estimatedCost = builder.estimatedCost;
        this.tenantId = builder.tenantId;
        this.userId = builder.userId;
        this.feature = builder.feature;
        this.cached = builder.cached;
        this.errorCode = builder.errorCode;
        this.metadata = builder.metadata;
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public String getRequestId() { return requestId; }
    public String getModel() { return model; }
    public String getProvider() { return provider; }
    public Instant getTimestamp() { return timestamp; }
    public long getLatencyMs() { return latencyMs; }
    public int getPromptTokens() { return promptTokens; }
    public int getCompletionTokens() { return completionTokens; }
    public int getTotalTokens() { return totalTokens; }
    public double getEstimatedCost() { return estimatedCost; }
    public String getTenantId() { return tenantId; }
    public String getUserId() { return userId; }
    public String getFeature() { return feature; }
    public boolean isCached() { return cached; }
    public String getErrorCode() { return errorCode; }
    public Map<String, Object> getMetadata() { return metadata; }

    public boolean isError() {
        return errorCode != null && !errorCode.isEmpty();
    }

    public static class Builder {
        private String requestId;
        private String model;
        private String provider;
        private Instant timestamp = Instant.now();
        private long latencyMs;
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
        private double estimatedCost;
        private String tenantId;
        private String userId;
        private String feature;
        private boolean cached;
        private String errorCode;
        private Map<String, Object> metadata = Map.of();

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder latencyMs(long latencyMs) {
            this.latencyMs = latencyMs;
            return this;
        }

        public Builder promptTokens(int promptTokens) {
            this.promptTokens = promptTokens;
            return this;
        }

        public Builder completionTokens(int completionTokens) {
            this.completionTokens = completionTokens;
            return this;
        }

        public Builder totalTokens(int totalTokens) {
            this.totalTokens = totalTokens;
            return this;
        }

        public Builder estimatedCost(double estimatedCost) {
            this.estimatedCost = estimatedCost;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder feature(String feature) {
            this.feature = feature;
            return this;
        }

        public Builder cached(boolean cached) {
            this.cached = cached;
            return this;
        }

        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public LLMMetrics build() {
            return new LLMMetrics(this);
        }
    }

    @Override
    public String toString() {
        return String.format(
            "LLMMetrics{requestId='%s', model='%s', provider='%s', latencyMs=%d, " +
            "tokens=%d, cost=$%.4f, feature='%s', error='%s'}",
            requestId, model, provider, latencyMs, totalTokens, estimatedCost, feature, errorCode
        );
    }
}
