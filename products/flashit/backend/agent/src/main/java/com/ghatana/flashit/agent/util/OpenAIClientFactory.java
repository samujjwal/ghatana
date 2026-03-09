package com.ghatana.flashit.agent.util;

import com.ghatana.flashit.agent.config.AgentConfig;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;

/**
 * Factory for creating a shared OpenAI client instance.
 *
 * <p>Consolidates OpenAI client creation that was previously duplicated
 * across 7 services, each constructing its own connection pool. A single
 * shared client reuses HTTP connections and reduces resource waste.
 *
 * @doc.type class
 * @doc.purpose Creates and provides a shared OpenAI HTTP client
 * @doc.layer product
 * @doc.pattern Factory
 */
public final class OpenAIClientFactory {

    private OpenAIClientFactory() {
    }

    /**
     * Creates a new OpenAI client configured from the given AgentConfig.
     *
     * @param config agent configuration with API key
     * @return a configured OpenAI client
     * @throws IllegalStateException if OpenAI API key is not configured
     */
    public static OpenAIClient create(AgentConfig config) {
        if (!config.isOpenAiConfigured()) {
            throw new IllegalStateException(
                    "OPENAI_API_KEY is not set. Cannot create OpenAI client.");
        }
        return OpenAIOkHttpClient.builder()
                .apiKey(config.getOpenAiApiKey())
                .build();
    }
}
