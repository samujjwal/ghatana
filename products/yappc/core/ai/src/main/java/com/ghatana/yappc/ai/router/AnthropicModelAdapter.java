package com.ghatana.yappc.ai.router;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Placeholder adapter for Anthropic API.
 * 
 * @doc.type class
 * @doc.purpose Anthropic API integration (placeholder)
 
 * @doc.layer core
 * @doc.pattern Adapter
*/
public final class AnthropicModelAdapter implements ModelAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(AnthropicModelAdapter.class);
    private final ModelConfig config;
    
    public AnthropicModelAdapter(ModelConfig config) {
        this.config = config;
    }
    
    @Override
    public Promise<Void> initialize() {
        logger.info("Anthropic adapter initialized for {}", config.getModelId());
        return Promise.complete();
    }
    
    @Override
    public Promise<AIResponse> execute(AIRequest request) {
        return Promise.ofException(
            new UnsupportedOperationException("Anthropic adapter not yet implemented"));
    }
    
    @Override
    public ModelConfig getConfig() {
        return config;
    }
    
    @Override
    public Promise<Boolean> isAvailable() {
        return Promise.of(false);
    }
    
    @Override
    public Promise<Void> shutdown() {
        return Promise.complete();
    }
}
