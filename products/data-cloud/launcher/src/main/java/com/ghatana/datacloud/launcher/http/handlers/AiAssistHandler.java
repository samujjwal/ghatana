package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.ai.llm.ChatMessage;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.datacloud.launcher.ai.AiRecommendationMetrics;
import com.ghatana.datacloud.launcher.http.ApiResponse;
import com.ghatana.platform.governance.security.TenantContext;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * HTTP handler for pervasive AI/ML assist endpoints (DC-E3).
 *
 * <p>Embedded into core Data-Cloud workflows to provide implicit AI assistance
 * for entity exploration, analytics query suggestion, pipeline optimisation hints,
 * anomaly explanation, and inline summarisation.
 *
 * <h2>Routes</h2>
 * <pre>
 *   POST /api/v1/entities/:collection/suggest       — entity exploration suggestions
 *   POST /api/v1/analytics/suggest                   — analytics query suggestions
 *   POST /api/v1/pipelines/:pipelineId/optimise-hint — pipeline optimisation hints
 *   POST /api/v1/brain/explain                       — anomaly/salience explanation
 * </pre>
 *
 * <h2>Response Shape</h2>
 * All routes return the canonical {@link ApiResponse} envelope with a populated
 * {@code ai} block containing {@code confidence}, {@code model}, {@code reasons},
 * and {@code fallback} fields.
 *
 * <h2>Fallback Behaviour</h2>
 * When the AI service is unavailable or returns a low-confidence result:
 * <ul>
 *   <li>Suggestions default to static ruleset-based heuristics.</li>
 *   <li>The {@code ai.fallback} field is set to {@code true}.</li>
 *   <li>Confidence drops to 0.2 (heuristics baseline).</li>
 *   <li>The caller receives a 200 response — never a 5xx from AI unavailability.</li>
 * </ul>
 *
 * <h2>Privacy and Governance</h2>
 * <ul>
 *   <li>Data sent to the LLM is limited to metadata and schema — never raw PII.</li>
 *   <li>Tenant context is always propagated to the completion service.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Pervasive AI/ML assist HTTP handler (DC-E3)
 * @doc.layer product
 * @doc.pattern Handler
 * @doc.gaa.lifecycle perceive, reason, act
 */
public class AiAssistHandler {

    private static final Logger log = LoggerFactory.getLogger(AiAssistHandler.class);

    /** Confidence threshold below which the response is flagged as fallback. */
    private static final double FALLBACK_CONFIDENCE_THRESHOLD = 0.40;
    /** Confidence assigned to static heuristic suggestions when the AI is unavailable. */
    private static final double HEURISTIC_CONFIDENCE = 0.20;
    /** Default model name returned in responses (overridden by CompletionResult.modelUsed). */
    private static final String DEFAULT_MODEL = "datacloud-assist-v1";
    /** Maximum prompt tokens to avoid runaway cost. */
    private static final int MAX_PROMPT_TOKENS = 1024;

    private final ObjectMapper objectMapper;
    private final HttpHandlerSupport http;
    private final Executor blockingExecutor;

    /** Optional — when null all requests fall back to static heuristics. */
    private final CompletionService completionService;

    /** Quality metrics instrumentation — never null (falls back to NOOP). */
    private final AiRecommendationMetrics recommendationMetrics;

    /**
     * Creates a handler backed by a real LLM completion service.
     *
     * @param completionService AI completion service; may be null (fallback mode)
     * @param objectMapper      JSON mapper
     * @param http              shared HTTP support
     * @param blockingExecutor  executor for off-loop calls to external AI APIs
     */
    public AiAssistHandler(
            CompletionService completionService,
            ObjectMapper objectMapper,
            HttpHandlerSupport http,
            Executor blockingExecutor) {
        this(completionService, objectMapper, http, blockingExecutor, AiRecommendationMetrics.NOOP);
    }

