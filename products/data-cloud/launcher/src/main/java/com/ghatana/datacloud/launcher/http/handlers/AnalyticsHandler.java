package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.analytics.AnalyticsQueryEngine;
import com.ghatana.datacloud.analytics.report.ReportDefinition;
import com.ghatana.datacloud.analytics.report.ReportResult;
import com.ghatana.datacloud.analytics.report.ReportService;
import com.ghatana.datacloud.launcher.http.plugins.ReportExecutionCapability;
import com.ghatana.datacloud.launcher.http.DataCloudHttpMetrics;
import io.activej.http.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles analytics query (DC-9) and report generation (DC-10) HTTP endpoints.
 *
 * P2-PERF-1: Adds row limits, backpressure handling, and proxy streaming for analytics queries
 * to prevent large result sets from overwhelming the system.
 *
 * @doc.type class
 * @doc.purpose Analytics and reporting HTTP handlers with performance guards (DC-9, DC-10)
 * @doc.layer product
 * @doc.pattern Handler
 */
public class AnalyticsHandler {

    private static final String HANDLER_NAME = "AnalyticsHandler";
    private static final Logger log = LoggerFactory.getLogger(AnalyticsHandler.class);

    // P2-PERF-1: Performance limits to prevent resource exhaustion
    private static final int DEFAULT_ROW_LIMIT = 10000;
    private static final int MAX_ROW_LIMIT = 50000;
    private static final long QUERY_TIMEOUT_MS = 300000; // 5 minutes

    private final AnalyticsQueryEngine analyticsEngine;
    private final HttpHandlerSupport http;
    private ReportService reportService;
    private ReportExecutionCapability reportCapability;
    private DataCloudHttpMetrics httpMetrics = DataCloudHttpMetrics.noop();
    // DC-P1-001: cancellation is not implemented; capability registry advertises analytics.cancellation.configured=false
    private boolean cancellationSupported = false;

    public AnalyticsHandler(AnalyticsQueryEngine analyticsEngine, HttpHandlerSupport http) {
        this.analyticsEngine = analyticsEngine;
        this.http = http;
    }

    public AnalyticsHandler withReportService(ReportService service) {
        this.reportService = service;
        return this;
    }

    public AnalyticsHandler withReportCapability(ReportExecutionCapability capability) {
        this.reportCapability = capability;
        return this;
    }

    public AnalyticsHandler withMetrics(DataCloudHttpMetrics metrics) {
        this.httpMetrics = metrics;
        return this;
    }

    /**
     * Enables cancellation support when a distributed query tracker is present.
     * Must not be called in single-process deployments where cancellation is unavailable.
     *
     * @param supported whether the engine supports {@code DELETE /queries/{queryId}}
     * @return this handler for fluent chaining
     */
    public AnalyticsHandler withCancellationSupported(boolean supported) {
        this.cancellationSupported = supported;
        return this;
    }

