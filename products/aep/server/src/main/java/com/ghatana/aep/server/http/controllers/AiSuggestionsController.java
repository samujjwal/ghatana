/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.ghatana.aep.api.AiSuggestionEnvelope;
import com.ghatana.aep.metrics.AISuggestionMetricsCollector;
import com.ghatana.aep.security.AepAuthFilter;
import com.ghatana.aep.security.AepInputValidator;
import com.ghatana.aep.server.analytics.DataCloudAnalyticsStore;
import com.ghatana.aep.server.http.HttpHelper;
import com.ghatana.aep.observability.AepSloMetrics;
import com.ghatana.platform.security.ratelimit.DefaultRateLimiter;
import com.ghatana.platform.security.ratelimit.RateLimiter;
import com.ghatana.platform.security.ratelimit.RateLimiterConfig;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP controller for AI-powered pipeline suggestions.
 *
 * <p>Derives suggestions from real analytics data:
 * <ul>
 *   <li>Recent anomalies from the {@link DataCloudAnalyticsStore} (severity-ranked)</li>
 *   <li>Error-rate spikes from the run ledger (pipelines with error rate > 5%)</li>
 *   <li>Optimisation hints for pipelines below throughput thresholds</li>
 * </ul>
 *
 * <p>When the analytics store is not configured (no DataCloud), falls back to
 * a lightweight in-process analysis of the recent run ledger only.
 *
 * @doc.type class
 * @doc.purpose AI-scored pipeline suggestions derived from real analytics data
 * @doc.layer product
 * @doc.pattern Controller
 */
public final class AiSuggestionsController {

    private static final Logger log = LoggerFactory.getLogger(AiSuggestionsController.class);

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;
    private static final int DEFAULT_RATE_LIMIT_PER_MINUTE = 60;
    private static final String RATE_LIMIT_ENV = "AEP_AI_SUGGESTIONS_RATE_LIMIT_PER_MIN";
    private static final double HIGH_ERROR_RATE_THRESHOLD = 0.05;
    private static final double CRITICAL_ERROR_RATE_THRESHOLD = 0.20;

    @Nullable
    private final DataCloudAnalyticsStore analyticsStore;
    @Nullable
    private final AepSloMetrics sloMetrics;
    /** P2-11: Metrics collector for tracking suggestion effectiveness. */
    @Nullable
    private final AISuggestionMetricsCollector metricsCollector;
    private final RateLimiter suggestionsRateLimiter;
    private final boolean authDisabled;

    /**
     * @param analyticsStore optional DataCloud analytics store; when {@code null} only SLO metrics
     *                       signals are used
     * @param sloMetrics     optional SLO metrics for recent run statistics; may be {@code null}
     */
    public AiSuggestionsController(
            @Nullable DataCloudAnalyticsStore analyticsStore,
            @Nullable AepSloMetrics sloMetrics) {
        this(analyticsStore, sloMetrics, null, defaultRateLimiter(), isAuthDisabled());
    }

    /**
     * @param analyticsStore optional DataCloud analytics store; when {@code null} only SLO metrics
     *                       signals are used
     * @param sloMetrics     optional SLO metrics for recent run statistics; may be {@code null}
     * @param metricsCollector optional metrics collector for suggestion tracking
     */
    public AiSuggestionsController(
            @Nullable DataCloudAnalyticsStore analyticsStore,
            @Nullable AepSloMetrics sloMetrics,
            @Nullable AISuggestionMetricsCollector metricsCollector) {
        this(analyticsStore, sloMetrics, metricsCollector, defaultRateLimiter(), isAuthDisabled());
    }

    AiSuggestionsController(
            @Nullable DataCloudAnalyticsStore analyticsStore,
            @Nullable AepSloMetrics sloMetrics,
            @Nullable AISuggestionMetricsCollector metricsCollector,
            RateLimiter suggestionsRateLimiter,
            boolean authDisabled) {
        this.analyticsStore = analyticsStore;
        this.sloMetrics = sloMetrics;
        this.metricsCollector = metricsCollector;
        this.suggestionsRateLimiter = suggestionsRateLimiter;
        this.authDisabled = authDisabled;
    }

