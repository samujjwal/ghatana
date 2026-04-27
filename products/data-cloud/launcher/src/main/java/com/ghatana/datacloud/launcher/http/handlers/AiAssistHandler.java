package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.ai.llm.ChatMessage;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.datacloud.governance.QuotaCheckResult;
import com.ghatana.datacloud.governance.TenantQuotaService;
import com.ghatana.datacloud.launcher.ai.AiRecommendationMetrics;
import com.ghatana.datacloud.launcher.http.ApiResponse;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
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

    /** Optional tenant quota service for AI token enforcement (P0.5). */
    private TenantQuotaService tenantQuotaService;

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
        String tenantId   = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String requestId  = resolveRequestId(request);
        long   startMs    = System.currentTimeMillis();

        Promise<HttpResponse> quotaErr = checkAiQuotaOrNull(tenantId, MAX_PROMPT_TOKENS);
        if (quotaErr != null) return quotaErr;

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
                                  AiRecommendationMetrics.TYPE_ENTITY_SUGGEST, tenantId, e);
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
        String tenantId  = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String requestId = resolveRequestId(request);
        long   startMs   = System.currentTimeMillis();

        Promise<HttpResponse> quotaErr = checkAiQuotaOrNull(tenantId, MAX_PROMPT_TOKENS);
        if (quotaErr != null) return quotaErr;

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
                                  AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, tenantId, e);
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
        String tenantId  = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String requestId = resolveRequestId(request);
        long   startMs   = System.currentTimeMillis();

        Promise<HttpResponse> quotaErr = checkAiQuotaOrNull(tenantId, MAX_PROMPT_TOKENS);
        if (quotaErr != null) return quotaErr;

        return request.loadBody(MAX_PROMPT_TOKENS * 4)
            .then(body -> {
                Map<String, Object> input = parseBody(body.getString(StandardCharsets.UTF_8));
                String query  = (String) input.getOrDefault("query", "");
                String schema = String.valueOf(input.getOrDefault("schema", "{}"));
                String context = String.valueOf(input.getOrDefault("context", "{}"));

                if (completionService == null) {
                    HttpResponse resp = heuristicAnalyticsAutomateResponse(query, schema, tenantId, requestId);
                    recommendationMetrics.recordRecommendation(
                        AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, tenantId,
                        HEURISTIC_CONFIDENCE, true, System.currentTimeMillis() - startMs);
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
                        return resp;
                    })
                    .then(Promise::of,
                          e -> {
                              log.warn("[DC-E3] analytics automate AI call failed tenant={}: {}", tenantId, e.getMessage());
                              recommendationMetrics.recordError(
                                  AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, tenantId, e);
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
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String requestId = resolveRequestId(request);
        long startMs = System.currentTimeMillis();

        Promise<HttpResponse> quotaErr = checkAiQuotaOrNull(tenantId, MAX_PROMPT_TOKENS);
        if (quotaErr != null) return quotaErr;

        return request.loadBody(MAX_PROMPT_TOKENS * 4)
            .then(body -> {
                Map<String, Object> input = parseBody(body.getString(StandardCharsets.UTF_8));
                String prompt = String.valueOf(input.getOrDefault("prompt", "")).trim();

                if (prompt.isBlank()) {
                    return Promise.of(http.errorResponse(400, "prompt is required"));
                }

                if (completionService == null) {
                    HttpResponse resp = heuristicPipelineDraftResponse(prompt, tenantId, requestId);
                    recommendationMetrics.recordRecommendation(
                        AiRecommendationMetrics.TYPE_PIPELINE_DRAFT,
                        tenantId,
                        HEURISTIC_CONFIDENCE,
                        true,
                        System.currentTimeMillis() - startMs);
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
                        return resp;
                    })
                    .then(Promise::of,
                        e -> {
                            log.warn("[DC-E3] pipeline draft AI call failed tenant={}: {}", tenantId, e.getMessage());
                            recommendationMetrics.recordError(
                                AiRecommendationMetrics.TYPE_PIPELINE_DRAFT,
                                tenantId,
                                e);
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
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String requestId = resolveRequestId(request);
        long startMs = System.currentTimeMillis();

        Promise<HttpResponse> quotaErr = checkAiQuotaOrNull(tenantId, MAX_PROMPT_TOKENS);
        if (quotaErr != null) return quotaErr;

        return request.loadBody(MAX_PROMPT_TOKENS * 4)
            .then(body -> {
                Map<String, Object> input = parseBody(body.getString(StandardCharsets.UTF_8));
                Map<String, Object> draft = (Map<String, Object>) input.getOrDefault("draft", Map.of());

                if (completionService == null) {
                    HttpResponse resp = heuristicPipelineRefineResponse(draftId, draft, tenantId, requestId);
                    recommendationMetrics.recordRecommendation(
                        AiRecommendationMetrics.TYPE_PIPELINE_DRAFT,
                        tenantId,
                        HEURISTIC_CONFIDENCE,
                        true,
                        System.currentTimeMillis() - startMs);
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
                        return resp;
                    })
                    .then(Promise::of,
                        e -> {
                            log.warn("[DC-E3] pipeline refine AI call failed draftId={} tenant={}: {}", draftId, tenantId, e.getMessage());
                            recommendationMetrics.recordError(
                                AiRecommendationMetrics.TYPE_PIPELINE_DRAFT,
                                tenantId,
                                e);
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
        String tenantId = http.requireTenantIdOrFail(request);
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
        String tenantId   = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String requestId  = resolveRequestId(request);
        long   startMs    = System.currentTimeMillis();

        Promise<HttpResponse> quotaErr = checkAiQuotaOrNull(tenantId, MAX_PROMPT_TOKENS);
        if (quotaErr != null) return quotaErr;

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
                                  AiRecommendationMetrics.TYPE_PIPELINE_HINT, tenantId, e);
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
        String tenantId  = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String requestId = resolveRequestId(request);
        long   startMs   = System.currentTimeMillis();

        Promise<HttpResponse> quotaErr = checkAiQuotaOrNull(tenantId, MAX_PROMPT_TOKENS);
        if (quotaErr != null) return quotaErr;

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
                                  AiRecommendationMetrics.TYPE_BRAIN_EXPLAIN, tenantId, e);
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
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String requestId = resolveRequestId(request);
        long startMs = System.currentTimeMillis();

        Promise<HttpResponse> quotaErr = checkAiQuotaOrNull(tenantId, MAX_PROMPT_TOKENS);
        if (quotaErr != null) return quotaErr;

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
                                  log.warn("[DC-E3] ai suggestions cross-surface call failed tenant={}: {}", tenantId, e.getMessage());
                                  recommendationMetrics.recordError(
                                      AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, tenantId, e);
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
                return Promise.of(http.jsonResponse(payload));
            });
    }

    /**
     * {@code POST /api/v1/ai/suggestions/:id/apply}
     *
     * <p>Apply an AI suggestion. Not yet implemented — returns 501 so the UI
     * boundary-error handling surfaces the missing capability.
     */
    public Promise<HttpResponse> handleApplyAiSuggestion(HttpRequest request) {
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        return Promise.of(http.errorResponse(501,
            "AI suggestion apply is not yet implemented. Use surface-specific action endpoints (e.g., pipeline execution, alert acknowledge) instead."));
    }

    /**
     * {@code GET /api/v1/ai/correlations}
     *
     * <p>Returns cross-surface AI correlations. Currently returns an empty list
     * with a boundary flag since the unified operation event model is not yet available.
     */
    public Promise<HttpResponse> handleAiCorrelations(HttpRequest request) {
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        Map<String, Object> payload = Map.of(
            "tenantId", tenantId,
            "correlations", List.of(),
            "count", 0,
            "generatedAt", Instant.now().toString()
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
        String tenantId = http.requireTenantIdOrFail(request);
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
                return resp;
            })
            .then(Promise::of,
                  e -> {
                      log.warn("[DC-E3] workflow advisory AI call failed workflowId={} tenant={}: {}", workflowId, tenantId, e.getMessage());
                      recommendationMetrics.recordError(
                          AiRecommendationMetrics.TYPE_PIPELINE_HINT, tenantId, e);
                      return Promise.of(heuristicPipelineHintResponse(workflowId, tenantId, requestId));
                  });
    }

    /**
     * {@code GET /api/v1/ai/advisories/quality/:collectionId}
     *
     * <p>Returns heuristic data-quality advisory for a collection.
     */
    public Promise<HttpResponse> handleAiQualityAdvisory(HttpRequest request) {
        String tenantId = http.requireTenantIdOrFail(request);
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
            "modelVersion", "heuristic-v1"
        );

        recommendationMetrics.recordRecommendation(
            AiRecommendationMetrics.TYPE_ANALYTICS_SUGGEST, tenantId,
            HEURISTIC_CONFIDENCE, true, System.currentTimeMillis() - startMs);
        return Promise.of(http.jsonResponse(payload));
    }

    /**
     * {@code GET /api/v1/ai/advisories/fabric/:collectionId}
     *
     * <p>Returns fabric tier placement advisory. Not yet implemented — returns
     * 501 so the UI boundary-error handling surfaces the missing capability.
     */
    public Promise<HttpResponse> handleAiFabricAdvisory(HttpRequest request) {
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        return Promise.of(http.errorResponse(501,
            "AI fabric advisory is not yet implemented. Use the tier migration endpoints for manual tier management."));
    }

    private static String resolveRequestId(HttpRequest request) {
        String rid = request.getHeader(io.activej.http.HttpHeaders.of("X-Request-ID"));
        return (rid != null && !rid.isBlank()) ? rid : UUID.randomUUID().toString();
    }
}
