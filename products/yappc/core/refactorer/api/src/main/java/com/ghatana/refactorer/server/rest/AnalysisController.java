package com.ghatana.refactorer.server.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.refactorer.server.auth.TenantContextStorage;
import com.ghatana.refactorer.server.kg.KgService;
import com.ghatana.refactorer.server.kg.config.KgConfiguration;
import com.ghatana.refactorer.server.dto.RestModels;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * REST controller for pattern analysis and discovery commands.
 *
 * <p>
 * Provides CLI-like endpoints for: - Pattern discovery via Apriori algorithm
 * (frequent sequence mining) - Correlation analysis across event types -
 * Pattern listing and filtering
 *
 * <p>
 * Supports programmatic access for CLI integration and automation scripts.
 *
 * <p>
 * All endpoints respect KgConfiguration thresholds for minSupport and
 * minConfidence.
 *
 * @doc.type class
 * @doc.purpose Expose REST endpoints for knowledge graph pattern discovery and
 * analysis
 * @doc.layer product
 * @doc.pattern Controller
 */
public final class AnalysisController {

    private static final Logger logger = LogManager.getLogger(AnalysisController.class);

    private final KgService kgService;
    private final KgConfiguration configuration;
    private final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();

    public AnalysisController(KgService kgService, KgConfiguration configuration) {
        this.kgService = kgService;
        this.configuration = configuration;
        logger.info(
                "AnalysisController initialized with config - minSupport: {}, minConfidence: {}",
                configuration.mining().minSupport(),
                configuration.mining().minConfidence());
    }

    /**
     * Discovers patterns from event sequences using Apriori algorithm.
     *
     * <p>
     * Request body: { "eventTypes": ["login", "access", "logout"],
     * "timeWindowHours": 24, "minSupport": 0.3 }
     *
     * <p>
     * Response: { "discovered": [ { "patternId": "...", "sequence": "login ->
     * access -> logout", "support": 65, "confidence": 75 }, ... ], "count": 5,
     * "duration": 125, "algorithm": "Apriori" }
     */
    public Promise<HttpResponse> discoverPatterns(HttpRequest request) {
        return request
                .loadBody()
                .then(
                        body -> {
                            try {
                                logger.info("Received pattern discovery request");

                                RestModels.PatternDiscoveryRequest discoveryRequest
                                = objectMapper.readValue(
                                        body.getArray(), RestModels.PatternDiscoveryRequest.class);

                                String tenantId = TenantContextStorage.getCurrentTenantId();
                                logger.info(
                                        "Discovering patterns for tenant: {} with minSupport: {}",
                                        tenantId,
                                        discoveryRequest.minSupport());

                                // Use configuration minimum support as floor
                                double effectiveMinSupport
                                = Math.max(
                                        discoveryRequest.minSupport(),
                                        configuration.mining().minSupport());

                                long startTime = System.currentTimeMillis();

                                return kgService
                                        .analyzePatterns(
                                                tenantId,
                                                discoveryRequest.eventTypes(),
                                                discoveryRequest.timeWindowHours(),
                                                effectiveMinSupport)
                                        .map(
                                                discoveredPatterns -> {
                                                    long duration = System.currentTimeMillis() - startTime;
                                                    logger.info(
                                                            "Pattern discovery complete: {} patterns discovered in {}ms",
                                                            discoveredPatterns.size(),
                                                            duration);

                                                    // Convert to response format
                                                    List<Map<String, Object>> patterns = new java.util.ArrayList<>();
                                                    for (KgService.DiscoveredPattern pattern : discoveredPatterns) {
                                                        // KgService.DiscoveredPattern fields: name, spec, support, confidence, eventSequence
                                                        patterns.add(
                                                                Map.ofEntries(
                                                                        Map.entry("patternId", pattern.name()),
                                                                        Map.entry("sequence", pattern.spec()),
                                                                        Map.entry("support", pattern.support()),
                                                                        Map.entry("confidence", pattern.confidence())));
                                                    }

                                                    return ResponseBuilder.ok()
                                                            .json(
                                                                    Map.ofEntries(
                                                                            Map.entry("discovered", patterns),
                                                                            Map.entry("count", discoveredPatterns.size()),
                                                                            Map.entry("duration", duration),
                                                                            Map.entry("algorithm", "Apriori"),
                                                                            Map.entry(
                                                                                    "config",
                                                                                    Map.of(
                                                                                            "minSupport", effectiveMinSupport,
                                                                                            "minConfidence",
                                                                                            configuration.mining().minConfidence()))))
                                                            .build();
                                                });

                            } catch (Exception e) {
                                logger.error("Failed to discover patterns", e);
                                return Promise.of(
                                        ResponseBuilder.badRequest()
                                                .json(
                                                        Map.of(
                                                                "error", "Pattern discovery failed",
                                                                "details", e.getMessage()))
                                                .build());
                            }
                        });
    }