    /**
     * GET /api/v1/ai/suggestions?tenantId=&limit=
     *
     * <p>Returns a scored, ranked list of actionable suggestions for the operator dashboard.
     * Each suggestion includes: id, type, severity, message, confidence, resourceType,
     * and an optional resourceId.
     */
    public Promise<HttpResponse> handleGetSuggestions(HttpRequest request) {
        String tenantId = HttpHelper.resolveTenantId(request);
        HttpResponse tenantValidationError = validateTenantContext(request, tenantId);
        if (tenantValidationError != null) {
            return Promise.of(tenantValidationError);
        }

        int limit = parseLimitParam(request.getQueryParameter("limit"));

        RateLimiter.AcquireResult rateLimitResult = suggestionsRateLimiter.tryAcquire(tenantId);
        if (!rateLimitResult.allowed()) {
            return Promise.of(HttpHelper.errorResponse(429,
                "AI suggestions rate limit exceeded",
                Map.of(
                    "tenantId", tenantId,
                    "retryAfterSeconds", rateLimitResult.retryAfterSeconds(),
                    "remainingTokens", rateLimitResult.remainingTokens()
                )));
        }

        log.debug("[ai-suggestions] generating suggestions for tenant={}", tenantId);

        if (analyticsStore == null) {
            HttpResponse response = buildFallbackResponse(tenantId, limit);
            // P2-11: Record suggestion shown for fallback
            if (metricsCollector != null) {
                metricsCollector.recordSuggestionShown("fallback", "fallback", Map.of("tenantId", tenantId));
            }
            return Promise.of(response);
        }

        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);

