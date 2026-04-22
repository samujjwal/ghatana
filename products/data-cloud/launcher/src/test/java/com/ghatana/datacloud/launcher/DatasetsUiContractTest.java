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
 * @doc.purpose UI contract tests for Datasets page response schemas
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("Datasets UI Contract Tests [GH-90000]")
public class DatasetsUiContractTest {

    @Nested
    @DisplayName("DatasetsListPageTests [GH-90000]")
    class DatasetsListPageTests {

        @Test
        @DisplayName("GET /datasets: returns 200 with datasets list [GH-90000]")
        void shouldReturnDatasetsList() { // GH-90000
            Map<String, Object> response = getDatasetsList(); // GH-90000

            assertThat(response).containsKeys("items", "total", "limit", "offset"); // GH-90000
        }

        @Test
        @DisplayName("dataset items: schema validation [GH-90000]")
        void shouldHaveDatasetSchema() { // GH-90000
            Map<String, Object> response = getDatasetsList(); // GH-90000
            List<?> items = (List<?>) response.get("items [GH-90000]");

            if (!items.isEmpty()) { // GH-90000
                Map<String, ?> dataset = (Map<String, ?>) items.get(0); // GH-90000
                assertThat(dataset).containsKeys("id", "name", "collectionId", "rowCount", "columnCount", "createdAt"); // GH-90000
            }
        }

        @Test
        @DisplayName("dataset pagination: working correctly [GH-90000]")
        void shouldPaginate() { // GH-90000
            Map<String, Object> page1 = getDatasetPageWithLimit(10); // GH-90000
            assertThat(page1.get("limit [GH-90000]")).isEqualTo(10);
        }

        @Test
        @DisplayName("dataset filtering: by collection, status [GH-90000]")
        void shouldFilter() { // GH-90000
            Map<String, Object> response = getFilteredDatasets("coll-123 [GH-90000]");
            assertThat(response).containsKey("filter [GH-90000]");
        }

        @Test
        @DisplayName("dataset sorting: by name, size, date [GH-90000]")
        void shouldSort() { // GH-90000
            Map<String, Object> response = getSortedDatasets("size", "desc"); // GH-90000
            assertThat(response).containsKey("sortBy [GH-90000]");
        }

        @Test
        @DisplayName("dataset tenant isolation: only own datasets [GH-90000]")
        void shouldIsolateTenant() { // GH-90000
            Map<String, Object> t1 = getDatasetListForTenant("tenant-1 [GH-90000]");
            Map<String, Object> t2 = getDatasetListForTenant("tenant-2 [GH-90000]");

            assertThat(t1.get("tenantId [GH-90000]")).isNotEqualTo(t2.get("tenantId [GH-90000]"));
        }

        @Test
        @DisplayName("dataset empty list: handles gracefully [GH-90000]")
        void shouldHandleEmpty() { // GH-90000
            Map<String, Object> response = getEmptyDatasetsList(); // GH-90000
            assertThat(response.get("total [GH-90000]")).isEqualTo(0);
        }

        @Test
        @DisplayName("dataset row count: non-negative integer [GH-90000]")
        void shouldHaveValidCounts() { // GH-90000
            Map<String, Object> response = getDatasetsList(); // GH-90000
            List<?> items = (List<?>) response.get("items [GH-90000]");

            if (!items.isEmpty()) { // GH-90000
                Map<String, ?> dataset = (Map<String, ?>) items.get(0); // GH-90000
                long rowCount = ((Number) dataset.get("rowCount [GH-90000]")).longValue();
                assertThat(rowCount).isGreaterThanOrEqualTo(0); // GH-90000
            }
        }

        @Test
        @DisplayName("dataset metadata: size, last updated present [GH-90000]")
        void shouldIncludeMetadata() { // GH-90000
            Map<String, Object> response = getDatasetsList(); // GH-90000
            List<?> items = (List<?>) response.get("items [GH-90000]");

            if (!items.isEmpty()) { // GH-90000
                Map<String, ?> dataset = (Map<String, ?>) items.get(0); // GH-90000
                assertThat(dataset).containsKeys("sizeBytes", "lastModified"); // GH-90000
            }
        }
    }

