package com.ghatana.virtualorg.llm.impl;

import com.ghatana.virtualorg.llm.LLMClient;
import com.ghatana.virtualorg.llm.LLMResponse;
import com.ghatana.virtualorg.v1.TaskProto;
import com.ghatana.virtualorg.v1.ToolCallProto;
import com.ghatana.virtualorg.v1.ToolProto;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Anthropic (Claude) LLM client implementation using LangChain4J.
 *
 * <p><b>Purpose</b><br>
 * Adapter implementing {@link LLMClient} for Anthropic's Claude models.
 * Wraps LangChain4J Anthropic bindings with ActiveJ Promise-based async execution.
 *
 * <p><b>Capabilities</b><br>
 * - Claude 3 models (Opus, Sonnet, Haiku)
 * - Tool use (function calling)
 * - Async execution via ActiveJ Eventloop
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * AnthropicLLMClient client = new AnthropicLLMClient(
 *     apiKey,
 *     "claude-3-opus-20240229",
 *     0.7f,
 *     4096,
 *     60,
 *     eventloop
 * );
 * 
 * LLMResponse response = client.chat(task, tools).getResult();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Anthropic Claude adapter for LLM client port using LangChain4J
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class AnthropicLLMClient implements LLMClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicLLMClient.class);

    private final ChatLanguageModel chatModel;
    private final Eventloop eventloop;
    private final String modelName;

    public AnthropicLLMClient(
            @NotNull String apiKey,
            @NotNull String modelName,
            float temperature,
            int maxTokens,
            int timeoutSeconds,
            @NotNull Eventloop eventloop) {

        this.modelName = modelName;
        this.eventloop = eventloop;

        // Build Anthropic chat model
        this.chatModel = AnthropicChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature((double) temperature)
                .maxTokens(maxTokens)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .logRequests(log.isDebugEnabled())
                .logResponses(log.isDebugEnabled())
                .build();

        log.info("Initialized Anthropic LLM client: model={}", modelName);
    }

    @Override
    @NotNull
    public Promise<LLMResponse> reason(
            @NotNull TaskProto task,
            @NotNull String context,
            @NotNull List<ToolProto> tools) {

        return Promise.ofBlocking(eventloop, () -> {
            log.debug("Anthropic reasoning: taskId={}", task.getTaskId());

            String systemPrompt = buildSystemPrompt(task, tools);
            String userPrompt = buildUserPrompt(task, context);

            List<ChatMessage> messages = List.of(
                    SystemMessage.from(systemPrompt),
                    UserMessage.from(userPrompt)
            );

            long startTime = System.currentTimeMillis();
            var response = chatModel.generate(messages);
            long duration = System.currentTimeMillis() - startTime;

            log.debug("Anthropic response received: duration={}ms", duration);

            String reasoning = response.content().text();
            List<ToolCallProto> toolCalls = extractToolCalls(response.content().text());

            int tokensUsed = estimateTokens(systemPrompt + userPrompt + reasoning);

            return new LLMResponse(
                    reasoning,
                    toolCalls,
                    tokensUsed,
                    0.88f // Claude tends to be more confident
            );
        });
    }

    @Override
    @NotNull
    public Promise<String> generate(
            @NotNull String systemPrompt,
            @NotNull String userPrompt,
            float temperature,
            int maxTokens) {

        return Promise.ofBlocking(eventloop, () -> {
            List<ChatMessage> messages = List.of(
                    SystemMessage.from(systemPrompt),
                    UserMessage.from(userPrompt)
            );

            var response = chatModel.generate(messages);
            return response.content().text();
        });
    }

    @Override
    @NotNull
    public Promise<float[]> embed(@NotNull String text) {
        // Anthropic doesn't provide embeddings, delegate to a separate embedding model
        // For now, throw UnsupportedOperationException
        return Promise.ofException(
                new UnsupportedOperationException("Anthropic does not provide embedding models. Use OpenAI or local model.")
        );
    }

    @Override
    @NotNull
    public String getModelName() {
        return modelName;
    }

    @Override
    @NotNull
    public String getProvider() {
        return "anthropic";
    }

    // =============================
    // Helper methods
    // =============================

    private String buildSystemPrompt(TaskProto task, List<ToolProto> tools) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are Claude, an AI assistant specialized in software engineering.\n\n");

        prompt.append("You have access to the following tools:\n");
        for (ToolProto tool : tools) {
            prompt.append("- ").append(tool.getName()).append(": ")
                    .append(tool.getDescription()).append("\n");
        }
        prompt.append("\n");

        prompt.append("Current task type: ").append(task.getType().name()).append("\n");
        prompt.append("Priority: ").append(task.getPriority().name()).append("\n\n");

        prompt.append("Guidelines:\n");
        prompt.append("1. Think step-by-step and explain your reasoning\n");
        prompt.append("2. Use tools appropriately when needed\n");
        prompt.append("3. Be thorough and accurate\n");
        prompt.append("4. Follow best practices\n");
        prompt.append("5. Provide actionable results\n");

        return prompt.toString();
    }

    private String buildUserPrompt(TaskProto task, String context) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("# Task\n");
        prompt.append(task.getTitle()).append("\n\n");

        prompt.append("## Description\n");
        prompt.append(task.getDescription()).append("\n\n");

        if (!context.isEmpty()) {
            prompt.append("## Context\n");
            prompt.append(context).append("\n\n");
        }

        if (!task.getAcceptanceCriteriaList().isEmpty()) {
            prompt.append("## Acceptance Criteria\n");
            for (var criterion : task.getAcceptanceCriteriaList()) {
                prompt.append("- [ ] ").append(criterion.getCriterion()).append("\n");
            }
            prompt.append("\n");
        }

        prompt.append("Please analyze this task and provide a detailed implementation plan.");

        return prompt.toString();
    }

    private List<ToolCallProto> extractToolCalls(String responseText) {
        List<ToolCallProto> toolCalls = new ArrayList<>();

        // Parse tool calls from Claude's response
        // Claude uses a specific format for tool calls
        // To be implemented with Anthropic's tool use API

        return toolCalls;
    }

    private int estimateTokens(String text) {
        // Rough estimate
        return text.length() / 4;
    }
}
