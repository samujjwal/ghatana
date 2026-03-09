package com.ghatana.yappc.ai.router;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Placeholder adapter for OpenAI API.
 * 
 * @doc.type class
 * @doc.purpose OpenAI API integration (placeholder)
 
 * @doc.layer core
 * @doc.pattern Adapter
*/
public final class OpenAIModelAdapter implements ModelAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(OpenAIModelAdapter.class);
    private final ModelConfig config;
    
    public OpenAIModelAdapter(ModelConfig config) {
        this.config = config;
    }
    
    @Override
    public Promise<Void> initialize() {
        logger.info("OpenAI adapter initialized for {}", config.getModelId());
        return Promise.complete();
    }
    
    @Override
    public Promise<AIResponse> execute(AIRequest request) {
        return Promise.ofException(
            new UnsupportedOperationException("OpenAI adapter not yet implemented"));
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