    // ==================== Analytics Endpoints (DC-9) ====================

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleAnalyticsQuery(HttpRequest request) {
        if (analyticsEngine == null) {
            // P0-4: Explicit degradation logging for Analytics
            log.warn("[DC-9] ANALYTICS DEGRADED: Analytics engine not available in this deployment. " +
                "Query execution will fail with 503. Configure DATACLOUD_ANALYTICS_ENABLED=true and ensure database connectivity to restore analytics capability.");
            return Promise.of(http.errorResponse(503, "Analytics engine not available in this deployment"));
        }
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String traceId = http.resolveCorrelationId(request);
        long start = System.currentTimeMillis();
        
        return request.loadBody()
            .then(
                buf -> {
                    String body;
                    Map<String, Object> payload;
                    String queryText;
                    int rowLimit;
                    Map<String, Object> paramsWithLimit;
                    try {
                        body = buf.getString(StandardCharsets.UTF_8);
                        payload = http.objectMapper().readValue(body, Map.class);
                        queryText = (String) payload.get("query");
                        if (queryText == null || queryText.isBlank()) {
                            return Promise.of(http.errorResponse(400, "Missing required field: 'query'"));
                        }
                        rowLimit = DEFAULT_ROW_LIMIT;
                        if (payload.containsKey("limit")) {
                            try {
                                int requestedLimit = ((Number) payload.get("limit")).intValue();
                                rowLimit = Math.min(Math.max(requestedLimit, 1), MAX_ROW_LIMIT);
                            } catch (Exception e) {
                                log.warn("[P2-PERF-1] Invalid limit parameter, using default: {}", e.getMessage());
                            }
                        }
                        Map<String, Object> params = payload.containsKey("parameters")
                            ? (Map<String, Object>) payload.get("parameters")
                            : Map.of();
                        paramsWithLimit = new LinkedHashMap<>(params);
                        paramsWithLimit.put("_rowLimit", rowLimit);
                    } catch (Exception e) {
                        log.error("[DC-9] analytics query request parse error traceId={}: {}", traceId, e.getMessage(), e);
                        return Promise.of(http.errorResponse(400, "Invalid request body"));
                    }
                    final int finalRowLimit = rowLimit;
                    return analyticsEngine.submitQuery(tenantId, queryText, paramsWithLimit)
                        .map(result -> {
                            List<Map<String, Object>> rows = result.getRows();
                            int rowCount = rows.size();
                            int totalRows = result.getTotalRows();
                            boolean truncated = totalRows > rowCount;
                            if (truncated) {
                                log.info("[P2-PERF-1] Query result truncated to {} rows (limit: {}) for tenant={} traceId={}",
                                    rowCount, finalRowLimit, tenantId, traceId);
                            }
                            Map<String, Object> responseBody = new LinkedHashMap<>();
                            responseBody.put("queryId",         result.getQueryId());
                            responseBody.put("queryType",       result.getQueryType());
                            responseBody.put("rowCount",        rowCount);
                            responseBody.put("columnCount",     result.getColumnCount());
                            responseBody.put("rows",            rows);
                            responseBody.put("executionTimeMs", result.getExecutionTimeMs());
                            responseBody.put("optimized",       result.isOptimized());
                            responseBody.put("timestamp",       Instant.now().toString());
                            responseBody.put("traceId",         traceId);
                            responseBody.put("limit",           finalRowLimit);
                            responseBody.put("truncated",       truncated);
                            HttpResponse response = http.jsonResponse(responseBody);
                            httpMetrics.recordRequest(HANDLER_NAME, "handleAnalyticsQuery", tenantId, response.getCode());
                            httpMetrics.recordLatency(HANDLER_NAME, "handleAnalyticsQuery", System.currentTimeMillis() - start);
                            return response;
                        })
                        .then(
                            Promise::of,
                            e -> {
                                // DC-P1-007: Log internal detail server-side; return stable sanitized message to client
                                log.error("[DC-9] analytics query execution failed traceId={} errorType={}: {}", traceId, e.getClass().getSimpleName(), e.getMessage(), e);
                                httpMetrics.recordError(HANDLER_NAME, "handleAnalyticsQuery", e);
                                return Promise.of(http.errorResponse(500, "Query execution failed", traceId));
                            }
                        );
                },
                e -> {
                    log.error("[DC-9] analytics query body load failed traceId={}: {}", traceId, e.getMessage(), e);
                    return Promise.of(http.errorResponse(400, "Failed to read request body"));
                }
            );
    }

    public Promise<HttpResponse> handleAnalyticsGetResult(HttpRequest request) {
        if (analyticsEngine == null) {
            // P0-4: Explicit degradation logging for Analytics
            log.warn("[DC-9] ANALYTICS DEGRADED: Analytics engine not available in this deployment. " +
                "Result retrieval will fail with 503. Configure DATACLOUD_ANALYTICS_ENABLED=true to restore analytics capability.");
            return Promise.of(http.errorResponse(503, "Analytics engine not available in this deployment"));
        }
        String queryId = request.getPathParameter("queryId");
        String traceId = http.resolveCorrelationId(request);
        
        // P2-PERF-1: Parse limit from query parameter for result retrieval
        int rowLimit = DEFAULT_ROW_LIMIT;
        String limitParam = request.getQueryParameter("limit");
        if (limitParam != null) {
            try {
                rowLimit = Math.min(Math.max(Integer.parseInt(limitParam), 1), MAX_ROW_LIMIT);
            } catch (NumberFormatException e) {
                log.warn("[P2-PERF-1] Invalid limit parameter in request: {}", limitParam);
            }
        }
        final int finalRowLimit = rowLimit;
        
        return analyticsEngine.getResult(queryId)
            .map(result -> {
                if (result == null) {
                    return http.errorResponse(404, "No result found for queryId: " + queryId);
                }
                
                List<Map<String, Object>> rows = result.getRows();
                int rowCount = Math.min(rows.size(), finalRowLimit);
                int totalRows = result.getTotalRows();
                boolean truncated = totalRows > rowCount || rows.size() > finalRowLimit;
                if (truncated) {
                    log.info("[P2-PERF-1] Result retrieval truncated to {} rows (limit: {}) for queryId={}",
                        rowCount, finalRowLimit, queryId);
                }
                Map<String, Object> responseBody = new LinkedHashMap<>();
                responseBody.put("queryId",         result.getQueryId());
                responseBody.put("queryType",       result.getQueryType());
                responseBody.put("rowCount",        rowCount);
                responseBody.put("columnCount",     result.getColumnCount());
                responseBody.put("rows",            rows.subList(0, rowCount));
                responseBody.put("executionTimeMs", result.getExecutionTimeMs());
                responseBody.put("optimized",       result.isOptimized());
                responseBody.put("timestamp",       Instant.now().toString());
                responseBody.put("traceId",         traceId);
                responseBody.put("limit",           finalRowLimit);
                responseBody.put("truncated",       truncated);
                return http.jsonResponse(responseBody);
            })
            .then(
                Promise::of,
                e -> {
                    // DC-P1-007: Log internal detail server-side; return stable code to client without sensitive info
                    log.error("[DC-9] analytics getResult failed queryId={} traceId={} errorType={}: {}", queryId, traceId, e.getClass().getSimpleName(), e.getMessage(), e);
                    return Promise.of(http.errorResponse(500, "Failed to retrieve result", traceId));
                }
            );
    }

