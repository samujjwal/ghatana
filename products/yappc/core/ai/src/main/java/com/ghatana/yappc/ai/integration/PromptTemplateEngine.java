package com.ghatana.yappc.ai.integration;

import java.util.Map;

/**
 * Functional interface for building prompts from a typed request and context map.
 *
 * <p>Default implementation serializes request and context to a JSON-like string.
 * For production usage with template variable substitution, prefer delegating to
 * {@link com.ghatana.ai.prompts.PromptTemplateManager} from the platform ai-integration module.
 *
 * @doc.type interface
 * @doc.purpose Defines the contract for prompt template engines
 * @doc.layer core
 * @doc.pattern Strategy
 */
@FunctionalInterface
public interface PromptTemplateEngine {

    /**
     * Builds a prompt string from the given request and execution context.
     *
     * @param request the typed request object
     * @param context key-value execution context with additional parameters
     * @param <Req> the request type
     * @return a formatted prompt string ready for LLM consumption
     */
    <Req> String buildPrompt(Req request, Map<String, Object> context);

    /**
     * Returns a default implementation that serializes request and context to string.
     */
    static PromptTemplateEngine defaultEngine() {
        return new PromptTemplateEngine() {
            @Override
            public <Req> String buildPrompt(Req request, Map<String, Object> context) {
                StringBuilder sb = new StringBuilder();
                sb.append("Request: ").append(request != null ? request.toString() : "null");
                if (context != null && !context.isEmpty()) {
                    sb.append("\nContext: ").append(context);
                }
                return sb.toString();
            }
        };
    }
}