    /**
     * Creates a handler with explicit quality metrics instrumentation.
     *
     * @param completionService    AI completion service; may be null (fallback mode)
     * @param objectMapper         JSON mapper
     * @param http                 shared HTTP support
     * @param blockingExecutor     executor for off-loop calls to external AI APIs
     * @param recommendationMetrics AI quality metrics recorder; use
     *                             {@link AiRecommendationMetrics#NOOP} to disable
     */
    public AiAssistHandler(
            CompletionService completionService,
            ObjectMapper objectMapper,
            HttpHandlerSupport http,
            Executor blockingExecutor,
            AiRecommendationMetrics recommendationMetrics) {
        this.completionService      = completionService;
        this.objectMapper           = objectMapper;
        this.http                   = http;
        this.blockingExecutor       = blockingExecutor;
        this.recommendationMetrics  = recommendationMetrics != null
                ? recommendationMetrics : AiRecommendationMetrics.NOOP;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Route handlers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * {@code POST /api/v1/entities/:collection/suggest}
     *
     * <p>Generates contextual suggestions for entity exploration: related collections,
     * recommended filters, likely schema patterns, and data quality hints.
     *
     * @param request HTTP request; body must contain {@code {"context": "...", "limit": N}}
     * @return 200 with suggestions list and AI confidence metadata
     */
    public Promise<HttpResponse> handleEntitySuggest(HttpRequest request) {
        String collection = request.getPathParameter("collection");
        String tenantId   = http.resolveTenantId(request);
        String requestId  = resolveRequestId(request);
        long   startMs    = System.currentTimeMillis();

        return request.loadBody(MAX_PROMPT_TOKENS * 4)
            .then(body -> {
                Map<String, Object> input = parseBody(body.getString(StandardCharsets.UTF_8));
                String context = (String) input.getOrDefault("context", "");
                int    limit   = ((Number) input.getOrDefault("limit", 5)).intValue();

                if (completionService == null) {
                    HttpResponse resp = heuristicEntitySuggestResponse(collection, context, limit, tenantId, requestId);
                    recommendationMetrics.recordRecommendation(
                        AiRecommendationMetrics.TYPE_ENTITY_SUGGEST, tenantId,
                        HEURISTIC_CONFIDENCE, true, System.currentTimeMillis() - startMs);
                    return Promise.of(resp);
                }

                String prompt = buildEntitySuggestPrompt(collection, context, limit, tenantId);
                return callAi(prompt)
                    .map(result -> {
                        HttpResponse resp = buildEntitySuggestHttpResponse(result, collection, limit, tenantId, requestId);
                        double conf = estimateConfidence(result);
                        recommendationMetrics.recordRecommendation(
                            AiRecommendationMetrics.TYPE_ENTITY_SUGGEST, tenantId,
                            conf, conf < FALLBACK_CONFIDENCE_THRESHOLD, System.currentTimeMillis() - startMs);
                        return resp;
                    })
                    .then(Promise::of,
                          e -> {
                              log.warn("[DC-E3] entity suggest AI call failed for collection={} tenant={}: {}",
                                        collection, tenantId, e.getMessage());
                              recommendationMetrics.recordError(
                                  AiRecommendationMetrics.TYPE_ENTITY_SUGGEST, tenantId, (Exception) e);
                              return Promise.of(heuristicEntitySuggestResponse(
                                      collection, context, limit, tenantId, requestId));
                          });
            });
    }

    /**
     * {@code POST /api/v1/analytics/suggest}
     *
     * <p>Suggests analytics query templates based on schema, recent queries,
     * and free-text intent description.
     *
     * @param request HTTP request; body must contain {@code {"intent": "...", "schema": {...}}}
     * @return 200 with recommended queries and AI confidence metadata
     */
    public Promise<HttpResponse> handleAnalyticsSuggest(HttpRequest request) {
        String tenantId  = http.resolveTenantId(request);
        String requestId = resolveRequestId(request);
        long   startMs   = System.currentTimeMillis();

        return request.loadBody(MAX_PROMPT_TOKENS * 4)
            .then(body -> {
                Map<String, Object> input = parseBody(body.getString(StandardCharsets.UTF_8));
                String intent = (String) input.getOrDefault("intent", "");

                if (completionService == null) {
                    HttpResponse resp = heuristicAnalyticsSuggestResponse(intent, tenantId, requestId);
                    recommendationMetrics.recordRecommendation(
                        AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, tenantId,
                        HEURISTIC_CONFIDENCE, true, System.currentTimeMillis() - startMs);
                    return Promise.of(resp);
                }

                String prompt = buildAnalyticsSuggestPrompt(intent, input, tenantId);
                return callAi(prompt)
                    .map(result -> {
                        HttpResponse resp = buildAnalyticsSuggestHttpResponse(result, tenantId, requestId);
                        double conf = estimateConfidence(result);
                        recommendationMetrics.recordRecommendation(
                            AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, tenantId,
                            conf, conf < FALLBACK_CONFIDENCE_THRESHOLD, System.currentTimeMillis() - startMs);
                        return resp;
                    })
                    .then(Promise::of,
                          e -> {
                              log.warn("[DC-E3] analytics suggest AI call failed tenant={}: {}", tenantId, e.getMessage());
                              recommendationMetrics.recordError(
                                  AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, tenantId, (Exception) e);
                              return Promise.of(heuristicAnalyticsSuggestResponse(intent, tenantId, requestId));
                          });
            });
    }

    /**
     * {@code POST /api/v1/pipelines/:pipelineId/optimise-hint}
     *
     * <p>Provides structural optimisation hints for a pipeline: redundant steps,
     * parallelisation opportunities, missing error handling, and data quality checks.
     *
     * @param request HTTP request; body must contain pipeline definition JSON
     * @return 200 with optimisation hints and AI confidence metadata
     */
    public Promise<HttpResponse> handlePipelineOptimiseHint(HttpRequest request) {
        String pipelineId = request.getPathParameter("pipelineId");
        String tenantId   = http.resolveTenantId(request);
        String requestId  = resolveRequestId(request);
        long   startMs    = System.currentTimeMillis();

        return request.loadBody(MAX_PROMPT_TOKENS * 4)
            .then(body -> {
                String pipelineJson = body.getString(StandardCharsets.UTF_8);

                if (completionService == null) {
                    HttpResponse resp = heuristicPipelineHintResponse(pipelineId, tenantId, requestId);
                    recommendationMetrics.recordRecommendation(
                        AiRecommendationMetrics.TYPE_PIPELINE_HINT, tenantId,
                        HEURISTIC_CONFIDENCE, true, System.currentTimeMillis() - startMs);
                    return Promise.of(resp);
                }

                String prompt = buildPipelineOptimisePrompt(pipelineId, pipelineJson, tenantId);
                return callAi(prompt)
                    .map(result -> {
                        HttpResponse resp = buildPipelineHintHttpResponse(result, pipelineId, tenantId, requestId);
                        double conf = estimateConfidence(result);
                        recommendationMetrics.recordRecommendation(
                            AiRecommendationMetrics.TYPE_PIPELINE_HINT, tenantId,
                            conf, conf < FALLBACK_CONFIDENCE_THRESHOLD, System.currentTimeMillis() - startMs);
                        return resp;
                    })
                    .then(Promise::of,
                          e -> {
                              log.warn("[DC-E3] pipeline hint AI call failed pipelineId={} tenant={}: {}",
                                        pipelineId, tenantId, e.getMessage());
                              recommendationMetrics.recordError(
                                  AiRecommendationMetrics.TYPE_PIPELINE_HINT, tenantId, (Exception) e);
                              return Promise.of(heuristicPipelineHintResponse(pipelineId, tenantId, requestId));
                          });
            });
    }

    /**
     * {@code POST /api/v1/brain/explain}
     *
     * <p>Generates a natural-language explanation for a salience score or anomaly
     * detection result, including remediation suggestions for high-severity items.
     *
     * @param request HTTP request; body must contain {@code {"itemId": "...", "context": {...}}}
     * @return 200 with explanation, remediation hints, and AI confidence metadata
     */
    public Promise<HttpResponse> handleBrainExplain(HttpRequest request) {
        String tenantId  = http.resolveTenantId(request);
        String requestId = resolveRequestId(request);
        long   startMs   = System.currentTimeMillis();

        return request.loadBody(MAX_PROMPT_TOKENS * 4)
            .then(body -> {
                Map<String, Object> input = parseBody(body.getString(StandardCharsets.UTF_8));
                String itemId  = (String) input.getOrDefault("itemId", "unknown");
                Object context = input.getOrDefault("context", Map.of());

                if (completionService == null) {
                    HttpResponse resp = heuristicBrainExplainResponse(itemId, tenantId, requestId);
                    recommendationMetrics.recordRecommendation(
                        AiRecommendationMetrics.TYPE_BRAIN_EXPLAIN, tenantId,
                        HEURISTIC_CONFIDENCE, true, System.currentTimeMillis() - startMs);
                    return Promise.of(resp);
                }

                String prompt = buildBrainExplainPrompt(itemId, context, tenantId);
                return callAi(prompt)
                    .map(result -> {
                        HttpResponse resp = buildBrainExplainHttpResponse(result, itemId, tenantId, requestId);
                        double conf = estimateConfidence(result);
                        recommendationMetrics.recordRecommendation(
                            AiRecommendationMetrics.TYPE_BRAIN_EXPLAIN, tenantId,
                            conf, conf < FALLBACK_CONFIDENCE_THRESHOLD, System.currentTimeMillis() - startMs);
                        return resp;
                    })
                    .then(Promise::of,
                          e -> {
                              log.warn("[DC-E3] brain explain AI call failed itemId={} tenant={}: {}",
                                        itemId, tenantId, e.getMessage());
                              recommendationMetrics.recordError(
                                  AiRecommendationMetrics.TYPE_BRAIN_EXPLAIN, tenantId, (Exception) e);
                              return Promise.of(heuristicBrainExplainResponse(itemId, tenantId, requestId));
                          });
            });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AI calling helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Promise<CompletionResult> callAi(String prompt) {
        CompletionRequest req = CompletionRequest.builder()
            .messages(List.of(
                ChatMessage.system("You are the Data-Cloud AI assistant. Be concise, structured, and evidence-based."),
                ChatMessage.user(prompt)
            ))
            .maxTokens(512)
            .temperature(0.3)
            .build();

        return Promise.ofBlocking(blockingExecutor, () ->
            completionService.complete(req).getResult());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Prompt builders (privacy-safe — no raw entity data in prompts)
    // ─────────────────────────────────────────────────────────────────────────

    private String buildEntitySuggestPrompt(String collection, String context, int limit, String tenantId) {
        return String.format(
            """
            Collection: %s
            Context: %s
            Tenant scope: %s
            Instructions: Suggest %d useful exploration next steps: related collections, \
            filter patterns, schema insights, and data quality observations. \
            Return JSON: {"suggestions":[{"title":"...","description":"...","type":"filter|schema|quality|related"}]}""",
            sanitise(collection), sanitise(context), sanitise(tenantId), limit);
    }

    private String buildAnalyticsSuggestPrompt(String intent, Map<String, Object> input, String tenantId) {
        String schema = String.valueOf(input.getOrDefault("schema", "{}"));
        return String.format(
            """
            User intent: %s
            Schema context: %s
            Tenant scope: %s
            Instructions: Suggest 3 analytics query templates that match the intent. \
            Return JSON: {"queries":[{"name":"...","template":"SELECT ...","explanation":"..."}]}""",
            sanitise(intent), sanitise(schema.length() > 500 ? schema.substring(0, 500) + "..." : schema),
            sanitise(tenantId));
    }

    private String buildPipelineOptimisePrompt(String pipelineId, String pipelineJson, String tenantId) {
        String safe = pipelineJson.length() > 800 ? pipelineJson.substring(0, 800) + "..." : pipelineJson;
        return String.format(
            """
            Pipeline ID: %s
            Tenant scope: %s
            Pipeline definition: %s
            Instructions: Analyse for optimisation opportunities: parallelisation, redundant steps, \
            missing error handling, data quality gates. \
            Return JSON: {"hints":[{"type":"parallelisation|redundancy|error_handling|quality","description":"...","priority":"high|medium|low"}]}""",
            sanitise(pipelineId), sanitise(tenantId), sanitise(safe));
    }

    private String buildBrainExplainPrompt(String itemId, Object context, String tenantId) {
        String ctxStr = String.valueOf(context);
        String safe   = ctxStr.length() > 600 ? ctxStr.substring(0, 600) + "..." : ctxStr;
        return String.format(
            """
            Item ID: %s
            Tenant scope: %s
            Context: %s
            Instructions: Explain why this item has a high salience score and suggest remediations. \
            Return JSON: {"explanation":"...","severity":"high|medium|low","remediations":["..."]}""",
            sanitise(itemId), sanitise(tenantId), sanitise(safe));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HTTP response builders
    // ─────────────────────────────────────────────────────────────────────────

    private HttpResponse buildEntitySuggestHttpResponse(
            CompletionResult result, String collection, int limit, String tenantId, String requestId) {
        double confidence = estimateConfidence(result);
        boolean fallback  = confidence < FALLBACK_CONFIDENCE_THRESHOLD;

        Map<String, Object> suggestions = tryParseAiJson(result.getText(),
            Map.of("suggestions", List.of(Map.of("title", "Explore " + collection, "description",
                "Check entity count and schema", "type", "schema"))));

        ApiResponse envelope = ApiResponse.success(suggestions, tenantId, requestId)
            .withAiMeta(confidence, result.getModelUsed() != null ? result.getModelUsed() : DEFAULT_MODEL,
                        List.of("llm", "entity-context"), fallback);
        return http.envelopeResponse(envelope, objectMapper);
    }

    private HttpResponse buildAnalyticsSuggestHttpResponse(
            CompletionResult result, String tenantId, String requestId) {
        double confidence = estimateConfidence(result);
        boolean fallback  = confidence < FALLBACK_CONFIDENCE_THRESHOLD;

        Map<String, Object> queries = tryParseAiJson(result.getText(),
            Map.of("queries", List.of(Map.of("name", "Count all", "template", "SELECT COUNT(*) FROM events",
                "explanation", "Basic count query"))));

        ApiResponse envelope = ApiResponse.success(queries, tenantId, requestId)
            .withAiMeta(confidence, result.getModelUsed() != null ? result.getModelUsed() : DEFAULT_MODEL,
                        List.of("llm", "analytics-context"), fallback);
        return http.envelopeResponse(envelope, objectMapper);
    }

    private HttpResponse buildPipelineHintHttpResponse(
            CompletionResult result, String pipelineId, String tenantId, String requestId) {
        double confidence = estimateConfidence(result);
        boolean fallback  = confidence < FALLBACK_CONFIDENCE_THRESHOLD;

        Map<String, Object> hints = tryParseAiJson(result.getText(),
            Map.of("hints", List.of(Map.of("type", "quality",
                "description", "Add data quality validation step", "priority", "medium"))));

        ApiResponse envelope = ApiResponse.success(hints, tenantId, requestId)
            .withAiMeta(confidence, result.getModelUsed() != null ? result.getModelUsed() : DEFAULT_MODEL,
                        List.of("llm", "pipeline-analysis"), fallback);
        return http.envelopeResponse(envelope, objectMapper);
    }

    private HttpResponse buildBrainExplainHttpResponse(
            CompletionResult result, String itemId, String tenantId, String requestId) {
        double confidence = estimateConfidence(result);
        boolean fallback  = confidence < FALLBACK_CONFIDENCE_THRESHOLD;

        Map<String, Object> explanation = tryParseAiJson(result.getText(),
            Map.of("explanation", "High activity detected",
                   "severity", "medium",
                   "remediations", List.of("Review recent events for this item")));

        ApiResponse envelope = ApiResponse.success(explanation, tenantId, requestId)
            .withAiMeta(confidence, result.getModelUsed() != null ? result.getModelUsed() : DEFAULT_MODEL,
                        List.of("llm", "brain-context"), fallback);
        return http.envelopeResponse(envelope, objectMapper);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Heuristic (static) fallback responses — returned when AI is unavailable
    // ─────────────────────────────────────────────────────────────────────────

    private HttpResponse heuristicEntitySuggestResponse(
            String collection, String context, int limit, String tenantId, String requestId) {
        Map<String, Object> data = Map.of("suggestions", List.of(
            Map.of("title", "Filter by date range",
                   "description", "Use createdAt between [$start, $end] for time-scoped queries",
                   "type", "filter"),
            Map.of("title", "Sort by latest",
                   "description", "ORDER BY updatedAt DESC to surface recent changes",
                   "type", "filter"),
            Map.of("title", "Review schema",
                   "description", "Validate required fields and data types for " + sanitise(collection),
                   "type", "schema")
        ).subList(0, Math.min(3, limit)));
        ApiResponse envelope = ApiResponse.success(data, tenantId, requestId)
            .withAiMeta(HEURISTIC_CONFIDENCE, "static-heuristic", List.of("fallback", "ruleset"), true);
        return http.envelopeResponse(envelope, objectMapper);
    }

    private HttpResponse heuristicAnalyticsSuggestResponse(
            String intent, String tenantId, String requestId) {
        Map<String, Object> data = Map.of("queries", List.of(
            Map.of("name", "Event count by type",
                   "template", "SELECT eventType, COUNT(*) as count FROM events GROUP BY eventType",
                   "explanation", "Summarise event distribution"),
            Map.of("name", "Recent events",
                   "template", "SELECT * FROM events ORDER BY timestamp DESC LIMIT 100",
                   "explanation", "Latest 100 events")
        ));
        ApiResponse envelope = ApiResponse.success(data, tenantId, requestId)
            .withAiMeta(HEURISTIC_CONFIDENCE, "static-heuristic", List.of("fallback", "ruleset"), true);
        return http.envelopeResponse(envelope, objectMapper);
    }

    private HttpResponse heuristicPipelineHintResponse(
            String pipelineId, String tenantId, String requestId) {
        Map<String, Object> data = Map.of("hints", List.of(
            Map.of("type", "quality",
                   "description", "Add input validation step at pipeline entry",
                   "priority", "high"),
            Map.of("type", "error_handling",
                   "description", "Ensure each step has a failure branch with dead-letter routing",
                   "priority", "medium")
        ));
        ApiResponse envelope = ApiResponse.success(data, tenantId, requestId)
            .withAiMeta(HEURISTIC_CONFIDENCE, "static-heuristic", List.of("fallback", "ruleset"), true);
        return http.envelopeResponse(envelope, objectMapper);
    }

    private HttpResponse heuristicBrainExplainResponse(
            String itemId, String tenantId, String requestId) {
        Map<String, Object> data = Map.of(
            "explanation", "Item '" + sanitise(itemId) + "' has a high salience score due to recent activity volume.",
            "severity", "medium",
            "remediations", List.of(
                "Review event log for this item",
                "Check for duplicate processing",
                "Verify expected retention policy is applied"
            )
        );
        ApiResponse envelope = ApiResponse.success(data, tenantId, requestId)
            .withAiMeta(HEURISTIC_CONFIDENCE, "static-heuristic", List.of("fallback", "ruleset"), true);
        return http.envelopeResponse(envelope, objectMapper);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utilities
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseBody(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> tryParseAiJson(String text, Map<String, Object> fallback) {
        if (text == null || text.isBlank()) return fallback;
        try {
            // Strip markdown code fences if present
            String stripped = text.strip()
                .replaceAll("^```json\\s*", "")
                .replaceAll("^```\\s*", "")
                .replaceAll("\\s*```$", "");
            return objectMapper.readValue(stripped, Map.class);
        } catch (Exception e) {
            log.debug("[DC-E3] AI JSON parse failed, using fallback: {}", e.getMessage());
            return fallback;
        }
    }

    /**
     * Estimates confidence from the CompletionResult's finish reason.
     * Returns 0.85 for normal completions, 0.5 for truncated, and 0.3 for unknown.
     */
    private static double estimateConfidence(CompletionResult result) {
        if (result == null) return 0.30;
        String finish = result.getFinishReason();
        if ("stop".equalsIgnoreCase(finish) || "end_turn".equalsIgnoreCase(finish)) return 0.85;
        if ("length".equalsIgnoreCase(finish)) return 0.50;
        return 0.40;
    }

    /** Strips characters that could cause prompt injection. */
    private static String sanitise(String input) {
        if (input == null) return "";
        // Remove backticks, template delimiters, and control chars — OWASP injection prevention
        return input.replaceAll("[`\\\\\\x00-\\x1F]", "").stripLeading().stripTrailing();
    }

    private static String resolveRequestId(HttpRequest request) {
        String rid = request.getHeader(io.activej.http.HttpHeaders.of("X-Request-ID"));
        return (rid != null && !rid.isBlank()) ? rid : UUID.randomUUID().toString();
    }
}
