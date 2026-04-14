package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.analytics.AnalyticsQueryEngine;
import com.ghatana.datacloud.analytics.QueryPlan;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP handler for storage cost estimation and per-collection cost reports (B11).
 *
 * <p>The {@code CostExplorer} UI component and {@code CostChart} already exist.
 * This handler closes the backend gap by exposing:
 * <ul>
 *   <li>{@code GET /api/v1/queries/estimate?sql=} — estimate cost of a SQL query
 *       using the existing {@link AnalyticsQueryEngine} {@link QueryPlan}</li>
 *   <li>{@code GET /api/v1/collections/:id/cost-report} — per-collection storage cost
 *       breakdown by tier (HOT/WARM/COLD)</li>
 * </ul>
 *
 * <p>Cost units are in "Data Cloud Credits" (DCC). Pricing model:
 * <ul>
 *   <li>HOT (Redis): 1.0 DCC per GB/day</li>
 *   <li>WARM (Iceberg/S3): 0.1 DCC per GB/day</li>
 *   <li>COLD (Glacier): 0.01 DCC per GB/day</li>
 *   <li>Query execution: 0.001 DCC per MB scanned</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Exposes query cost estimation and per-collection storage cost reports (B11)
 * @doc.layer product
 * @doc.pattern Handler
 */
public final class StorageCostHandler {

    private static final Logger log = LoggerFactory.getLogger(StorageCostHandler.class);

    // Pricing per GB/day per tier (DCC = Data Cloud Credits)
    private static final double HOT_DCC_PER_GB_DAY = 1.0;
    private static final double WARM_DCC_PER_GB_DAY = 0.1;
    private static final double COLD_DCC_PER_GB_DAY = 0.01;
    private static final double QUERY_DCC_PER_MB_SCANNED = 0.001;

    private final HttpHandlerSupport http;
    private final AnalyticsQueryEngine analyticsEngine;
    private final MetricsCollector metrics;

    /**
     * @param http            shared HTTP support
     * @param analyticsEngine used to build and inspect query plans (cost derived from
     *                        {@link QueryPlan#getEstimatedCost()})
     * @param metrics         observability metrics
     */
    public StorageCostHandler(
            HttpHandlerSupport http,
            AnalyticsQueryEngine analyticsEngine,
            MetricsCollector metrics) {
        this.http = Objects.requireNonNull(http, "http");
        this.analyticsEngine = Objects.requireNonNull(analyticsEngine, "analyticsEngine");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    // ─── GET /api/v1/queries/estimate ────────────────────────────────────────

    /**
     * Returns a cost estimate for a SQL query.
     *
     * <p>Query parameters:
     * <ul>
     *   <li>{@code sql} — SQL query string (required)</li>
     * </ul>
     */
    public Promise<HttpResponse> handleEstimateQuery(HttpRequest request) {
        String sql = request.getQueryParameter("sql");
        if (sql == null || sql.isBlank()) {
            return Promise.of(http.errorResponse(400, "Missing required query parameter: sql"));
        }
        String tenantId = http.resolveTenantId(request);
        metrics.incrementCounter("cost.estimate.query", "tenant", tenantId);

        // Submit a dry-run query — the engine builds a plan synchronously and caches it.
        // We capture the queryId from the plan after submitQuery resolves.
        return analyticsEngine.submitQuery(tenantId, sql, Map.of())
                .then(result -> analyticsEngine.getPlan(result.getQueryId()))
                .map(plan -> {
                    double estimatedMbScanned = plan.getEstimatedCost() * 1000;
                    double queryDcc = estimatedMbScanned * QUERY_DCC_PER_MB_SCANNED;

                    Map<String, Object> response = new HashMap<>();
                    response.put("queryId", plan.getQueryId());
                    response.put("sql", sql);
                    response.put("estimatedMbScanned", estimatedMbScanned);
                    response.put("estimatedCostDcc", queryDcc);
                    response.put("currency", "DCC");
                    response.put("breakdown", Map.of(
                            "queryExecution", queryDcc,
                            "dataSources", plan.getDataSources()
                    ));
                    response.put("optimized", plan.isOptimized());
                    return http.jsonResponse(200, response);
                });
    }

    // ─── GET /api/v1/collections/:id/cost-report ─────────────────────────────

    /**
     * Returns a storage cost breakdown for a collection by tier.
     */
    public Promise<HttpResponse> handleCollectionCostReport(HttpRequest request) {
        String collectionId = request.getPathParameter("id");
        String tenantId = http.resolveTenantId(request);
        metrics.incrementCounter("cost.report.collection", "tenant", tenantId, "collection", collectionId);

        return analyticsEngine
                .submitQuery(tenantId, "SELECT COUNT(*) FROM \"" + collectionId + "\"", Map.of())
                .then(result -> analyticsEngine.getPlan(result.getQueryId()))
                .map(plan -> buildCostReport(collectionId, tenantId, plan));
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private HttpResponse buildCostReport(String collectionId, String tenantId, QueryPlan plan) {
        double totalGb = Math.max(plan.getEstimatedCost(), 0.01);
        double hotGb = totalGb * 0.1;
        double warmGb = totalGb * 0.6;
        double coldGb = totalGb * 0.3;

        double hotDcc = hotGb * HOT_DCC_PER_GB_DAY;
        double warmDcc = warmGb * WARM_DCC_PER_GB_DAY;
        double coldDcc = coldGb * COLD_DCC_PER_GB_DAY;
        double totalDcc = hotDcc + warmDcc + coldDcc;

        List<Map<String, Object>> tiers = new ArrayList<>();
        tiers.add(tierEntry("HOT", hotGb, hotDcc, "Redis in-memory"));
        tiers.add(tierEntry("WARM", warmGb, warmDcc, "Apache Iceberg / S3"));
        tiers.add(tierEntry("COLD", coldGb, coldDcc, "S3 Glacier"));

        Map<String, Object> response = new HashMap<>();
        response.put("collectionId", collectionId);
        response.put("tenantId", tenantId);
        response.put("totalSizeGb", totalGb);
        response.put("totalCostDccPerDay", totalDcc);
        response.put("currency", "DCC");
        response.put("tiers", tiers);
        response.put("note", "Tier sizes are estimates based on query plan heuristics.");

        return http.jsonResponse(200, response);
    }

    private static Map<String, Object> tierEntry(String name, double sizeGb, double costDcc, String backend) {
        Map<String, Object> tier = new HashMap<>();
        tier.put("tier", name);
        tier.put("sizeGb", sizeGb);
        tier.put("costDccPerDay", costDcc);
        tier.put("backend", backend);
        return tier;
    }
}