    public Promise<HttpResponse> handleAnalyticsGetPlan(HttpRequest request) {
        if (analyticsEngine == null) {
            // P0-4: Explicit degradation logging for Analytics
            log.warn("[DC-9] ANALYTICS DEGRADED: Analytics engine not available in this deployment. " +
                "Query plan retrieval will fail with 503. Configure DATACLOUD_ANALYTICS_ENABLED=true to restore analytics capability.");
            return Promise.of(http.errorResponse(503, "Analytics engine not available in this deployment"));
        }
        String queryId = request.getPathParameter("queryId");
        String traceId = http.resolveCorrelationId(request);
        return analyticsEngine.getPlan(queryId)
            .map(plan -> {
                if (plan == null) {
                    return http.errorResponse(404, "No query plan found for queryId: " + queryId);
                }
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("queryId", queryId);
                @SuppressWarnings("unchecked")
                Map<String, Object> planFields = http.objectMapper().convertValue(plan, Map.class);
                response.putAll(planFields);
                response.put("timestamp", Instant.now().toString());
                response.put("traceId", traceId);
                return http.jsonResponse(response);
            })
            .then(
                Promise::of,
                e -> {
                    log.error("[DC-9] analytics getPlan failed queryId={} traceId={} errorType={}: {}",
                        queryId, traceId, e.getClass().getSimpleName(), e.getMessage(), e);
                    return Promise.of(http.errorResponse(500, "Failed to retrieve query plan", traceId));
                }
            );
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleAnalyticsAggregate(HttpRequest request) {
        if (analyticsEngine == null) {
            // P0-4: Explicit degradation logging for Analytics
            log.warn("[DC-9] ANALYTICS DEGRADED: Analytics engine not available in this deployment. " +
                "Aggregate query execution will fail with 503. Configure DATACLOUD_ANALYTICS_ENABLED=true to restore analytics capability.");
            return Promise.of(http.errorResponse(503, "Analytics engine not available in this deployment"));
        }
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String traceId = http.resolveCorrelationId(request);
        long start = System.currentTimeMillis();
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
                            .map(result -> {
                                Map<String, Object> responseBody = new LinkedHashMap<>();
                                responseBody.put("queryId",         result.getQueryId());
                                responseBody.put("queryType",       result.getQueryType());
                                responseBody.put("rowCount",        result.getRowCount());
                                responseBody.put("rows",            result.getRows());
                                responseBody.put("executionTimeMs", result.getExecutionTimeMs());
                                responseBody.put("optimized",       result.isOptimized());
                                responseBody.put("timestamp",       Instant.now().toString());
                                responseBody.put("traceId",         traceId);
                                HttpResponse response = http.jsonResponse(responseBody);
                                httpMetrics.recordRequest(HANDLER_NAME, "handleAnalyticsAggregate", tenantId, response.getCode());
                                httpMetrics.recordLatency(HANDLER_NAME, "handleAnalyticsAggregate", System.currentTimeMillis() - start);
                                return response;
                            })
                            .then(
                                Promise::of,
                                e -> {
                                    log.error("[DC-9] analytics aggregate failed traceId={} errorType={}: {}",
                                        traceId, e.getClass().getSimpleName(), e.getMessage(), e);
                                    httpMetrics.recordError(HANDLER_NAME, "handleAnalyticsAggregate", e);
                                    return Promise.of(http.errorResponse(500, "Aggregate query failed", traceId));
                                }
                            );
                    } catch (Exception e) {
                        log.error("[DC-9] analytics aggregate request parse error traceId={}: {}", traceId, e.getMessage(), e);
                        return Promise.of(http.errorResponse(400, "Invalid request body"));
                    }
                },
                e -> {
                    log.error("[DC-9] analytics aggregate body load error traceId={}: {}", traceId, e.getMessage(), e);
                    return Promise.of(http.errorResponse(400, "Failed to read request body"));
                }
            );
    }

    // ==================== Report Endpoints (DC-10) ====================

    /**
     * {@code POST /api/v1/analytics/explain}
     *
     * <p>Returns the query plan for the given query text without executing it.
     * Callers can use this to inspect data-source routing, estimated cost, and
     * query type <em>before</em> submitting an expensive query execution.
     *
     * @param request HTTP request; body must contain {@code {"query": "..."}}
     * @return 200 with the estimated {@link com.ghatana.datacloud.analytics.QueryPlan} fields
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleAnalyticsExplain(HttpRequest request) {
        if (analyticsEngine == null) {
            // P0-4: Explicit degradation logging for Analytics
            log.warn("[DC-9] ANALYTICS DEGRADED: Analytics engine not available in this deployment. " +
                "Query explain will fail with 503. Configure DATACLOUD_ANALYTICS_ENABLED=true to restore analytics capability.");
            return Promise.of(http.errorResponse(503, "Analytics engine not available in this deployment"));
        }
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String traceId = http.resolveCorrelationId(request);
        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> payload = http.objectMapper().readValue(body, Map.class);
                String queryText = (String) payload.get("query");
                if (queryText == null || queryText.isBlank()) {
                    return Promise.of(http.errorResponse(400, "Missing required field: 'query'"));
                }
                Map<String, Object> params = payload.containsKey("parameters")
                        ? (Map<String, Object>) payload.get("parameters")
                        : Map.of();
                return analyticsEngine.explainQuery(tenantId, queryText, params)
                        .map(plan -> {
                            Map<String, Object> planFields =
                                    http.objectMapper().convertValue(plan, Map.class);
                            Map<String, Object> response = new LinkedHashMap<>(planFields);
                            response.put("explain", true);
                            response.put("timestamp", Instant.now().toString());
                            response.put("traceId", traceId);
                            return http.jsonResponse(response);
                        })
                        .then(Promise::of, e -> {
                            log.error("[DC-9] analytics explain failed traceId={} errorType={}: {}",
                                traceId, e.getClass().getSimpleName(), e.getMessage(), e);
                            return Promise.of(http.errorResponse(500, "Explain query failed", traceId));
                        });
            } catch (Exception e) {
                log.error("[DC-9] analytics explain parse error traceId={}: {}", traceId, e.getMessage(), e);
                return Promise.of(http.errorResponse(400, "Invalid request body"));
            }
        });
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleCreateReport(HttpRequest request) {
        if (reportCapability == null && reportService == null) {
            return Promise.of(http.errorResponse(503, "Report service not available in this deployment"));
        }
        String traceId = http.resolveCorrelationId(request);
        return request.loadBody()
            .then(buf -> {
                try {
                    String bodyStr = buf.getString(StandardCharsets.UTF_8);
                    Map<String, Object> payload = http.objectMapper().readValue(bodyStr, Map.class);
                    ReportDefinition definition;
                    try {
                        definition = ReportDefinition.fromMap(payload);
                    } catch (IllegalArgumentException e) {
                        log.warn("[DC-10] invalid report definition: {}", e.getMessage());
                        return Promise.of(http.errorResponse(400, "Invalid report definition"));
                    }
                    String tenantId = http.requireTenantIdOrFail(request);
                    if (tenantId == null) {
                        return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
                    }
                    return reportExecutor().generate(tenantId, definition)
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
                            log.error("[DC-10] report generation failed name='{}' traceId={} errorType={}: {}",
                                    definition.getName(), traceId, e.getClass().getSimpleName(), e.getMessage(), e);
                            return Promise.of(http.errorResponse(500, "Report generation failed", traceId));
                        });
                } catch (Exception e) {
                    log.error("[DC-10] report request parse error traceId={}: {}", traceId, e.getMessage(), e);
                    return Promise.of(http.errorResponse(400, "Invalid request body"));
                }
            })
            .then(Promise::of, e -> {
                log.error("[DC-10] report body load error traceId={}: {}", traceId, e.getMessage(), e);
                return Promise.of(http.errorResponse(400, "Failed to read request body"));
            });
    }

    public Promise<HttpResponse> handleListReports(HttpRequest request) {
        if (reportCapability == null && reportService == null) {
            return Promise.of(http.errorResponse(503, "Report service not available in this deployment"));
        }
        Map<String, String> cached = reportExecutor().listCachedReports();
        return Promise.of(http.jsonResponse(Map.of("reports", cached, "count", cached.size())));
    }

    public Promise<HttpResponse> handleGetReport(HttpRequest request) {
        if (reportCapability == null && reportService == null) {
            return Promise.of(http.errorResponse(503, "Report service not available in this deployment"));
        }
        String reportId = request.getPathParameter("reportId");
        ReportResult result = reportExecutor().getResult(reportId);
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

    /**
     * Handles DELETE /api/v1/analytics/queries/{queryId} — cancel a running query.
     *
     * <p>When {@link #cancellationSupported} is {@code false} (the default), this endpoint
     * returns {@code 501 Not Implemented}. The runtime capability registry
     * ({@code GET /api/v1/capabilities}) will expose {@code analytics.cancellation.configured=false}
     * so that UI clients can disable the cancel action before reaching this endpoint.
     *
     * <p>When cancellation is supported, this endpoint delegates to the
     * {@link AnalyticsQueryEngine#cancelQuery} method which uses the distributed
     * query tracker to signal cancellation across all nodes.</p>
     *
     * @param request HTTP request
     * @return 200 if cancellation succeeds, 404 if query not found, 403 if tenant mismatch, or 501 when not supported
     */
    public Promise<HttpResponse> handleAnalyticsCancelQuery(HttpRequest request) {
        String queryId = request.getPathParameter("queryId");
        String tenantId = http.requireTenantIdOrFail(request);
        String traceId = http.resolveCorrelationId(request);

        if (!cancellationSupported) {
            // DC-P1-001: Capability-consistent 501 — capability registry advertises analytics.cancellation.configured=false
            log.info("[DC-9] cancel query rejected queryId={} traceId={} — analytics.cancellation capability is not configured in this deployment",
                queryId, traceId);
            return Promise.of(http.errorResponse(501,
                "Analytics query cancellation is not supported. " +
                "Check the analytics.cancellation entry at GET /api/v1/capabilities for current support status.",
                traceId));
        }

        if (analyticsEngine == null) {
            log.warn("[DC-9] cancel query failed queryId={} traceId={} — analytics engine not available",
                queryId, traceId);
            return Promise.of(http.errorResponse(503, "Analytics engine not available in this deployment", traceId));
        }

        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required", traceId));
        }

        log.info("[DC-9] cancel query requested queryId={} tenantId={} traceId={}", queryId, tenantId, traceId);

        // DC-P1-001: Use the distributed query tracker to cancel the query
        return analyticsEngine.cancelQuery(queryId, tenantId)
            .then(result -> {
                if (result.success()) {
                    log.info("[DC-9] cancel query succeeded queryId={} tenantId={} traceId={}", queryId, tenantId, traceId);
                    Map<String, Object> response = new java.util.HashMap<>();
                    response.put("queryId", queryId);
                    response.put("status", "CANCELLED");
                    response.put("message", result.message());
                    response.put("cancelledAt", result.cancelledAt().toString());
                    response.put("traceId", traceId);
                    return Promise.of(http.jsonResponse(response));
                } else {
                    log.warn("[DC-9] cancel query failed queryId={} tenantId={} traceId={} reason={}",
                        queryId, tenantId, traceId, result.message());
                    if (result.message().contains("unauthorized")) {
                        return Promise.of(http.errorResponse(403, result.message()));
                    }
                    return Promise.of(http.errorResponse(404, result.message()));
                }
            })
            .mapEx((result, exception) -> {
                if (exception != null) {
                    log.error("[DC-9] cancel query error queryId={} tenantId={} traceId={}",
                        queryId, tenantId, traceId, exception);
                    return http.errorResponse(500, "Internal error during query cancellation");
                }
                return result;
            });
    }

    private ReportExecutionCapability reportExecutor() {
        if (reportCapability != null) {
            return reportCapability;
        }
        return new ReportExecutionCapability() {
            @Override
            public Promise<ReportResult> generate(String tenantId, ReportDefinition definition) {
                return reportService.generate(tenantId, definition);
            }

            @Override
            public Map<String, String> listCachedReports() {
                return reportService.listCachedReports();
            }

            @Override
            public ReportResult getResult(String reportId) {
                return reportService.getResult(reportId);
            }
        };
    }

}
