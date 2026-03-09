/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Task 5.4 — LLM Agent with LangChain4j integration.
 */
package com.ghatana.agent.llm;

import com.ghatana.agent.*;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.runtime.AbstractTypedAgent;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM-backed agent that uses LangChain4j for natural language processing tasks.
 *
 * <h2>Capabilities</h2>
 * <ul>
 *   <li><b>Event Classification</b>: Classify events by category/severity using LLM</li>
 *   <li><b>Summarization</b>: Generate natural language summaries of event data</li>
 *   <li><b>Anomaly Explanation</b>: Explain why patterns are anomalous</li>
 *   <li><b>Dynamic Rule Generation</b>: Generate detection rules from natural language</li>
 * </ul>
 *
 * <h2>Architecture</h2>
 * <ul>
 *   <li>Type: {@link AgentType#PROBABILISTIC} with subtype "LLM"</li>
 *   <li>Prompt templates configured via {@link LLMAgentConfig}</li>
 *   <li>Response caching with configurable TTL</li>
 *   <li>Token budget management per request</li>
 *   <li>Fallback to deterministic response on LLM failure/timeout</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * ChatLanguageModel model = OpenAiChatModel.builder()
 *     .apiKey("sk-...")
 *     .modelName("gpt-4o-mini")
 *     .build();
 *
 * LLMAgent agent = new LLMAgent(
 *     "event-classifier",
 *     model,
 *     LLMAgentConfig.builder()
 *         .systemPrompt("You are an event classifier. Classify the following event...")
 *         .maxTokens(500)
 *         .cacheTtlSeconds(300)
 *         .build()
 * );
 *
 * agent.initialize(AgentConfig.builder().agentId("event-classifier")
 *     .type(AgentType.PROBABILISTIC).build());
 * AgentResult<String> result = agent.process(ctx, "Server CPU at 98% for 5 minutes").getResult();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Agent powered by large language model inference
 * @doc.layer platform
 * @doc.pattern Service
 * @doc.gaa.lifecycle reason
 */
public class LLMAgent extends AbstractTypedAgent<String, String> {

    private static final Logger log = LoggerFactory.getLogger(LLMAgent.class);

    private final String agentId;
    private final ChatLanguageModel chatModel;
    private final LLMAgentConfig llmConfig;
    private final ConcurrentHashMap<String, CacheEntry> responseCache;

    /**
     * Creates an LLM agent.
     *
     * @param agentId unique agent identifier
     * @param chatModel LangChain4j chat model (OpenAI, Anthropic, etc.)
     * @param llmConfig LLM-specific configuration
     */
    public LLMAgent(String agentId, ChatLanguageModel chatModel, LLMAgentConfig llmConfig) {
        this.agentId = Objects.requireNonNull(agentId, "agentId required");
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel required");
        this.llmConfig = Objects.requireNonNull(llmConfig, "llmConfig required");
        this.responseCache = new ConcurrentHashMap<>();
    }

    @Override
    public AgentDescriptor descriptor() {
        return AgentDescriptor.builder()
                .agentId(agentId)
                .name("LLM Agent: " + agentId)
                .version("1.0.0")
                .type(AgentType.PROBABILISTIC)
                .subtype("LLM")
                .determinism(DeterminismGuarantee.NONE)
                .failureMode(FailureMode.FALLBACK)
                .latencySla(Duration.ofSeconds(llmConfig.getTimeoutSeconds()))
                .capabilities(Set.of("nlp", "classification", "summarization",
                        "anomaly-explanation", "rule-generation"))
                .build();
    }

    @Override
    protected Promise<AgentResult<String>> doProcess(AgentContext ctx, String input) {
        Instant start = Instant.now();

        // Check cache first
        if (llmConfig.isCacheEnabled()) {
            String cacheKey = computeCacheKey(input);
            CacheEntry cached = responseCache.get(cacheKey);
            if (cached != null && !cached.isExpired(llmConfig.getCacheTtlSeconds())) {
                log.debug("Cache hit for agent={}, key={}", agentId, cacheKey);
                Duration elapsed = Duration.between(start, Instant.now());
                return Promise.of(AgentResult.successWithConfidence(
                        cached.response(), cached.confidence(), agentId, elapsed,
                        "Cached response (original confidence: " + cached.confidence() + ")"));
            }
        }

        try {
            // Build prompt with token budget
            String systemPrompt = llmConfig.getSystemPrompt();
            String userPrompt = buildUserPrompt(input);

            // Validate token budget
            int estimatedInputTokens = estimateTokens(systemPrompt) + estimateTokens(userPrompt);
            if (estimatedInputTokens > llmConfig.getMaxTokens()) {
                userPrompt = truncateToTokenBudget(userPrompt,
                        llmConfig.getMaxTokens() - estimateTokens(systemPrompt));
                log.warn("Input truncated to fit token budget: agent={}, estimated={}, max={}",
                        agentId, estimatedInputTokens, llmConfig.getMaxTokens());
            }

            // Call LLM
            ChatRequest request = ChatRequest.builder()
                    .messages(List.of(
                            new SystemMessage(systemPrompt),
                            new UserMessage(userPrompt)
                    ))
                    .build();

            ChatResponse response = chatModel.chat(request);
            AiMessage aiMessage = response.aiMessage();
            String responseText = aiMessage.text();
            double confidence = computeConfidence(response);
            Duration elapsed = Duration.between(start, Instant.now());

            // Record token usage
            TokenUsage tokenUsage = response.tokenUsage();
            if (tokenUsage != null) {
                log.info("LLM token usage: agent={}, input={}, output={}, total={}",
                        agentId,
                        tokenUsage.inputTokenCount(),
                        tokenUsage.outputTokenCount(),
                        tokenUsage.totalTokenCount());
            }

            // Cache response
            if (llmConfig.isCacheEnabled()) {
                responseCache.put(computeCacheKey(input),
                        new CacheEntry(responseText, confidence, Instant.now()));
                evictExpiredEntries();
            }

            return Promise.of(AgentResult.successWithConfidence(
                    responseText, confidence, agentId, elapsed,
                    "LLM response from " + (llmConfig.getModelName() != null ?
                            llmConfig.getModelName() : "default model")));

        } catch (Exception e) {
            Duration elapsed = Duration.between(start, Instant.now());
            log.warn("LLM call failed for agent={}, falling back to deterministic response: {}",
                    agentId, e.getMessage());

            // Fallback to deterministic response
            String fallbackResponse = llmConfig.getFallbackResponse();
            if (fallbackResponse != null && !fallbackResponse.isBlank()) {
                return Promise.of(AgentResult.successWithConfidence(
                        fallbackResponse, 0.1, agentId, elapsed,
                        "Fallback response due to LLM failure: " + e.getMessage()));
            }

            return Promise.of(AgentResult.failure(e, agentId, elapsed));
        }
    }

    // ─────────────────── Prompt Management ───────────────────

    private String buildUserPrompt(String input) {
        String template = llmConfig.getUserPromptTemplate();
        if (template != null && template.contains("{{input}}")) {
            return template.replace("{{input}}", input);
        }
        return input;
    }

    // ─────────────────── Token Budget ───────────────────

    /**
     * Estimates token count using the 4 chars/token heuristic.
     */
    int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (int) Math.ceil(text.length() / 4.0);
    }

    private String truncateToTokenBudget(String text, int maxTokens) {
        int maxChars = maxTokens * 4;
        if (text.length() <= maxChars) return text;
        return text.substring(0, maxChars) + "... [truncated]";
    }

    // ─────────────────── Confidence ───────────────────

    private double computeConfidence(ChatResponse response) {
        // Base confidence for LLM responses
        double confidence = llmConfig.getBaseConfidence();

        // Adjust if response was cut short (finish_reason != "stop")
        AiMessage msg = response.aiMessage();
        if (msg != null && msg.text() != null && msg.text().endsWith("...")) {
            confidence *= 0.8;
        }

        return Math.min(1.0, Math.max(0.0, confidence));
    }

    // ─────────────────── Cache ───────────────────

    private String computeCacheKey(String input) {
        return llmConfig.getSystemPrompt().hashCode() + ":" + input.hashCode();
    }

    private void evictExpiredEntries() {
        int ttl = llmConfig.getCacheTtlSeconds();
        responseCache.entrySet().removeIf(entry -> entry.getValue().isExpired(ttl));
    }

    /**
     * Returns the current cache size.
     */
    public int cacheSize() {
        return responseCache.size();
    }

    /**
     * Clears the response cache.
     */
    public void clearCache() {
        responseCache.clear();
    }

    // ─────────────────── Cache Entry ───────────────────

    record CacheEntry(String response, double confidence, Instant cachedAt) {
        boolean isExpired(int ttlSeconds) {
            return Instant.now().isAfter(cachedAt.plusSeconds(ttlSeconds));
        }
    }
}
