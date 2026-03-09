/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Task 5.4 — Configuration for LLMAgent.
 */
package com.ghatana.agent.llm;

import lombok.Builder;
import lombok.Value;

/**
 * Configuration for {@link LLMAgent}.
 *
 * <p>Defines prompt templates, token budgets, caching, and fallback behavior.</p>
 *
 * @doc.type class
 * @doc.purpose Configuration for LLM agent model selection and prompting
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
@Value
@Builder(toBuilder = true)
public class LLMAgentConfig {

    /**
     * System prompt providing the LLM with its role and instructions.
     */
    @Builder.Default
    String systemPrompt = "You are an AI assistant analyzing system events.";

    /**
     * User prompt template. Use {@code {{input}}} as the placeholder for user input.
     */
    String userPromptTemplate;

    /**
     * Maximum tokens for the combined prompt + response.
     */
    @Builder.Default
    int maxTokens = 2048;

    /**
     * LLM model name (for logging/metrics).
     */
    String modelName;

    /**
     * Timeout for LLM calls in seconds.
     */
    @Builder.Default
    int timeoutSeconds = 30;

    /**
     * Base confidence for LLM responses (0.0 to 1.0).
     */
    @Builder.Default
    double baseConfidence = 0.75;

    /**
     * Whether response caching is enabled.
     */
    @Builder.Default
    boolean cacheEnabled = true;

    /**
     * Cache TTL in seconds.
     */
    @Builder.Default
    int cacheTtlSeconds = 300;

    /**
     * Fallback response when LLM fails or times out.
     * If null or blank, the failure propagates.
     */
    String fallbackResponse;

    /**
     * Temperature for LLM (0.0 = deterministic, 1.0 = creative).
     */
    @Builder.Default
    double temperature = 0.3;
}
