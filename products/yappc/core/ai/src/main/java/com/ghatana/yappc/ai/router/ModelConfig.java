package com.ghatana.yappc.ai.router;

import java.util.*;

/**
 * Configuration for an AI model.
 * 
 * @doc.type class
 * @doc.purpose Model configuration
 
 * @doc.layer core
 * @doc.pattern Configuration
*/
public final class ModelConfig {
    
    private final String modelId;
    private final String displayName;
    private final String provider;
    private final Set<String> capabilities;
    private final int maxTokens;
    private final double costPerToken;
    private final List<String> fallbackChain;
    private final String endpoint;
    private final Map<String, String> headers;
    
    private ModelConfig(Builder builder) {
        this.modelId = builder.modelId;
        this.displayName = builder.displayName;
        this.provider = builder.provider;
        this.capabilities = Set.copyOf(builder.capabilities);
        this.maxTokens = builder.maxTokens;
        this.costPerToken = builder.costPerToken;
        this.fallbackChain = List.copyOf(builder.fallbackChain);
        this.endpoint = builder.endpoint;
        this.headers = Collections.unmodifiableMap(new HashMap<>(builder.headers));
    }
    
    public String getModelId() { return modelId; }
    public String getDisplayName() { return displayName; }
    public String getProvider() { return provider; }
    public Set<String> getCapabilities() { return capabilities; }
    public int getMaxTokens() { return maxTokens; }
    public double getCostPerToken() { return costPerToken; }
    public List<String> getFallbackChain() { return fallbackChain; }
    public String getEndpoint() { return endpoint; }
    public Map<String, String> getHeaders() { return headers; }
    
    public boolean hasCapability(String capability) {
        return capabilities.contains(capability);
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String modelId;
        private String displayName;
        private String provider;
        private Set<String> capabilities = new HashSet<>();
        private int maxTokens = 2048;
        private double costPerToken = 0.0;
        private List<String> fallbackChain = new ArrayList<>();
        private String endpoint = "http://localhost:11434";
        private Map<String, String> headers = new HashMap<>();
        
        public Builder modelId(String modelId) {
            this.modelId = modelId;
            return this;
        }
        
        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }
        
        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }
        
        public Builder capabilities(Set<String> capabilities) {
            this.capabilities = capabilities;
            return this;
        }
        
        public Builder addCapability(String capability) {
            this.capabilities.add(capability);
            return this;
        }
        
        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }
        
        public Builder costPerToken(double costPerToken) {
            this.costPerToken = costPerToken;
            return this;
        }
        
        public Builder fallbackChain(List<String> fallbackChain) {
            this.fallbackChain = fallbackChain;
            return this;
        }
        
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }
        
        public Builder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }
        
        public Builder addHeader(String key, String value) {
            this.headers.put(key, value);
            return this;
        }
        
        public ModelConfig build() {
            Objects.requireNonNull(modelId, "modelId is required");
            Objects.requireNonNull(displayName, "displayName is required");
            Objects.requireNonNull(provider, "provider is required");
            return new ModelConfig(this);
        }
    }
}
