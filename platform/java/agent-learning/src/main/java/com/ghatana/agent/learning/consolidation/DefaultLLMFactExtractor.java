package com.ghatana.agent.learning.consolidation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.agent.memory.model.Provenance;
import com.ghatana.agent.memory.model.episode.EnhancedEpisode;
import com.ghatana.agent.memory.model.fact.EnhancedFact;
import com.ghatana.ai.llm.ChatMessage;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.LLMGateway;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * LLM-backed implementation of {@link LLMFactExtractor}.
 *
 * <p>Submits a structured fact-extraction prompt to the configured {@link LLMGateway}
 * and parses the JSON response into a list of {@link EnhancedFact} SPO triples.
 *
 * <h3>Prompt strategy</h3>
 * <ol>
 *   <li>System message that constrains the LLM to JSON-only output.</li>
 *   <li>User message containing episode input, output, and metadata.</li>
 *   <li>Response is parsed as {@code {"facts": [{"subject", "predicate", "object",
 *       "confidence"}]}}.</li>
 * </ol>
 *
 * <p>The LLM call is wrapped in {@code Promise.ofBlocking} so the ActiveJ
 * event-loop thread is never blocked.
 *
 * @doc.type class
 * @doc.purpose Concrete LLM-based fact extractor (Phase 7)
 * @doc.layer agent-learning
 * @doc.pattern Strategy, Adapter
 */
public class DefaultLLMFactExtractor implements LLMFactExtractor {

    private static final Logger log = LoggerFactory.getLogger(DefaultLLMFactExtractor.class);

    /** Maximum tokens allowed in the LLM response to bound cost. */
    private static final int MAX_RESPONSE_TOKENS = 1_024;

    /** Shared virtual-thread executor for blocking LLM HTTP calls. */
    private static final Executor EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
        You are a knowledge-extraction engine. Given an agent interaction (input and output),
        extract a list of factual statements as subject-predicate-object (SPO) triples.
        Respond ONLY with valid JSON in this exact format — no markdown fences, no extra text:
        {
          "facts": [
            { "subject": "...", "predicate": "...", "object": "...", "confidence": 0.0-1.0 }
          ]
        }
        If no facts can be extracted, respond with {"facts":[]}.
        """;

    private final LLMGateway llmGateway;

    /**
     * Creates a fact extractor backed by the given LLM gateway.
     *
     * @param llmGateway the gateway used for completions
     */
    public DefaultLLMFactExtractor(@NotNull LLMGateway llmGateway) {
        this.llmGateway = llmGateway;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The LLM call is executed on a virtual-thread pool so the ActiveJ event-loop
     * is never blocked.
     */
    @Override
    public @NotNull Promise<List<EnhancedFact>> extractFacts(@NotNull EnhancedEpisode episode) {
        return Promise.ofBlocking(EXECUTOR, () -> doExtract(episode));
    }

    // ==================== Private Methods ====================

    private List<EnhancedFact> doExtract(EnhancedEpisode episode) {
        String userContent = buildUserPrompt(episode);

        CompletionRequest request = CompletionRequest.builder()
            .messages(List.of(
                ChatMessage.system(SYSTEM_PROMPT),
                ChatMessage.user(userContent)
            ))
            .maxTokens(MAX_RESPONSE_TOKENS)
            .temperature(0.2)   // low temperature — factual extraction
            .build();

        try {
            String llmText = llmGateway.complete(request).getResult().getText();
            return parseFacts(llmText, episode);
        } catch (Exception e) {
            log.warn("[LLMFactExtractor] extraction failed for agentId={} turnId={}: {}",
                episode.getAgentId(), episode.getTurnId(), e.getMessage());
            return List.of();
        }
    }

    private String buildUserPrompt(EnhancedEpisode episode) {
        return "Agent: " + episode.getAgentId() + "\n"
             + "Turn: " + episode.getTurnId() + "\n"
             + "INPUT:\n" + episode.getInput() + "\n\n"
             + "OUTPUT:\n" + episode.getOutput();
    }

    private List<EnhancedFact> parseFacts(String llmText, EnhancedEpisode episode) {
        try {
            // Strip any accidental markdown code fences
            String cleaned = llmText.trim();
            if (cleaned.startsWith("```")) {
                int start = cleaned.indexOf('\n') + 1;
                int end   = cleaned.lastIndexOf("```");
                cleaned   = (end > start) ? cleaned.substring(start, end).trim() : cleaned;
            }

            JsonNode root = MAPPER.readTree(cleaned);
            JsonNode factsNode = root.get("facts");
            if (factsNode == null || !factsNode.isArray()) {
                log.warn("[LLMFactExtractor] unexpected response shape: {}", llmText);
                return List.of();
            }

            List<EnhancedFact> facts = new ArrayList<>();
            for (JsonNode node : factsNode) {
                String subject    = textOf(node, "subject");
                String predicate  = textOf(node, "predicate");
                String object     = textOf(node, "object");
                double confidence = node.path("confidence").asDouble(0.5);

                if (subject.isBlank() || predicate.isBlank() || object.isBlank()) {
                    continue; // skip malformed triples
                }

                facts.add(EnhancedFact.builder()
                    .id(UUID.randomUUID().toString())
                    .agentId(episode.getAgentId())
                    .tenantId(episode.getTenantId())
                    .subject(subject)
                    .predicate(predicate)
                    .object(object)
                    .confidence(confidence)
                    .source("llm-extraction")
                    .provenance(Provenance.builder()
                        .source("llm-extraction")
                        .agentId(episode.getAgentId())
                        .parentItemId(episode.getId())
                        .confidenceSource(Provenance.ConfidenceSource.LLM_INFERENCE)
                        .build())
                    .build());
            }

            log.debug("[LLMFactExtractor] extracted {} facts for agentId={} turnId={}",
                facts.size(), episode.getAgentId(), episode.getTurnId());
            return List.copyOf(facts);

        } catch (Exception e) {
            log.warn("[LLMFactExtractor] JSON parse failed for agentId={}: {}",
                episode.getAgentId(), e.getMessage());
            return List.of();
        }
    }

    private static String textOf(JsonNode node, String field) {
        JsonNode f = node.get(field);
        return (f != null && f.isTextual()) ? f.asText().trim() : "";
    }
}
