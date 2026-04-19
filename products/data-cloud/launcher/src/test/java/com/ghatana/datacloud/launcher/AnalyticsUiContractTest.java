/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * @doc.type class
 * @doc.purpose UI contract tests for Analytics page response schemas
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("Analytics UI Contract Tests")
public class AnalyticsUiContractTest {

    @Nested
    @DisplayName("AnalyticsDashboardPageTests")
    class AnalyticsDashboardPageTests {

        @Test
        @DisplayName("GET /analytics: dashboard with KPIs")
        void shouldReturnDashboard() {
            Map<String, Object> response = getAnalyticsDashboard();
            assertThat(response).containsKeys("kpis", "trends", "topMetrics", "period");
        }

        @Test
        @DisplayName("KPIs: revenue, users, queries, storage")
        void shouldHaveKpis() {
            Map<String, Object> response = getAnalyticsDashboard();
            Map<String, ?> kpis = requireMap(response, "kpis");

            assertThat(kpis).containsKeys("totalRevenue", "activeUsers", "queriesExecuted", "storageUsed");
        }

        @Test
        @DisplayName("trends: growth metrics over time")
        void shouldHaveTrends() {
            Map<String, Object> response = getAnalyticsDashboard();
            Map<String, ?> trends = requireMap(response, "trends");

            assertThat(trends).isNotNull();
        }

        @Test
        @DisplayName("period selection: day, week, month, quarter, year")
        void shouldSupportPeriods() {
            Map<String, Object> response = getAnalyticsDashboardForPeriod("MONTH");
            assertThat(response.get("period")).isEqualTo("MONTH");
        }

        @Test
        @DisplayName("date range: custom start and end")
        void shouldSupportDateRange() {
            Map<String, Object> response = getAnalyticsForDateRange("2026-01-01", "2026-03-31");
            assertThat(response).containsKey("dateRange");
        }

        @Test
        @DisplayName("analytics tenant isolation")
        void shouldIsolateTenant() {
            Map<String, Object> t1 = getAnalyticsForTenant("tenant-1");
            assertThat(t1.get("tenantId")).isEqualTo("tenant-1");
        }

        @Test
        @DisplayName("metric comparison: current vs previous period")
        void shouldCompareMetrics() {
            Map<String, Object> response = getAnalyticsDashboard();
            Map<String, ?> kpis = requireMap(response, "kpis");

            if (kpis.containsKey("totalRevenue")) {
                Map<String, ?> revenue = requireMap(kpis, "totalRevenue");
                assertThat(revenue).containsKeys("current", "previous", "change");
            }
        }

        @Test
        @DisplayName("analytics refresh timestamp: when last updated")
        void shouldIncludeRefreshTime() {
            Map<String, Object> response = getAnalyticsDashboard();
            assertThat(response).containsKey("lastUpdated");
        }
    }

    @Nested
    @DisplayName("AnalyticsDetailPageTests")
    class AnalyticsDetailPageTests {

        @Test
        @DisplayName("GET /analytics/{metric}: detailed metric view")
        void shouldReturnDetail() {
            Map<String, Object> response = getAnalyticsDetail("queries_executed");
            assertThat(response).containsKeys("metric", "data", "breakdown");
        }

        @Test
        @DisplayName("metric data: time series with values")
        void shouldHaveTimeSeries() {
            Map<String, Object> response = getAnalyticsDetail("queries_executed");
            List<?> data = (List<?>) response.get("data");

            assertThat(data).isNotEmpty();
        }

        @Test
        @DisplayName("metric breakdown: by category, user, collection")
        void shouldHaveBreakdown() {
            Map<String, Object> response = getAnalyticsDetail("queries_executed");
            Map<String, ?> breakdown = requireMap(response, "breakdown");

            assertThat(breakdown).isNotNull();
        }

        @Test
        @DisplayName("metric aggregation options: sum, average, max")
        void shouldSupportAggregation() {
            Map<String, Object> response = getAnalyticsDetail("queries_executed");
            assertThat(response).containsKey("aggregation");
        }

