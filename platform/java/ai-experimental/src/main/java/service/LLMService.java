package com.ghatana.ai.service;

import io.activej.promise.Promise;

/**
 * @doc.type interface
 * @doc.purpose Unified interface for Large Language Model interactions.
 * @doc.layer core
 * @doc.pattern Service
 */
public interface LLMService {
    
    /**
     * Generates text based on a prompt.
     * @param prompt The input prompt.
     * @return A Promise resolving to the generated text.
     */
    Promise<String> generate(String prompt);
    
    /**
     * Generates a structural response based on a prompt (mocked as string for now).
     * @param prompt
     * @return
     */
    Promise<String> generateStructured(String prompt);
    
    /**
     * Chat with system prompt and user message.
     * @param systemPrompt The system prompt
     * @param userMessage The user message
     * @return A Promise resolving to the response
     */
    default Promise<String> chat(String systemPrompt, String userMessage) {
        String combinedPrompt = systemPrompt + "\n\nUser: " + userMessage;
        return generate(combinedPrompt);
    }
}