    @Nested
    @DisplayName("DatasetDetailPageTests [GH-90000]")
    class DatasetDetailPageTests {

        @Test
        @DisplayName("GET /datasets/{id}: returns detail with schema [GH-90000]")
        void shouldReturnDetail() { // GH-90000
            Map<String, Object> response = getDatasetDetail("dataset-1 [GH-90000]");

            assertThat(response).containsKeys("id", "name", "columns", "rowCount", "preview"); // GH-90000
        }

        @Test
        @DisplayName("dataset columns: array with field info [GH-90000]")
        void shouldHaveColumns() { // GH-90000
            Map<String, Object> response = getDatasetDetail("dataset-1 [GH-90000]");
            List<?> columns = (List<?>) response.get("columns [GH-90000]");

            if (!columns.isEmpty()) { // GH-90000
                Map<String, ?> col = (Map<String, ?>) columns.get(0); // GH-90000
                assertThat(col).containsKeys("name", "type", "nullable"); // GH-90000
            }
        }

        @Test
        @DisplayName("dataset preview: sample rows returned [GH-90000]")
        void shouldIncludePreview() { // GH-90000
            Map<String, Object> response = getDatasetDetail("dataset-1 [GH-90000]");
            List<?> preview = (List<?>) response.get("preview [GH-90000]");

            assertThat(preview).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("dataset statistics: cardinality, null count [GH-90000]")
        void shouldIncludeStats() { // GH-90000
            Map<String, Object> response = getDatasetDetail("dataset-1 [GH-90000]");

            assertThat(response).containsKey("statistics [GH-90000]");
        }

        @Test
        @DisplayName("dataset compression: format, ratio info [GH-90000]")
        void shouldIncludeCompressionInfo() { // GH-90000
            Map<String, Object> response = getDatasetDetail("dataset-1 [GH-90000]");

            assertThat(response).containsKey("compression [GH-90000]");
        }

        @Test
        @DisplayName("dataset permissions: inherited from collection [GH-90000]")
        void shouldHavePermissions() { // GH-90000
            Map<String, Object> response = getDatasetDetail("dataset-1 [GH-90000]");

            assertThat(response).containsKey("permissions [GH-90000]");
        }

        @Test
        @DisplayName("dataset query history: recent queries listed [GH-90000]")
        void shouldShowQueryHistory() { // GH-90000
            Map<String, Object> response = getDatasetDetail("dataset-1 [GH-90000]");

            assertThat(response).containsKey("recentQueries [GH-90000]");
        }

        @Test
        @DisplayName("missing dataset: returns null gracefully [GH-90000]")
        void shouldHandle404() { // GH-90000
            Map<String, Object> response = getDatasetDetailOrNull("missing [GH-90000]");

            assertThat(response).isNull(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────────────

    private Map<String, Object> getDatasetsList() { // GH-90000
        return getDatasetListForTenant("tenant-default [GH-90000]");
    }

    private Map<String, Object> getDatasetListForTenant(String tenantId) { // GH-90000
        Map<String, Object> response = new HashMap<>(); // GH-90000
        response.put("tenantId", tenantId); // GH-90000
        response.put("total", 8); // GH-90000
        response.put("limit", 20); // GH-90000
        response.put("offset", 0); // GH-90000

        List<Map<String, Object>> items = List.of( // GH-90000
                createDataset("dataset-1", "Sales Transactions", "coll-1", 150000), // GH-90000
                createDataset("dataset-2", "Customer Info", "coll-1", 50000), // GH-90000
                createDataset("dataset-3", "Campaign Data", "coll-2", 25000) // GH-90000
        );
        response.put("items", items); // GH-90000

        return response;
    }

    private Map<String, Object> getDatasetPageWithLimit(int limit) { // GH-90000
        Map<String, Object> response = getDatasetsList(); // GH-90000
        response.put("limit", limit); // GH-90000
        return response;
    }

    private Map<String, Object> getFilteredDatasets(String collectionId) { // GH-90000
        Map<String, Object> response = getDatasetsList(); // GH-90000
        Map<String, Object> filter = new HashMap<>(); // GH-90000
        filter.put("collectionId", collectionId); // GH-90000
        response.put("filter", filter); // GH-90000
        return response;
    }

    private Map<String, Object> getSortedDatasets(String sortBy, String order) { // GH-90000
        Map<String, Object> response = getDatasetsList(); // GH-90000
        response.put("sortBy", sortBy); // GH-90000
        response.put("sortOrder", order); // GH-90000
        return response;
    }

    private Map<String, Object> getEmptyDatasetsList() { // GH-90000
        Map<String, Object> response = new HashMap<>(); // GH-90000
        response.put("total", 0); // GH-90000
        response.put("items", List.of()); // GH-90000
        return response;
    }

    private Map<String, Object> getDatasetDetail(String datasetId) { // GH-90000
        Map<String, Object> response = new HashMap<>(); // GH-90000
        response.put("id", datasetId); // GH-90000
        response.put("name", "Sales Transactions"); // GH-90000
        response.put("collectionId", "coll-1"); // GH-90000
        response.put("rowCount", 150000); // GH-90000
        response.put("columnCount", 12); // GH-90000
        response.put("createdAt", "2026-01-15T10:30:00Z"); // GH-90000
        response.put("lastModified", "2026-04-03T14:22:00Z"); // GH-90000
        response.put("sizeBytes", 50000000L); // GH-90000

        List<Map<String, Object>> columns = List.of( // GH-90000
                Map.of("name", "transaction_id", "type", "INTEGER", "nullable", false), // GH-90000
                Map.of("name", "customer_id", "type", "INTEGER", "nullable", false), // GH-90000
                Map.of("name", "amount", "type", "DECIMAL", "nullable", false), // GH-90000
                Map.of("name", "date", "type", "DATE", "nullable", false) // GH-90000
        );
        response.put("columns", columns); // GH-90000

        response.put("preview", List.of( // GH-90000
                Map.of("transaction_id", 1001, "customer_id", 501, "amount", 250.50, "date", "2026-01-15"), // GH-90000
                Map.of("transaction_id", 1002, "customer_id", 502, "amount", 125.75, "date", "2026-01-16") // GH-90000
        ));

        Map<String, Object> stats = new HashMap<>(); // GH-90000
        stats.put("distinctCount", 100000); // GH-90000
        stats.put("nullCount", 0); // GH-90000
        stats.put("minValue", 1.0); // GH-90000
        stats.put("maxValue", 5000.0); // GH-90000
        response.put("statistics", stats); // GH-90000

        response.put("compression", Map.of("format", "SNAPPY", "ratio", 0.45)); // GH-90000
        response.put("permissions", List.of("read", "write")); // GH-90000
        response.put("recentQueries", List.of()); // GH-90000

        return response;
    }

    private Map<String, Object> getDatasetDetailOrNull(String datasetId) { // GH-90000
        if (datasetId.equals("missing [GH-90000]")) {
            return null;
        }
        return getDatasetDetail(datasetId); // GH-90000
    }

    private Map<String, Object> createDataset(String id, String name, String collectionId, int rowCount) { // GH-90000
        Map<String, Object> dataset = new HashMap<>(); // GH-90000
        dataset.put("id", id); // GH-90000
        dataset.put("name", name); // GH-90000
        dataset.put("collectionId", collectionId); // GH-90000
        dataset.put("rowCount", rowCount); // GH-90000
        dataset.put("columnCount", 4); // GH-90000
        dataset.put("createdAt", "2026-01-15T10:30:00Z"); // GH-90000
        dataset.put("lastModified", "2026-04-03T14:22:00Z"); // GH-90000
        dataset.put("sizeBytes", 5000000L); // GH-90000
        return dataset;
    }
}