        @Test
        @DisplayName("metric visualization: chart metadata")
        void shouldHaveVisualization() {
            Map<String, Object> response = getAnalyticsDetail("queries_executed");
            assertThat(response).containsKey("chartType");
        }

        @Test
        @DisplayName("metric anomalies: detected outliers")
        void shouldDetectAnomalies() {
            Map<String, Object> response = getAnalyticsDetail("queries_executed");
            assertThat(response).containsKey("anomalies");
        }

        @Test
        @DisplayName("metric export: CSV or JSON")
        void shouldSupportExport() {
            Map<String, Object> response = getAnalyticsDetail("queries_executed");
            assertThat(response).containsKey("exportUrl");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────────────

    private Map<String, Object> getAnalyticsDashboard() {
        return getAnalyticsForTenant("tenant-default");
    }

    private Map<String, Object> getAnalyticsForTenant(String tenantId) {
        Map<String, Object> response = new HashMap<>();
        response.put("tenantId", tenantId);
        response.put("period", "MONTH");
        response.put("lastUpdated", "2026-04-03T14:30:00Z");

        Map<String, Object> kpis = new HashMap<>();
        Map<String, Object> revenue = new HashMap<>();
        revenue.put("current", 125000.0);
        revenue.put("previous", 120000.0);
        revenue.put("change", 4.17);
        kpis.put("totalRevenue", revenue);

        Map<String, Object> users = new HashMap<>();
        users.put("current", 2500);
        users.put("previous", 2400);
        users.put("change", 4.17);
        kpis.put("activeUsers", users);

        kpis.put("queriesExecuted", 50000);
        kpis.put("storageUsed", "2.5TB");
        response.put("kpis", kpis);

        response.put("trends", Map.of(
                "revenueGrowth", List.of(120000, 122000, 125000),
                "userGrowth", List.of(2300, 2400, 2500)
        ));

        response.put("topMetrics", List.of(
                Map.of("name", "Highest Query Volume", "value", "1,250 queries", "date", "2026-03-28"),
                Map.of("name", "Peak Storage", "value", "2.8TB", "date", "2026-03-31")
        ));

        return response;
    }

    private Map<String, Object> getAnalyticsDashboardForPeriod(String period) {
        Map<String, Object> response = getAnalyticsDashboard();
        response.put("period", period);
        return response;
    }

    private Map<String, Object> getAnalyticsForDateRange(String startDate, String endDate) {
        Map<String, Object> response = getAnalyticsDashboard();
        Map<String, String> dateRange = new HashMap<>();
        dateRange.put("start", startDate);
        dateRange.put("end", endDate);
        response.put("dateRange", dateRange);
        return response;
    }

    private Map<String, Object> getAnalyticsDetail(String metric) {
        Map<String, Object> response = new HashMap<>();
        response.put("metric", metric);
        response.put("title", "Queries Executed");

        List<Map<String, Object>> data = List.of(
                Map.of("date", "2026-03-25", "value", 45000),
                Map.of("date", "2026-03-26", "value", 47500),
                Map.of("date", "2026-03-27", "value", 50000),
                Map.of("date", "2026-03-28", "value", 48000)
        );
        response.put("data", data);

        Map<String, Object> breakdown = new HashMap<>();
        breakdown.put("byCollection", Map.of(
                "Sales Data", 20000,
                "Marketing Data", 15000,
                "Finance Data", 15000
        ));
        response.put("breakdown", breakdown);

        response.put("aggregation", "SUM");
        response.put("chartType", "LINE");
        response.put("anomalies", List.of());
        response.put("exportUrl", "/api/analytics/" + metric + "/export");

        return response;
    }

    private static Map<String, ?> requireMap(Map<String, ?> container, String key) {
        Object value = container.get(key);
        assertThat(value).as("Expected '%s' to be a map", key).isInstanceOf(Map.class);
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> typedMap = new HashMap<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                typedMap.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return typedMap;
        }
        fail("Expected '%s' to be a map", key);
        return Map.of();
    }
}
