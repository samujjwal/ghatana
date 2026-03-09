package com.ghatana.yappc.ai.router;

import io.activej.promise.Promise;

/**
 * Adapter interface for AI model providers.
 * 
 * @doc.type interface
 * @doc.purpose Abstract AI model interaction
 
 * @doc.layer core
 * @doc.pattern Adapter
*/
public interface ModelAdapter {
    
    /**
     * Initializes the adapter.
     */
    Promise<Void> initialize();
    
    /**
     * Executes an AI request.
     * 
     * @param request the AI request
     * @return Promise containing the AI response
     */
    Promise<AIResponse> execute(AIRequest request);
    
    /**
     * Gets the model configuration.
     */
    ModelConfig getConfig();
    
    /**
     * Checks if the model is available.
     */
    Promise<Boolean> isAvailable();
    
    /**
     * Shuts down the adapter.
     */
    Promise<Void> shutdown();
}
