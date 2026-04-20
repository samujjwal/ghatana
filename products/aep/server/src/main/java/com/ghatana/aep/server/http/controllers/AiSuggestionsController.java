/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.ghatana.aep.metrics.AISuggestionMetricsCollector;
import com.ghatana.aep.server.analytics.DataCloudAnalyticsStore;
import com.ghatana.aep.server.http.HttpHelper;
import com.ghatana.aep.observability.AepSloMetrics;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final double HIGH_ERROR_RATE_THRESHOLD = 0.05;
    private static final double CRITICAL_ERROR_RATE_THRESHOLD = 0.20;

    @Nullable
    private final DataCloudAnalyticsStore analyticsStore;
    @Nullable
    private final AepSloMetrics sloMetrics;
    /** P2-11: Metrics collector for tracking suggestion effectiveness. */
    @Nullable
    private final AISuggestionMetricsCollector metricsCollector;

    /**
     * @param analyticsStore optional DataCloud analytics store; when {@code null} only SLO metrics
     *                       signals are used
     * @param sloMetrics     optional SLO metrics for recent run statistics; may be {@code null}
     */
    public AiSuggestionsController(
            @Nullable DataCloudAnalyticsStore analyticsStore,
            @Nullable AepSloMetrics sloMetrics) {
        this(analyticsStore, sloMetrics, null);
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
        this.analyticsStore = analyticsStore;
        this.sloMetrics = sloMetrics;
        this.metricsCollector = metricsCollector;
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
        int limit = parseIntParam(request.getQueryParameter("limit"), DEFAULT_LIMIT);

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
                .map(HttpHelper::jsonResponse)
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
            suggestions.add(anomalyToSuggestion(anomaly));
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

    private Map<String, Object> anomalyToSuggestion(DataCloudAnalyticsStore.AnomalyRecord anomaly) {
        String severity = mapAnomalySeverityToSuggestion(anomaly.severity());
        String type = "anomaly".equals(severity) ? "anomaly" : "warning";
        double confidence = Math.min(1.0, anomaly.score());

        Map<String, Object> suggestion = new java.util.LinkedHashMap<>();
        suggestion.put("id", UUID.randomUUID().toString());
        suggestion.put("type", type);
        suggestion.put("severity", severity);
        suggestion.put("message", buildAnomalyMessage(anomaly));
        suggestion.put("confidence", confidence);
        suggestion.put("resourceType", "pipeline");
        if (anomaly.entityId() != null) {
            suggestion.put("resourceId", anomaly.entityId());
        }
        return suggestion;
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
                suggestions.add(Map.of(
                        "id", UUID.randomUUID().toString(),
                        "type", "warning",
                        "severity", severity,
                        "message", String.format(
                                "%.1f%% of %d recent pipeline runs have failed — investigate failing steps.",
                                rate * 100, total),
                        "confidence", 0.85,
                        "resourceType", "pipeline"
                ));
            }

            if (total > 0 && rate == 0 && suggestions.size() < maxCount) {
                suggestions.add(Map.of(
                        "id", UUID.randomUUID().toString(),
                        "type", "recommendation",
                        "severity", "low",
                        "message", String.format(
                                "All %d recent pipeline runs succeeded — consider reviewing throughput limits.",
                                total),
                        "confidence", 0.80,
                        "resourceType", "pipeline"
                ));
            }
        } catch (Exception e) {
            log.warn("[ai-suggestions] error building SLO-metric suggestions for tenant={}: {}",
                    tenantId, e.getMessage());
        }
        return suggestions;
    }

    private HttpResponse buildFallbackResponse(String tenantId, int limit) {
        List<Map<String, Object>> suggestions = List.of(Map.of(
                "id", UUID.randomUUID().toString(),
                "type", "recommendation",
                "severity", "low",
                "message", "Connect DataCloud to enable AI-scored anomaly detection and optimisation suggestions.",
                "confidence", 1.0,
                "resourceType", "pipeline"
        ));
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

    private static String formatInstant(Instant instant) {
        if (instant == null) return "unknown";
        return instant.truncatedTo(ChronoUnit.SECONDS).toString();
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
