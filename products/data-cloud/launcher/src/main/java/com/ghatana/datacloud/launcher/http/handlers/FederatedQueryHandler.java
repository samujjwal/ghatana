package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.analytics.AnalyticsQueryEngine;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP handler for federated Trino queries (B13).
 *
 * <p>Enables cross-tier SQL via Trino's {@code EventCloudConnector}, which spans
 * HOT (Redis), WARM (Iceberg), and COLD (Glacier) storage in a single query.
 *
 * <p>When a Trino coordinator JDBC URL is configured ({@code TRINO_URL} env var),
 * queries are forwarded via the JDBC thin client. If Trino is not configured, the
 * handler transparently falls back to the local {@link AnalyticsQueryEngine} —
 * degraded but never a hard failure.
 *
 * <p>Routes wired in {@code DataCloudHttpServer}:
 * <ul>
 *   <li>{@code POST /api/v1/queries/federated} — run a federated SQL query</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Routes SQL queries through the Trino EventCloud connector (B13)
 * @doc.layer product
 * @doc.pattern Handler
 */
public final class FederatedQueryHandler {

    private static final Logger log = LoggerFactory.getLogger(FederatedQueryHandler.class);

    private final HttpHandlerSupport http;
    private final AnalyticsQueryEngine analyticsEngine;
    private final MetricsCollector metrics;
    /** Trino coordinator JDBC URL — null when Trino is not configured. */
    private final String trinoUrl;
    /** JDBC query timeout in seconds. Default 30. */
    private final int queryTimeoutSeconds;

    /** Default query timeout when not explicitly configured. */
    static final int DEFAULT_QUERY_TIMEOUT_SECONDS = 30;

    /**
     * @param http            shared HTTP support
     * @param analyticsEngine fallback when Trino is not available
     * @param metrics         observability metrics
     * @param trinoUrl        Trino coordinator JDBC URL, e.g. {@code jdbc:trino://host:8080/eventcloud},
     *                        or {@code null} to always use the fallback engine
     */
    public FederatedQueryHandler(
            HttpHandlerSupport http,
            AnalyticsQueryEngine analyticsEngine,
            MetricsCollector metrics,
            String trinoUrl) {
        this(http, analyticsEngine, metrics, trinoUrl, DEFAULT_QUERY_TIMEOUT_SECONDS);
    }

    /**
     * @param http                 shared HTTP support
     * @param analyticsEngine      fallback when Trino is not available
     * @param metrics              observability metrics
     * @param trinoUrl             Trino coordinator JDBC URL, or {@code null}
     * @param queryTimeoutSeconds  per-query timeout; must be positive
     */
    public FederatedQueryHandler(
            HttpHandlerSupport http,
            AnalyticsQueryEngine analyticsEngine,
            MetricsCollector metrics,
            String trinoUrl,
            int queryTimeoutSeconds) {
        this.http = Objects.requireNonNull(http, "http");
        this.analyticsEngine = Objects.requireNonNull(analyticsEngine, "analyticsEngine");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.trinoUrl = trinoUrl;
        if (queryTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("queryTimeoutSeconds must be positive, got: " + queryTimeoutSeconds);
        }
        this.queryTimeoutSeconds = queryTimeoutSeconds;
    }

    // ─── POST /api/v1/queries/federated ───────────────────────────────────────

