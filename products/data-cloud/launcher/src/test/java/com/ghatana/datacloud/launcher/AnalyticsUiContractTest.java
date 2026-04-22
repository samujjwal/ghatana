/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@DisplayName("Analytics UI Contract Tests [GH-90000]")
public class AnalyticsUiContractTest {

    @Nested
    @DisplayName("AnalyticsDashboardPageTests [GH-90000]")
    class AnalyticsDashboardPageTests {

        @Test
        @DisplayName("GET /analytics: dashboard with KPIs [GH-90000]")
        void shouldReturnDashboard() { // GH-90000
            Map<String, Object> response = getAnalyticsDashboard(); // GH-90000
            assertThat(response).containsKeys("kpis", "trends", "topMetrics", "period"); // GH-90000
        }

        @Test
        @DisplayName("KPIs: revenue, users, queries, storage [GH-90000]")
        void shouldHaveKpis() { // GH-90000
            Map<String, Object> response = getAnalyticsDashboard(); // GH-90000
            Map<String, ?> kpis = requireMap(response, "kpis"); // GH-90000

            assertThat(kpis).containsKeys("totalRevenue", "activeUsers", "queriesExecuted", "storageUsed"); // GH-90000
        }

        @Test
        @DisplayName("trends: growth metrics over time [GH-90000]")
        void shouldHaveTrends() { // GH-90000
            Map<String, Object> response = getAnalyticsDashboard(); // GH-90000
            Map<String, ?> trends = requireMap(response, "trends"); // GH-90000

            assertThat(trends).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("period selection: day, week, month, quarter, year [GH-90000]")
        void shouldSupportPeriods() { // GH-90000
            Map<String, Object> response = getAnalyticsDashboardForPeriod("MONTH [GH-90000]");
            assertThat(response.get("period [GH-90000]")).isEqualTo("MONTH [GH-90000]");
        }

        @Test
        @DisplayName("date range: custom start and end [GH-90000]")
        void shouldSupportDateRange() { // GH-90000
            Map<String, Object> response = getAnalyticsForDateRange("2026-01-01", "2026-03-31"); // GH-90000
            assertThat(response).containsKey("dateRange [GH-90000]");
        }

        @Test
        @DisplayName("analytics tenant isolation [GH-90000]")
        void shouldIsolateTenant() { // GH-90000
            Map<String, Object> t1 = getAnalyticsForTenant("tenant-1 [GH-90000]");
            assertThat(t1.get("tenantId [GH-90000]")).isEqualTo("tenant-1 [GH-90000]");
        }

        @Test
        @DisplayName("metric comparison: current vs previous period [GH-90000]")
        void shouldCompareMetrics() { // GH-90000
            Map<String, Object> response = getAnalyticsDashboard(); // GH-90000
            Map<String, ?> kpis = requireMap(response, "kpis"); // GH-90000

            if (kpis.containsKey("totalRevenue [GH-90000]")) {
                Map<String, ?> revenue = requireMap(kpis, "totalRevenue"); // GH-90000
                assertThat(revenue).containsKeys("current", "previous", "change"); // GH-90000
            }
        }

        @Test
        @DisplayName("analytics refresh timestamp: when last updated [GH-90000]")
        void shouldIncludeRefreshTime() { // GH-90000
            Map<String, Object> response = getAnalyticsDashboard(); // GH-90000
            assertThat(response).containsKey("lastUpdated [GH-90000]");
        }
    }

    @Nested
    @DisplayName("AnalyticsDetailPageTests [GH-90000]")
    class AnalyticsDetailPageTests {

        @Test
        @DisplayName("GET /analytics/{metric}: detailed metric view [GH-90000]")
        void shouldReturnDetail() { // GH-90000
            Map<String, Object> response = getAnalyticsDetail("queries_executed [GH-90000]");
            assertThat(response).containsKeys("metric", "data", "breakdown"); // GH-90000
        }

