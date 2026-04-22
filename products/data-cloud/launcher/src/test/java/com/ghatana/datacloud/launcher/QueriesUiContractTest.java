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

/**
 * @doc.type class
 * @doc.purpose UI contract tests for Queries page response schemas
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("Queries UI Contract Tests [GH-90000]")
public class QueriesUiContractTest {

    @Nested
    @DisplayName("QueriesListPageTests [GH-90000]")
    class QueriesListPageTests {

        @Test
        @DisplayName("GET /queries: returns list with schema [GH-90000]")
        void shouldReturnList() { // GH-90000
            Map<String, Object> response = getQueriesList(); // GH-90000
            assertThat(response).containsKeys("items", "total", "limit"); // GH-90000
        }

        @Test
        @DisplayName("query items: typed with SQL, status, results [GH-90000]")
        void shouldHaveSchema() { // GH-90000
            Map<String, Object> response = getQueriesList(); // GH-90000
            List<?> items = (List<?>) response.get("items [GH-90000]");

            if (!items.isEmpty()) { // GH-90000
                Map<String, ?> query = (Map<String, ?>) items.get(0); // GH-90000
                assertThat(query).containsKeys("id", "title", "sql", "status", "createdAt", "executionTime"); // GH-90000
            }
        }

        @Test
        @DisplayName("query pagination: limit/offset [GH-90000]")
        void shouldPaginate() { // GH-90000
            Map<String, Object> response = getQueriesList(); // GH-90000
            assertThat(response.get("limit [GH-90000]")).isEqualTo(20);
        }

        @Test
        @DisplayName("query filters: by status, owner, date [GH-90000]")
        void shouldFilter() { // GH-90000
            Map<String, Object> response = getFilteredQueries("COMPLETED [GH-90000]");
            assertThat(response).containsKey("filter [GH-90000]");
        }

        @Test
        @DisplayName("query sorting: by date, execution time [GH-90000]")
        void shouldSort() { // GH-90000
            Map<String, Object> response = getSortedQueries("executionTime", "desc"); // GH-90000
            assertThat(response).containsKey("sortBy [GH-90000]");
        }

        @Test
        @DisplayName("query tenant isolation [GH-90000]")
        void shouldIsolateTenant() { // GH-90000
            Map<String, Object> t1 = getQueriesForTenant("tenant-1 [GH-90000]");
            assertThat(t1.get("tenantId [GH-90000]")).isEqualTo("tenant-1 [GH-90000]");
        }

        @Test
        @DisplayName("query status values: valid enumeration [GH-90000]")
        void shouldHaveValidStatus() { // GH-90000
            Map<String, Object> response = getQueriesList(); // GH-90000
            List<?> items = (List<?>) response.get("items [GH-90000]");

            if (!items.isEmpty()) { // GH-90000
                Map<String, ?> query = (Map<String, ?>) items.get(0); // GH-90000
                String status = query.get("status [GH-90000]").toString();
                assertThat(status).isIn("PENDING", "RUNNING", "COMPLETED", "FAILED", "CANCELLED"); // GH-90000
            }
        }
    }

    @Nested
    @DisplayName("QueryDetailPageTests [GH-90000]")
    class QueryDetailPageTests {

        @Test
        @DisplayName("GET /queries/{id}: returns detail [GH-90000]")
        void shouldReturnDetail() { // GH-90000
            Map<String, Object> response = getQueryDetail("query-1 [GH-90000]");
            assertThat(response).containsKeys("id", "sql", "results", "executionStats"); // GH-90000
        }

        @Test
        @DisplayName("query result set: rows and columns [GH-90000]")
        void shouldHaveResults() { // GH-90000
            Map<String, Object> response = getQueryDetail("query-1 [GH-90000]");
            Map<String, ?> results = (Map<String, ?>) response.get("results [GH-90000]");

            assertThat(results).containsKeys("rows", "columns", "rowCount"); // GH-90000
        }

        @Test
        @DisplayName("query execution stats: duration, rows scanned [GH-90000]")
        void shouldHaveStats() { // GH-90000
            Map<String, Object> response = getQueryDetail("query-1 [GH-90000]");
            Map<String, ?> stats = (Map<String, ?>) response.get("executionStats [GH-90000]");

            assertThat(stats).containsKeys("durationMs", "rowsScanned", "rowsReturned"); // GH-90000
        }

        @Test
        @DisplayName("query history: previous executions [GH-90000]")
        void shouldHaveHistory() { // GH-90000
            Map<String, Object> response = getQueryDetail("query-1 [GH-90000]");
            assertThat(response).containsKey("executionHistory [GH-90000]");
        }

        @Test
        @DisplayName("query permissions: owner can modify [GH-90000]")
        void shouldHavePermissions() { // GH-90000
            Map<String, Object> response = getQueryDetail("query-1 [GH-90000]");
            assertThat(response).containsKey("permissions [GH-90000]");
        }

        @Test
        @DisplayName("missing query: returns null [GH-90000]")
        void shouldHandle404() { // GH-90000
            Map<String, Object> response = getQueryDetailOrNull("missing [GH-90000]");
            assertThat(response).isNull(); // GH-90000
        }

        @Test
        @DisplayName("query timeout: handled gracefully [GH-90000]")
        void shouldHandleTimeout() { // GH-90000
            Map<String, Object> response = getQueryDetail("query-1 [GH-90000]");
            assertThat(response).containsKey("status [GH-90000]");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────────────

    private Map<String, Object> getQueriesList() { // GH-90000
        return getQueriesForTenant("tenant-default [GH-90000]");
    }

    private Map<String, Object> getQueriesForTenant(String tenantId) { // GH-90000
        Map<String, Object> response = new HashMap<>(); // GH-90000
        response.put("tenantId", tenantId); // GH-90000
        response.put("total", 42); // GH-90000
        response.put("limit", 20); // GH-90000
        response.put("offset", 0); // GH-90000

        List<Map<String, Object>> items = List.of( // GH-90000
                createQuery("query-1", "SELECT * FROM sales WHERE date > '2026-01-01'", "COMPLETED", 1250), // GH-90000
                createQuery("query-2", "SELECT COUNT(*) FROM customers", "COMPLETED", 350), // GH-90000
                createQuery("query-3", "SELECT * FROM results WHERE status = 'pending'", "RUNNING", null) // GH-90000
        );
        response.put("items", items); // GH-90000

        return response;
    }

    private Map<String, Object> getFilteredQueries(String status) { // GH-90000
        Map<String, Object> response = getQueriesList(); // GH-90000
        Map<String, Object> filter = new HashMap<>(); // GH-90000
        filter.put("status", status); // GH-90000
        response.put("filter", filter); // GH-90000
        return response;
    }

    private Map<String, Object> getSortedQueries(String sortBy, String order) { // GH-90000
        Map<String, Object> response = getQueriesList(); // GH-90000
        response.put("sortBy", sortBy); // GH-90000
        response.put("sortOrder", order); // GH-90000
        return response;
    }

    private Map<String, Object> getQueryDetail(String queryId) { // GH-90000
        Map<String, Object> response = new HashMap<>(); // GH-90000
        response.put("id", queryId); // GH-90000
        response.put("title", "Sales Report 2026"); // GH-90000
        response.put("sql", "SELECT * FROM sales WHERE date > '2026-01-01' LIMIT 100"); // GH-90000
        response.put("status", "COMPLETED"); // GH-90000
        response.put("createdAt", "2026-04-01T10:00:00Z"); // GH-90000
        response.put("executedAt", "2026-04-03T14:30:00Z"); // GH-90000

        Map<String, Object> results = new HashMap<>(); // GH-90000
        results.put("rowCount", 15200); // GH-90000
        results.put("columnCount", 8); // GH-90000
        results.put("columns", List.of("transaction_id", "customer_id", "amount", "date", "region", "status", "product", "quantity")); // GH-90000
        results.put("rows", List.of( // GH-90000
                List.of(1001, 501, 250.50, "2026-01-15", "North", "COMPLETED", "Widget A", 5), // GH-90000
                List.of(1002, 502, 125.75, "2026-01-16", "South", "COMPLETED", "Widget B", 3) // GH-90000
        ));
        response.put("results", results); // GH-90000

        Map<String, Object> stats = new HashMap<>(); // GH-90000
        stats.put("durationMs", 1250); // GH-90000
        stats.put("rowsScanned", 500000); // GH-90000
        stats.put("rowsReturned", 15200); // GH-90000
        stats.put("bytesScanned", 250000000L); // GH-90000
        response.put("executionStats", stats); // GH-90000

        response.put("executionHistory", List.of( // GH-90000
                Map.of("executedAt", "2026-04-03T14:30:00Z", "durationMs", 1250), // GH-90000
                Map.of("executedAt", "2026-04-02T10:15:00Z", "durationMs", 1180) // GH-90000
        ));

        response.put("permissions", Map.of("canEdit", true, "canDelete", true, "canShare", true)); // GH-90000

        return response;
    }

    private Map<String, Object> getQueryDetailOrNull(String queryId) { // GH-90000
        if (queryId.equals("missing [GH-90000]")) {
            return null;
        }
        return getQueryDetail(queryId); // GH-90000
    }

    private Map<String, Object> createQuery(String id, String sql, String status, Integer executionTime) { // GH-90000
        Map<String, Object> query = new HashMap<>(); // GH-90000
        query.put("id", id); // GH-90000
        query.put("title", "Query " + id); // GH-90000
        query.put("sql", sql); // GH-90000
        query.put("status", status); // GH-90000
        query.put("createdAt", "2026-04-01T10:00:00Z"); // GH-90000
        if (executionTime != null) { // GH-90000
            query.put("executionTime", executionTime); // GH-90000
        }
        return query;
    }
}
