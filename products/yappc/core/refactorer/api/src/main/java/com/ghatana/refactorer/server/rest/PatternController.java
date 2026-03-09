package com.ghatana.refactorer.server.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.refactorer.server.auth.TenantContextStorage;
import com.ghatana.refactorer.server.kg.KgService;
import com.ghatana.refactorer.server.dto.RestModels;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * REST controller for pattern management via Knowledge Graph.
 *
 *
 *
 * <p>
 * Provides endpoints for:
 *
 * - Submitting patterns for compilation
 *
 * - Querying patterns by criteria
 *
 * - Activating/deactivating patterns
 *
 * - Analyzing event sequences for pattern discovery
 *
 *
 *
 * @doc.type class
 *
 * @doc.purpose Handle HTTP endpoints for pattern workflows and delegate to
 * service-layer collaborators.
 *
 * @doc.layer product
 *
 * @doc.pattern Controller
 *
 */
public final class PatternController {

    private static final Logger logger = LogManager.getLogger(PatternController.class);

    private final KgService kgService;
    private final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();

    public PatternController(KgService kgService) {
        this.kgService = kgService;
    }

    /**
     * Submits a pattern specification to the Knowledge Graph.
     *
     * <p>
     * Request body: { "name": "...", "spec": "SEQ(A,B)", "confidence": 75,
     * "tags": [...] } Response: { "patternId": "...", "name": "...",
     * "confidence": 75, "status": "DRAFT" }
     */
    public Promise<HttpResponse> submitPattern(HttpRequest request) {
        return request
                .loadBody()
                .then(
                        body -> {
                            try {
                                logger.info("Received pattern submission request");

                                RestModels.PatternSubmissionRequest patternRequest
                                = objectMapper.readValue(
                                        body.getArray(), RestModels.PatternSubmissionRequest.class);

                                String tenantId = TenantContextStorage.getCurrentTenantId();
                                logger.debug("Submitting pattern for tenant: {}", tenantId);

                                return kgService
                                        .submitPattern(
                                                tenantId,
                                                patternRequest.name(),
                                                patternRequest.spec(),
                                                Map.of(
                                                        "confidence",
                                                        String.valueOf(patternRequest.confidence()),
                                                        "tags",
                                                        String.join(",", patternRequest.tags())))
                                        .map(
                                                compiled
                                                -> ResponseBuilder.created()
                                                        .json(
                                                                Map.of(
                                                                        "patternId",
                                                                        compiled.patternId(),
                                                                        "name",
                                                                        compiled.name(),
                                                                        "confidence",
                                                                        compiled.confidence(),
                                                                        "status",
                                                                        "DRAFT"))
                                                        .build());

                            } catch (Exception e) {
                                logger.error("Error processing pattern submission", e);
                                return Promise.of(
                                        ResponseBuilder.internalServerError()
                                                .json(
                                                        Map.of(
                                                                "error",
                                                                "PATTERN_SUBMISSION_ERROR",
                                                                "message",
                                                                e.getMessage()))
                                                .build());
                            }
                        });
    }

    /**
     * Queries patterns matching given criteria.
     *
     * <p>
     * Query parameters: - eventTypes (optional): Comma-separated event types to
     * filter by - minConfidence (optional, default 0): Minimum confidence
     * threshold 0-100
     *
     * <p>
     * Response: { "patterns": [...], "count": N }
     */
    public Promise<HttpResponse> queryPatterns(HttpRequest request) {
        try {
            logger.info("Received pattern query request");

            String tenantId = TenantContextStorage.getCurrentTenantId();
            String eventTypesParam = request.getQueryParameter("eventTypes");
            List<String> eventTypes
                    = eventTypesParam == null
                            ? List.of()
                            : List.of(eventTypesParam.split(","));

            double minConfidence
                    = Double.parseDouble(
                            Optional.ofNullable(request.getQueryParameter("minConfidence")).orElse("0"))
                    / 100.0;

            logger.debug(
                    "Querying patterns for tenant: {} with minConfidence: {}",
                    tenantId,
                    minConfidence);

            return kgService
                    .queryPatterns(tenantId, eventTypes, minConfidence)
                    .map(
                            patterns
                            -> ResponseBuilder.ok()
                                    .json(
                                            Map.of(
                                                    "patterns",
                                                    patterns.stream()
                                                            .map(
                                                                    p
                                                                    -> Map.of(
                                                                            "id",
                                                                            p.id(),
                                                                            "name",
                                                                            p.name(),
                                                                            "confidence",
                                                                            p.confidence(),
                                                                            "status",
                                                                            p.status().toString(),
                                                                            "matchCount",
                                                                            p.matchCount()))
                                                            .toList(),
                                                    "count",
                                                    patterns.size()))
                                    .build());

        } catch (Exception e) {
            logger.error("Error querying patterns", e);
            return Promise.of(
                    ResponseBuilder.badRequest()
                            .json(Map.of("error", "QUERY_ERROR", "message", e.getMessage()))
                            .build());
        }
    }