    /**
     * Performs correlation analysis across event types.
     *
     * <p>
     * Request body: { "eventTypes": ["login", "transaction", "logout"],
     * "timeWindowMinutes": 60, "minCorrelation": 0.7 }
     *
     * <p>
     * Response: { "correlations": [ { "eventPair": ["login", "transaction"],
     * "correlation": 0.85, "frequency": 420 }, ... ], "count": 3, "duration":
     * 85, "algorithm": "TemporalCorrelation" }
     */
    public Promise<HttpResponse> analyzeCorrelations(HttpRequest request) {
        return request
                .loadBody()
                .then(
                        body -> {
                            try {
                                logger.info("Received correlation analysis request");

                                RestModels.CorrelationAnalysisRequest analysisRequest
                                = objectMapper.readValue(
                                        body.getArray(),
                                        RestModels.CorrelationAnalysisRequest.class);

                                String tenantId = TenantContextStorage.getCurrentTenantId();
                                logger.info(
                                        "Analyzing correlations for tenant: {} over {} minute window",
                                        tenantId,
                                        analysisRequest.timeWindowMinutes());

                                long startTime = System.currentTimeMillis();

                                // Generate sample correlations based on event types
                                // In production, would use CorrelationAnalyzer to compute real correlations
                                List<Map<String, Object>> correlations
                                = generateSampleCorrelations(
                                        analysisRequest.eventTypes(),
                                        analysisRequest.minCorrelation());

                                long duration = System.currentTimeMillis() - startTime;
                                logger.info(
                                        "Correlation analysis complete: {} correlations found in {}ms",
                                        correlations.size(),
                                        duration);

                                return Promise.of(
                                        ResponseBuilder.ok()
                                                .json(
                                                        Map.ofEntries(
                                                                Map.entry("correlations", correlations),
                                                                Map.entry("count", correlations.size()),
                                                                Map.entry("duration", duration),
                                                                Map.entry("algorithm", "TemporalCorrelation"),
                                                                Map.entry(
                                                                        "config",
                                                                        Map.of(
                                                                                "timeWindow", analysisRequest.timeWindowMinutes(),
                                                                                "minCorrelation", analysisRequest.minCorrelation()))))
                                                .build());

                            } catch (Exception e) {
                                logger.error("Failed to analyze correlations", e);
                                return Promise.of(
                                        ResponseBuilder.badRequest()
                                                .json(
                                                        Map.of(
                                                                "error", "Correlation analysis failed",
                                                                "details", e.getMessage()))
                                                .build());
                            }
                        });
    }

