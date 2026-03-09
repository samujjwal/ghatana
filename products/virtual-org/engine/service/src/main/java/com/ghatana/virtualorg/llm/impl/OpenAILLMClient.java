package com.ghatana.virtualorg.llm.impl;

import com.ghatana.virtualorg.llm.LLMClient;
import com.ghatana.virtualorg.llm.LLMResponse;
import com.ghatana.virtualorg.v1.TaskProto;
import com.ghatana.virtualorg.v1.ToolCallProto;
import com.ghatana.virtualorg.v1.ToolProto;
import com.google.protobuf.Timestamp;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * OpenAI LLM client implementation using LangChain4J.
 *
 * <p><b>Purpose</b><br>
 * Adapter implementing {@link LLMClient} for OpenAI's GPT models (GPT-4, GPT-3.5).
 * Wraps LangChain4J OpenAI bindings with ActiveJ Promise-based async execution.
 *
 * <p><b>Capabilities</b><br>
 * - GPT-4, GPT-4 Turbo, GPT-3.5 models
 * - Function calling (tool execution)
 * - Embeddings via text-embedding-ada-002
 * - Async execution via ActiveJ Eventloop
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * OpenAILLMClient client = new OpenAILLMClient(
 *     apiKey,
 *     "gpt-4-turbo",
 *     0.7f,
 *     2048,
 *     Duration.ofSeconds(60),
 *     eventloop
 * );
 * 
 * LLMResponse response = client.chat(task, tools).getResult();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose OpenAI adapter for LLM client port using LangChain4J
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class OpenAILLMClient implements LLMClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAILLMClient.class);

    private final ChatLanguageModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final Eventloop eventloop;
    private final String modelName;

    public OpenAILLMClient(
            @NotNull String apiKey,
            @NotNull String modelName,
            float temperature,
            int maxTokens,
            int timeoutSeconds,
            @NotNull Eventloop eventloop) {

        this.modelName = modelName;
        this.eventloop = eventloop;

        // Build OpenAI chat model
        this.chatModel = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature((double) temperature)
                .maxTokens(maxTokens)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .logRequests(log.isDebugEnabled())
                .logResponses(log.isDebugEnabled())
                .build();

        // Build OpenAI embedding model
        this.embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName("text-embedding-ada-002")
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();

        log.info("Initialized OpenAI LLM client: model={}", modelName);
    }

    @Override
    @NotNull
    public Promise<LLMResponse> reason(
            @NotNull TaskProto task,
            @NotNull String context,
            @NotNull List<ToolProto> tools) {

        log.debug("OpenAI reasoning: taskId={}", task.getTaskId());

        // Build system prompt
        String systemPrompt = buildSystemPrompt(task, tools);

        // Build user prompt with context
        String userPrompt = buildUserPrompt(task, context);

        // Create messages
        List<ChatMessage> messages = List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from(userPrompt)
        );

        // Call OpenAI (blocking operation)
        return Promise.ofBlocking(eventloop, () -> {
            try {
                long startTime = System.currentTimeMillis();
                var response = chatModel.generate(messages);
                long duration = System.currentTimeMillis() - startTime;

                log.debug("OpenAI response received: duration={}ms", duration);

                // Parse response
                String reasoning = response.content().text();
                List<ToolCallProto> toolCalls = extractToolCalls(response.content());

                // Estimate tokens (approximate)
                int tokensUsed = estimateTokens(systemPrompt + userPrompt + reasoning);

                return new LLMResponse(
                        reasoning,
                        toolCalls,
                        tokensUsed,
                        0.85f // Default confidence
                );
            } catch (Exception e) {
                log.error("OpenAI API call failed: taskId={}", task.getTaskId(), e);
                throw e; // Re-throw to propagate through Promise
            }
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
            log.debug("OpenAI generation: systemPromptLength={}, userPromptLength={}",
                    systemPrompt.length(), userPrompt.length());

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
        return Promise.ofBlocking(eventloop, () -> {
            log.debug("OpenAI embedding: textLength={}", text.length());

            var response = embeddingModel.embed(text);
            var vectorList = response.content().vectorAsList();
            
            float[] result = new float[vectorList.size()];
            for (int i = 0; i < vectorList.size(); i++) {
                result[i] = vectorList.get(i).floatValue();
            }
            return result;
        });
    }

    @Override
    @NotNull
    public String getModelName() {
        return modelName;
    }

    @Override
    @NotNull
    public String getProvider() {
        return "openai";
    }

    // =============================
    // Helper methods
    // =============================

    private String buildSystemPrompt(TaskProto task, List<ToolProto> tools) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are a software engineering agent with the following capabilities:\n\n");

        // Add tool descriptions
        if (!tools.isEmpty()) {
            prompt.append("Available tools:\n");
            for (ToolProto tool : tools) {
                prompt.append("- ").append(tool.getName()).append(": ")
                        .append(tool.getDescription()).append("\n");
            }
            prompt.append("\n");
        }

        prompt.append("Your task:\n");
        prompt.append("Type: ").append(task.getType().name()).append("\n");
        prompt.append("Priority: ").append(task.getPriority().name()).append("\n");
        prompt.append("\n");

        prompt.append("Instructions:\n");
        prompt.append("1. Analyze the task carefully\n");
        prompt.append("2. Break down the work into steps\n");
        prompt.append("3. Use available tools when needed\n");
        prompt.append("4. Provide clear reasoning for your approach\n");
        prompt.append("5. Return structured results\n");

        return prompt.toString();
    }

    private String buildUserPrompt(TaskProto task, String context) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Task: ").append(task.getTitle()).append("\n\n");
        prompt.append("Description:\n").append(task.getDescription()).append("\n\n");

        if (!context.isEmpty()) {
            prompt.append("Context:\n").append(context).append("\n\n");
        }

        if (!task.getAcceptanceCriteriaList().isEmpty()) {
            prompt.append("Acceptance Criteria:\n");
            for (var criterion : task.getAcceptanceCriteriaList()) {
                prompt.append("- ").append(criterion.getCriterion()).append("\n");
            }
            prompt.append("\n");
        }

        prompt.append("Please provide a detailed plan and execute the task.");

        return prompt.toString();
    }

    // Stub for tool calls extraction - to be implemented with LangChain4j function calling
    @SuppressWarnings("unused")
    private List<ToolCallProto> extractToolCalls(Object content) {
        // OpenAI function calling integration would go here
        // For now, return empty list
        return List.of();
    }

    private int estimateTokens(String text) {
        // Rough estimate: ~4 characters per token
        return text.length() / 4;
    }

    private Timestamp currentTimestamp() {
        Instant now = Instant.now();
        return Timestamp.newBuilder()
                .setSeconds(now.getEpochSecond())
                .setNanos(now.getNano())
                .build();
    }
}
