package com.ghatana.virtualorg.llm;

import com.ghatana.virtualorg.v1.TaskProto;
import com.ghatana.virtualorg.v1.ToolProto;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Interface for LLM clients that provide reasoning and generation capabilities.
 *
 * <p><b>Purpose</b><br>
 * Provides abstraction for Large Language Model providers (OpenAI, Anthropic, local models)
 * to enable agent reasoning, decision-making, and tool calling capabilities.
 *
 * <p><b>Architecture Role</b><br>
 * Port interface in hexagonal architecture. Implementations (adapters) wrap
 * specific LLM providers while agents depend only on this abstraction.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * LLMClient llm = new OpenAILLMClient(apiKey, model);
 * Promise<LLMResponse> response = llm.reason(task, context, tools);
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Abstraction for Large Language Model provider integrations
 * @doc.layer product
 * @doc.pattern Port
 */
public interface LLMClient {

    /**
     * Generates a response for the given task and context.
     *
     * @param task    the task to reason about
     * @param context the contextual information
     * @param tools   the available tools
     * @return a promise of the LLM response
     */
    @NotNull
    Promise<LLMResponse> reason(
            @NotNull TaskProto task,
            @NotNull String context,
            @NotNull List<ToolProto> tools
    );

    /**
     * Generates a response for a given prompt.
     *
     * @param systemPrompt the system prompt
     * @param userPrompt   the user prompt
     * @param temperature  the sampling temperature
     * @param maxTokens    the maximum tokens
     * @return a promise of the response text
     */
    @NotNull
    Promise<String> generate(
            @NotNull String systemPrompt,
            @NotNull String userPrompt,
            float temperature,
            int maxTokens
    );

    /**
     * Generates embeddings for the given text.
     *
     * @param text the text to embed
     * @return a promise of the embedding vector
     */
    @NotNull
    Promise<float[]> embed(@NotNull String text);

    /**
     * Generates a completion with tool support.
     *
     * @param systemPrompt the system prompt
     * @param userPrompt   the user prompt
     * @param tools        available tools
     * @param temperature  sampling temperature
     * @param maxTokens    maximum tokens
     * @return a promise of the LLM response
     */
    @NotNull
    default Promise<LLMResponse> complete(
            @NotNull String systemPrompt,
            @NotNull String userPrompt,
            @NotNull List<ToolProto> tools,
            float temperature,
            int maxTokens) {
        // Default implementation delegates to generate
        return generate(systemPrompt, userPrompt, temperature, maxTokens)
                .map(content -> new LLMResponse(content, List.of(), 0, 1.0f));
    }

    /**
     * Gets the name of the LLM model.
     *
     * @return the model name
     */
    @NotNull
    String getModelName();

    /**
     * Gets the provider name (e.g., "openai", "anthropic", "local").
     *
     * @return the provider name
     */
    @NotNull
    String getProvider();
}
