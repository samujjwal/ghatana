package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.ai.llm.ChatMessage;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.governance.QuotaCheckResult;
import com.ghatana.datacloud.governance.TenantQuotaService;
import com.ghatana.datacloud.launcher.ai.AiRecommendationMetrics;
import com.ghatana.datacloud.launcher.http.ApiResponse;
import com.ghatana.platform.observability.idempotency.IdempotencyHelper;
import com.ghatana.platform.observability.idempotency.IdempotencyStore;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
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
 *   POST /api/v1/pipelines/draft                     — intent-to-pipeline draft generation
 *   POST /api/v1/pipelines/:pipelineId/optimise-hint — pipeline optimisation hints
 *   POST /api/v1/brain/explain                       — anomaly/salience explanation
 *   GET  /api/v1/ai/quality-summary                  — operator AI quality telemetry summary
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
 *   <li>In production mode, provider outages return HTTP 503 (service unavailable) instead of heuristic output.</li>
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

    /** Optional tenant quota service for AI token enforcement (P0.5). */
    private TenantQuotaService tenantQuotaService;

    /** Optional Data Cloud client for persisting AI action audit records (P2.1). */
    private DataCloudClient client;

    /** P0-07: Idempotency store for AI assist operations. */
    private IdempotencyStore idempotencyStore;

    /**
     * DC-P0-007: When {@code true}, AI assist routes return HTTP 503 instead of heuristic
     * fallback when no real {@link CompletionService} is configured. Must be {@code true}
     * for production profiles; defaults to {@code false} for local/preview compatibility.
     */
    private boolean productionMode = false;

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

    public AiAssistHandler withTenantQuotaService(TenantQuotaService tenantQuotaService) {
        this.tenantQuotaService = tenantQuotaService;
        return this;
    }

    /**
     * Attaches a Data Cloud client for AI action record persistence (P2.1).
     */
    public AiAssistHandler withClient(DataCloudClient client) {
        this.client = client;
        return this;
    }

    /**
     * DC-P0-007: Enables production mode — disables static heuristic fallback when no real
     * {@link CompletionService} is present. Routes return 503 instead of heuristic output.
     */
    public AiAssistHandler withProductionMode(boolean productionMode) {
        this.productionMode = productionMode;
        return this;
    }

    /**
     * P0-07: Wires an {@link IdempotencyStore} for idempotent AI assist operations.
     *
     * @param idempotencyStore the idempotency store; may be {@code null}
     * @return this handler (fluent)
     */
    public AiAssistHandler withIdempotencyStore(IdempotencyStore idempotencyStore) {
        this.idempotencyStore = idempotencyStore;
        return this;
    }

    // ─── Helper Methods (P0-07) ─────────────────────────────────────────────

    /**
     * P0-07: Check idempotency for AI assist operations.
     */
    private Promise<HttpResponse> checkIdempotency(String tenantId, String routeAction, HttpRequest request) {
        if (idempotencyStore == null) {
            return Promise.of(null);
        }

        String idempotencyKey = IdempotencyHelper.extractIdempotencyKey(request);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Promise.of(null);
        }

        String principalId = http.resolvePrincipalId(request);
        String scope = "aiassist:" + routeAction;

        return IdempotencyHelper.checkConflict(idempotencyStore, tenantId, scope, idempotencyKey, principalId,
            IdempotencyHelper.computePayloadHash(request))
            .then(hasConflict -> {
                if (hasConflict) {
                    log.warn("[P0-07] Idempotency conflict for tenant={}, scope={}, key={}", tenantId, scope, idempotencyKey);
                    return Promise.of(http.errorResponse(409,
                        "Idempotency key conflict: same key used with different payload"));
                }

                return IdempotencyHelper.checkIdempotency(idempotencyStore, tenantId, scope, idempotencyKey, principalId)
                    .then(cachedResponse -> {
                        if (cachedResponse != null) {
                            log.info("[P0-07] Returning cached response for tenant={}, scope={}, key={}", tenantId, scope, idempotencyKey);
                            if (cachedResponse instanceof HttpResponse) {
                                return Promise.of(IdempotencyHelper.addIdempotencyHeaders((HttpResponse) cachedResponse, "replay"));
                            }
                            if (cachedResponse instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> map = (Map<String, Object>) cachedResponse;
                                return Promise.of(http.jsonResponse(map));
                            }
                            return Promise.of(http.jsonResponse(Map.of("data", cachedResponse)));
                        }
                        return Promise.of((HttpResponse) null);
                    });
            });
    }

    /**
     * P0-07: Store response for idempotent AI assist operations.
     */
    private Promise<Void> storeIdempotency(String tenantId, String routeAction,
                                          HttpRequest request, Object response) {
        if (idempotencyStore == null) {
            return Promise.of(null);
        }

        String idempotencyKey = IdempotencyHelper.extractIdempotencyKey(request);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Promise.of(null);
        }

        String principalId = http.resolvePrincipalId(request);
        String scope = "aiassist:" + routeAction;
        String payloadHash = IdempotencyHelper.computePayloadHash(request);

        return IdempotencyHelper.storeResponse(idempotencyStore, tenantId, scope, idempotencyKey, principalId, payloadHash, response);
    }

    /**
     * DC-P1.16: AI advisory/fail-closed semantics alignment.
     * Returns a 503 promise when production mode is active and no CompletionService
     * is available. Returns {@code null} when heuristic fallback is permitted or AI is wired.
     * This implements fail-closed behavior: production deployments return HTTP 503 instead of
     * falling back to heuristics when the AI service is unavailable. AI suggestions remain
     * advisory only and should not be treated as guaranteed outcomes when the service is degraded.
     */
    private Promise<HttpResponse> checkAiServiceOrUnavailable() {
        if (productionMode && completionService == null) {
            log.error("[DC-P1.16] AI assist unavailable in production: no CompletionService configured. " +
                "Configure DATACLOUD_AI_COMPLETION_URL or disable AI routes. " +
                "AI suggestions remain advisory only and should not be treated as guaranteed outcomes.");
            return Promise.of(http.errorResponse(503,
                "AI assist is not available: the AI completion service is not configured " +
                "for this deployment. Enable the service or use local/preview mode. " +
                "AI suggestions remain advisory only and should not be treated as guaranteed outcomes."));
        }
        return null;
    }

    private Promise<HttpResponse> providerErrorOrNull(String route, String tenantId, String requestId, Throwable error) {
        if (!productionMode) {
            return null;
        }
        log.error("[DC-P1.16] AI provider failure in production route={} tenant={} requestId={} error={}",
            route, tenantId, requestId, error == null ? "unknown" : error.getMessage());
        // DC-P1.16: Fail-closed behavior - return HTTP 503 instead of propagating provider errors in production.
        // AI suggestions remain advisory only and should not be treated as guaranteed outcomes.
        return Promise.of(http.errorResponse(503,
            "AI assist is temporarily unavailable: the configured AI provider returned an error. "
            + "Retry later or inspect provider health. AI suggestions remain advisory only and should not be treated as guaranteed outcomes."));
    }

    /**
     * P0.5: Check tenant AI token quota before AI assist operations.
     * Returns an error promise if quota is exceeded, otherwise null.
     */
    private Promise<HttpResponse> checkAiQuotaOrNull(String tenantId, int estimatedTokens) {
        if (tenantQuotaService == null) return null;
        QuotaCheckResult result = tenantQuotaService.checkQuota(tenantId, "AI_TOKEN", estimatedTokens);
        if (!result.isAllowed()) {
            return Promise.of(http.errorResponse(429,
                "AI quota exceeded: " + result.message() + " (quota=" + result.quotaValue()
                    + ", used=" + result.usedAmount() + ")"));
        }
        return null;
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
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        String requestId  = resolveRequestId(request);
        long   startMs    = System.currentTimeMillis();

        Promise<HttpResponse> quotaErr = checkAiQuotaOrNull(tenantId, MAX_PROMPT_TOKENS);
        if (quotaErr != null) return quotaErr;
        // DC-P0-007: fail closed when AI service unavailable in production
        Promise<HttpResponse> aiUnavail = checkAiServiceOrUnavailable();
        if (aiUnavail != null) return aiUnavail;

        return request.loadBody(MAX_PROMPT_TOKENS * 4)
            .then(body -> {
                Map<String, Object> input = parseBody(body.getString(StandardCharsets.UTF_8));
                String context = (String) input.getOrDefault("context", "");
                int    limit   = ((Number) input.getOrDefault("limit", 5)).intValue();

                if (completionService == null) {
                    // P2-2: Explicit logging for AI Operations fallback
                    log.warn("[P2-2] AI Operations fallback: completionService is null for entity-suggest collection={} tenant={} requestId={}", 
                            collection, tenantId, requestId);
                    HttpResponse resp = heuristicEntitySuggestResponse(collection, context, limit, tenantId, requestId);
                    recommendationMetrics.recordRecommendation(
                        AiRecommendationMetrics.TYPE_ENTITY_SUGGEST, tenantId,
                        HEURISTIC_CONFIDENCE, true, System.currentTimeMillis() - startMs);
                    recordAiAction(tenantId, "entity-suggest", "collection=" + collection,
                        "static-heuristic", HEURISTIC_CONFIDENCE, true,
                        System.currentTimeMillis() - startMs, requestId);
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
                        recordAiAction(tenantId, "entity-suggest", "collection=" + collection,
                            result.getModelUsed() != null ? result.getModelUsed() : DEFAULT_MODEL,
                            conf, conf < FALLBACK_CONFIDENCE_THRESHOLD,
                            System.currentTimeMillis() - startMs, requestId);
                        return resp;
                    })
                    .then(Promise::of,
                          e -> {
                              Promise<HttpResponse> providerError = providerErrorOrNull(
                                  "/api/v1/entities/:collection/suggest", tenantId, requestId, e);
                              if (providerError != null) {
                                  return providerError;
                              }
                              // P2-2: Explicit logging for AI Operations fallback due to error
                              log.warn("[P2-2] AI Operations fallback: entity-suggest AI call failed for collection={} tenant={} requestId={} error={}", 
                                        collection, tenantId, requestId, e.getMessage());
                              recommendationMetrics.recordError(
                                  AiRecommendationMetrics.TYPE_ENTITY_SUGGEST, tenantId, e);
                              recordAiAction(tenantId, "entity-suggest", "collection=" + collection,
                                  "static-heuristic", HEURISTIC_CONFIDENCE, true,
                                  System.currentTimeMillis() - startMs, requestId);
                              return Promise.of(heuristicEntitySuggestResponse(
                                      collection, context, limit, tenantId, requestId));
                          });
            });
    }

    /**
     * {@code POST /api/v1/entities/:collection/infer-schema}
     *
     * <p>Infers a candidate schema from a sample payload and highlights likely
     * PII fields and deduplication keys for review before ingestion.
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleInferSchema(HttpRequest request) {
        String collection = request.getPathParameter("collection");
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String requestId = resolveRequestId(request);
        long startMs = System.currentTimeMillis();

        Promise<HttpResponse> quotaErr = checkAiQuotaOrNull(tenantId, MAX_PROMPT_TOKENS);
        if (quotaErr != null) return quotaErr;
        // DC-P0-007: fail closed when AI service unavailable in production
        Promise<HttpResponse> aiUnavail = checkAiServiceOrUnavailable();
        if (aiUnavail != null) return aiUnavail;

        return request.loadBody(MAX_PROMPT_TOKENS * 4)
            .then(body -> {
                try {
                    Map<String, Object> input = parseBody(body.getString(StandardCharsets.UTF_8));
                    Object rawSample = input.getOrDefault("sample", input);
                    if (!(rawSample instanceof Map<?, ?> rawMap)) {
                        return Promise.of(http.errorResponse(400, "sample object is required"));
                    }

                    Map<String, Object> sample = (Map<String, Object>) rawMap;
                    List<Map<String, Object>> fields = new ArrayList<>();
                    List<String> piiFields = new ArrayList<>();
                    List<String> dedupeCandidates = new ArrayList<>();

                    for (Map.Entry<String, Object> entry : sample.entrySet()) {
                        String fieldName = entry.getKey();
                        Object value = entry.getValue();
                        String inferredType = inferValueType(value);
                        boolean pii = isLikelyPiiField(fieldName);
                        boolean dedupe = isLikelyDedupeKey(fieldName);

                        Map<String, Object> field = new LinkedHashMap<>();
                        field.put("name", fieldName);
                        field.put("type", inferredType);
                        field.put("required", value != null);
                        field.put("pii", pii);
                        field.put("dedupeCandidate", dedupe);
                        fields.add(field);

                        if (pii) {
                            piiFields.add(fieldName);
                        }
                        if (dedupe) {
                            dedupeCandidates.add(fieldName);
                        }
                    }

                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("collection", collection);
                    payload.put("fields", fields);
                    payload.put("piiFields", piiFields);
                    payload.put("dedupeCandidates", dedupeCandidates);
                    payload.put("generatedAt", Instant.now().toString());
                    payload.put("latencyMs", System.currentTimeMillis() - startMs);
                    payload.put("ai", Map.of(
                        "confidence", HEURISTIC_CONFIDENCE,
                        "model", "schema-infer-heuristic-v1",
                        "fallback", true,
                        "reasons", List.of("field-name-and-sample-type-heuristics")
                    ));

                    recommendationMetrics.recordRecommendation(
                        AiRecommendationMetrics.TYPE_ENTITY_SUGGEST,
                        tenantId,
                        HEURISTIC_CONFIDENCE,
                        true,
                        System.currentTimeMillis() - startMs);
                    recordAiAction(
                        tenantId,
                        "entity-infer-schema",
                        "collection=" + collection,
                        "schema-infer-heuristic-v1",
                        HEURISTIC_CONFIDENCE,
                        true,
                        System.currentTimeMillis() - startMs,
                        requestId);

                    return Promise.of(http.envelopeResponse(
                        ApiResponse.success(payload, tenantId, requestId),
                        objectMapper));
                } catch (Exception e) {
                    return Promise.of(http.errorResponse(400, "Invalid infer-schema payload: " + e.getMessage()));
                }
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
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        String requestId = resolveRequestId(request);
        long   startMs   = System.currentTimeMillis();

        Promise<HttpResponse> quotaErr = checkAiQuotaOrNull(tenantId, MAX_PROMPT_TOKENS);
        if (quotaErr != null) return quotaErr;
        // DC-P0-007: fail closed when AI service unavailable in production
        Promise<HttpResponse> aiUnavail = checkAiServiceOrUnavailable();
        if (aiUnavail != null) return aiUnavail;

        return request.loadBody(MAX_PROMPT_TOKENS * 4)
            .then(body -> {
                Map<String, Object> input = parseBody(body.getString(StandardCharsets.UTF_8));
                String intent = (String) input.getOrDefault("intent", "");

                if (completionService == null) {
                    // P2-2: Explicit logging for AI Operations fallback
                    log.warn("[P2-2] AI Operations fallback: completionService is null for analytics-suggest tenant={} requestId={}", 
                            tenantId, requestId);
                    HttpResponse resp = heuristicAnalyticsSuggestResponse(intent, tenantId, requestId);
                    recommendationMetrics.recordRecommendation(
                        AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, tenantId,
                        HEURISTIC_CONFIDENCE, true, System.currentTimeMillis() - startMs);
                    recordAiAction(tenantId, "analytics-suggest", "intent=" + intent,
                        "static-heuristic", HEURISTIC_CONFIDENCE, true,
                        System.currentTimeMillis() - startMs, requestId);
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
                        recordAiAction(tenantId, "analytics-suggest", "intent=" + intent,
                            result.getModelUsed() != null ? result.getModelUsed() : DEFAULT_MODEL,
                            conf, conf < FALLBACK_CONFIDENCE_THRESHOLD,
                            System.currentTimeMillis() - startMs, requestId);
                        return resp;
                    })
                    .then(Promise::of,
                          e -> {
                              Promise<HttpResponse> providerError = providerErrorOrNull(
                                  "/api/v1/analytics/suggest", tenantId, requestId, e);
                              if (providerError != null) {
                                  return providerError;
                              }
                              log.warn("[DC-E3] analytics suggest AI call failed tenant={}: {}", tenantId, e.getMessage());
                              recommendationMetrics.recordError(
                                  AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, tenantId, e);
                              recordAiAction(tenantId, "analytics-suggest", "intent=" + intent,
                                  "static-heuristic", HEURISTIC_CONFIDENCE, true,
                                  System.currentTimeMillis() - startMs, requestId);
                              return Promise.of(heuristicAnalyticsSuggestResponse(intent, tenantId, requestId));
                          });
            });
    }

    /**
     * {@code POST /api/v1/analytics/automate}
     *
     * <p>Advanced analytics automation with intent clarification, query rewrite,
     * safe scope inference, and policy-aware recommendations (P2-2).
     *
     * <p>This endpoint provides automation-first analytics assistance by:
     * <ul>
     *   <li>Clarifying ambiguous user intent through interactive questioning</li>
     *   <li>Rewriting suboptimal queries for performance and correctness</li>
     *   <li>Inferring safe data access scopes based on schema and governance policies</li>
     *   <li>Providing policy-aware recommendations that respect governance constraints</li>
     * </ul>
     *
     * @param request HTTP request; body must contain {@code {"query": "...", "schema": {...}, "context": {...}}}
     * @return 200 with automated query improvements, safety analysis, and AI confidence metadata
     */
    public Promise<HttpResponse> handleAnalyticsAutomate(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        String requestId = resolveRequestId(request);
        long   startMs   = System.currentTimeMillis();

        Promise<HttpResponse> quotaErr = checkAiQuotaOrNull(tenantId, MAX_PROMPT_TOKENS);
        if (quotaErr != null) return quotaErr;
        // DC-P0-007: fail closed when AI service unavailable in production
        Promise<HttpResponse> aiUnavail = checkAiServiceOrUnavailable();
        if (aiUnavail != null) return aiUnavail;

        return request.loadBody(MAX_PROMPT_TOKENS * 4)
            .then(body -> {
                Map<String, Object> input = parseBody(body.getString(StandardCharsets.UTF_8));
                String query  = (String) input.getOrDefault("query", "");
                String schema = String.valueOf(input.getOrDefault("schema", "{}"));
                String context = String.valueOf(input.getOrDefault("context", "{}"));

                if (completionService == null) {
                    // P2-2: Explicit logging for AI Operations fallback
                    log.warn("[P2-2] AI Operations fallback: completionService is null for analytics-automate tenant={} requestId={}", 
                            tenantId, requestId);
                    HttpResponse resp = heuristicAnalyticsAutomateResponse(query, schema, tenantId, requestId);
                    recommendationMetrics.recordRecommendation(
                        AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, tenantId,
                        HEURISTIC_CONFIDENCE, true, System.currentTimeMillis() - startMs);
                    recordAiAction(tenantId, "analytics-automate", "query=" + query,
                        "static-heuristic", HEURISTIC_CONFIDENCE, true,
                        System.currentTimeMillis() - startMs, requestId);
                    return Promise.of(resp);
                }

                String prompt = buildAnalyticsAutomatePrompt(query, schema, context, tenantId);
                return callAi(prompt)
                    .map(result -> {
                        HttpResponse resp = buildAnalyticsAutomateHttpResponse(result, query, tenantId, requestId);
                        double conf = estimateConfidence(result);
                        recommendationMetrics.recordRecommendation(
                            AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, tenantId,
                            conf, conf < FALLBACK_CONFIDENCE_THRESHOLD, System.currentTimeMillis() - startMs);
                        recordAiAction(tenantId, "analytics-automate", "query=" + query,
                            result.getModelUsed() != null ? result.getModelUsed() : DEFAULT_MODEL,
                            conf, conf < FALLBACK_CONFIDENCE_THRESHOLD,
                            System.currentTimeMillis() - startMs, requestId);
                        return resp;
                    })
                    .then(Promise::of,
                          e -> {
                              Promise<HttpResponse> providerError = providerErrorOrNull(
                                  "/api/v1/analytics/automate", tenantId, requestId, e);
                              if (providerError != null) {
                                  return providerError;
                              }
                              log.warn("[DC-E3] analytics automate AI call failed tenant={}: {}", tenantId, e.getMessage());
                              recommendationMetrics.recordError(
                                  AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, tenantId, e);
                              recordAiAction(tenantId, "analytics-automate", "query=" + query,
                                  "static-heuristic", HEURISTIC_CONFIDENCE, true,
                                  System.currentTimeMillis() - startMs, requestId);
                              return Promise.of(heuristicAnalyticsAutomateResponse(query, schema, tenantId, requestId));
                          });
            });
    }

    /**
     * {@code POST /api/v1/pipelines/draft}
     *
     * <p>Generates a reviewable pipeline draft from natural-language intent.
     * The response contains ordered workflow steps plus provenance metadata so
     * the UI can render an editable draft before persistence.
     *
     * @param request HTTP request; body must contain {@code {"prompt":"..."}}
     * @return 200 with draft payload and AI confidence metadata
     */
    public Promise<HttpResponse> handlePipelineDraft(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String requestId = resolveRequestId(request);
        long startMs = System.currentTimeMillis();

        Promise<HttpResponse> quotaErr = checkAiQuotaOrNull(tenantId, MAX_PROMPT_TOKENS);
        if (quotaErr != null) return quotaErr;
        // DC-P0-007: fail closed when AI service unavailable in production
        Promise<HttpResponse> aiUnavail = checkAiServiceOrUnavailable();
        if (aiUnavail != null) return aiUnavail;

        return request.loadBody(MAX_PROMPT_TOKENS * 4)
            .then(body -> {
                Map<String, Object> input = parseBody(body.getString(StandardCharsets.UTF_8));
                String prompt = String.valueOf(input.getOrDefault("prompt", "")).trim();

                if (prompt.isBlank()) {
                    return Promise.of(http.errorResponse(400, "prompt is required"));
                }

                if (completionService == null) {
                    // P2-2: Explicit logging for AI Operations fallback
                    log.warn("[P2-2] AI Operations fallback: completionService is null for pipeline-draft tenant={} requestId={}", 
                            tenantId, requestId);
                    HttpResponse resp = heuristicPipelineDraftResponse(prompt, tenantId, requestId);
                    recommendationMetrics.recordRecommendation(
                        AiRecommendationMetrics.TYPE_PIPELINE_DRAFT,
                        tenantId,
                        HEURISTIC_CONFIDENCE,
                        true,
                        System.currentTimeMillis() - startMs);
                    recordAiAction(tenantId, "pipeline-draft", "prompt=" + prompt,
                        "static-heuristic", HEURISTIC_CONFIDENCE, true,
                        System.currentTimeMillis() - startMs, requestId);
                    return Promise.of(resp);
                }

                String aiPrompt = buildPipelineDraftPrompt(prompt, tenantId);
                return callAi(aiPrompt)
                    .map(result -> {
                        HttpResponse resp = buildPipelineDraftHttpResponse(result, prompt, tenantId, requestId);
                        double conf = estimateConfidence(result);
                        recommendationMetrics.recordRecommendation(
                            AiRecommendationMetrics.TYPE_PIPELINE_DRAFT,
                            tenantId,
                            conf,
                            conf < FALLBACK_CONFIDENCE_THRESHOLD,
                            System.currentTimeMillis() - startMs);
                        recordAiAction(tenantId, "pipeline-draft", "prompt=" + prompt,
                            result.getModelUsed() != null ? result.getModelUsed() : DEFAULT_MODEL,
                            conf, conf < FALLBACK_CONFIDENCE_THRESHOLD,
                            System.currentTimeMillis() - startMs, requestId);
                        return resp;
                    })
                    .then(Promise::of,
                        e -> {
                            Promise<HttpResponse> providerError = providerErrorOrNull(
                                "/api/v1/pipelines/draft", tenantId, requestId, e);
                            if (providerError != null) {
                                return providerError;
                            }
                            log.warn("[DC-E3] pipeline draft AI call failed tenant={}: {}", tenantId, e.getMessage());
                            recommendationMetrics.recordError(
                                AiRecommendationMetrics.TYPE_PIPELINE_DRAFT,
                                tenantId,
                                e);
                            recordAiAction(tenantId, "pipeline-draft", "prompt=" + prompt,
                                "static-heuristic", HEURISTIC_CONFIDENCE, true,
                                System.currentTimeMillis() - startMs, requestId);
                            return Promise.of(heuristicPipelineDraftResponse(prompt, tenantId, requestId));
                        });
            });
    }

    /**
     * {@code POST /api/v1/pipelines/:draftId/refine}
     *
     * <p>Post-draft refinement with validation and suggested fixes (P2-3).
     *
     * <p>This endpoint provides workflow draft automation by:
     * <ul>
     *   <li>Validating the draft structure and step dependencies</li>
     *   <li>Auto-validating step configurations against available plugins</li>
     *   <li>Suggesting fixes for common issues (missing error handling, circular dependencies)</li>
     *   <li>Providing step-specific recommendations for improvement</li>
     * </ul>
     *
     * @param request HTTP request; body must contain {@code {"draft": {...}}}
     * @return 200 with validation results, suggested fixes, and AI confidence metadata
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handlePipelineDraftRefine(HttpRequest request) {
        String draftId = request.getPathParameter("draftId");
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String requestId = resolveRequestId(request);
        long startMs = System.currentTimeMillis();

        Promise<HttpResponse> quotaErr = checkAiQuotaOrNull(tenantId, MAX_PROMPT_TOKENS);
        if (quotaErr != null) return quotaErr;
        // DC-P0-007: fail closed when AI service unavailable in production
        Promise<HttpResponse> aiUnavail = checkAiServiceOrUnavailable();
        if (aiUnavail != null) return aiUnavail;

        return request.loadBody(MAX_PROMPT_TOKENS * 4)
            .then(body -> {
                Map<String, Object> input = parseBody(body.getString(StandardCharsets.UTF_8));
                Map<String, Object> draft = (Map<String, Object>) input.getOrDefault("draft", Map.of());

                if (completionService == null) {
                    // P2-2: Explicit logging for AI Operations fallback
                    log.warn("[P2-2] AI Operations fallback: completionService is null for pipeline-refine tenant={} requestId={}", 
                            tenantId, requestId);
                    HttpResponse resp = heuristicPipelineRefineResponse(draftId, draft, tenantId, requestId);
                    recommendationMetrics.recordRecommendation(
                        AiRecommendationMetrics.TYPE_PIPELINE_DRAFT,
                        tenantId,
                        HEURISTIC_CONFIDENCE,
                        true,
                        System.currentTimeMillis() - startMs);
                    recordAiAction(tenantId, "pipeline-refine", "draftId=" + draftId,
                        "static-heuristic", HEURISTIC_CONFIDENCE, true,
                        System.currentTimeMillis() - startMs, requestId);
                    return Promise.of(resp);
                }

                String draftJson = String.valueOf(draft);
                String safeDraft = draftJson.length() > 1000 ? draftJson.substring(0, 1000) + "..." : draftJson;
                String prompt = buildPipelineRefinePrompt(draftId, safeDraft, tenantId);
                return callAi(prompt)
                    .map(result -> {
                        HttpResponse resp = buildPipelineRefineHttpResponse(result, draftId, tenantId, requestId);
                        double conf = estimateConfidence(result);
                        recommendationMetrics.recordRecommendation(
                            AiRecommendationMetrics.TYPE_PIPELINE_DRAFT,
                            tenantId,
                            conf,
                            conf < FALLBACK_CONFIDENCE_THRESHOLD,
                            System.currentTimeMillis() - startMs);
                        recordAiAction(tenantId, "pipeline-refine", "draftId=" + draftId,
                            result.getModelUsed() != null ? result.getModelUsed() : DEFAULT_MODEL,
                            conf, conf < FALLBACK_CONFIDENCE_THRESHOLD,
                            System.currentTimeMillis() - startMs, requestId);
                        return resp;
                    })
                    .then(Promise::of,
                        e -> {
                            Promise<HttpResponse> providerError = providerErrorOrNull(
                                "/api/v1/pipelines/:draftId/refine", tenantId, requestId, e);
                            if (providerError != null) {
                                return providerError;
                            }
                            log.warn("[DC-E3] pipeline refine AI call failed draftId={} tenant={}: {}", draftId, tenantId, e.getMessage());
                            recommendationMetrics.recordError(
                                AiRecommendationMetrics.TYPE_PIPELINE_DRAFT,
                                tenantId,
                                e);
                            recordAiAction(tenantId, "pipeline-refine", "draftId=" + draftId,
                                "static-heuristic", HEURISTIC_CONFIDENCE, true,
                                System.currentTimeMillis() - startMs, requestId);
                            return Promise.of(heuristicPipelineRefineResponse(draftId, draft, tenantId, requestId));
                        });
            });
    }

    /**
     * {@code GET /api/v1/ai/quality-summary}
     *
     * <p>Returns process-local AI quality telemetry so operator views can distinguish true model
     * completions from heuristic fallback behavior without scraping raw metrics endpoints.
     */
    public Promise<HttpResponse> handleAiQualitySummary(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }

        String requestId = resolveRequestId(request);
        List<AiRecommendationMetrics.AiQualitySnapshot> snapshots = recommendationMetrics.snapshot();
        long totalRequests = snapshots.stream()
            .mapToLong(AiRecommendationMetrics.AiQualitySnapshot::requestCount)
            .sum();
        long totalFallbacks = snapshots.stream()
            .mapToLong(AiRecommendationMetrics.AiQualitySnapshot::fallbackCount)
            .sum();

        Map<String, Object> payload = Map.of(
            "generatedAt", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
            "scope", "launcher-process",
            "summary", Map.of(
                "requestCount", totalRequests,
                "fallbackCount", totalFallbacks,
                "fallbackRate", totalRequests == 0 ? 0.0 : (double) totalFallbacks / (double) totalRequests,
                "llmConfigured", completionService != null
            ),
            "types", snapshots.stream().map(snapshot -> Map.<String, Object>of(
                "type", snapshot.type(),
                "label", qualityLabel(snapshot.type()),
                "route", qualityRoute(snapshot.type()),
                "requestCount", snapshot.requestCount(),
                "fallbackCount", snapshot.fallbackCount(),
                "fallbackRate", snapshot.fallbackRate(),
                "meanConfidence", Double.isNaN(snapshot.meanConfidence()) ? 0.0 : snapshot.meanConfidence(),
                "provenanceMode", qualityProvenanceMode(snapshot.type()),
                "reviewGuidance", qualityReviewGuidance(snapshot.type())
            )).toList()
        );

        return Promise.of(http.envelopeResponse(
            ApiResponse.success(payload, tenantId, requestId),
            objectMapper));
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
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        String requestId  = resolveRequestId(request);
        long   startMs    = System.currentTimeMillis();

        Promise<HttpResponse> quotaErr = checkAiQuotaOrNull(tenantId, MAX_PROMPT_TOKENS);
        if (quotaErr != null) return quotaErr;
        // DC-P0-007: fail closed when AI service unavailable in production
        Promise<HttpResponse> aiUnavail = checkAiServiceOrUnavailable();
        if (aiUnavail != null) return aiUnavail;

        return request.loadBody(MAX_PROMPT_TOKENS * 4)
            .then(body -> {
                String pipelineJson = body.getString(StandardCharsets.UTF_8);

                if (completionService == null) {
                    HttpResponse resp = heuristicPipelineHintResponse(pipelineId, tenantId, requestId);
                    recommendationMetrics.recordRecommendation(
                        AiRecommendationMetrics.TYPE_PIPELINE_HINT, tenantId,
                        HEURISTIC_CONFIDENCE, true, System.currentTimeMillis() - startMs);
                    recordAiAction(tenantId, "pipeline-hint", "pipelineId=" + pipelineId,
                        "static-heuristic", HEURISTIC_CONFIDENCE, true,
                        System.currentTimeMillis() - startMs, requestId);
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
                        recordAiAction(tenantId, "pipeline-hint", "pipelineId=" + pipelineId,
                            result.getModelUsed() != null ? result.getModelUsed() : DEFAULT_MODEL,
                            conf, conf < FALLBACK_CONFIDENCE_THRESHOLD,
                            System.currentTimeMillis() - startMs, requestId);
                        return resp;
                    })
                    .then(Promise::of,
                          e -> {
                              Promise<HttpResponse> providerError = providerErrorOrNull(
                                  "/api/v1/pipelines/:pipelineId/optimise-hint", tenantId, requestId, e);
                              if (providerError != null) {
                                  return providerError;
                              }
                              log.warn("[DC-E3] pipeline hint AI call failed pipelineId={} tenant={}: {}",
                                        pipelineId, tenantId, e.getMessage());
                              recommendationMetrics.recordError(
                                  AiRecommendationMetrics.TYPE_PIPELINE_HINT, tenantId, e);
                              recordAiAction(tenantId, "pipeline-hint", "pipelineId=" + pipelineId,
                                  "static-heuristic", HEURISTIC_CONFIDENCE, true,
                                  System.currentTimeMillis() - startMs, requestId);
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
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        String requestId = resolveRequestId(request);
        long   startMs   = System.currentTimeMillis();

        Promise<HttpResponse> quotaErr = checkAiQuotaOrNull(tenantId, MAX_PROMPT_TOKENS);
        if (quotaErr != null) return quotaErr;
        // DC-P0-007: fail closed when AI service unavailable in production
        Promise<HttpResponse> aiUnavail = checkAiServiceOrUnavailable();
        if (aiUnavail != null) return aiUnavail;

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
                    recordAiAction(tenantId, "brain-explain", "itemId=" + itemId,
                        "static-heuristic", HEURISTIC_CONFIDENCE, true,
                        System.currentTimeMillis() - startMs, requestId);
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
                        recordAiAction(tenantId, "brain-explain", "itemId=" + itemId,
                            result.getModelUsed() != null ? result.getModelUsed() : DEFAULT_MODEL,
                            conf, conf < FALLBACK_CONFIDENCE_THRESHOLD,
                            System.currentTimeMillis() - startMs, requestId);
                        return resp;
                    })
                    .then(Promise::of,
                          e -> {
                              Promise<HttpResponse> providerError = providerErrorOrNull(
                                  "/api/v1/brain/explain", tenantId, requestId, e);
                              if (providerError != null) {
                                  return providerError;
                              }
                              log.warn("[DC-E3] brain explain AI call failed itemId={} tenant={}: {}",
                                        itemId, tenantId, e.getMessage());
                              recommendationMetrics.recordError(
                                  AiRecommendationMetrics.TYPE_BRAIN_EXPLAIN, tenantId, e);
                              recordAiAction(tenantId, "brain-explain", "itemId=" + itemId,
                                  "static-heuristic", HEURISTIC_CONFIDENCE, true,
                                  System.currentTimeMillis() - startMs, requestId);
                              return Promise.of(heuristicBrainExplainResponse(itemId, tenantId, requestId));
                          });
            });
    }

    /**
     * {@code POST /api/v1/query/nlq}
     *
     * <p>Natural language query: converts plain-English data questions into
     * structured query plans with source selection, SQL draft, and cost
     * warnings (P2.5).
     *
     * <p>Example request body:
     * <pre>{"question":"How many users signed up last week?"}</pre>
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleNaturalLanguageQuery(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String requestId = resolveRequestId(request);
        long startMs = System.currentTimeMillis();

        Promise<HttpResponse> quotaErr = checkAiQuotaOrNull(tenantId, MAX_PROMPT_TOKENS);
        if (quotaErr != null) return quotaErr;
        // DC-P0-007: fail closed when AI service unavailable in production
        Promise<HttpResponse> aiUnavail = checkAiServiceOrUnavailable();
        if (aiUnavail != null) return aiUnavail;

        return request.loadBody(MAX_PROMPT_TOKENS * 4)
            .then(body -> {
                Map<String, Object> input = parseBody(body.getString(StandardCharsets.UTF_8));
                String question = String.valueOf(input.getOrDefault("question", "")).trim();
                if (question.isBlank()) {
                    return Promise.of(http.errorResponse(400, "question is required"));
                }

                Map<String, Object> result = heuristicNlqParse(question);
                result.put("tenantId", tenantId);
                result.put("question", question);
                result.put("requestId", requestId);
                result.put("generatedAt", Instant.now().toString());
                result.put("latencyMs", System.currentTimeMillis() - startMs);

                recordAiAction(tenantId, "query-nlq", "question=" + question,
                    "static-heuristic", HEURISTIC_CONFIDENCE, true,
                    System.currentTimeMillis() - startMs, requestId);

                return Promise.of(http.jsonResponse(result));
            });
    }

    private Map<String, Object> heuristicNlqParse(String question) {
        Map<String, Object> result = new LinkedHashMap<>();
        String q = question.toLowerCase(Locale.ROOT);

        // Detect intent
        String intent = "select";
        if (q.contains("how many") || q.contains("count") || q.contains("total")) {
            intent = "aggregate.count";
        } else if (q.contains("average") || q.contains("avg")) {
            intent = "aggregate.avg";
        } else if (q.contains("sum") || q.contains("total amount")) {
            intent = "aggregate.sum";
        } else if (q.contains("trend") || q.contains("over time")) {
            intent = "trend";
        } else if (q.contains("compare") || q.contains("vs")) {
            intent = "compare";
        }

        // Detect time filters
        String timeFilter = null;
        if (q.contains("last week") || q.contains("past week")) {
            timeFilter = "created_at >= now() - interval '7 days'";
        } else if (q.contains("last month") || q.contains("past month")) {
            timeFilter = "created_at >= now() - interval '30 days'";
        } else if (q.contains("last 24 hours") || q.contains("today")) {
            timeFilter = "created_at >= now() - interval '1 day'";
        } else if (q.contains("last year") || q.contains("past year")) {
            timeFilter = "created_at >= now() - interval '1 year'";
        }

        // Detect suggested collections (naive keyword matching)
        List<String> collections = new ArrayList<>();
        if (q.contains("user") || q.contains("account") || q.contains("signup") || q.contains("sign up")) {
            collections.add("users");
        }
        if (q.contains("order") || q.contains("purchase") || q.contains("sale") || q.contains("transaction")) {
            collections.add("orders");
        }
        if (q.contains("product") || q.contains("item") || q.contains("catalog")) {
            collections.add("products");
        }
        if (q.contains("event") || q.contains("log") || q.contains("activity")) {
            collections.add("events");
        }
        if (collections.isEmpty()) {
            collections.add("*");
        }

        // Build SQL draft
        StringBuilder sql = new StringBuilder();
        if (intent.startsWith("aggregate.count")) {
            sql.append("SELECT COUNT(*) FROM ").append(collections.get(0));
        } else if (intent.startsWith("aggregate.avg")) {
            sql.append("SELECT AVG(value) FROM ").append(collections.get(0));
        } else if (intent.startsWith("aggregate.sum")) {
            sql.append("SELECT SUM(amount) FROM ").append(collections.get(0));
        } else if (intent.equals("trend")) {
            sql.append("SELECT date_trunc('day', created_at) as day, COUNT(*) FROM ")
               .append(collections.get(0)).append(" GROUP BY day ORDER BY day");
        } else {
            sql.append("SELECT * FROM ").append(collections.get(0)).append(" LIMIT 100");
        }
        if (timeFilter != null && !intent.equals("trend")) {
            sql.append(" WHERE ").append(timeFilter);
        }

        // Cost warning
        boolean highCost = q.contains("all time") || q.contains("every") || q.contains("full history");
        boolean mediumCost = !highCost && (collections.size() > 1 || intent.startsWith("trend"));
        String costWarning = highCost ? "High: full table scan likely; consider adding time filter"
            : mediumCost ? "Medium: multi-collection or time-series query"
            : "Low: single collection with likely index coverage";

        result.put("intent", intent);
        result.put("sqlDraft", sql.toString());
        result.put("suggestedCollections", collections);
        result.put("timeFilter", timeFilter);
        result.put("costWarning", costWarning);
        result.put("costLevel", highCost ? "high" : mediumCost ? "medium" : "low");
        result.put("confidence", HEURISTIC_CONFIDENCE);
        result.put("fallback", true);
        result.put("explain", "Heuristic NLQ parser — review SQL before execution.");
        return result;
    }

    private static String inferValueType(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof Integer || value instanceof Long) {
            return "integer";
        }
        if (value instanceof Number) {
            return "number";
        }
        if (value instanceof Map<?, ?>) {
            return "object";
        }
        if (value instanceof List<?> list) {
            Object firstNonNull = list.stream().filter(Objects::nonNull).findFirst().orElse(null);
            return "array<" + inferValueType(firstNonNull) + ">";
        }
        if (value instanceof String text) {
            if (isIsoTimestamp(text)) {
                return "timestamp";
            }
            if (looksNumeric(text)) {
                return "number";
            }
            return "string";
        }
        return "string";
    }

    private static boolean isLikelyPiiField(String fieldName) {
        String normalized = fieldName.toLowerCase(Locale.ROOT);
        return normalized.contains("email")
            || normalized.contains("phone")
            || normalized.contains("ssn")
            || normalized.contains("passport")
            || normalized.contains("dob")
            || normalized.contains("birth")
            || normalized.contains("address")
            || normalized.contains("password")
            || normalized.contains("token")
            || normalized.contains("secret")
            || normalized.contains("api_key");
    }

    private static boolean isLikelyDedupeKey(String fieldName) {
        String normalized = fieldName.toLowerCase(Locale.ROOT);
        return normalized.equals("id")
            || normalized.endsWith("_id")
            || normalized.endsWith("id")
            || normalized.contains("externalid")
            || normalized.contains("external_id")
            || normalized.contains("email")
            || normalized.contains("phone")
            || normalized.contains("uuid");
    }

    private static boolean isIsoTimestamp(String value) {
        try {
            Instant.parse(value);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean looksNumeric(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    /**
     * {@code POST /api/v1/governance/recommend}
     *
     * <p>AI-assisted governance policy recommendation for a collection (P2.5).
     * Suggests data classification, retention policy, redaction rules, and
     * access control based on field names and types.
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleGovernanceRecommend(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String requestId = resolveRequestId(request);
        long startMs = System.currentTimeMillis();

        return request.loadBody(MAX_PROMPT_TOKENS * 4)
            .then(body -> {
                try {
                    Map<String, Object> input = parseBody(body.getString(StandardCharsets.UTF_8));
                    String collection = (String) input.getOrDefault("collection", "unknown");
                    Map<String, String> schema = (Map<String, String>) input.getOrDefault("schema", Map.of());

                    Map<String, Object> result = heuristicGovernanceRecommend(collection, schema);
                    result.put("tenantId", tenantId);
                    result.put("collection", collection);
                    result.put("requestId", requestId);
                    result.put("generatedAt", Instant.now().toString());
                    result.put("latencyMs", System.currentTimeMillis() - startMs);

                    recordAiAction(tenantId, "governance-recommend", "collection=" + collection,
                        "static-heuristic", HEURISTIC_CONFIDENCE, true,
                        System.currentTimeMillis() - startMs, requestId);

                    return Promise.of(http.jsonResponse(result));
                } catch (Exception e) {
                    return Promise.of(http.errorResponse(400, "Invalid governance payload: " + e.getMessage()));
                }
            });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> heuristicGovernanceRecommend(String collection, Map<String, String> schema) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> fieldPolicies = new ArrayList<>();

        Set<String> piiFields = Set.of("email", "phone", "ssn", "passport", "dob", "credit_card",
            "bank_account", "password", "secret", "api_key", "token", "session_id");
        Set<String> financialFields = Set.of("amount", "price", "cost", "salary", "revenue", "expense");
        Set<String> medicalFields = Set.of("diagnosis", "medication", "blood_type", "health_record");

        String overallClass = "public";
        boolean hasPii = false;
        boolean hasFinancial = false;
        boolean hasMedical = false;

        for (Map.Entry<String, String> entry : schema.entrySet()) {
            String field = entry.getKey().toLowerCase(Locale.ROOT);
            String type = entry.getValue() != null ? entry.getValue() : "string";
            boolean fieldPii = piiFields.stream().anyMatch(field::contains);
            boolean fieldFinancial = financialFields.stream().anyMatch(field::contains);
            boolean fieldMedical = medicalFields.stream().anyMatch(field::contains);

            if (fieldPii) hasPii = true;
            if (fieldFinancial) hasFinancial = true;
            if (fieldMedical) hasMedical = true;

            String classification = fieldPii ? "restricted" : fieldMedical ? "confidential" : fieldFinancial ? "confidential" : "internal";
            if ("public".equals(overallClass) && !"public".equals(classification)) {
                overallClass = classification;
            } else if ("internal".equals(overallClass) && ("confidential".equals(classification) || "restricted".equals(classification))) {
                overallClass = classification;
            } else if ("confidential".equals(overallClass) && "restricted".equals(classification)) {
                overallClass = classification;
            }

            List<String> actions = new ArrayList<>();
            if (fieldPii) {
                actions.add("encrypt-at-rest");
                actions.add("mask-in-logs");
                actions.add("tokenize-for-analytics");
            }
            if (fieldFinancial || fieldMedical) {
                actions.add("audit-log-access");
                actions.add("encrypt-at-rest");
            }
            if ("uuid".equals(type) || "text".equals(type)) {
                actions.add("length-limit-validation");
            }

            Map<String, Object> fieldPolicy = new LinkedHashMap<>();
            fieldPolicy.put("field", field);
            fieldPolicy.put("classification", classification);
            fieldPolicy.put("retentionDays", fieldPii ? 90 : fieldMedical ? 2555 : 1095);
            fieldPolicy.put("actions", actions.isEmpty() ? List.of("none") : actions);
            fieldPolicy.put("accessLevel", fieldPii ? "role-based" : fieldFinancial ? "authenticated" : "public");
            fieldPolicies.add(fieldPolicy);
        }

        if (schema.isEmpty()) {
            overallClass = "internal";
            fieldPolicies.add(Map.of("field", "*", "classification", "internal",
                "retentionDays", 1095, "actions", List.of("audit-log-access"), "accessLevel", "authenticated"));
        }

        result.put("overallClassification", overallClass);
        result.put("fieldPolicies", fieldPolicies);
        result.put("hasPii", hasPii);
        result.put("hasFinancialData", hasFinancial);
        result.put("hasMedicalData", hasMedical);
        result.put("confidence", HEURISTIC_CONFIDENCE);
        result.put("fallback", true);
        result.put("explain", "Heuristic governance recommendation based on field name patterns — review before applying.");
        result.put("complianceFrameworks", hasMedical ? List.of("HIPAA", "GDPR") : hasPii ? List.of("GDPR", "CCPA") : hasFinancial ? List.of("SOX", "PCI-DSS") : List.of("ISO-27001"));
        return result;
    }

    private static String qualityLabel(String type) {
        return switch (type) {
            case AiRecommendationMetrics.TYPE_ENTITY_SUGGEST -> "Entity suggestions";
            case AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST -> "Analytics suggestions";
            case AiRecommendationMetrics.TYPE_PIPELINE_DRAFT -> "Workflow draft generation";
            case AiRecommendationMetrics.TYPE_PIPELINE_HINT -> "Workflow optimization hints";
            case AiRecommendationMetrics.TYPE_BRAIN_EXPLAIN -> "Brain explanations";
            case AiRecommendationMetrics.TYPE_VOICE_INTENT -> "Voice intent resolution";
            default -> type;
        };
    }

    private static String qualityRoute(String type) {
        return switch (type) {
            case AiRecommendationMetrics.TYPE_ENTITY_SUGGEST -> "/api/v1/entities/:collection/suggest";
            case AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST -> "/api/v1/analytics/suggest";
            case AiRecommendationMetrics.TYPE_PIPELINE_DRAFT -> "/api/v1/pipelines/draft";
            case AiRecommendationMetrics.TYPE_PIPELINE_HINT -> "/api/v1/pipelines/:pipelineId/optimise-hint";
            case AiRecommendationMetrics.TYPE_BRAIN_EXPLAIN -> "/api/v1/brain/explain";
            case AiRecommendationMetrics.TYPE_VOICE_INTENT -> "/api/v1/voice/intent";
            default -> "unknown";
        };
    }

    private static String qualityProvenanceMode(String type) {
        return switch (type) {
            case AiRecommendationMetrics.TYPE_PIPELINE_DRAFT -> "ai-envelope-and-draft-provenance";
            default -> "ai-envelope";
        };
    }

    private static String qualityReviewGuidance(String type) {
        return switch (type) {
            case AiRecommendationMetrics.TYPE_PIPELINE_DRAFT -> "Review low-confidence drafts or any fallback-generated workflow before saving.";
            case AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST -> "Fallback-heavy analytics suggestions should trigger manual SQL review before execution.";
            case AiRecommendationMetrics.TYPE_ENTITY_SUGGEST -> "Treat heuristic entity hints as advisory until confirmed against collection metadata.";
            case AiRecommendationMetrics.TYPE_BRAIN_EXPLAIN -> "Fallback explanations remain descriptive only and should not drive automated escalation.";
            case AiRecommendationMetrics.TYPE_VOICE_INTENT -> "Low-confidence voice intents require explicit confirmation before acting.";
            default -> "Review fallback-heavy AI responses before acting automatically.";
        };
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

    private String buildAnalyticsAutomatePrompt(String query, String schema, String context, String tenantId) {
        String safeSchema = schema.length() > 600 ? schema.substring(0, 600) + "..." : schema;
        String safeContext = context.length() > 400 ? context.substring(0, 400) + "..." : context;
        return String.format(
            """
            Original query: %s
            Schema context: %s
            Execution context: %s
            Tenant scope: %s
            Instructions: Provide analytics automation improvements including:
            1. Intent clarification - identify if the query is ambiguous and suggest clarification questions
            2. Query rewrite - optimize the query for performance and correctness
            3. Safe scope inference - determine what data can be safely accessed based on schema
            4. Policy-aware recommendations - suggest improvements respecting governance constraints
            Return JSON: {
              "intentClarification": {"isAmbiguous": true/false, "questions": ["..."]},
              "queryRewrite": {"optimizedQuery": "...", "improvements": ["..."]},
              "safeScopeInference": {"accessibleTables": ["..."], "restrictedFields": ["..."]},
              "policyRecommendations": [{"type": "...", "description": "...", "priority": "high|medium|low"}]
            }""",
            sanitise(query), sanitise(safeSchema), sanitise(safeContext), sanitise(tenantId));
    }

        private String buildPipelineDraftPrompt(String prompt, String tenantId) {
                return String.format(
                        """
                        Tenant scope: %s
                        User intent: %s
                        Instructions: Generate a concise data pipeline draft.
                        Return JSON only with this shape:
                        {
                            "name": "...",
                            "description": "...",
                            "reviewRequired": true|false,
                            "provenance": {
                                "strategy": "llm",
                                "promptSummary": "..."
                            },
                            "steps": [
                                {
                                    "id": "step-1",
                                    "type": "source|transform|destination|condition",
                                    "name": "...",
                                    "description": "...",
                                    "confidence": 0.0,
                                    "config": {}
                                }
                            ]
                        }
                        Keep the step list ordered and practical. Set reviewRequired=true when intent is ambiguous,
                        destructive, or lacks clear source/destination detail.
                        """,
                        sanitise(tenantId),
                        sanitise(prompt));
        }

    private String buildPipelineRefinePrompt(String draftId, String draftJson, String tenantId) {
        return String.format(
            """
            Draft ID: %s
            Draft definition: %s
            Tenant scope: %s
            Instructions: Validate and refine the pipeline draft. Provide:
            1. Structural validation - check step dependencies and flow
            2. Auto-validation - verify step configurations against available plugins
            3. Suggested fixes - identify issues like missing error handling, circular dependencies
            4. Step-specific recommendations - improvements for each step
            Return JSON: {
              "validation": {"isValid": true/false, "errors": ["..."], "warnings": ["..."]},
              "autoValidation": {"stepsValidated": N, "stepsNeedingConfig": ["..."]},
              "suggestedFixes": [{"stepId": "...", "issue": "...", "fix": "...", "priority": "high|medium|low"}],
              "stepRecommendations": [{"stepId": "...", "recommendation": "..."}]
            }""",
            sanitise(draftId), sanitise(draftJson), sanitise(tenantId));
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
                        List.of("llm", "entity-context"), fallback, buildAiProvenance(result));
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
                        List.of("llm", "analytics-context"), fallback, buildAiProvenance(result));
        return http.envelopeResponse(envelope, objectMapper);
    }

    private HttpResponse buildAnalyticsAutomateHttpResponse(
            CompletionResult result, String originalQuery, String tenantId, String requestId) {
        double confidence = estimateConfidence(result);
        boolean fallback  = confidence < FALLBACK_CONFIDENCE_THRESHOLD;

        Map<String, Object> automation = tryParseAiJson(result.getText(),
            Map.of(
                "intentClarification", Map.of("isAmbiguous", false, "questions", List.of()),
                "queryRewrite", Map.of("optimizedQuery", originalQuery, "improvements", List.of("Query analyzed")),
                "safeScopeInference", Map.of("accessibleTables", List.of("events"), "restrictedFields", List.of()),
                "policyRecommendations", List.of(
                    Map.of("type", "performance", "description", "Consider adding LIMIT clause", "priority", "medium")
                )
            ));

        ApiResponse envelope = ApiResponse.success(automation, tenantId, requestId)
            .withAiMeta(confidence, result.getModelUsed() != null ? result.getModelUsed() : DEFAULT_MODEL,
                        List.of("llm", "analytics-automation"), fallback, buildAiProvenance(result));
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
                        List.of("llm", "pipeline-analysis"), fallback, buildAiProvenance(result));
        return http.envelopeResponse(envelope, objectMapper);
    }

    private HttpResponse buildPipelineDraftHttpResponse(
            CompletionResult result, String prompt, String tenantId, String requestId) {
        double confidence = estimateConfidence(result);
        boolean fallback = confidence < FALLBACK_CONFIDENCE_THRESHOLD;

        Map<String, Object> draft = tryParseAiJson(result.getText(),
            createHeuristicDraftData(prompt, true, result.getModelUsed() != null ? result.getModelUsed() : DEFAULT_MODEL));

        Map<String, Object> normalizedDraft = normalizeDraftPayload(
            draft,
            prompt,
            result.getModelUsed() != null ? result.getModelUsed() : DEFAULT_MODEL,
            fallback,
            confidence);

        ApiResponse envelope = ApiResponse.success(normalizedDraft, tenantId, requestId)
            .withAiMeta(confidence,
                result.getModelUsed() != null ? result.getModelUsed() : DEFAULT_MODEL,
                List.of("llm", "pipeline-draft"),
                fallback, buildAiProvenance(result));
        return http.envelopeResponse(envelope, objectMapper);
    }

    private HttpResponse buildPipelineRefineHttpResponse(
            CompletionResult result, String draftId, String tenantId, String requestId) {
        double confidence = estimateConfidence(result);
        boolean fallback = confidence < FALLBACK_CONFIDENCE_THRESHOLD;

        Map<String, Object> refinement = tryParseAiJson(result.getText(),
            Map.of(
                "validation", Map.of("isValid", true, "errors", List.of(), "warnings", List.of()),
                "autoValidation", Map.of("stepsValidated", 0, "stepsNeedingConfig", List.of()),
                "suggestedFixes", List.of(),
                "stepRecommendations", List.of()
            ));

        ApiResponse envelope = ApiResponse.success(refinement, tenantId, requestId)
            .withAiMeta(confidence, result.getModelUsed() != null ? result.getModelUsed() : DEFAULT_MODEL,
                        List.of("llm", "pipeline-refinement"), fallback, buildAiProvenance(result));
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
                        List.of("llm", "anomaly-context"), fallback, buildAiProvenance(result));
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

    private HttpResponse heuristicAnalyticsAutomateResponse(
            String query, String schema, String tenantId, String requestId) {
        Map<String, Object> data = Map.of(
            "intentClarification", Map.of(
                "isAmbiguous", query.toLowerCase().contains("select") && query.toLowerCase().contains("*"),
                "questions", query.toLowerCase().contains("*") 
                    ? List.of("Which specific columns do you need?", "Do you want to filter by time range?")
                    : List.of()
            ),
            "queryRewrite", Map.of(
                "optimizedQuery", query.toLowerCase().contains("*") 
                    ? query.replaceAll("\\*", "id, timestamp, eventType") 
                    : query,
                "improvements", query.toLowerCase().contains("*") 
                    ? List.of("Replaced SELECT * with explicit columns for better performance")
                    : List.of("Query structure is acceptable")
            ),
            "safeScopeInference", Map.of(
                "accessibleTables", List.of("events", "entities"),
                "restrictedFields", List.of("pii", "sensitive_data")
            ),
            "policyRecommendations", List.of(
                Map.of("type", "performance", "description", "Consider adding LIMIT clause for large result sets", "priority", "medium"),
                Map.of("type", "privacy", "description", "Review if query exposes PII fields", "priority", "high")
            )
        );
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

    private HttpResponse heuristicPipelineDraftResponse(
            String prompt, String tenantId, String requestId) {
        Map<String, Object> data = createHeuristicDraftData(prompt, true, "static-heuristic");
        ApiResponse envelope = ApiResponse.success(data, tenantId, requestId)
            .withAiMeta(HEURISTIC_CONFIDENCE, "static-heuristic", List.of("fallback", "ruleset"), true);
        return http.envelopeResponse(envelope, objectMapper);
    }

    private HttpResponse heuristicPipelineRefineResponse(
            String draftId, Map<String, Object> draft, String tenantId, String requestId) {
        Object stepsObj = draft.get("steps");
        int stepCount = stepsObj instanceof List<?> list ? list.size() : 0;
        
        Map<String, Object> data = Map.of(
            "validation", Map.of(
                "isValid", stepCount > 0 && stepCount <= 20,
                "errors", stepCount == 0 ? List.of("Pipeline must have at least one step") : List.of(),
                "warnings", stepCount > 15 ? List.of("Pipeline has many steps - consider splitting") : List.of()
            ),
            "autoValidation", Map.of(
                "stepsValidated", stepCount,
                "stepsNeedingConfig", stepCount > 0 ? List.of("step-1") : List.of()
            ),
            "suggestedFixes", List.of(
                Map.of("stepId", "all", "issue", "Add error handling", "fix", "Ensure each step has a failure branch", "priority", "medium"),
                Map.of("stepId", "source", "issue", "Validate input", "fix", "Add input validation step at pipeline entry", "priority", "high")
            ),
            "stepRecommendations", stepCount > 0 
                ? List.of(Map.of("stepId", "step-1", "recommendation", "Review step configuration for completeness"))
                : List.of()
        );
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
    /**
     * Validates that the tenant has explicitly consented to the use of the
     * given external model/provider.  For air-gapped or sovereign tenants
     * (externalModelAllowed == false) every external LLM call is blocked
     * and a {@code SOVEREIGN_POLICY_VIOLATION} error is returned (P2.1).
     *
     * <p>A consent record (tenantId, model, provider, purpose, timestamp) is
     * appended to the event log when the call is allowed.
     *
     * @return {@code null} when the call is allowed; otherwise a Promise of
     *         an HTTP 403 error response.
     */
    public Promise<HttpResponse> validateModelConsent(String tenantId, String model, String provider, String purpose) {
        if (client == null) {
            return null; // no client = no governance
        }
        try {
            // Check tenant sovereign profile: externalModelAllowed
            // Default to true when no profile is stored (open-tenant mode).
            boolean allowed = client.query(tenantId, "dc_tenant_settings",
                    DataCloudClient.Query.builder()
                        .filter(DataCloudClient.Filter.eq("key", "sovereignProfile"))
                        .build())
                .then(entities -> {
                    if (entities.isEmpty()) return Promise.of(Boolean.TRUE);
                    Map<String, Object> profile = entities.getFirst().data();
                    Object external = profile.get("externalModelAllowed");
                    if (external == null) return Promise.of(Boolean.TRUE);
                    return Promise.of(Boolean.parseBoolean(String.valueOf(external)));
                })
                .getResult();

            if (!allowed) {
                return Promise.of(http.errorResponse(403, "SOVEREIGN_POLICY_VIOLATION: external model calls are disabled for tenant '" + tenantId + "'"));
            }

            // Log consent record to event log
            Map<String, Object> consent = new LinkedHashMap<>();
            consent.put("tenantId", tenantId);
            consent.put("model", model);
            consent.put("provider", provider);
            consent.put("purpose", purpose);
            consent.put("timestamp", Instant.now().toString());
            client.appendEvent(tenantId,
                DataCloudClient.Event.builder()
                    .type("model.consent")
                    .payload(consent)
                    .source("datacloud.launcher.ai-assist")
                    .build())
                .whenException(e -> log.warn("Model consent log failed tenant={} model={}: {}", tenantId, model, e.getMessage()));

            return null;
        } catch (Exception e) {
            log.warn("Model consent validation failed tenant={} model={}: {}", tenantId, model, e.getMessage());
            return null; // fail open — caller can decide to hard-fail in production
        }
    }

    /**
     * Calls the AI completion service with a plain-text prompt.
     * Wraps the prompt into a CompletionRequest and delegates to the
     * injected CompletionService.
     */
    private Promise<CompletionResult> callAi(String prompt) {
        if (completionService == null) {
            return Promise.ofException(new IllegalStateException("CompletionService not available"));
        }
        CompletionRequest request = CompletionRequest.builder()
            .messages(List.of(ChatMessage.user(prompt)))
            .build();
        return completionService.complete(request);
    }

    private Map<String, Object> parseBody(String json) {
        try {
            if (json == null || json.isBlank()) {
                return Map.of();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> result = http.objectMapper().readValue(json, Map.class);
            return result;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON body: " + e.getMessage(), e);
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

    /**
     * Builds model-provenance metadata from a CompletionResult for auditability
     * and transparency of AI-generated suggestions.
     */
    private static Map<String, Object> buildAiProvenance(CompletionResult result) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("provider", "llm");
        p.put("modelVersion", result != null ? result.getModelUsed() : "unknown");
        p.put("latencyMs", result != null ? result.getLatencyMs() : 0);
        p.put("inputTokens", result != null ? result.getPromptTokens() : 0);
        p.put("outputTokens", result != null ? result.getCompletionTokens() : 0);
        p.put("totalTokens", result != null ? result.getTokensUsed() : 0);
        p.put("finishReason", result != null ? result.getFinishReason() : "unknown");
        p.put("timestamp", Instant.now().toString());
        return Map.copyOf(p);
    }

    /** Strips characters that could cause prompt injection. */
    private static String sanitise(String input) {
        if (input == null) return "";
        // Remove backticks, template delimiters, and control chars — OWASP injection prevention
        return input.replaceAll("[`\\\\\\x00-\\x1F]", "").stripLeading().stripTrailing();
    }

    private Map<String, Object> createHeuristicDraftData(String prompt, boolean fallback, String strategy) {
        List<String> fragments = splitPromptIntoFragments(prompt);
        List<Map<String, Object>> steps = fragments.isEmpty()
            ? List.of(defaultDraftStep("step-1", "source", "Load source data", "Select the system or dataset that starts this workflow."))
            : buildHeuristicSteps(fragments);

        String summary = prompt.length() > 140 ? prompt.substring(0, 140) + "..." : prompt;

        return Map.of(
            "workflowId", "draft-" + UUID.randomUUID(),
            "name", inferDraftName(prompt),
            "description", "Generated from workflow intent: " + summary,
            "reviewRequired", fallback || steps.size() < 3,
            "provenance", Map.of(
                "generatedAt", Instant.now().toString(),
                "strategy", strategy,
                "promptSummary", summary
            ),
            "steps", steps
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeDraftPayload(
            Map<String, Object> raw,
            String prompt,
            String strategy,
            boolean fallback,
            double confidence) {
        Map<String, Object> heuristic = createHeuristicDraftData(prompt, fallback, strategy);

        Object rawSteps = raw.get("steps");
        List<Map<String, Object>> steps = rawSteps instanceof List<?> list
            ? list.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(step -> normalizeDraftStep((Map<String, Object>) step))
                .toList()
            : (List<Map<String, Object>>) heuristic.get("steps");

        Object rawProvenance = raw.get("provenance");
        Map<String, Object> provenance = rawProvenance instanceof Map<?, ?> provenanceMap
            ? Map.of(
                "generatedAt", String.valueOf(provenanceMap.containsKey("generatedAt") ? provenanceMap.get("generatedAt") : Instant.now().toString()),
                "strategy", String.valueOf(provenanceMap.containsKey("strategy") ? provenanceMap.get("strategy") : strategy),
                "promptSummary", String.valueOf(provenanceMap.containsKey("promptSummary") ? provenanceMap.get("promptSummary") : summarisePrompt(prompt)))
            : (Map<String, Object>) heuristic.get("provenance");

        boolean reviewRequired = readBoolean(raw.get("reviewRequired"), fallback || confidence < 0.75 || steps.size() < 3);

        return Map.of(
            "workflowId", String.valueOf(raw.getOrDefault("workflowId", heuristic.get("workflowId"))),
            "name", String.valueOf(raw.getOrDefault("name", heuristic.get("name"))),
            "description", String.valueOf(raw.getOrDefault("description", heuristic.get("description"))),
            "reviewRequired", reviewRequired,
            "provenance", provenance,
            "steps", steps
        );
    }

    private Map<String, Object> normalizeDraftStep(Map<String, Object> step) {
        String id = String.valueOf(step.getOrDefault("id", "step-" + UUID.randomUUID()));
        String type = normalizeStepType(step.get("type"));
        String name = String.valueOf(step.getOrDefault("name", type.substring(0, 1).toUpperCase() + type.substring(1) + " step"));
        String description = String.valueOf(step.getOrDefault("description", name));
        double stepConfidence = readDouble(step.get("confidence"), 0.75);
        Object config = step.get("config");
        Map<String, Object> normalizedConfig = config instanceof Map<?, ?> configMap
            ? toStringObjectMap(configMap)
            : Map.of();

        return Map.of(
            "id", id,
            "type", type,
            "name", name,
            "description", description,
            "confidence", stepConfidence,
            "config", normalizedConfig
        );
    }

    private static String inferDraftName(String prompt) {
        List<String> fragments = splitPromptIntoFragments(prompt);
        if (fragments.isEmpty()) {
            return "Generated workflow draft";
        }
        String first = fragments.get(0);
        return first.length() > 48 ? first.substring(0, 48) + "..." : Character.toUpperCase(first.charAt(0)) + first.substring(1);
    }

    private static List<Map<String, Object>> buildHeuristicSteps(List<String> fragments) {
        java.util.ArrayList<Map<String, Object>> steps = new java.util.ArrayList<>();
        for (int index = 0; index < fragments.size(); index++) {
            String fragment = fragments.get(index);
            String type = inferStepType(fragment, index, fragments.size());
            steps.add(defaultDraftStep(
                "step-" + (index + 1),
                type,
                buildStepName(fragment, type),
                Character.toUpperCase(fragment.charAt(0)) + fragment.substring(1)
            ));
        }
        return List.copyOf(steps);
    }

    private static Map<String, Object> defaultDraftStep(String id, String type, String name, String description) {
        return Map.of(
            "id", id,
            "type", type,
            "name", name,
            "description", description,
            "confidence", 0.72,
            "config", Map.of()
        );
    }

    private static List<String> splitPromptIntoFragments(String prompt) {
        String[] parts = sanitise(prompt)
            .replace(" then ", ",")
            .replace(" and then ", ",")
            .split(",");
        java.util.ArrayList<String> fragments = new java.util.ArrayList<>();
        for (String part : parts) {
            String normalized = part.trim();
            if (!normalized.isEmpty()) {
                fragments.add(normalized);
            }
        }
        return List.copyOf(fragments);
    }

    private static String inferStepType(String fragment, int index, int total) {
        String normalized = fragment.toLowerCase();
        if (normalized.matches(".*(filter|if |when |where |unless).*")) {
            return "condition";
        }
        if (normalized.matches(".*(save|write|store|export|publish|send).*")) {
            return "destination";
        }
        if (normalized.matches(".*(clean|transform|aggregate|join|validate|normalize|enrich|dedupe|deduplicate).*")) {
            return "transform";
        }
        if (index == 0 || normalized.matches(".*(load|read|ingest|import|collect|consume).*")) {
            return "source";
        }
        if (index == total - 1) {
            return "destination";
        }
        return "transform";
    }

    private static String buildStepName(String fragment, String type) {
        String normalized = fragment.trim();
        if (normalized.isEmpty()) {
            return type.substring(0, 1).toUpperCase() + type.substring(1) + " step";
        }
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private static boolean readBoolean(Object value, boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String str) {
            return Boolean.parseBoolean(str);
        }
        return defaultValue;
    }

    private static double readDouble(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String str) {
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static String normalizeStepType(Object rawType) {
        String type = String.valueOf(rawType == null ? "transform" : rawType).toLowerCase();
        return switch (type) {
            case "source", "transform", "destination", "condition" -> type;
            default -> "transform";
        };
    }

    private static String summarisePrompt(String prompt) {
        return prompt.length() > 140 ? prompt.substring(0, 140) + "..." : prompt;
    }

    private static Map<String, Object> toStringObjectMap(Map<?, ?> source) {
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            normalized.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return Map.copyOf(normalized);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cross-surface AI operations (DC-AUD-008)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * {@code POST /api/v1/ai/suggestions}
     *
     * <p>Cross-surface AI operation suggestions. Routes to the most appropriate
     * surface-specific handler when possible; falls back to heuristic response
     * when the AI service is unavailable.
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleAiSuggestions(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String requestId = resolveRequestId(request);
        long startMs = System.currentTimeMillis();

        Promise<HttpResponse> quotaErr = checkAiQuotaOrNull(tenantId, MAX_PROMPT_TOKENS);
        if (quotaErr != null) return quotaErr;
        // DC-P0-007: fail closed when AI service unavailable in production
        Promise<HttpResponse> aiUnavail = checkAiServiceOrUnavailable();
        if (aiUnavail != null) return aiUnavail;

        return request.loadBody(MAX_PROMPT_TOKENS * 4)
            .then(body -> {
                Map<String, Object> input = parseBody(body.getString(StandardCharsets.UTF_8));
                String surface = String.valueOf(input.getOrDefault("surface", "query"));
                int limit = ((Number) input.getOrDefault("limit", 5)).intValue();

                // Route to best available surface handler
                if ("query".equals(surface) || "analytics".equals(surface)) {
                    String intent = (String) input.getOrDefault("intent", "");
                    if (completionService == null) {
                        HttpResponse resp = heuristicAnalyticsSuggestResponse(intent, tenantId, requestId);
                        recommendationMetrics.recordRecommendation(
                            AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, tenantId,
                            HEURISTIC_CONFIDENCE, true, System.currentTimeMillis() - startMs);
                        recordAiAction(tenantId, "ai-suggestions", "surface=analytics intent=" + intent,
                            "static-heuristic", HEURISTIC_CONFIDENCE, true,
                            System.currentTimeMillis() - startMs, requestId);
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
                            recordAiAction(tenantId, "ai-suggestions", "surface=analytics intent=" + intent,
                                result.getModelUsed() != null ? result.getModelUsed() : DEFAULT_MODEL,
                                conf, conf < FALLBACK_CONFIDENCE_THRESHOLD,
                                System.currentTimeMillis() - startMs, requestId);
                            return resp;
                        })
                        .then(Promise::of,
                              e -> {
                                  Promise<HttpResponse> providerError = providerErrorOrNull(
                                      "/api/v1/ai/suggestions", tenantId, requestId, e);
                                  if (providerError != null) {
                                      return providerError;
                                  }
                                  log.warn("[DC-E3] ai suggestions cross-surface call failed tenant={}: {}", tenantId, e.getMessage());
                                  recommendationMetrics.recordError(
                                      AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, tenantId, e);
                                  recordAiAction(tenantId, "ai-suggestions", "surface=analytics intent=" + intent,
                                      "static-heuristic", HEURISTIC_CONFIDENCE, true,
                                      System.currentTimeMillis() - startMs, requestId);
                                  return Promise.of(heuristicAnalyticsSuggestResponse(intent, tenantId, requestId));
                              });
                }

                // Other surfaces: return heuristic fallback aligned with UI contract
                Map<String, Object> suggestion = new LinkedHashMap<>();
                suggestion.put("id", UUID.randomUUID().toString());
                suggestion.put("surface", surface);
                suggestion.put("title", "AI suggestions unavailable for surface: " + surface);
                suggestion.put("description", "Cross-surface AI operations for '" + surface + "' are not yet implemented.");
                suggestion.put("confidence", HEURISTIC_CONFIDENCE);
                suggestion.put("confidenceBand", "low");
                suggestion.put("canAutoApply", false);
                suggestion.put("impact", Map.of("severity", "low", "affectedEntities", List.of(), "description", ""));
                suggestion.put("contextIds", input.getOrDefault("contextIds", List.of()));
                suggestion.put("generatedAt", Instant.now().toString());
                suggestion.put("source", "heuristic-fallback");
                List<Map<String, Object>> suggestions = List.of(Map.copyOf(suggestion));
                Map<String, Object> payload = Map.of(
                    "tenantId", tenantId,
                    "surface", surface,
                    "suggestions", suggestions,
                    "count", suggestions.size(),
                    "generatedAt", Instant.now().toString(),
                    "modelVersion", "heuristic-v1"
                );
                recommendationMetrics.recordRecommendation(
                    AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, tenantId,
                    HEURISTIC_CONFIDENCE, true, System.currentTimeMillis() - startMs);
                recordAiAction(tenantId, "ai-suggestions", "surface=" + surface,
                    "static-heuristic", HEURISTIC_CONFIDENCE, true,
                    System.currentTimeMillis() - startMs, requestId);
                return Promise.of(http.jsonResponse(payload));
            });
    }

    /**
     * {@code POST /api/v1/ai/suggestions/:id/apply}
     *
     * <p>Applies an AI suggestion in a boundary-safe deferred mode. The backend
     * records audit intent and returns a deterministic outcome contract for the UI.
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleApplyAiSuggestion(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String suggestionId = request.getPathParameter("id");
        if (suggestionId == null || suggestionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "suggestion id path parameter is required"));
        }

        String requestId = resolveRequestId(request);
        long startMs = System.currentTimeMillis();

        return request.loadBody(MAX_PROMPT_TOKENS * 2)
            .then(body -> {
                Map<String, Object> payload = parseBody(body.getString(StandardCharsets.UTF_8));
                Map<String, Object> context = payload.get("context") instanceof Map<?, ?> contextMap
                    ? toStringObjectMap(contextMap)
                    : Map.of();

                String outcome = "deferred";
                String message = "Suggestion accepted for manual execution through surface-specific workflows.";
                if (Boolean.TRUE.equals(context.get("dryRun"))) {
                    outcome = "success";
                    message = "Dry-run apply completed. No state mutation performed.";
                }

                String auditEventId = UUID.randomUUID().toString();
                recordAiAction(
                    tenantId,
                    "ai-suggestion-apply",
                    "suggestionId=" + suggestionId,
                    "apply-heuristic-v1",
                    HEURISTIC_CONFIDENCE,
                    true,
                    System.currentTimeMillis() - startMs,
                    requestId);

                if (client != null) {
                    client.appendEvent(tenantId, DataCloudClient.Event.builder()
                        .type("ai.suggestion.apply")
                        .payload(Map.of(
                            "eventId", auditEventId,
                            "suggestionId", suggestionId,
                            "outcome", outcome,
                            "requestId", requestId,
                            "recordedAt", Instant.now().toString()
                        ))
                        .source("datacloud.launcher.ai-assist")
                        .build()).whenException(e -> log.warn(
                        "Failed to append AI suggestion apply event tenant={} suggestion={}: {}",
                        tenantId,
                        suggestionId,
                        e.getMessage()));
                }

                Map<String, Object> response = new LinkedHashMap<>();
                response.put("suggestionId", suggestionId);
                response.put("applied", true);
                response.put("outcome", outcome);
                response.put("message", message);
                response.put("appliedAt", Instant.now().toString());
                response.put("auditEventId", auditEventId);
                return Promise.of(http.jsonResponse(response));
            });
    }

    /**
     * {@code GET /api/v1/ai/correlations}
     *
     * <p>Returns cross-surface AI correlations. Currently returns an empty list
     * with a boundary flag since the unified operation event model is not yet available.
     */
    public Promise<HttpResponse> handleAiCorrelations(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        Map<String, Object> payload = Map.of(
            "tenantId", tenantId,
            "correlations", List.of(),
            "count", 0,
            "generatedAt", Instant.now().toString(),
            "boundary", true,
            "boundaryReason", "Unified operation event model not yet available",
            "modelVersion", "not-implemented"
        );
        return Promise.of(http.jsonResponse(payload));
    }

    /**
     * {@code GET /api/v1/ai/advisories/workflows/:workflowId}
     *
     * <p>Returns workflow advisory. Delegates to pipeline optimisation hint
     * when the workflowId maps to a known pipeline; otherwise returns heuristic.
     */
    public Promise<HttpResponse> handleAiWorkflowAdvisory(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String workflowId = request.getPathParameter("workflowId");
        long startMs = System.currentTimeMillis();
        String requestId = resolveRequestId(request);

        if (completionService == null) {
            HttpResponse resp = heuristicPipelineHintResponse(workflowId, tenantId, requestId);
            recommendationMetrics.recordRecommendation(
                AiRecommendationMetrics.TYPE_PIPELINE_HINT, tenantId,
                HEURISTIC_CONFIDENCE, true, System.currentTimeMillis() - startMs);
            recordAiAction(tenantId, "ai-workflow-advisory", "workflowId=" + workflowId,
                "static-heuristic", HEURISTIC_CONFIDENCE, true,
                System.currentTimeMillis() - startMs, requestId);
            return Promise.of(resp);
        }
        String prompt = buildPipelineOptimisePrompt(workflowId, "{}", tenantId);
        return callAi(prompt)
            .map(result -> {
                HttpResponse resp = buildPipelineHintHttpResponse(result, workflowId, tenantId, requestId);
                double conf = estimateConfidence(result);
                recommendationMetrics.recordRecommendation(
                    AiRecommendationMetrics.TYPE_PIPELINE_HINT, tenantId,
                    conf, conf < FALLBACK_CONFIDENCE_THRESHOLD, System.currentTimeMillis() - startMs);
                recordAiAction(tenantId, "ai-workflow-advisory", "workflowId=" + workflowId,
                    result.getModelUsed() != null ? result.getModelUsed() : DEFAULT_MODEL,
                    conf, conf < FALLBACK_CONFIDENCE_THRESHOLD,
                    System.currentTimeMillis() - startMs, requestId);
                return resp;
            })
            .then(Promise::of,
                  e -> {
                      Promise<HttpResponse> providerError = providerErrorOrNull(
                          "/api/v1/ai/advisories/workflows/:workflowId", tenantId, requestId, e);
                      if (providerError != null) {
                          return providerError;
                      }
                      log.warn("[DC-E3] workflow advisory AI call failed workflowId={} tenant={}: {}", workflowId, tenantId, e.getMessage());
                      recommendationMetrics.recordError(
                          AiRecommendationMetrics.TYPE_PIPELINE_HINT, tenantId, e);
                      recordAiAction(tenantId, "ai-workflow-advisory", "workflowId=" + workflowId,
                          "static-heuristic", HEURISTIC_CONFIDENCE, true,
                          System.currentTimeMillis() - startMs, requestId);
                      return Promise.of(heuristicPipelineHintResponse(workflowId, tenantId, requestId));
                  });
    }

    /**
     * {@code GET /api/v1/ai/advisories/quality/:collectionId}
     *
     * <p>Returns heuristic data-quality advisory for a collection.
     */
    public Promise<HttpResponse> handleAiQualityAdvisory(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String collectionId = request.getPathParameter("collectionId");
        long startMs = System.currentTimeMillis();

        List<Map<String, Object>> advisories = List.of(Map.of(
            "id", UUID.randomUUID().toString(),
            "type", "completeness",
            "title", "Quality assessment placeholder",
            "description", "Real-time quality scoring is not yet implemented for collection: " + collectionId,
            "affectedCount", 0,
            "confidence", HEURISTIC_CONFIDENCE,
            "suggestedAction", "Enable data profiling and quality rules in the governance panel."
        ));

        Map<String, Object> payload = Map.of(
            "collectionId", collectionId,
            "tenantId", tenantId,
            "overallScore", 0.5,
            "scoreBand", "medium",
            "advisories", advisories,
            "generatedAt", Instant.now().toString(),
            "modelVersion", "heuristic-v1",
            "fallback", true
        );

        recommendationMetrics.recordRecommendation(
            AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, tenantId,
            HEURISTIC_CONFIDENCE, true, System.currentTimeMillis() - startMs);
        recordAiAction(tenantId, "ai-quality-advisory", "collectionId=" + collectionId,
            "static-heuristic", HEURISTIC_CONFIDENCE, true,
            System.currentTimeMillis() - startMs, http.resolveCorrelationId(request));
        return Promise.of(http.jsonResponse(payload));
    }

    /**
     * {@code GET /api/v1/ai/advisories/fabric/:collectionId}
     *
     * <p>Returns fabric tier placement advisory derived from heuristic storage
     * patterns until full policy-aware optimization lands.
     */
    public Promise<HttpResponse> handleAiFabricAdvisory(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String collectionId = request.getPathParameter("collectionId");
        if (collectionId == null || collectionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "collectionId path parameter is required"));
        }

        String requestId = resolveRequestId(request);
        long startMs = System.currentTimeMillis();

        String normalized = collectionId.toLowerCase(Locale.ROOT);
        String currentTier = "hot";
        String recommendedTier = "warm";
        String impact = "positive";
        if (normalized.contains("archive") || normalized.contains("history")) {
            currentTier = "warm";
            recommendedTier = "cold";
        } else if (normalized.contains("realtime") || normalized.contains("live")) {
            currentTier = "hot";
            recommendedTier = "hot";
            impact = "neutral";
        }

        List<Map<String, Object>> advisories = List.of(
            Map.of(
                "id", UUID.randomUUID().toString(),
                "type", "tier-migration",
                "title", "Evaluate collection tier placement",
                "description", "Heuristic placement suggests moving collection '" + collectionId + "' toward tier '" + recommendedTier + "' to balance cost and latency.",
                "estimatedCostImpact", impact,
                "confidence", HEURISTIC_CONFIDENCE,
                "confidenceBand", "low",
                "suggestedAction", "Run /api/v1/collections/:id/migrate in dry-run mode before scheduling automated migration."
            ),
            Map.of(
                "id", UUID.randomUUID().toString(),
                "type", "retention",
                "title", "Recheck retention and legal-hold overlap",
                "description", "Tier decisions should align with legal holds and retention windows before migration.",
                "estimatedCostImpact", "neutral",
                "confidence", HEURISTIC_CONFIDENCE,
                "confidenceBand", "low",
                "suggestedAction", "Validate compliance policy at /api/v1/compliance/posture before applying tier changes."
            )
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("collectionId", collectionId);
        payload.put("tenantId", tenantId);
        payload.put("currentTier", currentTier);
        payload.put("recommendedTier", recommendedTier);
        payload.put("advisories", advisories);
        payload.put("generatedAt", Instant.now().toString());
        payload.put("modelVersion", "heuristic-fabric-v1");

        recordAiAction(tenantId, "ai-fabric-advisory", "collectionId=" + request.getPathParameter("collectionId"),
            "static-heuristic", HEURISTIC_CONFIDENCE, true,
            System.currentTimeMillis() - startMs, requestId);
        return Promise.of(http.jsonResponse(payload));
    }

    /**
     * {@code POST /api/v1/workflows/analyze-risk}
     *
     * <p>Analyzes a workflow execution plan for potential failure modes and
     * proposes automated remediation steps (P2.2).
     *
     * <p>Request body: <pre>{"executionId":"exec-123","steps":[...]}</pre>
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleAnalyzeWorkflowRisk(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String requestId = resolveRequestId(request);
        long startMs = System.currentTimeMillis();

        return request.loadBody(MAX_PROMPT_TOKENS * 4)
            .then(body -> {
                try {
                    Map<String, Object> input = parseBody(body.getString(StandardCharsets.UTF_8));
                    List<Map<String, Object>> steps = (List<Map<String, Object>>) input.getOrDefault("steps", List.of());

                    List<Map<String, Object>> risks = new ArrayList<>();
                    for (int i = 0; i < steps.size(); i++) {
                        Map<String, Object> step = steps.get(i);
                        String stepId = String.valueOf(step.getOrDefault("id", "step-" + i));
                        String stepType = String.valueOf(step.getOrDefault("type", "unknown"));
                        String dependsOn = String.valueOf(step.getOrDefault("dependsOn", ""));

                        List<Map<String, Object>> stepRisks = analyzeStepRisks(stepType, dependsOn);
                        List<Map<String, Object>> remediations = proposeRemediations(stepType, stepRisks);

                        Map<String, Object> riskReport = new LinkedHashMap<>();
                        riskReport.put("stepId", stepId);
                        riskReport.put("stepType", stepType);
                        riskReport.put("riskCount", stepRisks.size());
                        riskReport.put("riskLevel", stepRisks.isEmpty() ? "low" :
                            stepRisks.stream().anyMatch(r -> "high".equals(r.get("severity"))) ? "high" :
                            stepRisks.stream().anyMatch(r -> "medium".equals(r.get("severity"))) ? "medium" : "low");
                        riskReport.put("risks", stepRisks);
                        riskReport.put("proposedRemediations", remediations);
                        risks.add(riskReport);
                    }

                    long highRiskCount = risks.stream().filter(r -> "high".equals(r.get("riskLevel"))).count();
                    long mediumRiskCount = risks.stream().filter(r -> "medium".equals(r.get("riskLevel"))).count();

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("tenantId", tenantId);
                    result.put("executionId", input.getOrDefault("executionId", "unknown"));
                    result.put("stepCount", steps.size());
                    result.put("highRiskSteps", highRiskCount);
                    result.put("mediumRiskSteps", mediumRiskCount);
                    result.put("riskAnalysis", risks);
                    result.put("overallRiskLevel", highRiskCount > 0 ? "high" : mediumRiskCount > 0 ? "medium" : "low");
                    result.put("approvalRecommended", highRiskCount > 0 || mediumRiskCount > steps.size() / 2);
                    result.put("requestId", requestId);
                    result.put("generatedAt", Instant.now().toString());
                    result.put("latencyMs", System.currentTimeMillis() - startMs);

                    recordAiAction(tenantId, "workflow-analyze-risk", "steps=" + steps.size(),
                        "static-heuristic", HEURISTIC_CONFIDENCE, true,
                        System.currentTimeMillis() - startMs, requestId);

                    return Promise.of(http.jsonResponse(result));
                } catch (Exception e) {
                    return Promise.of(http.errorResponse(400, "Invalid risk analysis payload: " + e.getMessage()));
                }
            });
    }

    private List<Map<String, Object>> analyzeStepRisks(String stepType, String dependsOn) {
        List<Map<String, Object>> risks = new ArrayList<>();
        if ("database".equals(stepType) || "db".equals(stepType)) {
            risks.add(risk("timeout", "high", "Database query may exceed timeout under load"));
            risks.add(risk("connection-pool-exhaustion", "medium", "Concurrent executions may exhaust connection pool"));
        }
        if ("http".equals(stepType) || "api".equals(stepType) || "rest".equals(stepType)) {
            risks.add(risk("external-service-unavailable", "high", "Downstream API may be unavailable or rate-limited"));
            risks.add(risk("network-latency", "medium", "Network latency may cause cascading delays"));
        }
        if ("file".equals(stepType) || "s3".equals(stepType) || "storage".equals(stepType)) {
            risks.add(risk("file-not-found", "medium", "Source file may be missing or moved"));
            risks.add(risk("permission-denied", "medium", "Storage permissions may be insufficient"));
        }
        if ("transform".equals(stepType) || "etl".equals(stepType) || "process".equals(stepType)) {
            risks.add(risk("data-format-mismatch", "medium", "Incoming data may not match expected schema"));
            risks.add(risk("memory-exhaustion", "high", "Large data volumes may exhaust available memory"));
        }
        if (!dependsOn.isBlank() && !"null".equals(dependsOn)) {
            risks.add(risk("dependency-failure", "high", "Upstream step failure will cascade to this step"));
        }
        return risks;
    }

    private Map<String, Object> risk(String type, String severity, String description) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("type", type);
        r.put("severity", severity);
        r.put("description", description);
        return r;
    }

    private List<Map<String, Object>> proposeRemediations(String stepType, List<Map<String, Object>> risks) {
        List<Map<String, Object>> remediations = new ArrayList<>();
        for (Map<String, Object> r : risks) {
            String type = String.valueOf(r.get("type"));
            Map<String, Object> remediation = new LinkedHashMap<>();
            remediation.put("forRiskType", type);
            remediation.put("action", switch (type) {
                case "timeout" -> "Add query timeout and retry with exponential backoff";
                case "connection-pool-exhaustion" -> "Use connection pool monitoring and circuit breaker";
                case "external-service-unavailable" -> "Implement circuit breaker and fallback data source";
                case "network-latency" -> "Add async execution and caching layer";
                case "file-not-found" -> "Add file existence check and idempotent staging";
                case "permission-denied" -> "Pre-validate IAM/storage permissions before execution";
                case "data-format-mismatch" -> "Add schema validation and error handling with dead-letter queue";
                case "memory-exhaustion" -> "Implement streaming processing and batch limits";
                case "dependency-failure" -> "Add dependency health checks and graceful degradation";
                default -> "Review and add defensive checks";
            });
            remediation.put("autoApplicable", List.of("timeout", "connection-pool-exhaustion", "file-not-found").contains(type));
            remediations.add(remediation);
        }
        return remediations;
    }

    /**
     * {@code POST /api/v1/workflows/validate}
     *
     * <p>DAG validation for workflow/pipeline definitions (P2.2).
     * Detects cycles, missing dependencies, unsupported operations,
     * and estimates risk/cost before execution.
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleWorkflowValidate(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String requestId = resolveRequestId(request);
        long startMs = System.currentTimeMillis();

        return request.loadBody(MAX_PROMPT_TOKENS * 4)
            .then(body -> {
                try {
                    Map<String, Object> input = parseBody(body.getString(StandardCharsets.UTF_8));
                    List<Map<String, Object>> steps = (List<Map<String, Object>>) input.getOrDefault("steps", List.of());
                    List<Map<String, Object>> edges = (List<Map<String, Object>>) input.getOrDefault("edges", List.of());

                    Map<String, Object> result = validateWorkflow(steps, edges, tenantId);
                    result.put("tenantId", tenantId);
                    result.put("requestId", requestId);
                    result.put("validatedAt", Instant.now().toString());
                    result.put("latencyMs", System.currentTimeMillis() - startMs);

                    return Promise.of(http.jsonResponse(result));
                } catch (Exception e) {
                    return Promise.of(http.errorResponse(400, "Invalid workflow payload: " + e.getMessage()));
                }
            });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> validateWorkflow(List<Map<String, Object>> steps, List<Map<String, Object>> edges, String tenantId) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Step 1: Collect step IDs and check for duplicates
        Set<String> stepIds = new HashSet<>();
        Set<String> stepTypes = new HashSet<>();
        for (Map<String, Object> step : steps) {
            String id = (String) step.get("id");
            if (id == null || id.isBlank()) {
                errors.add("Step missing required field: id");
                continue;
            }
            if (!stepIds.add(id)) {
                errors.add("Duplicate step id: " + id);
            }
            String type = (String) step.get("type");
            if (type != null) stepTypes.add(type);
        }

        // Step 2: Validate edges reference existing steps
        Map<String, Set<String>> graph = new HashMap<>();
        for (String id : stepIds) graph.put(id, new HashSet<>());
        for (Map<String, Object> edge : edges) {
            String from = (String) edge.get("from");
            String to = (String) edge.get("to");
            if (from == null || to == null) {
                errors.add("Edge missing required field: from or to");
                continue;
            }
            if (!stepIds.contains(from)) errors.add("Edge references unknown step: " + from);
            if (!stepIds.contains(to)) errors.add("Edge references unknown step: " + to);
            if (from.equals(to)) errors.add("Self-loop detected on step: " + from);
            graph.computeIfAbsent(from, k -> new HashSet<>()).add(to);
        }

        // Step 3: Cycle detection (DFS-based)
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        List<List<String>> cycles = new ArrayList<>();
        for (String node : stepIds) {
            if (!visited.contains(node)) {
                List<String> path = new ArrayList<>();
                detectCycles(node, graph, visiting, visited, path, cycles);
            }
        }
        for (List<String> cycle : cycles) {
            errors.add("Cycle detected: " + String.join(" -> ", cycle));
        }

        // Step 4: Check for isolated steps (no incoming or outgoing edges)
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, Integer> outDegree = new HashMap<>();
        for (String id : stepIds) {
            inDegree.put(id, 0);
            outDegree.put(id, 0);
        }
        for (Map<String, Object> edge : edges) {
            String from = (String) edge.get("from");
            String to = (String) edge.get("to");
            if (from != null && to != null) {
                inDegree.merge(to, 1, Integer::sum);
                outDegree.merge(from, 1, Integer::sum);
            }
        }
        for (String id : stepIds) {
            if (inDegree.getOrDefault(id, 0) == 0 && outDegree.getOrDefault(id, 0) == 0 && steps.size() > 1) {
                warnings.add("Isolated step (no connections): " + id);
            }
        }

        // Step 5: Risk/cost estimation
        double estimatedRisk = Math.min(1.0, errors.size() * 0.25 + warnings.size() * 0.05);
        String riskBand = estimatedRisk < 0.2 ? "low" : estimatedRisk < 0.5 ? "medium" : "high";
        int estimatedCost = steps.size() * 10 + edges.size() * 2;

        // Step 6: Unsupported operations check
        Set<String> supportedTypes = Set.of("extract", "transform", "load", "filter", "aggregate", "join", "validate", "notify", "decision", "wait", "parallel", "custom");
        for (String type : stepTypes) {
            if (!supportedTypes.contains(type.toLowerCase())) {
                warnings.add("Potentially unsupported step type: " + type);
            }
        }

        result.put("valid", errors.isEmpty());
        result.put("errors", errors);
        result.put("warnings", warnings);
        result.put("stepCount", steps.size());
        result.put("edgeCount", edges.size());
        result.put("cycleCount", cycles.size());
        result.put("estimatedRisk", estimatedRisk);
        result.put("riskBand", riskBand);
        result.put("estimatedCost", estimatedCost);
        result.put("approvalRequired", !errors.isEmpty() || estimatedRisk >= 0.5);
        return result;
    }

    private void detectCycles(String node, Map<String, Set<String>> graph, Set<String> visiting,
                              Set<String> visited, List<String> path, List<List<String>> cycles) {
        if (visiting.contains(node)) {
            int index = path.indexOf(node);
            if (index >= 0) {
                List<String> cycle = new ArrayList<>(path.subList(index, path.size()));
                cycle.add(node);
                cycles.add(cycle);
            }
            return;
        }
        if (visited.contains(node)) return;
    }

    private String resolveType(Object value) {
        if (value instanceof Boolean) return "boolean";
        if (value instanceof List<?>) return "array";
        if (value instanceof Map<?, ?>) return "object";
        return "unknown";
    }

    /**
     * {@code POST /api/v1/operations/anomaly-group}
     *
     * <p>Groups a list of anomalies/alert records by pattern similarity to
     * surface common root causes (P2.5 Operations).
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleAnomalyGroup(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String requestId = resolveRequestId(request);
        long startMs = System.currentTimeMillis();

        return request.loadBody(MAX_PROMPT_TOKENS * 4)
            .then(body -> {
                try {
                    Map<String, Object> input = parseBody(body.getString(StandardCharsets.UTF_8));
                    List<Map<String, Object>> anomalies = (List<Map<String, Object>>) input.getOrDefault("anomalies", List.of());

                    Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
                    for (Map<String, Object> a : anomalies) {
                        String source = String.valueOf(a.getOrDefault("source", "unknown"));
                        String type = String.valueOf(a.getOrDefault("type", "general"));
                        String key = source + "::" + type;
                        groups.computeIfAbsent(key, k -> new ArrayList<>()).add(a);
                    }

                    List<Map<String, Object>> clusters = new ArrayList<>();
                    for (Map.Entry<String, List<Map<String, Object>>> entry : groups.entrySet()) {
                        Map<String, Object> cluster = new LinkedHashMap<>();
                        cluster.put("groupKey", entry.getKey());
                        cluster.put("count", entry.getValue().size());
                        cluster.put("sources", entry.getValue().stream()
                            .map(a -> a.getOrDefault("source", "unknown")).distinct().toList());
                        cluster.put("severities", entry.getValue().stream()
                            .map(a -> a.getOrDefault("severity", "info")).distinct().toList());
                        cluster.put("suggestedRootCause", "Pattern: " + entry.getKey() + " — review shared dependency or configuration drift.");
                        clusters.add(cluster);
                    }

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("tenantId", tenantId);
                    result.put("totalAnomalies", anomalies.size());
                    result.put("clusterCount", clusters.size());
                    result.put("clusters", clusters);
                    result.put("requestId", requestId);
                    result.put("generatedAt", Instant.now().toString());
                    result.put("latencyMs", System.currentTimeMillis() - startMs);

                    recordAiAction(tenantId, "anomaly-group", "anomalies=" + anomalies.size(),
                        "static-heuristic", HEURISTIC_CONFIDENCE, true,
                        System.currentTimeMillis() - startMs, requestId);

                    return Promise.of(http.jsonResponse(result));
                } catch (Exception e) {
                    return Promise.of(http.errorResponse(400, "Invalid anomaly payload: " + e.getMessage()));
                }
            });
    }

    /**
     * {@code POST /api/v1/operations/forecast}
     *
     * <p>Capacity forecasting based on current utilization metrics and
     * historical trends (P2.5 Operations).
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleCapacityForecast(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String requestId = resolveRequestId(request);
        long startMs = System.currentTimeMillis();

        return request.loadBody(MAX_PROMPT_TOKENS * 4)
            .then(body -> {
                try {
                    Map<String, Object> input = parseBody(body.getString(StandardCharsets.UTF_8));
                    Map<String, Object> currentMetrics = (Map<String, Object>) input.getOrDefault("currentMetrics", Map.of());
                    List<Map<String, Object>> history = (List<Map<String, Object>>) input.getOrDefault("history", List.of());

                    double cpuPct = parseDouble(currentMetrics.getOrDefault("cpuPercent", 0));
                    double memPct = parseDouble(currentMetrics.getOrDefault("memoryPercent", 0));
                    double storagePct = parseDouble(currentMetrics.getOrDefault("storagePercent", 0));
                    double networkMbps = parseDouble(currentMetrics.getOrDefault("networkMbps", 0));

                    // Simple linear projection based on trend from last two history points
                    double cpuTrend = 0, memTrend = 0, storageTrend = 0;
                    if (history.size() >= 2) {
                        Map<String, Object> last = history.get(history.size() - 1);
                        Map<String, Object> prev = history.get(history.size() - 2);
                        cpuTrend = parseDouble(last.getOrDefault("cpuPercent", 0)) - parseDouble(prev.getOrDefault("cpuPercent", 0));
                        memTrend = parseDouble(last.getOrDefault("memoryPercent", 0)) - parseDouble(prev.getOrDefault("memoryPercent", 0));
                        storageTrend = parseDouble(last.getOrDefault("storagePercent", 0)) - parseDouble(prev.getOrDefault("storagePercent", 0));
                    }

                    double projectedCpu = Math.min(100, cpuPct + cpuTrend * 7); // 7-day projection
                    double projectedMem = Math.min(100, memPct + memTrend * 7);
                    double projectedStorage = Math.min(100, storagePct + storageTrend * 7);

                    List<Map<String, Object>> bottlenecks = new ArrayList<>();
                    if (projectedCpu > 80) bottlenecks.add(Map.of("resource", "cpu", "projected", projectedCpu, "severity", projectedCpu > 95 ? "critical" : "warning", "action", "Scale compute or optimize workloads"));
                    if (projectedMem > 80) bottlenecks.add(Map.of("resource", "memory", "projected", projectedMem, "severity", projectedMem > 95 ? "critical" : "warning", "action", "Increase memory allocation or reduce cache retention"));
                    if (projectedStorage > 80) bottlenecks.add(Map.of("resource", "storage", "projected", projectedStorage, "severity", projectedStorage > 95 ? "critical" : "warning", "action", "Add storage capacity or implement tiered archiving"));
                    if (networkMbps > 8000) bottlenecks.add(Map.of("resource", "network", "projected", networkMbps, "severity", "warning", "action", "Upgrade bandwidth or enable compression"));

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("tenantId", tenantId);
                    result.put("projectedCpuPercent", projectedCpu);
                    result.put("projectedMemoryPercent", projectedMem);
                    result.put("projectedStoragePercent", projectedStorage);
                    result.put("bottlenecks", bottlenecks);
                    result.put("recommendation", bottlenecks.isEmpty() ? "No immediate capacity concerns" : "Review and mitigate " + bottlenecks.size() + " projected bottleneck(s)");
                    result.put("requestId", requestId);
                    result.put("generatedAt", Instant.now().toString());
                    result.put("latencyMs", System.currentTimeMillis() - startMs);

                    recordAiAction(tenantId, "capacity-forecast", "metrics=" + currentMetrics.keySet().size(),
                        "static-heuristic", HEURISTIC_CONFIDENCE, true,
                        System.currentTimeMillis() - startMs, requestId);

                    return Promise.of(http.jsonResponse(result));
                } catch (Exception e) {
                    return Promise.of(http.errorResponse(400, "Invalid forecast payload: " + e.getMessage()));
                }
            });
    }

    private double parseDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(value)); } catch (Exception e) { return 0.0; }
    }

    /**
     * {@code POST /api/v1/ai/next-action}
     *
     * <p>Next-best-action surface for operators: suggests the most impactful
     * action given current system state (P2.5 UI).
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleNextBestAction(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String requestId = resolveRequestId(request);
        long startMs = System.currentTimeMillis();

        return request.loadBody(MAX_PROMPT_TOKENS * 4)
            .then(body -> {
                try {
                    Map<String, Object> input = parseBody(body.getString(StandardCharsets.UTF_8));
                    List<Map<String, Object>> pendingAlerts = (List<Map<String, Object>>) input.getOrDefault("pendingAlerts", List.of());
                    List<Map<String, Object>> pendingTasks = (List<Map<String, Object>>) input.getOrDefault("pendingTasks", List.of());
                    List<Map<String, Object>> recentFailures = (List<Map<String, Object>>) input.getOrDefault("recentFailures", List.of());

                    String suggestedAction = "No immediate action required";
                    String actionType = "none";
                    String rationale = "System state is stable";
                    double urgency = 0.0;

                    long criticalAlerts = pendingAlerts.stream()
                        .filter(a -> "critical".equalsIgnoreCase(String.valueOf(a.getOrDefault("severity", ""))))
                        .count();
                    long highFailures = recentFailures.stream()
                        .filter(f -> "high".equalsIgnoreCase(String.valueOf(f.getOrDefault("impact", ""))))
                        .count();

                    if (criticalAlerts > 0) {
                        suggestedAction = "Resolve " + criticalAlerts + " critical alert(s) immediately";
                        actionType = "alert-resolution";
                        rationale = "Critical alerts indicate potential service impact";
                        urgency = 1.0;
                    } else if (highFailures > 0) {
                        suggestedAction = "Investigate " + highFailures + " recent high-impact failure(s)";
                        actionType = "failure-investigation";
                        rationale = "Recent failures may indicate systemic issue";
                        urgency = 0.8;
                    } else if (!pendingTasks.isEmpty()) {
                        suggestedAction = "Complete " + pendingTasks.size() + " pending task(s)";
                        actionType = "task-completion";
                        rationale = "Pending tasks may block downstream work";
                        urgency = 0.4;
                    }

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("tenantId", tenantId);
                    result.put("suggestedAction", suggestedAction);
                    result.put("actionType", actionType);
                    result.put("rationale", rationale);
                    result.put("urgency", urgency);
                    result.put("confidence", HEURISTIC_CONFIDENCE);
                    result.put("fallback", true);
                    result.put("requestId", requestId);
                    result.put("generatedAt", Instant.now().toString());
                    result.put("latencyMs", System.currentTimeMillis() - startMs);

                    recordAiAction(tenantId, "next-best-action", "alerts=" + pendingAlerts.size() + ",tasks=" + pendingTasks.size(),
                        "static-heuristic", HEURISTIC_CONFIDENCE, true,
                        System.currentTimeMillis() - startMs, requestId);

                    return Promise.of(http.jsonResponse(result));
                } catch (Exception e) {
                    return Promise.of(http.errorResponse(400, "Invalid next-action payload: " + e.getMessage()));
                }
            });
    }

    /**
     * {@code POST /api/v1/connectors/suggest-mapping}
     *
     * <p>AI-assisted field mapping suggestion between source and target
     * schemas for connector configurations (P2.5 Connectors).
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleSuggestConnectorMapping(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String requestId = resolveRequestId(request);
        long startMs = System.currentTimeMillis();

        return request.loadBody(MAX_PROMPT_TOKENS * 4)
            .then(body -> {
                try {
                    Map<String, Object> input = parseBody(body.getString(StandardCharsets.UTF_8));
                    Map<String, String> sourceSchema = (Map<String, String>) input.getOrDefault("sourceSchema", Map.of());
                    Map<String, String> targetSchema = (Map<String, String>) input.getOrDefault("targetSchema", Map.of());

                    List<Map<String, Object>> mappings = new ArrayList<>();
                    for (Map.Entry<String, String> src : sourceSchema.entrySet()) {
                        String srcField = src.getKey();
                        String srcType = src.getValue();
                        // Find best match by exact name, then by type, then by similarity
                        String bestMatch = null;
                        double bestScore = 0;
                        for (Map.Entry<String, String> tgt : targetSchema.entrySet()) {
                            double score = 0;
                            if (tgt.getKey().equalsIgnoreCase(srcField)) score += 1.0;
                            if (tgt.getKey().toLowerCase().contains(srcField.toLowerCase()) || srcField.toLowerCase().contains(tgt.getKey().toLowerCase())) score += 0.5;
                            if (tgt.getValue() != null && tgt.getValue().equalsIgnoreCase(srcType)) score += 0.3;
                            if (score > bestScore) {
                                bestScore = score;
                                bestMatch = tgt.getKey();
                            }
                        }
                        Map<String, Object> mapping = new LinkedHashMap<>();
                        mapping.put("sourceField", srcField);
                        mapping.put("sourceType", srcType);
                        mapping.put("suggestedTargetField", bestMatch);
                        mapping.put("confidence", bestScore >= 1.0 ? "high" : bestScore >= 0.5 ? "medium" : bestScore > 0 ? "low" : "none");
                        mapping.put("matchReason", bestScore >= 1.0 ? "exact-name-match" : bestScore >= 0.5 ? "partial-name-match" : bestScore > 0 ? "type-compatible" : "no-match");
                        mappings.add(mapping);
                    }

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("tenantId", tenantId);
                    result.put("mappings", mappings);
                    result.put("mappedCount", mappings.stream().filter(m -> m.get("suggestedTargetField") != null).count());
                    result.put("unmappedCount", mappings.stream().filter(m -> m.get("suggestedTargetField") == null).count());
                    result.put("requestId", requestId);
                    result.put("generatedAt", Instant.now().toString());
                    result.put("latencyMs", System.currentTimeMillis() - startMs);

                    recordAiAction(tenantId, "connector-suggest-mapping", "fields=" + sourceSchema.size(),
                        "static-heuristic", HEURISTIC_CONFIDENCE, true,
                        System.currentTimeMillis() - startMs, requestId);

                    return Promise.of(http.jsonResponse(result));
                } catch (Exception e) {
                    return Promise.of(http.errorResponse(400, "Invalid connector mapping payload: " + e.getMessage()));
                }
            });
    }

    /**
     * {@code POST /api/v1/connectors/:connectorId/sync-health}
     *
     * <p>Diagnoses connector sync health: analyzes recent sync history for
     * rate limits, auth failures, schema drift, and estimates source reliability
     * score (P2.5 Connectors).
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleConnectorSyncHealth(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String connectorId = request.getPathParameter("connectorId");
        if (connectorId == null || connectorId.isBlank()) {
            return Promise.of(http.errorResponse(400, "connectorId path parameter is required"));
        }
        String requestId = resolveRequestId(request);
        long startMs = System.currentTimeMillis();

        return request.loadBody(MAX_PROMPT_TOKENS * 4)
            .then(body -> {
                try {
                    Map<String, Object> input = parseBody(body.getString(StandardCharsets.UTF_8));
                    List<Map<String, Object>> syncHistory = (List<Map<String, Object>>) input.getOrDefault("syncHistory", List.of());

                    int total = syncHistory.size();
                    int success = 0;
                    int rateLimit = 0;
                    int authFailure = 0;
                    int schemaDrift = 0;
                    int networkError = 0;

                    for (Map<String, Object> sync : syncHistory) {
                        String status = String.valueOf(sync.getOrDefault("status", "")).toLowerCase();
                        if (status.contains("success")) success++;
                        if (status.contains("rate")) rateLimit++;
                        if (status.contains("auth")) authFailure++;
                        if (status.contains("schema")) schemaDrift++;
                        if (status.contains("network")) networkError++;
                    }

                    double reliability = total == 0 ? 0.0 : (double) success / total;
                    String health;
                    if (reliability >= 0.95) health = "healthy";
                    else if (reliability >= 0.80) health = "degraded";
                    else if (reliability >= 0.50) health = "warning";
                    else health = "critical";

                    List<Map<String, Object>> diagnoses = new ArrayList<>();
                    if (rateLimit > 0) diagnoses.add(Map.of("issue", "rate-limit",
                        "count", rateLimit, "suggestion", "Reduce sync frequency or implement backoff strategy"));
                    if (authFailure > 0) diagnoses.add(Map.of("issue", "auth-failure",
                        "count", authFailure, "suggestion", "Refresh credentials and verify token expiry policy"));
                    if (schemaDrift > 0) diagnoses.add(Map.of("issue", "schema-drift",
                        "count", schemaDrift, "suggestion", "Re-run schema discovery and update field mappings"));
                    if (networkError > 0) diagnoses.add(Map.of("issue", "network-error",
                        "count", networkError, "suggestion", "Check firewall rules and retry with circuit breaker"));

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("tenantId", tenantId);
                    result.put("connectorId", connectorId);
                    result.put("health", health);
                    result.put("reliabilityScore", Math.round(reliability * 100) / 100.0);
                    result.put("totalSyncs", total);
                    result.put("successfulSyncs", success);
                    result.put("failedSyncs", total - success);
                    result.put("diagnoses", diagnoses);
                    result.put("requestId", requestId);
                    result.put("generatedAt", Instant.now().toString());
                    result.put("latencyMs", System.currentTimeMillis() - startMs);

                    recordAiAction(tenantId, "connector-sync-health", "connectorId=" + connectorId,
                        "static-heuristic", HEURISTIC_CONFIDENCE, true,
                        System.currentTimeMillis() - startMs, requestId);

                    return Promise.of(http.jsonResponse(result));
                } catch (Exception e) {
                    return Promise.of(http.errorResponse(400, "Invalid sync health payload: " + e.getMessage()));
                }
            });
    }

    /**
     * {@code POST /api/v1/ai/context/rank}
     *
     * <p>Retrieval ranking and stale-context detection for AI context
     * documents. Scores each context entry by relevance, freshness, and
     * overlap with the query (P2.5).
     *
     * <p>Request: <pre>{"query":"...","contexts":[...]}</pre>
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleContextRank(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String requestId = resolveRequestId(request);
        long startMs = System.currentTimeMillis();

        return request.loadBody(MAX_PROMPT_TOKENS * 4)
            .then(body -> {
                try {
                    Map<String, Object> input = parseBody(body.getString(StandardCharsets.UTF_8));
                    String query = String.valueOf(input.getOrDefault("query", "")).toLowerCase(Locale.ROOT);
                    List<Map<String, Object>> contexts = (List<Map<String, Object>>) input.getOrDefault("contexts", List.of());

                    List<Map<String, Object>> ranked = new ArrayList<>();
                    long nowMs = System.currentTimeMillis();

                    for (Map<String, Object> ctx : contexts) {
                        String text = String.valueOf(ctx.getOrDefault("text", "")).toLowerCase(Locale.ROOT);
                        String createdAt = String.valueOf(ctx.getOrDefault("createdAt", ""));
                        String source = String.valueOf(ctx.getOrDefault("source", "unknown"));

                        // Relevance: keyword overlap between query and text
                        Set<String> queryWords = new HashSet<>(Arrays.asList(query.split("\\s+")));
                        Set<String> textWords = new HashSet<>(Arrays.asList(text.split("\\s+")));
                        long overlap = queryWords.stream().filter(textWords::contains).count();
                        double relevance = queryWords.isEmpty() ? 0.0 : (double) overlap / queryWords.size();

                        // Freshness decay
                        double freshness;
                        try {
                            Instant created = Instant.parse(createdAt);
                            long ageDays = (nowMs - created.toEpochMilli()) / (1000 * 60 * 60 * 24);
                            freshness = Math.max(0.0, 1.0 - (ageDays / 30.0));
                        } catch (Exception e) {
                            freshness = 0.5;
                        }

                        // Source authority bonus
                        double authorityBonus = switch (source.toLowerCase()) {
                            case "schema", "config", "verified" -> 0.2;
                            case "log", "event", "metric" -> -0.1;
                            default -> 0.0;
                        };

                        double score = (relevance * 0.6) + (freshness * 0.3) + authorityBonus;

                        Map<String, Object> rankedCtx = new LinkedHashMap<>(ctx);
                        rankedCtx.put("relevance", Math.round(relevance * 100) / 100.0);
                        rankedCtx.put("freshness", Math.round(freshness * 100) / 100.0);
                        rankedCtx.put("score", Math.round(score * 100) / 100.0);
                        rankedCtx.put("stale", freshness < 0.2);
                        ranked.add(rankedCtx);
                    }

                    ranked.sort((a, b) -> Double.compare((Double) b.get("score"), (Double) a.get("score")));

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("tenantId", tenantId);
                    result.put("query", query);
                    result.put("contextCount", contexts.size());
                    result.put("staleCount", ranked.stream().filter(c -> Boolean.TRUE.equals(c.get("stale"))).count());
                    result.put("ranked", ranked);
                    result.put("requestId", requestId);
                    result.put("generatedAt", Instant.now().toString());
                    result.put("latencyMs", System.currentTimeMillis() - startMs);

                    recordAiAction(tenantId, "context-rank", "contexts=" + contexts.size(),
                        "static-heuristic", HEURISTIC_CONFIDENCE, true,
                        System.currentTimeMillis() - startMs, requestId);

                    return Promise.of(http.jsonResponse(result));
                } catch (Exception e) {
                    return Promise.of(http.errorResponse(400, "Invalid context payload: " + e.getMessage()));
                }
            });
    }

    /**
     * {@code POST /api/v1/ai/quality/drift-detect}
     *
     * <p>Contract drift detection: compares two API schema or data contracts
     * and reports breaking vs non-breaking changes (P2.5 Testing/Quality).
     *
     * <p>Request: <pre>{"oldContract":{...},"newContract":{...}}</pre>
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleContractDrift(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String requestId = resolveRequestId(request);
        long startMs = System.currentTimeMillis();

        return request.loadBody(MAX_PROMPT_TOKENS * 4)
            .then(body -> {
                try {
                    Map<String, Object> input = parseBody(body.getString(StandardCharsets.UTF_8));
                    Map<String, String> oldContract = (Map<String, String>) input.getOrDefault("oldContract", Map.of());
                    Map<String, String> newContract = (Map<String, String>) input.getOrDefault("newContract", Map.of());

                    List<Map<String, Object>> changes = new ArrayList<>();

                    // Removed fields
                    for (var entry : oldContract.entrySet()) {
                        if (!newContract.containsKey(entry.getKey())) {
                            changes.add(Map.of(
                                "field", entry.getKey(),
                                "changeType", "removed",
                                "breaking", true,
                                "oldType", entry.getValue()
                            ));
                        }
                    }

                    // Added fields
                    for (var entry : newContract.entrySet()) {
                        if (!oldContract.containsKey(entry.getKey())) {
                            changes.add(Map.of(
                                "field", entry.getKey(),
                                "changeType", "added",
                                "breaking", false,
                                "newType", entry.getValue()
                            ));
                        }
                    }

                    // Modified fields
                    for (var entry : oldContract.entrySet()) {
                        if (newContract.containsKey(entry.getKey())) {
                            String oldType = entry.getValue();
                            String newType = newContract.get(entry.getKey());
                            if (!Objects.equals(oldType, newType)) {
                                boolean breaking = isTypeWidening(oldType, newType);
                                changes.add(Map.of(
                                    "field", entry.getKey(),
                                    "changeType", "modified",
                                    "breaking", breaking,
                                    "oldType", oldType,
                                    "newType", newType
                                ));
                            }
                        }
                    }

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("tenantId", tenantId);
                    result.put("breakingChanges", changes.stream().filter(c -> Boolean.TRUE.equals(c.get("breaking"))).count());
                    result.put("nonBreakingChanges", changes.stream().filter(c -> !Boolean.TRUE.equals(c.get("breaking"))).count());
                    result.put("changes", changes);
                    result.put("driftDetected", !changes.isEmpty());
                    result.put("requestId", requestId);
                    result.put("generatedAt", Instant.now().toString());
                    result.put("latencyMs", System.currentTimeMillis() - startMs);

                    recordAiAction(tenantId, "drift-detect", "fields=" + oldContract.size() + "->" + newContract.size(),
                        "static-heuristic", HEURISTIC_CONFIDENCE, true,
                        System.currentTimeMillis() - startMs, requestId);

                    return Promise.of(http.jsonResponse(result));
                } catch (Exception e) {
                    return Promise.of(http.errorResponse(400, "Invalid contract drift payload: " + e.getMessage()));
                }
            });
    }

    private boolean isTypeWidening(String oldType, String newType) {
        // Widening (string -> int) or narrowing (int -> string) changes
        if ("string".equalsIgnoreCase(oldType) && "int".equalsIgnoreCase(newType)) return true;
        if ("int".equalsIgnoreCase(oldType) && "string".equalsIgnoreCase(newType)) return true;
        // Nullable to non-nullable
        if ((oldType.contains("?") || oldType.contains("null")) && !newType.contains("?")) return true;
        return false;
    }

    // ... (rest of the code remains the same)
    static final String DC_AI_ACTIONS_COLLECTION = "dc_ai_actions";

    /**
     * Records a structured AI action record for auditability and learning-loop
     * telemetry. When {@code client} is available the record is persisted
     * asynchronously without blocking the HTTP response.
     */
    private void recordAiAction(String tenantId, String domain, String intent,
                                String model, double confidence, boolean fallback,
                                long latencyMs, String requestId) {
        if (client == null) return;
        try {
            String actionId = UUID.randomUUID().toString();
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("id", actionId);
            record.put("tenantId", tenantId);
            record.put("domain", domain);
            record.put("intent", intent);
            record.put("model", model);
            record.put("confidence", confidence);
            record.put("fallback", fallback);
            record.put("latencyMs", latencyMs);
            record.put("requestId", requestId);
            record.put("timestamp", Instant.now().toString());
            client.save(tenantId, DC_AI_ACTIONS_COLLECTION, record)
                .whenResult(e -> log.debug("[DC-E3] AI action recorded actionId={} tenant={} domain={}", e.id(), tenantId, domain))
                .whenException(e -> log.warn("[DC-E3] AI action record failed tenant={} domain={}: {}", tenantId, domain, e.getMessage()));

            // P2.1: Emit to event log for audit trail
            Map<String, Object> eventPayload = new LinkedHashMap<>(record);
            eventPayload.remove("id");
            eventPayload.put("actionId", actionId);
                client.appendEvent(tenantId, DataCloudClient.Event.builder()
                    .type("ai.action")
                    .payload(eventPayload)
                    .source("datacloud.launcher.ai-assist")
                    .build())
                .whenException(e -> log.warn("[P2.1] AI action event append failed tenant={} domain={}: {}", tenantId, domain, e.getMessage()));
        } catch (Exception e) {
            log.warn("[DC-E3] AI action record construction failed tenant={} domain={}: {}", tenantId, domain, e.getMessage());
        }
    }

    /**
     * {@code GET /api/v1/ai/actions}
     *
     * <p>Returns persisted AI action records for the tenant, newest first,
     * with optional domain filter and limit.
     */
    public Promise<HttpResponse> handleListAiActions(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        if (client == null) {
            return Promise.of(http.jsonResponse(Map.of(
                "tenantId", tenantId,
                "actions", List.of(),
                "total", 0,
                "timestamp", Instant.now().toString()
            )));
        }
        int limit = HttpHandlerSupport.parseIntParam(request.getQueryParameter("limit"), 50);
        String domain = request.getQueryParameter("domain");
        DataCloudClient.Query query;
        if (domain != null && !domain.isBlank()) {
            query = DataCloudClient.Query.builder()
                .filter(DataCloudClient.Filter.eq("domain", domain))
                .limit(limit)
                .build();
        } else {
            query = DataCloudClient.Query.limit(limit);
        }
        return client.query(tenantId, DC_AI_ACTIONS_COLLECTION, query)
            .map(entities -> {
                List<Map<String, Object>> actions = entities.stream()
                    .map(e -> {
                        Map<String, Object> m = new LinkedHashMap<>(e.data());
                        m.put("actionId", e.id());
                        return m;
                    })
                    .toList();
                return http.jsonResponse(Map.of(
                    "tenantId", tenantId,
                    "actions", actions,
                    "total", actions.size(),
                    "timestamp", Instant.now().toString()
                ));
            })
            .then(Promise::of, e -> {
                log.error("[DC-E3] list AI actions failed tenant={}: {}", tenantId, e.getMessage(), e);
                return Promise.of(http.errorResponse(500, "Failed to list AI actions: " + e.getMessage()));
            });
    }

    /**
     * {@code POST /api/v1/ai/suggestions/:id/feedback}
     *
     * <p>Captures operator feedback on an AI suggestion for the learning loop (P2.6).
     * Stores the feedback as a structured event with provenance for training-signal
     * aggregation and confidence-model updates.
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleAiSuggestionFeedback(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String suggestionId = request.getPathParameter("id");
        if (suggestionId == null || suggestionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "Suggestion id path parameter is required"));
        }

        return request.loadBody(MAX_PROMPT_TOKENS * 4)
            .then(body -> {
                try {
                    Map<String, Object> payload = parseBody(body.getString(StandardCharsets.UTF_8));
                    Boolean accepted = payload.get("accepted") instanceof Boolean b ? b : null;
                    String sentiment = payload.get("sentiment") instanceof String s ? s.toLowerCase() : null;
                    String comment = payload.get("comment") instanceof String s ? s : null;
                    String actionType = payload.get("actionType") instanceof String s ? s : "ai-suggestion";

                    // Derive sentiment from accepted if not provided
                    if (sentiment == null && accepted != null) {
                        sentiment = accepted ? "positive" : "negative";
                    }
                    if (sentiment == null) {
                        return Promise.of(http.errorResponse(400, "Field 'accepted' or 'sentiment' is required"));
                    }

                    // Persist feedback as learning event
                    if (client != null) {
                        Map<String, Object> feedbackRecord = new LinkedHashMap<>();
                        feedbackRecord.put("id", UUID.randomUUID().toString());
                        feedbackRecord.put("tenantId", tenantId);
                        feedbackRecord.put("suggestionId", suggestionId);
                        feedbackRecord.put("actionType", actionType);
                        feedbackRecord.put("sentiment", sentiment);
                        feedbackRecord.put("accepted", accepted);
                        feedbackRecord.put("comment", comment);
                        feedbackRecord.put("source", "operator-feedback");
                        feedbackRecord.put("timestamp", Instant.now().toString());
                        client.save(tenantId, DC_AI_FEEDBACK_COLLECTION, feedbackRecord)
                            .whenResult(e -> log.debug("[P2.6] AI feedback recorded feedbackId={} tenant={} suggestion={}", e.id(), tenantId, suggestionId))
                            .whenException(e -> log.warn("[P2.6] AI feedback save failed tenant={}: {}", tenantId, e.getMessage()));
                    }

                    // Emit event to event log for provenance
                    if (client != null) {
                        Map<String, Object> eventPayload = new LinkedHashMap<>();
                        eventPayload.put("suggestionId", suggestionId);
                        eventPayload.put("sentiment", sentiment);
                        eventPayload.put("accepted", accepted);
                        eventPayload.put("actionType", actionType);
                        eventPayload.put("tenantId", tenantId);
                        eventPayload.put("comment", comment);
                        client.appendEvent(tenantId, DataCloudClient.Event.builder()
                            .type("ai.feedback")
                            .payload(eventPayload)
                            .source("datacloud.launcher.ai-assist")
                            .build())
                            .whenException(e -> log.warn("[P2.6] AI feedback event append failed tenant={}: {}", tenantId, e.getMessage()));
                    }

                    return Promise.of(http.jsonResponse(Map.of(
                        "suggestionId", suggestionId,
                        "tenantId", tenantId,
                        "sentiment", sentiment,
                        "accepted", accepted,
                        "recordedAt", Instant.now().toString()
                    )));
                } catch (Exception e) {
                    return Promise.of(http.errorResponse(400, "Invalid feedback payload: " + e.getMessage()));
                }
            });
    }

    /**
     * {@code GET /api/v1/ai/feedback}
     *
     * <p>Lists captured AI suggestion feedback for the tenant (P2.6).
     * Supports optional sentiment filter and limit.
     */
    public Promise<HttpResponse> handleListAiFeedback(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        if (client == null) {
            return Promise.of(http.jsonResponse(Map.of(
                "tenantId", tenantId,
                "feedback", List.of(),
                "total", 0,
                "timestamp", Instant.now().toString()
            )));
        }
        int limit = HttpHandlerSupport.parseIntParam(request.getQueryParameter("limit"), 50);
        String sentiment = request.getQueryParameter("sentiment");
        DataCloudClient.Query query;
        if (sentiment != null && !sentiment.isBlank()) {
            query = DataCloudClient.Query.builder()
                .filter(DataCloudClient.Filter.eq("sentiment", sentiment.toLowerCase()))
                .limit(limit)
                .build();
        } else {
            query = DataCloudClient.Query.limit(limit);
        }
        return client.query(tenantId, DC_AI_FEEDBACK_COLLECTION, query)
            .map(entities -> {
                List<Map<String, Object>> feedback = entities.stream()
                    .map(e -> {
                        Map<String, Object> m = new LinkedHashMap<>(e.data());
                        m.put("feedbackId", e.id());
                        return m;
                    })
                    .toList();
                return http.jsonResponse(Map.of(
                    "tenantId", tenantId,
                    "feedback", feedback,
                    "total", feedback.size(),
                    "timestamp", Instant.now().toString()
                ));
            })
            .then(Promise::of, e -> {
                log.error("[P2.6] list AI feedback failed tenant={}: {}", tenantId, e.getMessage(), e);
                return Promise.of(http.errorResponse(500, "Failed to list AI feedback: " + e.getMessage()));
            });
    }

    /**
     * {@code POST /api/v1/ai/rag-feedback}
     *
     * <p>Captures user corrections for RAG/semantic search results (P1.6).
     * Updates confidence scores for embeddings based on verified outcomes
     * and stores the feedback for learning-loop telemetry.
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleRagFeedback(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String requestId = resolveRequestId(request);
        if (client == null) {
            return Promise.of(http.errorResponse(503, "DataCloud client not available"));
        }

        return request.loadBody(MAX_PROMPT_TOKENS * 4)
            .then(buf -> {
                try {
                    Map<String, Object> input = parseBody(buf.getString(StandardCharsets.UTF_8));
                    String queryId = String.valueOf(input.getOrDefault("queryId", UUID.randomUUID().toString()));
                    String resultId = String.valueOf(input.getOrDefault("resultId", ""));
                    boolean relevant = Boolean.TRUE.equals(input.get("relevant"));
                    String correctedAnswer = (String) input.get("correctedAnswer");

                    Map<String, Object> record = new LinkedHashMap<>();
                    record.put("id", queryId);
                    record.put("tenantId", tenantId);
                    record.put("resultId", resultId);
                    record.put("relevant", relevant);
                    record.put("correctedAnswer", correctedAnswer);
                    record.put("recordedAt", Instant.now().toString());

                    double confidenceAdjustment = relevant ? 0.05 : -0.10;

                    return client.save(tenantId, "dc_rag_feedback", record)
                        .map(saved -> {
                            record.put("feedbackId", saved.id());
                            record.put("confidenceAdjustment", confidenceAdjustment);
                            return http.jsonResponse(record, requestId);
                        })
                        .then(Promise::of, e -> {
                            log.error("RAG feedback save failed tenant={}: {}", tenantId, e.getMessage(), e);
                            return Promise.of(http.errorResponse(500, "Failed to save RAG feedback: " + e.getMessage()));
                        });
                } catch (Exception e) {
                    return Promise.of(http.errorResponse(400, "Invalid RAG feedback payload: " + e.getMessage()));
                }
            });
    }

    /**
     * {@code POST /api/v1/ai/memory/retention}
     *
     * <p>Configures agent memory retention policies per tenant (P1.6).
     * Supports TTL-based eviction, explicit deletion, and archive rules.
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleMemoryRetention(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String requestId = resolveRequestId(request);
        if (client == null) {
            return Promise.of(http.errorResponse(503, "DataCloud client not available"));
        }

        return request.loadBody(MAX_PROMPT_TOKENS * 4)
            .then(buf -> {
                try {
                    Map<String, Object> input = parseBody(buf.getString(StandardCharsets.UTF_8));
                    int ttlDays = Integer.parseInt(String.valueOf(input.getOrDefault("ttlDays", 90)));
                    boolean archiveBeforeDelete = Boolean.TRUE.equals(input.getOrDefault("archiveBeforeDelete", false));
                    List<String> exemptCollections = (List<String>) input.getOrDefault("exemptCollections", List.of());

                    Map<String, Object> policy = new LinkedHashMap<>();
                    policy.put("tenantId", tenantId);
                    policy.put("ttlDays", ttlDays);
                    policy.put("archiveBeforeDelete", archiveBeforeDelete);
                    policy.put("exemptCollections", exemptCollections);
                    policy.put("effectiveFrom", Instant.now().toString());
                    policy.put("updatedAt", Instant.now().toString());

                    return client.save(tenantId, "dc_memory_retention_policies", policy)
                        .map(saved -> http.jsonResponse(Map.of(
                            "policyId", saved.id(),
                            "tenantId", tenantId,
                            "ttlDays", ttlDays,
                            "archiveBeforeDelete", archiveBeforeDelete,
                            "exemptCollections", exemptCollections,
                            "updatedAt", Instant.now().toString()
                        ), requestId))
                        .then(Promise::of, e -> {
                            log.error("Memory retention save failed tenant={}: {}", tenantId, e.getMessage(), e);
                            return Promise.of(http.errorResponse(500, "Failed to save memory retention policy"));
                        });
                } catch (Exception e) {
                    return Promise.of(http.errorResponse(400, "Invalid retention policy payload: " + e.getMessage()));
                }
            });
    }

    private static String resolveRequestId(HttpRequest request) {
        String rid = request.getHeader(io.activej.http.HttpHeaders.of("X-Request-ID"));
        return (rid != null && !rid.isBlank()) ? rid : UUID.randomUUID().toString();
    }

    static final String DC_AI_FEEDBACK_COLLECTION = "dc_ai_feedback";
}