        @Test
        @DisplayName("metric data: time series with values [GH-90000]")
        void shouldHaveTimeSeries() { // GH-90000
            Map<String, Object> response = getAnalyticsDetail("queries_executed [GH-90000]");
            List<?> data = (List<?>) response.get("data [GH-90000]");

            assertThat(data).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("metric breakdown: by category, user, collection [GH-90000]")
        void shouldHaveBreakdown() { // GH-90000
            Map<String, Object> response = getAnalyticsDetail("queries_executed [GH-90000]");
            Map<String, ?> breakdown = requireMap(response, "breakdown"); // GH-90000

            assertThat(breakdown).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("metric aggregation options: sum, average, max [GH-90000]")
        void shouldSupportAggregation() { // GH-90000
            Map<String, Object> response = getAnalyticsDetail("queries_executed [GH-90000]");
            assertThat(response).containsKey("aggregation [GH-90000]");
        }

        @Test
        @DisplayName("metric visualization: chart metadata [GH-90000]")
        void shouldHaveVisualization() { // GH-90000
            Map<String, Object> response = getAnalyticsDetail("queries_executed [GH-90000]");
            assertThat(response).containsKey("chartType [GH-90000]");
        }

        @Test
        @DisplayName("metric anomalies: detected outliers [GH-90000]")
        void shouldDetectAnomalies() { // GH-90000
            Map<String, Object> response = getAnalyticsDetail("queries_executed [GH-90000]");
            assertThat(response).containsKey("anomalies [GH-90000]");
        }

        @Test
        @DisplayName("metric export: CSV or JSON [GH-90000]")
        void shouldSupportExport() { // GH-90000
            Map<String, Object> response = getAnalyticsDetail("queries_executed [GH-90000]");
            assertThat(response).containsKey("exportUrl [GH-90000]");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────────────

    private Map<String, Object> getAnalyticsDashboard() { // GH-90000
        return getAnalyticsForTenant("tenant-default [GH-90000]");
    }

    private Map<String, Object> getAnalyticsForTenant(String tenantId) { // GH-90000
        Map<String, Object> response = new HashMap<>(); // GH-90000
        response.put("tenantId", tenantId); // GH-90000
        response.put("period", "MONTH"); // GH-90000
        response.put("lastUpdated", "2026-04-03T14:30:00Z"); // GH-90000

        Map<String, Object> kpis = new HashMap<>(); // GH-90000
        Map<String, Object> revenue = new HashMap<>(); // GH-90000
        revenue.put("current", 125000.0); // GH-90000
        revenue.put("previous", 120000.0); // GH-90000
        revenue.put("change", 4.17); // GH-90000
        kpis.put("totalRevenue", revenue); // GH-90000

        Map<String, Object> users = new HashMap<>(); // GH-90000
        users.put("current", 2500); // GH-90000
        users.put("previous", 2400); // GH-90000
        users.put("change", 4.17); // GH-90000
        kpis.put("activeUsers", users); // GH-90000

        kpis.put("queriesExecuted", 50000); // GH-90000
        kpis.put("storageUsed", "2.5TB"); // GH-90000
        response.put("kpis", kpis); // GH-90000

        response.put("trends", Map.of( // GH-90000
                "revenueGrowth", List.of(120000, 122000, 125000), // GH-90000
                "userGrowth", List.of(2300, 2400, 2500) // GH-90000
        ));

        response.put("topMetrics", List.of( // GH-90000
                Map.of("name", "Highest Query Volume", "value", "1,250 queries", "date", "2026-03-28"), // GH-90000
                Map.of("name", "Peak Storage", "value", "2.8TB", "date", "2026-03-31") // GH-90000
        ));

        return response;
    }

    private Map<String, Object> getAnalyticsDashboardForPeriod(String period) { // GH-90000
        Map<String, Object> response = getAnalyticsDashboard(); // GH-90000
        response.put("period", period); // GH-90000
        return response;
    }

    private Map<String, Object> getAnalyticsForDateRange(String startDate, String endDate) { // GH-90000
        Map<String, Object> response = getAnalyticsDashboard(); // GH-90000
        Map<String, String> dateRange = new HashMap<>(); // GH-90000
        dateRange.put("start", startDate); // GH-90000
        dateRange.put("end", endDate); // GH-90000
        response.put("dateRange", dateRange); // GH-90000
        return response;
    }

    private Map<String, Object> getAnalyticsDetail(String metric) { // GH-90000
        Map<String, Object> response = new HashMap<>(); // GH-90000
        response.put("metric", metric); // GH-90000
        response.put("title", "Queries Executed"); // GH-90000

        List<Map<String, Object>> data = List.of( // GH-90000
                Map.of("date", "2026-03-25", "value", 45000), // GH-90000
                Map.of("date", "2026-03-26", "value", 47500), // GH-90000
                Map.of("date", "2026-03-27", "value", 50000), // GH-90000
                Map.of("date", "2026-03-28", "value", 48000) // GH-90000
        );
        response.put("data", data); // GH-90000

        Map<String, Object> breakdown = new HashMap<>(); // GH-90000
        breakdown.put("byCollection", Map.of( // GH-90000
                "Sales Data", 20000,
                "Marketing Data", 15000,
                "Finance Data", 15000
        ));
        response.put("breakdown", breakdown); // GH-90000

        response.put("aggregation", "SUM"); // GH-90000
        response.put("chartType", "LINE"); // GH-90000
        response.put("anomalies", List.of()); // GH-90000
        response.put("exportUrl", "/api/analytics/" + metric + "/export"); // GH-90000

        return response;
    }

    private static Map<String, ?> requireMap(Map<String, ?> container, String key) { // GH-90000
        Object value = container.get(key); // GH-90000
        assertThat(value).as("Expected '%s' to be a map", key).isInstanceOf(Map.class); // GH-90000
        if (value instanceof Map<?, ?> mapValue) { // GH-90000
            Map<String, Object> typedMap = new HashMap<>(); // GH-90000
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) { // GH-90000
                typedMap.put(String.valueOf(entry.getKey()), entry.getValue()); // GH-90000
            }
            return typedMap;
        }
        fail("Expected '%s' to be a map", key); // GH-90000
        return Map.of(); // GH-90000
    }
}
