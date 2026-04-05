/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.ai;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Provider interface for LLM services.
 *
 * @doc.type interface
 * @doc.purpose LLM provider abstraction
 * @doc.layer product
 * @doc.pattern Provider, Adapter
 */
public interface LLMProvider {

    /**
     * Send completion request to LLM.
     *
     * @param request completion request
     * @return promise of completion response
     */
    Promise<CompletionResponse> complete(CompletionRequest request);

    /**
     * Send chat completion request.
     *
     * @param request chat request
     * @return promise of chat response
     */
    Promise<ChatResponse> chat(ChatRequest request);

    /**
     * Get available models.
     *
     * @return promise of model list
     */
    Promise<List<ModelInfo>> getModels();

    /**
     * Get provider status.
     *
     * @return promise of status
     */
    Promise<ProviderStatus> getStatus();

    /**
     * Get provider name.
     *
     * @return provider identifier
     */
    String getName();

    /**
     * Completion request.
     */
    record CompletionRequest(
        String model,
        String prompt,
        Double temperature,
        Integer maxTokens,
        List<String> stopSequences,
        Map<String, Object> parameters
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String model;
            private String prompt;
            private Double temperature = 0.7;
            private Integer maxTokens = 1000;
            private List<String> stopSequences = List.of();
            private Map<String, Object> parameters = Map.of();

            public Builder model(String model) { this.model = model; return this; }
            public Builder prompt(String prompt) { this.prompt = prompt; return this; }
            public Builder temperature(double temp) { this.temperature = temp; return this; }
            public Builder maxTokens(int max) { this.maxTokens = max; return this; }
            public Builder stopSequences(List<String> stops) { this.stopSequences = stops; return this; }
            public Builder parameters(Map<String, Object> params) { this.parameters = params; return this; }

            public CompletionRequest build() {
                return new CompletionRequest(model, prompt, temperature, maxTokens, stopSequences, parameters);
            }
        }
    }

    /**
     * Completion response.
     */
    record CompletionResponse(
        String id,
        String text,
        int tokensUsed,
        int promptTokens,
        int completionTokens,
        String finishReason,
        long latencyMs,
        String model
    ) {}

    /**
     * Chat request.
     */
    record ChatRequest(
        String model,
        List<ChatMessage> messages,
        Double temperature,
        Integer maxTokens,
        Map<String, Object> parameters
    ) {}

    /**
     * Chat message.
     */
    record ChatMessage(
        String role,
        String content
    ) {}

    /**
     * Chat response.
     */
    record ChatResponse(
        String id,
        List<ChatMessage> messages,
        int tokensUsed,
        String finishReason,
        long latencyMs,
        String model
    ) {}

    /**
     * Model information.
     */
    record ModelInfo(
        String id,
        String name,
        String provider,
        int maxTokens,
        List<String> capabilities,
        boolean available
    ) {}

    /**
     * Provider status.
     */
    record ProviderStatus(
        String provider,
        boolean healthy,
        String message,
        long requestsInFlight,
        double averageLatencyMs,
        Instant lastChecked
    ) {}
}