    /**
     * Executes a federated SQL query.
     *
     * <p>Request body (JSON):
     * <pre>{@code { "sql": "SELECT ...", "parameters": {} } }</pre>
     *
     * <p>Response (JSON): same shape as the direct analytics query result.
     */
    public Promise<HttpResponse> handleFederatedQuery(HttpRequest request) {
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        metrics.incrementCounter("query.federated", "tenant", tenantId);

        return request.loadBody().then(body -> {
            Map<String, Object> payload;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = (Map<String, Object>) http.objectMapper().readValue(
                        body.getString(StandardCharsets.UTF_8), Map.class);
                payload = parsed;
            } catch (Exception e) {
                return Promise.of(http.errorResponse(400, "Invalid JSON body: " + e.getMessage()));
            }

            Object sqlRaw = payload.get("sql");
            if (sqlRaw == null || String.valueOf(sqlRaw).isBlank()) {
                return Promise.of(http.errorResponse(400, "Missing required field: sql"));
            }
            String sql = String.valueOf(sqlRaw).trim();

            if (trinoUrl != null) {
                return executeViaTrino(sql, tenantId);
            } else {
                log.debug("Trino not configured — executing federated query via local analytics engine for tenant {}",
                        tenantId);
                return executeViaLocalEngine(sql, tenantId);
            }
        });
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Executes the query through the Trino JDBC thin client.
     *
     * <p>Uses blocking I/O wrapped in {@link Promise#ofBlocking} to keep the
     * ActiveJ event loop free.
     *
     * <p>Tenant isolation is achieved by setting the catalog to
     * {@code eventcloud_<tenantId>}. A lightweight {@code EXPLAIN} is run first
     * to surface the estimated cost before executing.
     */
    private Promise<HttpResponse> executeViaTrino(String sql, String tenantId) {
        return Promise.ofBlocking(http.blockingExecutor(), () -> {
            long startMs = System.currentTimeMillis();
            // Tenant-scoped catalog: eventcloud_<tenantId>
            String tenantCatalog = "eventcloud_" + tenantId;
            String tenantUrl = buildTenantUrl(trinoUrl, tenantCatalog);
            try (java.sql.Connection conn = java.sql.DriverManager.getConnection(tenantUrl);
                 java.sql.Statement explainStmt = conn.createStatement();
                 java.sql.Statement execStmt = conn.createStatement()) {

                // 1. Cost estimation via EXPLAIN
                String estimatedCost = "unknown";
                try (java.sql.ResultSet explainRs = explainStmt.executeQuery("EXPLAIN " + sql)) {
                    if (explainRs.next()) {
                        estimatedCost = explainRs.getString(1);
                    }
                } catch (Exception explainEx) {
                    log.debug("EXPLAIN failed for tenant {} (non-fatal): {}", tenantId, explainEx.getMessage());
                }

                // 2. Enforce query timeout before main execution
                execStmt.setQueryTimeout(queryTimeoutSeconds);

                // 3. Execute query
                try (java.sql.ResultSet rs = execStmt.executeQuery(sql)) {

                    java.sql.ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();
                    List<String> columns = new ArrayList<>(colCount);
                    for (int c = 1; c <= colCount; c++) {
                        columns.add(meta.getColumnName(c));
                    }

                    List<Map<String, Object>> rows = new ArrayList<>();
                    while (rs.next()) {
                        Map<String, Object> row = new HashMap<>(colCount);
                        for (int c = 1; c <= colCount; c++) {
                            row.put(columns.get(c - 1), rs.getObject(c));
                        }
                        rows.add(row);
                    }

                    long durationMs = System.currentTimeMillis() - startMs;
                    metrics.incrementCounter("query.federated.trino.success", "tenant", tenantId);

                    Map<String, Object> result = new HashMap<>();
                    result.put("queryId", java.util.UUID.randomUUID().toString());
                    result.put("queryType", "FEDERATED_TRINO");
                    result.put("tenantCatalog", tenantCatalog);
                    result.put("rowCount", rows.size());
                    result.put("columnCount", colCount);
                    result.put("rows", rows);
                    result.put("estimatedCost", estimatedCost);
                    result.put("executionTimeMs", durationMs);
                    result.put("queryTimeoutSeconds", queryTimeoutSeconds);
                    result.put("optimized", true);
                    result.put("timestamp", java.time.Instant.now().toString());
                    return http.jsonResponse(200, result);
                }

            } catch (SQLException e) {
                log.error("Trino federated query failed for tenant {}: {}", tenantId, e.getMessage(), e);
                metrics.incrementCounter("query.federated.trino.error", "tenant", tenantId);
                throw e;
            }
        }).mapException(e -> {
            // Propagate as a wrapped exception; the error middleware will convert to 500
            return new RuntimeException("Federated query failed via Trino: " + e.getMessage(), e);
        });
    }

    /**
     * Builds a Trino JDBC URL scoped to the tenant catalog.
     * Replaces the catalog component in the URL if present, otherwise appends it.
     *
     * <p>e.g. {@code jdbc:trino://host:8080/eventcloud} →
     *         {@code jdbc:trino://host:8080/eventcloud_tenantA}
     */
    static String buildTenantUrl(String baseUrl, String tenantCatalog) {
        if (baseUrl == null) return null;
        // Find the first slash after "://" to locate the path component
        int schemeEnd = baseUrl.indexOf("://");
        int pathSlash = schemeEnd >= 0
                ? baseUrl.indexOf('/', schemeEnd + 3)
                : baseUrl.indexOf('/');
        if (pathSlash < 0) {
            // No path segment: just append
            return baseUrl + "/" + tenantCatalog;
        }
        // Replace path segment (keep up to and including the leading slash)
        return baseUrl.substring(0, pathSlash + 1) + tenantCatalog;
    }

    /**
     * Fallback: delegates to the local {@link AnalyticsQueryEngine}.
     */
    private Promise<HttpResponse> executeViaLocalEngine(String sql, String tenantId) {
        return analyticsEngine.submitQuery(tenantId, sql, Map.of())
                .map(result -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("queryId", result.getQueryId());
                    response.put("queryType", "FEDERATED_FALLBACK");
                    response.put("rowCount", result.getRowCount());
                    response.put("columnCount", result.getColumnCount());
                    response.put("rows", result.getRows());
                    response.put("executionTimeMs", result.getExecutionTimeMs());
                    response.put("optimized", result.isOptimized());
                    response.put("timestamp", java.time.Instant.now().toString());
                    response.put("warning", "Trino not configured — query executed via local analytics engine. "
                            + "Set TRINO_URL to enable cross-tier federated queries.");
                    return http.jsonResponse(200, response);
                });
    }
}
