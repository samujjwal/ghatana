package com.ghatana.yappc.ai.router;

import java.util.*;

/**
 * Represents an AI response with metadata.
 * 
 * @doc.type class
 * @doc.purpose AI response encapsulation
 
 * @doc.layer core
 * @doc.pattern DTO
*/
public final class AIResponse {
    
    private final String requestId;
    private final String modelId;
    private final String content;
    private final Map<String, Object> metadata;
    private final ResponseMetrics metrics;
    private final boolean cacheHit;
    private final boolean fallbackUsed;
    private final long timestamp;
    
    private AIResponse(Builder builder) {
        this.requestId = builder.requestId;
        this.modelId = builder.modelId;
        this.content = builder.content;
        this.metadata = Collections.unmodifiableMap(new HashMap<>(builder.metadata));
        this.metrics = builder.metrics;
        this.cacheHit = builder.cacheHit;
        this.fallbackUsed = builder.fallbackUsed;
        this.timestamp = System.currentTimeMillis();
    }
    
    public String getRequestId() { return requestId; }
    public String getModelId() { return modelId; }
    public String getContent() { return content; }
    public Map<String, Object> getMetadata() { return metadata; }
    public ResponseMetrics getMetrics() { return metrics; }
    public boolean isCacheHit() { return cacheHit; }
    public boolean isFallbackUsed() { return fallbackUsed; }
    public long getTimestamp() { return timestamp; }
    
    /**
     * Creates a copy with cache hit flag set.
     */
    public AIResponse withCacheHit(boolean cacheHit) {
        return new Builder()
            .requestId(requestId)
            .modelId(modelId)
            .content(content)
            .metadata(new HashMap<>(metadata))
            .metrics(metrics)
            .cacheHit(cacheHit)
            .fallbackUsed(fallbackUsed)
            .build();
    }
    
    /**
     * Creates a copy with fallback flag set.
     */
    public AIResponse withFallbackUsed(boolean fallbackUsed) {
        return new Builder()
            .requestId(requestId)
            .modelId(modelId)
            .content(content)
            .metadata(new HashMap<>(metadata))
            .metrics(metrics)
            .cacheHit(cacheHit)
            .fallbackUsed(fallbackUsed)
            .build();
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String requestId;
        private String modelId;
        private String content;
        private Map<String, Object> metadata = new HashMap<>();
        private ResponseMetrics metrics;
        private boolean cacheHit = false;
        private boolean fallbackUsed = false;
        
        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }
        
        public Builder modelId(String modelId) {
            this.modelId = modelId;
            return this;
        }
        
        public Builder content(String content) {
            this.content = content;
            return this;
        }
        
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }
        
        public Builder addMetadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }
        
        public Builder metrics(ResponseMetrics metrics) {
            this.metrics = metrics;
            return this;
        }
        
        public Builder cacheHit(boolean cacheHit) {
            this.cacheHit = cacheHit;
            return this;
        }
        
        public Builder fallbackUsed(boolean fallbackUsed) {
            this.fallbackUsed = fallbackUsed;
            return this;
        }
        
        public AIResponse build() {
            Objects.requireNonNull(requestId, "requestId is required");
            Objects.requireNonNull(modelId, "modelId is required");
            Objects.requireNonNull(content, "content is required");
            return new AIResponse(this);
        }
    }
    
    /**
     * Response performance metrics.
     */
    public static final class ResponseMetrics {
        private final long latencyMs;
        private final int tokenCount;
        private final int promptTokens;
        private final int completionTokens;
        private final double cost;
        
        private ResponseMetrics(Builder builder) {
            this.latencyMs = builder.latencyMs;
            this.tokenCount = builder.tokenCount;
            this.promptTokens = builder.promptTokens;
            this.completionTokens = builder.completionTokens;
            this.cost = builder.cost;
        }
        
        public long getLatencyMs() { return latencyMs; }
        public int getTokenCount() { return tokenCount; }
        public int getPromptTokens() { return promptTokens; }
        public int getCompletionTokens() { return completionTokens; }
        public double getCost() { return cost; }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private long latencyMs;
            private int tokenCount;
            private int promptTokens;
            private int completionTokens;
            private double cost;
            
            public Builder latencyMs(long latencyMs) {
                this.latencyMs = latencyMs;
                return this;
            }
            
            public Builder tokenCount(int tokenCount) {
                this.tokenCount = tokenCount;
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
            
            public Builder cost(double cost) {
                this.cost = cost;
                return this;
            }
            
            public ResponseMetrics build() {
                return new ResponseMetrics(this);
            }
        }
    }
}
