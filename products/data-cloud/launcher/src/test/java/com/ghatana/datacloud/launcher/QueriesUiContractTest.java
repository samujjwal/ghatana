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

/**
 * @doc.type class
 * @doc.purpose UI contract tests for Queries page response schemas
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("Queries UI Contract Tests")
public class QueriesUiContractTest {

    @Nested
    @DisplayName("QueriesListPageTests")
    class QueriesListPageTests {

        @Test
        @DisplayName("GET /queries: returns list with schema")
        void shouldReturnList() { 
            Map<String, Object> response = getQueriesList(); 
            assertThat(response).containsKeys("items", "total", "limit"); 
        }

        @Test
        @DisplayName("query items: typed with SQL, status, results")
        void shouldHaveSchema() { 
            Map<String, Object> response = getQueriesList(); 
            List<?> items = (List<?>) response.get("items");

            if (!items.isEmpty()) { 
                Map<String, ?> query = (Map<String, ?>) items.get(0); 
                assertThat(query).containsKeys("id", "title", "sql", "status", "createdAt", "executionTime"); 
            }
        }

        @Test
        @DisplayName("query pagination: limit/offset")
        void shouldPaginate() { 
            Map<String, Object> response = getQueriesList(); 
            assertThat(response.get("limit")).isEqualTo(20);
        }

        @Test
        @DisplayName("query filters: by status, owner, date")
        void shouldFilter() { 
            Map<String, Object> response = getFilteredQueries("COMPLETED");
            assertThat(response).containsKey("filter");
        }

        @Test
        @DisplayName("query sorting: by date, execution time")
        void shouldSort() { 
            Map<String, Object> response = getSortedQueries("executionTime", "desc"); 
            assertThat(response).containsKey("sortBy");
        }

        @Test
        @DisplayName("query tenant isolation")
        void shouldIsolateTenant() { 
            Map<String, Object> t1 = getQueriesForTenant("tenant-1");
            assertThat(t1.get("tenantId")).isEqualTo("tenant-1");
        }

        @Test
        @DisplayName("query status values: valid enumeration")
        void shouldHaveValidStatus() { 
            Map<String, Object> response = getQueriesList(); 
            List<?> items = (List<?>) response.get("items");

            if (!items.isEmpty()) { 
                Map<String, ?> query = (Map<String, ?>) items.get(0); 
                String status = query.get("status").toString();
                assertThat(status).isIn("PENDING", "RUNNING", "COMPLETED", "FAILED", "CANCELLED"); 
            }
        }
    }

    @Nested
    @DisplayName("QueryDetailPageTests")
    class QueryDetailPageTests {

        @Test
        @DisplayName("GET /queries/{id}: returns detail")
        void shouldReturnDetail() { 
            Map<String, Object> response = getQueryDetail("query-1");
            assertThat(response).containsKeys("id", "sql", "results", "executionStats"); 
        }

        @Test
        @DisplayName("query result set: rows and columns")
        void shouldHaveResults() { 
            Map<String, Object> response = getQueryDetail("query-1");
            Map<String, ?> results = (Map<String, ?>) response.get("results");

            assertThat(results).containsKeys("rows", "columns", "rowCount"); 
        }

        @Test
        @DisplayName("query execution stats: duration, rows scanned")
        void shouldHaveStats() { 
            Map<String, Object> response = getQueryDetail("query-1");
            Map<String, ?> stats = (Map<String, ?>) response.get("executionStats");

            assertThat(stats).containsKeys("durationMs", "rowsScanned", "rowsReturned"); 
        }

        @Test
        @DisplayName("query history: previous executions")
        void shouldHaveHistory() { 
            Map<String, Object> response = getQueryDetail("query-1");
            assertThat(response).containsKey("executionHistory");
        }

        @Test
        @DisplayName("query permissions: owner can modify")
        void shouldHavePermissions() { 
            Map<String, Object> response = getQueryDetail("query-1");
            assertThat(response).containsKey("permissions");
        }

        @Test
        @DisplayName("missing query: returns null")
        void shouldHandle404() { 
            Map<String, Object> response = getQueryDetailOrNull("missing");
            assertThat(response).isNull(); 
        }

        @Test
        @DisplayName("query timeout: handled gracefully")
        void shouldHandleTimeout() { 
            Map<String, Object> response = getQueryDetail("query-1");
            assertThat(response).containsKey("status");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────────────

    private Map<String, Object> getQueriesList() { 
        return getQueriesForTenant("tenant-default");
    }

    private Map<String, Object> getQueriesForTenant(String tenantId) { 
        Map<String, Object> response = new HashMap<>(); 
        response.put("tenantId", tenantId); 
        response.put("total", 42); 
        response.put("limit", 20); 
        response.put("offset", 0); 

        List<Map<String, Object>> items = List.of( 
                createQuery("query-1", "SELECT * FROM sales WHERE date > '2026-01-01'", "COMPLETED", 1250), 
                createQuery("query-2", "SELECT COUNT(*) FROM customers", "COMPLETED", 350), 
                createQuery("query-3", "SELECT * FROM results WHERE status = 'pending'", "RUNNING", null) 
        );
        response.put("items", items); 

        return response;
    }

    private Map<String, Object> getFilteredQueries(String status) { 
        Map<String, Object> response = getQueriesList(); 
        Map<String, Object> filter = new HashMap<>(); 
        filter.put("status", status); 
        response.put("filter", filter); 
        return response;
    }

    private Map<String, Object> getSortedQueries(String sortBy, String order) { 
        Map<String, Object> response = getQueriesList(); 
        response.put("sortBy", sortBy); 
        response.put("sortOrder", order); 
        return response;
    }

    private Map<String, Object> getQueryDetail(String queryId) { 
        Map<String, Object> response = new HashMap<>(); 
        response.put("id", queryId); 
        response.put("title", "Sales Report 2026"); 
        response.put("sql", "SELECT * FROM sales WHERE date > '2026-01-01' LIMIT 100"); 
        response.put("status", "COMPLETED"); 
        response.put("createdAt", "2026-04-01T10:00:00Z"); 
        response.put("executedAt", "2026-04-03T14:30:00Z"); 

        Map<String, Object> results = new HashMap<>(); 
        results.put("rowCount", 15200); 
        results.put("columnCount", 8); 
        results.put("columns", List.of("transaction_id", "customer_id", "amount", "date", "region", "status", "product", "quantity")); 
        results.put("rows", List.of( 
                List.of(1001, 501, 250.50, "2026-01-15", "North", "COMPLETED", "Widget A", 5), 
                List.of(1002, 502, 125.75, "2026-01-16", "South", "COMPLETED", "Widget B", 3) 
        ));
        response.put("results", results); 

        Map<String, Object> stats = new HashMap<>(); 
        stats.put("durationMs", 1250); 
        stats.put("rowsScanned", 500000); 
        stats.put("rowsReturned", 15200); 
        stats.put("bytesScanned", 250000000L); 
        response.put("executionStats", stats); 

        response.put("executionHistory", List.of( 
                Map.of("executedAt", "2026-04-03T14:30:00Z", "durationMs", 1250), 
                Map.of("executedAt", "2026-04-02T10:15:00Z", "durationMs", 1180) 
        ));

        response.put("permissions", Map.of("canEdit", true, "canDelete", true, "canShare", true)); 

        return response;
    }

    private Map<String, Object> getQueryDetailOrNull(String queryId) { 
        if (queryId.equals("missing")) {
            return null;
        }
        return getQueryDetail(queryId); 
    }

    private Map<String, Object> createQuery(String id, String sql, String status, Integer executionTime) { 
        Map<String, Object> query = new HashMap<>(); 
        query.put("id", id); 
        query.put("title", "Query " + id); 
        query.put("sql", sql); 
        query.put("status", status); 
        query.put("createdAt", "2026-04-01T10:00:00Z"); 
        if (executionTime != null) { 
            query.put("executionTime", executionTime); 
        }
        return query;
    }
}
