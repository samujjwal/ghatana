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
     * Generates a structural response based on a prompt with schema validation.
     * The LLM response is parsed and validated against the provided schema class using Jackson.
     * @param prompt The input prompt.
     * @param schemaClass The Java class to parse and validate the response against.
     * @param <T> The type of the structured output.
     * @return A Promise resolving to the parsed and validated structured output.
     * @throws RuntimeException if the LLM response is invalid JSON or does not match the schema.
     */
    <T> Promise<T> generateStructured(String prompt, Class<T> schemaClass);

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