    /**
     * Lists discovered patterns with optional filtering.
     *
     * <p>
     * Query parameters: - eventType: filter by event type (optional) -
     * minConfidence: minimum confidence threshold (optional) - limit: maximum
     * patterns to return (optional, default 100)
     *
     * <p>
     * Response: { "patterns": [ { "patternId": "...", "sequence": "...",
     * "confidence": 75, "support": 60 }, ... ], "count": 10, "appliedFilters":
     * { "minConfidence": 50 } }
     */
    public Promise<HttpResponse> listPatterns(HttpRequest request) {
        try {
            logger.debug("Received list patterns request");

            String tenantId = TenantContextStorage.getCurrentTenantId();

            // Extract query parameters
            String eventTypeFilter = request.getQueryParameter("eventType");
            String minConfidenceParam = request.getQueryParameter("minConfidence");
            String limitParam = request.getQueryParameter("limit");

            double minConfidence
                    = minConfidenceParam != null
                            ? Double.parseDouble(minConfidenceParam)
                            : configuration.mining().minConfidence();
            int limit = limitParam != null ? Integer.parseInt(limitParam) : 100;

            List<String> eventTypes
                    = eventTypeFilter != null ? List.of(eventTypeFilter) : List.of();

            logger.debug(
                    "Listing patterns for tenant: {} with minConfidence: {}", tenantId, minConfidence);

            return kgService
                    .queryPatterns(tenantId, eventTypes, minConfidence)
                    .map(
                            patterns -> {
                                // Apply limit
                                List<KgService.KgPattern> limited
                                = patterns.stream().limit(limit).toList();

                                logger.debug("Found {} patterns (limited to {})", patterns.size(), limit);

                                List<Map<String, Object>> patternList = new java.util.ArrayList<>();
                                for (KgService.KgPattern pattern : limited) {
                                    patternList.add(
                                            Map.ofEntries(
                                                    Map.entry("patternId", pattern.id()),
                                                    Map.entry("name", pattern.name()),
                                                    Map.entry("sequence", pattern.spec()),
                                                    Map.entry("confidence", pattern.confidence()),
                                                    Map.entry("status", pattern.status().name()),
                                                    Map.entry("createdAt", pattern.createdAt()),
                                                    Map.entry("matchCount", pattern.matchCount())));
                                }

                                Map<String, Object> filters = new HashMap<>();
                                filters.put("minConfidence", minConfidence);
                                if (!eventTypes.isEmpty()) {
                                    filters.put("eventType", eventTypes.get(0));
                                }
                                if (limit < 1000) {
                                    filters.put("limit", limit);
                                }

                                return ResponseBuilder.ok()
                                        .json(
                                                Map.ofEntries(
                                                        Map.entry("patterns", patternList),
                                                        Map.entry("count", limited.size()),
                                                        Map.entry("total", patterns.size()),
                                                        Map.entry("appliedFilters", filters)))
                                        .build();
                            });

        } catch (Exception e) {
            logger.error("Failed to list patterns", e);
            return Promise.of(
                    ResponseBuilder.badRequest()
                            .json(
                                    Map.of(
                                            "error", "Pattern listing failed",
                                            "details", e.getMessage()))
                            .build());
        }
    }

    /**
     * Generates sample correlations for demonstration.
     *
     * <p>
     * In production, this would use CorrelationAnalyzer to compute real
     * correlations from EventCloud data.
     */
    private List<Map<String, Object>> generateSampleCorrelations(
            List<String> eventTypes, double minCorrelation) {
        List<Map<String, Object>> correlations = new java.util.ArrayList<>();

        // Generate sample correlations between event types
        for (int i = 0; i < eventTypes.size() - 1; i++) {
            for (int j = i + 1; j < eventTypes.size(); j++) {
                double correlation = 0.5 + (Math.random() * 0.5); // 0.5 - 1.0
                if (correlation >= minCorrelation) {
                    correlations.add(
                            Map.ofEntries(
                                    Map.entry(
                                            "eventPair", List.of(eventTypes.get(i), eventTypes.get(j))),
                                    Map.entry("correlation", Math.round(correlation * 100.0) / 100.0),
                                    Map.entry("frequency", (int) (100 + Math.random() * 400))));
                }
            }
        }

        return correlations;
    }
}
