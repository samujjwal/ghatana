package com.ghatana.agent.learning.consolidation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.agent.memory.model.Provenance;
import com.ghatana.agent.memory.model.episode.EnhancedEpisode;
import com.ghatana.agent.memory.model.fact.EnhancedFact;
import com.ghatana.ai.llm.ChatMessage;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

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

    /** P1-8: Timeout for LLM calls to prevent indefinite blocking. */
    private static final Duration LLM_TIMEOUT = Duration.ofSeconds(30);

    /** Shared virtual-thread executor for blocking LLM HTTP calls. */
    private static final Executor EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LLMGateway llmGateway;
    /** P2-16: Metrics collector for token count tracking and learning quality observability. */
    private final MetricsCollector metricsCollector;

    private static final String METRIC_LLM_TIMEOUT = "llm.fact_extractor.timeout";
    private static final String METRIC_LLM_FAILURE = "llm.fact_extractor.failure";
    private static final String METRIC_PARSE_FAILURE = "llm.fact_extractor.parse_failure";
    private static final String METRIC_EMPTY_RESPONSE = "llm.fact_extractor.empty_response";
    private static final String METRIC_FACTS_EXTRACTED = "llm.fact_extractor.facts_extracted";

    private static final String SYSTEM_PROMPT = """
        You are a knowledge-extraction engine. Given an agent interaction (input and output),
        extract a list of factual statements as subject-predicate-object (SPO) triples.
        Respond ONLY with valid JSON in this exact format - no markdown fences, no extra text:
        {
          "facts": [
            { "subject": "...", "predicate": "...", "object": "...", "confidence": 0.0-1.0 }
          ]
        }
        If no facts can be extracted, respond with {"facts":[]}.
        """;

    /**
     * Creates a fact extractor backed by the given LLM gateway.
     *
     * @param llmGateway the gateway used for completions
     */
    public DefaultLLMFactExtractor(@NotNull LLMGateway llmGateway) {
        this(llmGateway, null);
    }

    /**
     * Creates a fact extractor backed by the given LLM gateway with metrics collection.
     *
     * @param llmGateway the gateway used for completions
     * @param metricsCollector optional metrics collector for token tracking
     */
    public DefaultLLMFactExtractor(@NotNull LLMGateway llmGateway,
                                   @Nullable MetricsCollector metricsCollector) {
        this.llmGateway = llmGateway;
        this.metricsCollector = metricsCollector;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The LLM call runs on the ActiveJ eventloop via {@code llmGateway.complete()}.
     * JSON parsing runs on a virtual-thread pool via {@code Promise.ofBlocking}, keeping
     * the eventloop free of CPU-bound work. Error recovery returns an empty list so that
     * a failed extraction never blocks the learning pipeline.
     */
    @Override
    public @NotNull Promise<List<EnhancedFact>> extractFacts(@NotNull EnhancedEpisode episode) {
        String userPrompt = buildUserPrompt(episode);
        
        CompletionRequest request = CompletionRequest.builder()
            .messages(List.of(
                ChatMessage.system(SYSTEM_PROMPT),
                ChatMessage.user(userPrompt)
            ))
            .maxTokens(MAX_RESPONSE_TOKENS)
            .temperature(0.2)
            .build();

        // Capture the MDC context from the event-loop thread so that correlationId and traceId
        // (set by RequestObservationFilter) are propagated into the virtual-thread pool.
        // Group 9 / DC-OBS-009: unified trace propagation across async LLM workflows.
        final Map<String, String> callerMdc = MDC.getCopyOfContextMap() != null
            ? new HashMap<>(MDC.getCopyOfContextMap())
            : Map.of();

        Promise<? extends com.ghatana.ai.llm.CompletionResult> completionPromise;
        try {
            completionPromise = llmGateway.complete(request);
        } catch (Exception e) {
            return recoverExtractionFailure(e, episode);
        }

        // Chain: (1) LLM call on eventloop -> (2) JSON parsing on virtual thread
        // Any failure in either step is caught by the error-recovery branch.
        // P1-8: Add timeout to prevent indefinite blocking on LLM calls
        // P2-16: Add token count logging and metrics
        return Promise.ofFuture(completionPromise
            .toCompletableFuture()
            .orTimeout(LLM_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS))
            .then(
                response -> {
                    // P2-16: Log and emit token count metrics
                    log.info("[LLMFactExtractor] LLM call completed for agentId={} turnId={} " +
                            "tokensUsed={} promptTokens={} completionTokens={} model={}",
                        episode.getAgentId(), episode.getTurnId(),
                        response.getTokensUsed(), response.getPromptTokens(),
                        response.getCompletionTokens(), response.getModelUsed());
                    
                    if (metricsCollector != null) {
                        metricsCollector.increment("llm.fact_extractor.tokens.total",
                            response.getTokensUsed(), java.util.Map.of());
                        metricsCollector.increment("llm.fact_extractor.tokens.prompt",
                            response.getPromptTokens(), java.util.Map.of());
                        metricsCollector.increment("llm.fact_extractor.tokens.completion",
                            response.getCompletionTokens(), java.util.Map.of());
                    }

                    // Restore caller MDC on the blocking thread so trace context is preserved.
                    return Promise.ofBlocking(EXECUTOR, () -> {
                        if (!callerMdc.isEmpty()) {
                            MDC.setContextMap(callerMdc);
                        }
                        try {
                            return parseFacts(response.getText(), episode);
                        } finally {
                            MDC.clear();
                        }
                    });
                },
                e -> recoverExtractionFailure(e, episode)
            );
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

            if (facts.isEmpty()) {
                log.warn("[LLMFactExtractor] LLM returned 0 usable facts for agentId={} turnId={} "
                         + "- response may contain malformed triples",
                    episode.getAgentId(), episode.getTurnId());
                if (metricsCollector != null) {
                    metricsCollector.increment(METRIC_EMPTY_RESPONSE, 1,
                        metricTags(episode));
                }
            } else {
                if (metricsCollector != null) {
                    metricsCollector.increment(METRIC_FACTS_EXTRACTED, facts.size(),
                        metricTags(episode));
                }
            }
            log.debug("[LLMFactExtractor] extracted {} facts for agentId={} turnId={}",
                facts.size(), episode.getAgentId(), episode.getTurnId());
            return List.copyOf(facts);

        } catch (Exception e) {
            log.error("[LLMFactExtractor] JSON parse failed for agentId={} turnId={}: {} "
                      + "- learning quality degraded",
                episode.getAgentId(), episode.getTurnId(), e.getMessage());
            if (metricsCollector != null) {
                metricsCollector.increment(METRIC_PARSE_FAILURE, 1,
                    metricTags(episode));
            }
            return List.of();
        }
    }

    private Promise<List<EnhancedFact>> recoverExtractionFailure(Throwable throwable, EnhancedEpisode episode) {
        Throwable failure = throwable instanceof CompletionException && throwable.getCause() != null
            ? throwable.getCause()
            : throwable;
        if (failure instanceof TimeoutException) {
            log.error("[LLMFactExtractor] extraction timed out after {}ms for agentId={} turnId={} "
                      + "- learning quality degraded, no facts will be consolidated for this episode",
                LLM_TIMEOUT.toMillis(), episode.getAgentId(), episode.getTurnId());
            if (metricsCollector != null) {
                metricsCollector.increment(METRIC_LLM_TIMEOUT, 1, metricTags(episode));
            }
        } else {
            log.error("[LLMFactExtractor] extraction failed for agentId={} turnId={}: {} "
                      + "- learning quality degraded, no facts will be consolidated for this episode",
                episode.getAgentId(), episode.getTurnId(), failure.getMessage());
            if (metricsCollector != null) {
                metricsCollector.increment(METRIC_LLM_FAILURE, 1, metricTags(episode));
            }
        }
        return Promise.of(List.of());
    }

    private static Map<String, String> metricTags(EnhancedEpisode episode) {
        return Map.of(
            "agentId", safeMetricTag(episode.getAgentId()),
            "tenantId", safeMetricTag(episode.getTenantId())
        );
    }

    private static String safeMetricTag(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private static String textOf(JsonNode node, String field) {
        JsonNode f = node.get(field);
        return (f != null && f.isTextual()) ? f.asText().trim() : "";
    }
}