    /**
     * Analyzes event sequences for pattern discovery.
     *
     * <p>
     * Request body: { "timeWindowHours": 24, "minSupport": 0.5 } Response: {
     * "patterns": [...], "discovered": N }
     */
    public Promise<HttpResponse> analyzePatterns(HttpRequest request) {
        return request
                .loadBody()
                .then(
                        body -> {
                            try {
                                logger.info("Received pattern analysis request");

                                RestModels.PatternAnalysisRequest analysisRequest
                                = objectMapper.readValue(
                                        body.getArray(), RestModels.PatternAnalysisRequest.class);

                                String tenantId = TenantContextStorage.getCurrentTenantId();
                                logger.debug("Analyzing patterns for tenant: {}", tenantId);

                                return kgService
                                        .analyzePatterns(
                                                tenantId,
                                                analysisRequest.eventTypes(),
                                                analysisRequest.timeWindowHours(),
                                                analysisRequest.minSupport())
                                        .map(
                                                discovered
                                                -> ResponseBuilder.ok()
                                                        .json(
                                                                Map.of(
                                                                        "patterns",
                                                                        discovered.stream()
                                                                                .map(
                                                                                        p
                                                                                        -> Map.of(
                                                                                                "name",
                                                                                                p.name(),
                                                                                                "spec",
                                                                                                p.spec(),
                                                                                                "support",
                                                                                                p.support(),
                                                                                                "confidence",
                                                                                                p.confidence()))
                                                                                .toList(),
                                                                        "discovered",
                                                                        discovered.size()))
                                                        .build());

                            } catch (Exception e) {
                                logger.error("Error analyzing patterns", e);
                                return Promise.of(
                                        ResponseBuilder.internalServerError()
                                                .json(
                                                        Map.of(
                                                                "error",
                                                                "ANALYSIS_ERROR",
                                                                "message",
                                                                e.getMessage()))
                                                .build());
                            }
                        });
    }

    /**
     * Gets Knowledge Graph statistics.
     *
     * Response: { "totalPatterns": N, "activePatterns": N, "totalMatches": N,
     * "averageConfidence": X }
     */
    public Promise<HttpResponse> getStatistics(HttpRequest request) {
        try {
            logger.info("Received KG statistics request");

            String tenantId = TenantContextStorage.getCurrentTenantId();

            return kgService
                    .getStatistics(tenantId)
                    .map(
                            stats
                            -> ResponseBuilder.ok()
                                    .json(
                                            Map.of(
                                                    "totalPatterns",
                                                    stats.totalPatterns(),
                                                    "activePatterns",
                                                    stats.activePatterns(),
                                                    "totalMatches",
                                                    stats.totalMatches(),
                                                    "averageConfidence",
                                                    Math.round(stats.averageConfidence() * 100.0) / 100.0))
                                    .build());

        } catch (Exception e) {
            logger.error("Error getting KG statistics", e);
            return Promise.of(
                    ResponseBuilder.internalServerError()
                            .json(Map.of("error", "STATS_ERROR", "message", e.getMessage()))
                            .build());
        }
    }
}
