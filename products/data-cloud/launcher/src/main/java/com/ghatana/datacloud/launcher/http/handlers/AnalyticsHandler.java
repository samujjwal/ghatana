package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.analytics.AnalyticsQueryEngine;
import com.ghatana.datacloud.analytics.report.ReportDefinition;
import com.ghatana.datacloud.analytics.report.ReportResult;
import com.ghatana.datacloud.analytics.report.ReportService;
import io.activej.http.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles analytics query (DC-9) and report generation (DC-10) HTTP endpoints.
 *
 * @doc.type class
 * @doc.purpose Analytics and reporting HTTP handlers (DC-9, DC-10)
 * @doc.layer product
 * @doc.pattern Handler
 */
public class AnalyticsHandler {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsHandler.class);

    private final AnalyticsQueryEngine analyticsEngine;
    private final HttpHandlerSupport http;
    private ReportService reportService;

    public AnalyticsHandler(AnalyticsQueryEngine analyticsEngine, HttpHandlerSupport http) {
        this.analyticsEngine = analyticsEngine;
        this.http = http;
    }

    public AnalyticsHandler withReportService(ReportService service) {
        this.reportService = service;
        return this;
    }

    // ==================== Analytics Endpoints (DC-9) ====================

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleAnalyticsQuery(HttpRequest request) {
        if (analyticsEngine == null) {
            return Promise.of(http.errorResponse(503, "Analytics engine not available in this deployment"));
        }
        try {
            String tenantId = http.resolveTenantId(request);
            String body = request.loadBody().getResult().getString(StandardCharsets.UTF_8);
            Map<String, Object> payload = http.objectMapper().readValue(body, Map.class);
            String queryText = (String) payload.get("query");
            if (queryText == null || queryText.isBlank()) {
                return Promise.of(http.errorResponse(400, "Missing required field: 'query'"));
            }
            Map<String, Object> params = payload.containsKey("parameters")
                ? (Map<String, Object>) payload.get("parameters")
                : Map.of();
            return analyticsEngine.submitQuery(tenantId, queryText, params)
                .map(result -> http.jsonResponse(Map.of(
                    "queryId",         result.getQueryId(),
                    "queryType",       result.getQueryType(),
                    "rowCount",        result.getRowCount(),
                    "columnCount",     result.getColumnCount(),
                    "rows",            result.getRows(),
                    "executionTimeMs", result.getExecutionTimeMs(),
                    "optimized",       result.isOptimized(),
                    "timestamp",       Instant.now().toString()
                )))
                .then(
                    response -> Promise.of(response),
                    e -> {
                        log.error("[DC-9] analytics query failed: {}", e.getMessage(), e);
                        return Promise.of(http.errorResponse(500, "Query execution failed: " + e.getMessage()));
                    }
                );
        } catch (Exception e) {
            log.error("[DC-9] analytics query request parse error: {}", e.getMessage(), e);
            return Promise.of(http.errorResponse(400, "Invalid request: " + e.getMessage()));
        }
    }

    public Promise<HttpResponse> handleAnalyticsGetResult(HttpRequest request) {
        if (analyticsEngine == null) {
            return Promise.of(http.errorResponse(503, "Analytics engine not available in this deployment"));
        }
        String queryId = request.getPathParameter("queryId");
        return analyticsEngine.getResult(queryId)
            .map(result -> {
                if (result == null) {
                    return http.errorResponse(404, "No result found for queryId: " + queryId);
                }
                return http.jsonResponse(Map.of(
                    "queryId",         result.getQueryId(),
                    "queryType",       result.getQueryType(),
                    "rowCount",        result.getRowCount(),
                    "columnCount",     result.getColumnCount(),
                    "rows",            result.getRows(),
                    "executionTimeMs", result.getExecutionTimeMs(),
                    "optimized",       result.isOptimized(),
                    "timestamp",       Instant.now().toString()
                ));
            })
            .then(
                response -> Promise.of(response),
                e -> {
                    log.error("[DC-9] analytics getResult failed queryId={}: {}", queryId, e.getMessage(), e);
                    return Promise.of(http.errorResponse(500, "Failed to retrieve result: " + e.getMessage()));
                }
            );
    }

    public Promise<HttpResponse> handleAnalyticsGetPlan(HttpRequest request) {
        if (analyticsEngine == null) {
            return Promise.of(http.errorResponse(503, "Analytics engine not available in this deployment"));
        }
        String queryId = request.getPathParameter("queryId");
        return analyticsEngine.getPlan(queryId)
            .map(plan -> {
                if (plan == null) {
                    return http.errorResponse(404, "No query plan found for queryId: " + queryId);
                }
                // Flatten plan fields into top-level response (consistent with query/result endpoints)
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("queryId", queryId);
                if (plan instanceof Map<?, ?> planMap) {
                    planMap.forEach((k, v) -> response.put(String.valueOf(k), v));
                } else {
                    response.put("plan", plan);
                }
                response.put("timestamp", Instant.now().toString());
                return http.jsonResponse(response);
            })
            .then(
                response -> Promise.of(response),
                e -> {
                    log.error("[DC-9] analytics getPlan failed queryId={}: {}", queryId, e.getMessage(), e);
                    return Promise.of(http.errorResponse(500, "Failed to retrieve plan: " + e.getMessage()));
                }
            );
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleAnalyticsAggregate(HttpRequest request) {
        if (analyticsEngine == null) {
            return Promise.of(http.errorResponse(503, "Analytics engine not available in this deployment"));
        }
        String tenantId = http.resolveTenantId(request);
        return request.loadBody()
            .then(
                buf -> {
                    try {
                        String bodyStr       = buf.getString(StandardCharsets.UTF_8);
                        Map<String, Object> payload = http.objectMapper().readValue(bodyStr, Map.class);
                        String queryText = (String) payload.get("query");
                        if (queryText == null || queryText.isBlank()) {
                            return Promise.of(http.errorResponse(400, "Missing required field: 'query'"));
                        }
                        String upperQuery = queryText.toUpperCase();
                        if (!upperQuery.contains("GROUP BY") && !upperQuery.contains("COUNT(")
                                && !upperQuery.contains("SUM(") && !upperQuery.contains("AVG(")) {
                            return Promise.of(http.errorResponse(400,
                                "Aggregate endpoint requires a query with GROUP BY, COUNT, SUM, or AVG"));
                        }
                        Map<String, Object> params = payload.containsKey("parameters")
                            ? (Map<String, Object>) payload.get("parameters")
                            : Map.of();
                        return analyticsEngine.submitQuery(tenantId, queryText, params)
                            .map(result -> http.jsonResponse(Map.of(
                                "queryId",         result.getQueryId(),
                                "queryType",       result.getQueryType(),
                                "rowCount",        result.getRowCount(),
                                "rows",            result.getRows(),
                                "executionTimeMs", result.getExecutionTimeMs(),
                                "optimized",       result.isOptimized(),
                                "timestamp",       Instant.now().toString()
                            )))
                            .then(
                                response -> Promise.of(response),
                                e -> {
                                    log.error("[DC-9] analytics aggregate failed: {}", e.getMessage(), e);
                                    return Promise.of(http.errorResponse(500, "Aggregate query failed: " + e.getMessage()));
                                }
                            );
                    } catch (Exception e) {
                        log.error("[DC-9] analytics aggregate request parse error: {}", e.getMessage(), e);
                        return Promise.of(http.errorResponse(400, "Invalid request: " + e.getMessage()));
                    }
                },
                e -> {
                    log.error("[DC-9] analytics aggregate body load error: {}", e.getMessage(), e);
                    return Promise.of(http.errorResponse(400, "Failed to read request body: " + e.getMessage()));
                }
            );
    }

    // ==================== Report Endpoints (DC-10) ====================

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleCreateReport(HttpRequest request) {
        if (reportService == null) {
            return Promise.of(http.errorResponse(503, "Report service not available in this deployment"));
        }
        return request.loadBody()
            .then(buf -> {
                try {
                    String bodyStr = buf.getString(StandardCharsets.UTF_8);
                    Map<String, Object> payload = http.objectMapper().readValue(bodyStr, Map.class);
                    ReportDefinition definition;
                    try {
                        definition = ReportDefinition.fromMap(payload);
                    } catch (IllegalArgumentException e) {
                        return Promise.of(http.errorResponse(400, "Invalid report definition: " + e.getMessage()));
                    }
                    String tenantId = http.resolveTenantId(request);
                    return reportService.generate(tenantId, definition)
                        .map(result -> {
                            Map<String, Object> response = new LinkedHashMap<>();
                            response.put("reportId",       result.getReportId());
                            response.put("reportName",     result.getReportName());
                            response.put("format",         result.getFormat().name());
                            response.put("rowCount",       result.getRowCount());
                            response.put("contentType",    result.getContentType());
                            response.put("executionTimeMs", result.getExecutionTime().toMillis());
                            response.put("generatedAt",    result.getGeneratedAt().toString());
                            if (result.getFormattedBody() != null) {
                                response.put("body", result.getFormattedBody());
                            } else {
                                response.put("rows", result.getRows());
                            }
                            return http.jsonResponse(response);
                        })
                        .then(Promise::of, e -> {
                            log.error("[DC-10] report generation failed name='{}': {}",
                                    definition.getName(), e.getMessage(), e);
                            return Promise.of(http.errorResponse(500, "Report generation failed: " + e.getMessage()));
                        });
                } catch (Exception e) {
                    log.error("[DC-10] report request parse error: {}", e.getMessage(), e);
                    return Promise.of(http.errorResponse(400, "Invalid request body: " + e.getMessage()));
                }
            })
            .then(Promise::of, e -> {
                log.error("[DC-10] report body load error: {}", e.getMessage(), e);
                return Promise.of(http.errorResponse(400, "Failed to read request body: " + e.getMessage()));
            });
    }

    public Promise<HttpResponse> handleListReports(HttpRequest request) {
        if (reportService == null) {
            return Promise.of(http.errorResponse(503, "Report service not available in this deployment"));
        }
        Map<String, String> cached = reportService.listCachedReports();
        return Promise.of(http.jsonResponse(Map.of("reports", cached, "count", cached.size())));
    }

    public Promise<HttpResponse> handleGetReport(HttpRequest request) {
        if (reportService == null) {
            return Promise.of(http.errorResponse(503, "Report service not available in this deployment"));
        }
        String reportId = request.getPathParameter("reportId");
        ReportResult result = reportService.getResult(reportId);
        if (result == null) {
            return Promise.of(http.errorResponse(404, "No cached report found for reportId: " + reportId));
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("reportId",        result.getReportId());
        response.put("reportName",      result.getReportName());
        response.put("format",          result.getFormat().name());
        response.put("rowCount",        result.getRowCount());
        response.put("contentType",     result.getContentType());
        response.put("executionTimeMs", result.getExecutionTime().toMillis());
        response.put("generatedAt",     result.getGeneratedAt().toString());
        if (result.getFormattedBody() != null) {
            response.put("body", result.getFormattedBody());
        } else {
            response.put("rows", result.getRows());
        }
        return Promise.of(http.jsonResponse(response));
    }
}
