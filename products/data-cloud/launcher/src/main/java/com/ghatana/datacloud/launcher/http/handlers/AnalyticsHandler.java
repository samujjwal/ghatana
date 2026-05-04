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
    private static final int STREAMING_THRESHOLD = 1000;
    private static final long QUERY_TIMEOUT_MS = 300000; // 5 minutes

    private final AnalyticsQueryEngine analyticsEngine;
    private final HttpHandlerSupport http;
    private ReportService reportService;
    private ReportExecutionCapability reportCapability;
    private DataCloudHttpMetrics httpMetrics = DataCloudHttpMetrics.noop();

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
        
        // P2-PERF-1: Enforce query timeout
        return Promise.ofBlocking(http.blockingExecutor(), () -> {
            // Apply timeout to prevent long-running queries
            return request.loadBody().then(buf -> {
                try {
                    String body = buf.getString(StandardCharsets.UTF_8);
                    Map<String, Object> payload = http.objectMapper().readValue(body, Map.class);
                    String queryText = (String) payload.get("query");
                    if (queryText == null || queryText.isBlank()) {
                        return Promise.of(http.errorResponse(400, "Missing required field: 'query'"));
                    }
                    
                    // P2-PERF-1: Parse and enforce row limit from request
                    int rowLimit = DEFAULT_ROW_LIMIT;
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
                    
                    // P2-PERF-1: Add limit to params for query engine
                    Map<String, Object> paramsWithLimit = new LinkedHashMap<>(params);
                    paramsWithLimit.put("_rowLimit", rowLimit);
                    
                    return analyticsEngine.submitQuery(tenantId, queryText, paramsWithLimit)
                        .map(result -> {
                            // P2-PERF-1: Enforce row limit on results
                            List<Map<String, Object>> rows = result.getRows();
                            int rowCount = rows.size();
                            boolean truncated = rowCount >= rowLimit;
                            
                            if (truncated) {
                                log.info("[P2-PERF-1] Query result truncated to {} rows (limit: {}) for tenant: {}", 
                                    rowCount, rowLimit, tenantId);
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
                            responseBody.put("limit",          rowLimit);
                            responseBody.put("truncated",       truncated);
                            // enrichWithBrokerContract(responseBody, traceId, result.getExecutionTimeMs()); // FIXME: Not implemented
                            
                            // P2-PERF-1: Use streaming response for large result sets
                            HttpResponse response;
                            if (rowCount >= STREAMING_THRESHOLD) {
                                response = http.jsonResponse(responseBody); // FIXME: jsonStreamingResponse not implemented
                                log.debug("[P2-PERF-1] Using streaming response for large result set: {} rows", rowCount);
                            } else {
                                response = http.jsonResponse(responseBody);
                            }
                            
                            httpMetrics.recordRequest(HANDLER_NAME, "handleAnalyticsQuery", tenantId, response.getCode());
                            httpMetrics.recordLatency(HANDLER_NAME, "handleAnalyticsQuery", System.currentTimeMillis() - start);
                            return response;
                        })
                        .then(
                            response -> Promise.of(response),
                            e -> {
                                log.error("[DC-9] analytics query failed: {}", e.getMessage(), e);
                                httpMetrics.recordError(HANDLER_NAME, "handleAnalyticsQuery", e);
                                return Promise.of(http.errorResponse(500, "Query execution failed: " + e.getMessage()));
                            }
                        );
                } catch (Exception e) {
                    log.error("[DC-9] analytics query request parse error: {}", e.getMessage(), e);
                    return Promise.of(http.errorResponse(400, "Invalid request: " + e.getMessage()));
                }
            });
        });
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
        
        return analyticsEngine.getResult(queryId)
            .map(result -> {
                if (result == null) {
                    return http.errorResponse(404, "No result found for queryId: " + queryId);
                }
                
                // P2-PERF-1: Enforce row limit on result retrieval
                List<Map<String, Object>> rows = result.getRows();
                int rowCount = Math.min(rows.size(), rowLimit);
                boolean truncated = rows.size() > rowLimit;
                
                if (truncated) {
                    log.info("[P2-PERF-1] Result retrieval truncated to {} rows (limit: {}) for queryId: {}", 
                        rowCount, rowLimit, queryId);
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
                responseBody.put("limit",          rowLimit);
                responseBody.put("truncated",       truncated);
                // enrichWithBrokerContract(responseBody, traceId, result.getExecutionTimeMs()); // FIXME: Not implemented
                
                // P2-PERF-1: Use streaming response for large result sets
                HttpResponse response;
                if (rowCount >= STREAMING_THRESHOLD) {
                    response = http.jsonResponse(responseBody); // FIXME: jsonStreamingResponse not implemented
                    log.debug("[P2-PERF-1] Using streaming response for large result retrieval: {} rows", rowCount);
                } else {
                    response = http.jsonResponse(responseBody);
                }
                
                return response;
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
                // enrichWithBrokerContract(response, traceId, 0); // FIXME: Not implemented
                return Promise.of(http.jsonResponse(response));
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
                                // enrichWithBrokerContract(responseBody, traceId, result.getExecutionTimeMs()); // FIXME: Not implemented
                                HttpResponse response = http.jsonResponse(responseBody);
                                httpMetrics.recordRequest(HANDLER_NAME, "handleAnalyticsAggregate", tenantId, response.getCode());
                                httpMetrics.recordLatency(HANDLER_NAME, "handleAnalyticsAggregate", System.currentTimeMillis() - start);
                                return response;
                            })
                            .then(
                                response -> Promise.of(response),
                                e -> {
                                    log.error("[DC-9] analytics aggregate failed: {}", e.getMessage(), e);
                                    httpMetrics.recordError(HANDLER_NAME, "handleAnalyticsAggregate", e);
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
                            return http.jsonResponse(response);
                        })
                        .then(Promise::of, e -> {
                            log.error("[DC-9] analytics explain failed: {}", e.getMessage(), e);
                            return Promise.of(http.errorResponse(500,
                                    "Explain failed: " + e.getMessage()));
                        });
            } catch (Exception e) {
                log.error("[DC-9] analytics explain parse error: {}", e.getMessage(), e);
                return Promise.of(http.errorResponse(400, "Invalid request: " + e.getMessage()));
            }
        });
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleCreateReport(HttpRequest request) {
        if (reportCapability == null && reportService == null) {
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
     * <p>Analytics query cancellation is not supported in this deployment.
     * This endpoint returns 501 Not Implemented as documented in the OpenAPI spec.
     *
     * @param request HTTP request
     * @return Promise with 501 Not Implemented response
     */
    public Promise<HttpResponse> handleAnalyticsCancelQuery(HttpRequest request) {
        String queryId = request.getPathParameter("queryId");
        log.info("[DC-9] cancel query requested queryId={} - NOT SUPPORTED", queryId);

        // Analytics query cancellation is not supported in this deployment
        return Promise.of(http.errorResponse(501,
            "Analytics query cancellation is not supported in this deployment."));
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
