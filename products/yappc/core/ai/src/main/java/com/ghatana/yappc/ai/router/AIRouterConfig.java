package com.ghatana.yappc.ai.router;

import com.ghatana.yappc.ai.router.ModelSelector.SelectionStrategy;
import com.ghatana.yappc.ai.router.SemanticCache.CacheConfig;

/**
 * Configuration for AI Model Router.
 * 
 * @doc.type class
 * @doc.purpose Router configuration
 
 * @doc.layer core
 * @doc.pattern Configuration
*/
public final class AIRouterConfig {
    
    private final SelectionStrategy selectionStrategy;
    private final CacheConfig cacheConfig;
    private final String defaultModel;
    
    private AIRouterConfig(Builder builder) {
        this.selectionStrategy = builder.selectionStrategy;
        this.cacheConfig = builder.cacheConfig;
        this.defaultModel = builder.defaultModel;
    }
    
    public SelectionStrategy getSelectionStrategy() {
        return selectionStrategy;
    }
    
    public CacheConfig getCacheConfig() {
        return cacheConfig;
    }
    
    public String getDefaultModel() {
        return defaultModel;
    }
    
    public static AIRouterConfig defaults() {
        return builder().build();
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private SelectionStrategy selectionStrategy = SelectionStrategy.TASK_BASED;
        private CacheConfig cacheConfig = CacheConfig.defaults();
        private String defaultModel = "llama3.2";
        
        public Builder selectionStrategy(SelectionStrategy strategy) {
            this.selectionStrategy = strategy;
            return this;
        }
        
        public Builder cacheConfig(CacheConfig config) {
            this.cacheConfig = config;
            return this;
        }
        
        public Builder defaultModel(String model) {
            this.defaultModel = model;
            return this;
        }
        
        public AIRouterConfig build() {
            return new AIRouterConfig(this);
        }
    }
}