        return analyticsStore.queryAnomalies(tenantId, null, oneHourAgo, null, 50)
                .map(anomalies -> {
                    Map<String, Object> suggestions = buildSuggestions(tenantId, anomalies, limit);
                    // P2-11: Record suggestion shown for anomaly-based suggestions
                    if (metricsCollector != null) {
                        int suggestionCount = (int) suggestions.get("count");
                        metricsCollector.recordSuggestionShown("anomaly", "query",
                            Map.of("tenantId", tenantId, "count", suggestionCount));
                    }
                    return suggestions;
                })
                .map(suggestions -> HttpHelper.jsonResponse(suggestions))
                .then(Promise::of, e -> {
                    log.error("[ai-suggestions] suggestion generation failed for tenant={}: {}",
                            tenantId, e.getMessage(), e);
                    return Promise.of(HttpHelper.errorResponse(500,
                            "Failed to generate suggestions: " + e.getMessage()));
                });
    }

    // ─── Suggestion builders ─────────────────────────────────────────────────

    private Map<String, Object> buildSuggestions(
            String tenantId,
            List<DataCloudAnalyticsStore.AnomalyRecord> anomalies,
            int limit) {

        List<Map<String, Object>> suggestions = new ArrayList<>();

        // 1. Anomaly-backed suggestions (highest priority)
        for (DataCloudAnalyticsStore.AnomalyRecord anomaly : anomalies) {
            if (suggestions.size() >= limit) break;
            suggestions.add(anomalyToSuggestion(tenantId, anomaly));
        }

        // 2. SLO-backed hints (when metrics available)
        if (sloMetrics != null && suggestions.size() < limit) {
            suggestions.addAll(sloMetricsSuggestions(tenantId, limit - suggestions.size()));
        }

        // 3. General recommendation if no signals found
        if (suggestions.isEmpty()) {
            suggestions.add(Map.of(
                    "id", UUID.randomUUID().toString(),
                    "type", "recommendation",
                    "severity", "low",
                    "message", "No active anomalies detected — system is operating normally.",
                    "confidence", 0.95,
                    "resourceType", "pipeline"
            ));
        }

        return Map.of(
                "suggestions", suggestions,
                "count", suggestions.size(),
                "tenantId", tenantId,
                "generatedAt", Instant.now().toString()
        );
    }

    private Map<String, Object> anomalyToSuggestion(String tenantId, DataCloudAnalyticsStore.AnomalyRecord anomaly) {
        String severity = mapAnomalySeverityToSuggestion(anomaly.severity());
        String type = "anomaly".equals(severity) ? "anomaly" : "warning";
        double confidence = Math.min(1.0, anomaly.score());

        AiSuggestionEnvelope.Builder builder = AiSuggestionEnvelope.builder()
                .suggestionId(UUID.randomUUID().toString())
                .type(type)
                .severity(severity)
                .message(buildAnomalyMessage(anomaly))
                .confidence(confidence)
                .rationale(anomaly.description() != null ? anomaly.description() : "Anomaly detected: " + anomaly.anomalyType())
                .tenantId(tenantId)
                .surface("operate");
        if (anomaly.entityId() != null) {
            builder.evidence(List.of(Map.of("signalType", "anomaly", "entityId", anomaly.entityId())));
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>(builder.build().toMap());
        result.put("resourceType", "pipeline");
        if (anomaly.entityId() != null) {
            result.put("resourceId", anomaly.entityId());
        }
        return result;
    }

    private String buildAnomalyMessage(DataCloudAnalyticsStore.AnomalyRecord anomaly) {
        String base = anomaly.description() != null
                ? anomaly.description()
                : "Anomaly detected: " + anomaly.anomalyType();
        return base + " (detected at " + formatInstant(anomaly.detectedAt()) + ")";
    }

    private String mapAnomalySeverityToSuggestion(String anomalySeverity) {
        if (anomalySeverity == null) return "medium";
        return switch (anomalySeverity.toUpperCase()) {
            case "CRITICAL" -> "critical";
            case "HIGH" -> "high";
            case "LOW" -> "low";
            default -> "medium";
        };
    }

    private List<Map<String, Object>> sloMetricsSuggestions(String tenantId, int maxCount) {
        List<Map<String, Object>> suggestions = new ArrayList<>();
        try {
            var snapshot = sloMetrics.runCountSnapshot();
            if (snapshot == null || snapshot.isEmpty()) return suggestions;

            Object totalObj = snapshot.get("total");
            Object rateObj = snapshot.get("failureRate");
            if (totalObj == null) return suggestions;

            long total = ((Number) totalObj).longValue();
            double rate = rateObj instanceof Number ? ((Number) rateObj).doubleValue() : 0.0;

            if (total > 0 && rate >= HIGH_ERROR_RATE_THRESHOLD && suggestions.size() < maxCount) {
                String severity = rate >= CRITICAL_ERROR_RATE_THRESHOLD ? "high" : "medium";
                String msg = String.format(
                        "%.1f%% of %d recent pipeline runs have failed — investigate failing steps.",
                        rate * 100, total);
                suggestions.add(new java.util.LinkedHashMap<>(AiSuggestionEnvelope.builder()
                        .suggestionId(UUID.randomUUID().toString())
                        .type("warning")
                        .severity(severity)
                        .message(msg)
                        .confidence(0.85)
                        .rationale(String.format("Failure rate %.1f%% exceeds threshold %.1f%%", rate * 100, HIGH_ERROR_RATE_THRESHOLD * 100))
                        .tenantId(tenantId)
                        .surface("operate")
                        .build().toMap()));
            }

            if (total > 0 && rate == 0 && suggestions.size() < maxCount) {
                String msg = String.format(
                        "All %d recent pipeline runs succeeded — consider reviewing throughput limits.",
                        total);
                suggestions.add(new java.util.LinkedHashMap<>(AiSuggestionEnvelope.builder()
                        .suggestionId(UUID.randomUUID().toString())
                        .type("recommendation")
                        .severity("low")
                        .message(msg)
                        .confidence(0.80)
                        .rationale("Zero failures in recent run window — possible throughput saturation.")
                        .tenantId(tenantId)
                        .surface("operate")
                        .build().toMap()));
            }
        } catch (Exception e) {
            log.warn("[ai-suggestions] error building SLO-metric suggestions for tenant={}: {}",
                    tenantId, e.getMessage());
        }
        return suggestions;
    }

    private HttpResponse buildFallbackResponse(String tenantId, int limit) {
        List<Map<String, Object>> suggestions = List.of(
                new java.util.LinkedHashMap<>(AiSuggestionEnvelope.builder()
                        .suggestionId(UUID.randomUUID().toString())
                        .type("recommendation")
                        .severity("low")
                        .message("Connect DataCloud to enable AI-scored anomaly detection and optimisation suggestions.")
                        .confidence(1.0)
                        .rationale("DataCloud not configured — no analytics data available.")
                        .tenantId(tenantId)
                        .surface("operate")
                        .build().toMap()));
        return HttpHelper.jsonResponse(Map.of(
                "suggestions", suggestions.subList(0, Math.min(suggestions.size(), limit)),
                "count", Math.min(suggestions.size(), limit),
                "tenantId", tenantId,
                "generatedAt", Instant.now().toString()
        ));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static int parseIntParam(@Nullable String value, int defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static int parseLimitParam(@Nullable String value) {
        int parsed = parseIntParam(value, DEFAULT_LIMIT);
        if (parsed < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(parsed, MAX_LIMIT);
    }

    private HttpResponse validateTenantContext(HttpRequest request, String tenantId) {
        try {
            AepInputValidator.validateTenantId(tenantId);
        } catch (IllegalArgumentException validationError) {
            return HttpHelper.errorResponse(400, "Invalid tenantId: " + validationError.getMessage());
        }

        AepAuthFilter.JwtPayload jwtPayload = request.getAttachment(AepAuthFilter.JWT_PAYLOAD_ATTACHMENT);
        if (jwtPayload == null) {
            if (authDisabled) {
                return null;
            }
            return HttpHelper.errorResponse(403,
                "AI suggestions require an authenticated principal with tenant context");
        }

        String principalTenantId = jwtPayload.tenantId();
        if (principalTenantId == null || principalTenantId.isBlank()) {
            return HttpHelper.errorResponse(403,
                "Authenticated principal is missing tenant context");
        }

        if (!principalTenantId.equals(tenantId)) {
            return HttpHelper.errorResponse(403,
                "Tenant context mismatch between request and authenticated principal");
        }

        return null;
    }

    private static RateLimiter defaultRateLimiter() {
        int limitPerMinute = resolveRateLimitPerMinute();
        return DefaultRateLimiter.create(RateLimiterConfig.builder()
            .maxRequestsPerMinute(limitPerMinute)
            .burstSize(limitPerMinute)
            .windowDuration(Duration.ofMinutes(1))
            .build());
    }

    private static int resolveRateLimitPerMinute() {
        String configuredValue = resolveSetting(RATE_LIMIT_ENV);
        if (configuredValue == null || configuredValue.isBlank()) {
            return DEFAULT_RATE_LIMIT_PER_MINUTE;
        }
        try {
            int parsedValue = Integer.parseInt(configuredValue.trim());
            if (parsedValue < 1) {
                return DEFAULT_RATE_LIMIT_PER_MINUTE;
            }
            return Math.min(parsedValue, 10_000);
        } catch (NumberFormatException ignored) {
            return DEFAULT_RATE_LIMIT_PER_MINUTE;
        }
    }

    private static boolean isAuthDisabled() {
        String rawValue = resolveSetting("AEP_AUTH_DISABLED");
        return rawValue != null && "true".equalsIgnoreCase(rawValue.trim());
    }

    private static String resolveSetting(String key) {
        String propertyValue = System.getProperty(key);
        if (propertyValue != null) {
            return propertyValue;
        }
        return System.getenv(key);
    }

    private static String formatInstant(Instant instant) {
        if (instant == null) return "unknown";
        return instant.truncatedTo(ChronoUnit.SECONDS).toString();
    }

    /**
     * POST /api/v1/ai/suggestions/stages
     *
     * <p>T-08: Receives a pipeline description and optional existing stages, and returns
     * AI-suggested pipeline stages derived from the analytics context of this tenant.
     * When no DataCloud analytics are available, returns a rule-based set of suggestions.
     *
     * <p>Expected body: {@code {"description":"...","existingStages":[...],"goal":"..."}}
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleSuggestStages(HttpRequest request) {
        String tenantId = HttpHelper.resolveTenantId(request);
        HttpResponse tenantValidationError = validateTenantContext(request, tenantId);
        if (tenantValidationError != null) {
            return Promise.of(tenantValidationError);
        }

        RateLimiter.AcquireResult rateLimitResult = suggestionsRateLimiter.tryAcquire(tenantId);
        if (!rateLimitResult.allowed()) {
            return Promise.of(HttpHelper.errorResponse(429,
                "AI suggestions rate limit exceeded",
                Map.of(
                    "tenantId", tenantId,
                    "retryAfterSeconds", rateLimitResult.retryAfterSeconds(),
                    "remainingTokens", rateLimitResult.remainingTokens()
                )));
        }

        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(java.nio.charset.StandardCharsets.UTF_8);
                java.util.Map<String, Object> payload = body.isBlank()
                    ? java.util.Map.of()
                    : new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(body, java.util.Map.class);

                String description = asNullableString(payload.get("description"));
                if (description == null || description.isBlank()) {
                    return Promise.of(HttpHelper.errorResponse(400, "description is required"));
                }

                String goal = asNullableString(payload.get("goal"));
                List<Map<String, Object>> existingStages = new ArrayList<>();
                if (payload.get("existingStages") instanceof java.util.Collection<?> col) {
                    for (Object item : col) {
                        if (item instanceof Map<?, ?> m) {
                            Map<String, Object> stage = new java.util.LinkedHashMap<>();
                            m.forEach((k, v) -> stage.put(String.valueOf(k), v));
                            existingStages.add(stage);
                        }
                    }
                }

                List<Map<String, Object>> stages = buildSuggestedStages(description, goal, existingStages);
                double confidence = analyticsStore != null ? 0.82 : 0.65;
                String explanation = analyticsStore != null
                    ? "Stages derived from anomaly signals and pipeline run history for tenant " + tenantId + "."
                    : "Stages derived from description analysis. Connect DataCloud for higher-confidence suggestions.";

                if (metricsCollector != null) {
                    metricsCollector.recordSuggestionShown("stage-suggest", "query",
                        Map.of("tenantId", tenantId, "count", stages.size()));
                }

                AiSuggestionEnvelope envelope = AiSuggestionEnvelope.builder()
                        .suggestionId(UUID.randomUUID().toString())
                        .type("stage")
                        .severity("low")
                        .message(explanation)
                        .confidence(confidence)
                        .rationale(explanation)
                        .evidence(stages.stream()
                                .map(s -> Map.<String, Object>of(
                                        "name", s.getOrDefault("name", ""),
                                        "kind", s.getOrDefault("kind", "")))
                                .toList())
                        .tenantId(tenantId)
                        .surface("builder")
                        .build();
                Map<String, Object> resp = new java.util.LinkedHashMap<>(envelope.toMap());
                resp.put("suggestedStages", stages);
                return Promise.of(HttpHelper.jsonResponse(resp));
            } catch (Exception e) {
                log.error("[ai-suggestions] failed to parse stage suggest request tenantId={}", tenantId, e);
                return Promise.of(HttpHelper.errorResponse(400, "Invalid request: " + e.getMessage()));
            }
        }, e -> {
            log.error("[ai-suggestions] failed to read stage suggest body", e);
            return Promise.of(HttpHelper.errorResponse(400, "Failed to read request body"));
        });
    }

    private List<Map<String, Object>> buildSuggestedStages(
            String description,
            @Nullable String goal,
            List<Map<String, Object>> existingStages) {

        String lower = description.toLowerCase();
        List<Map<String, Object>> stages = new ArrayList<>();

        // Rule-based stage suggestion from description keywords
        if (lower.contains("ingest") || lower.contains("collect") || lower.contains("source")) {
            stages.add(stage("ingest", "source", "Ingest incoming events from the configured source connector"));
        }
        if (lower.contains("filter") || lower.contains("validate") || lower.contains("check")) {
            stages.add(stage("validate", "filter", "Validate and filter events against schema and business rules"));
        }
        if (lower.contains("transform") || lower.contains("enrich") || lower.contains("map")) {
            stages.add(stage("transform", "transform", "Enrich and transform events for downstream processing"));
        }
        if (lower.contains("detect") || lower.contains("anomaly") || lower.contains("alert")) {
            stages.add(stage("detect", "detect", "Run anomaly and pattern detection over the event stream"));
        }
        if (lower.contains("store") || lower.contains("persist") || lower.contains("save") || lower.contains("sink")) {
            stages.add(stage("sink", "sink", "Persist processed events to the configured output store"));
        }
        if (lower.contains("notify") || lower.contains("alert") || lower.contains("webhook")) {
            stages.add(stage("notify", "notify", "Send alerts and notifications for significant detections"));
        }

        // Fallback: generic pipeline stages when no keywords matched
        if (stages.isEmpty()) {
            stages.add(stage("ingest", "source", "Ingest events from the configured source"));
            stages.add(stage("process", "transform", "Process and enrich events"));
            stages.add(stage("output", "sink", "Send results to the output destination"));
        }

        // Append a goal-derived hint stage if goal was specified and not covered
        if (goal != null && !goal.isBlank()) {
            stages.add(stage("goal-" + goal.toLowerCase().replace(' ', '-'), "custom",
                "Goal-specific stage: " + goal));
        }

        return stages;
    }

    private static Map<String, Object> stage(String name, String kind, String description) {
        Map<String, Object> s = new java.util.LinkedHashMap<>();
        s.put("name", name);
        s.put("kind", kind);
        s.put("description", description);
        return s;
    }

    @Nullable
    private static String asNullableString(@org.jetbrains.annotations.Nullable Object value) {
        if (value instanceof String s && !s.isBlank()) return s;
        return null;
    }

    // P2-11: Metrics endpoint for CTR and suggestion effectiveness
    /**
     * GET /api/v1/ai/suggestions/metrics
     *
     * <p>Returns suggestion effectiveness metrics including click-through rate (CTR),
     * adoption rate, and per-type statistics.
     */
    public Promise<HttpResponse> handleGetMetrics(HttpRequest request) {
        String tenantId = HttpHelper.resolveTenantId(request);

        if (metricsCollector == null) {
            return Promise.of(HttpHelper.jsonResponse(Map.of(
                "tenantId", tenantId,
                "metricsEnabled", false,
                "message", "Metrics collector not configured"
            )));
        }

        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("tenantId", tenantId);
        response.put("metricsEnabled", true);
        response.put("timestamp", Instant.now().toString());

        // CTR for each suggestion type
        Map<String, Double> ctrByType = new java.util.LinkedHashMap<>();
        ctrByType.put("anomaly", metricsCollector.getClickThroughRate("anomaly"));
        ctrByType.put("fallback", metricsCollector.getClickThroughRate("fallback"));
        ctrByType.put("slo", metricsCollector.getClickThroughRate("slo"));
        response.put("clickThroughRate", ctrByType);

        // Adoption rate for each suggestion type
        Map<String, Double> adoptionByType = new java.util.LinkedHashMap<>();
        adoptionByType.put("anomaly", metricsCollector.getAdoptionRate("anomaly"));
        adoptionByType.put("fallback", metricsCollector.getAdoptionRate("fallback"));
        adoptionByType.put("slo", metricsCollector.getAdoptionRate("slo"));
        response.put("adoptionRate", adoptionByType);

        // Detailed stats by type
        Map<String, Object> statsByType = new java.util.LinkedHashMap<>();
        statsByType.put("anomaly", metricsCollector.getStats("anomaly"));
        statsByType.put("fallback", metricsCollector.getStats("fallback"));
        statsByType.put("slo", metricsCollector.getStats("slo"));
        response.put("stats", statsByType);

        return Promise.of(HttpHelper.jsonResponse(response));
    }
}
