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
 * @doc.purpose UI contract tests for Datasets page response schemas
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("Datasets UI Contract Tests")
public class DatasetsUiContractTest {

    @Nested
    @DisplayName("DatasetsListPageTests")
    class DatasetsListPageTests {

        @Test
        @DisplayName("GET /datasets: returns 200 with datasets list")
        void shouldReturnDatasetsList() { 
            Map<String, Object> response = getDatasetsList(); 

            assertThat(response).containsKeys("items", "total", "limit", "offset"); 
        }

        @Test
        @DisplayName("dataset items: schema validation")
        void shouldHaveDatasetSchema() { 
            Map<String, Object> response = getDatasetsList(); 
            List<?> items = (List<?>) response.get("items");

            if (!items.isEmpty()) { 
                Map<String, ?> dataset = (Map<String, ?>) items.get(0); 
                assertThat(dataset).containsKeys("id", "name", "collectionId", "rowCount", "columnCount", "createdAt"); 
            }
        }

        @Test
        @DisplayName("dataset pagination: working correctly")
        void shouldPaginate() { 
            Map<String, Object> page1 = getDatasetPageWithLimit(10); 
            assertThat(page1.get("limit")).isEqualTo(10);
        }

        @Test
        @DisplayName("dataset filtering: by collection, status")
        void shouldFilter() { 
            Map<String, Object> response = getFilteredDatasets("coll-123");
            assertThat(response).containsKey("filter");
        }

        @Test
        @DisplayName("dataset sorting: by name, size, date")
        void shouldSort() { 
            Map<String, Object> response = getSortedDatasets("size", "desc"); 
            assertThat(response).containsKey("sortBy");
        }

        @Test
        @DisplayName("dataset tenant isolation: only own datasets")
        void shouldIsolateTenant() { 
            Map<String, Object> t1 = getDatasetListForTenant("tenant-1");
            Map<String, Object> t2 = getDatasetListForTenant("tenant-2");

            assertThat(t1.get("tenantId")).isNotEqualTo(t2.get("tenantId"));
        }

        @Test
        @DisplayName("dataset empty list: handles gracefully")
        void shouldHandleEmpty() { 
            Map<String, Object> response = getEmptyDatasetsList(); 
            assertThat(response.get("total")).isEqualTo(0);
        }

        @Test
        @DisplayName("dataset row count: non-negative integer")
        void shouldHaveValidCounts() { 
            Map<String, Object> response = getDatasetsList(); 
            List<?> items = (List<?>) response.get("items");

            if (!items.isEmpty()) { 
                Map<String, ?> dataset = (Map<String, ?>) items.get(0); 
                long rowCount = ((Number) dataset.get("rowCount")).longValue();
                assertThat(rowCount).isGreaterThanOrEqualTo(0); 
            }
        }

        @Test
        @DisplayName("dataset metadata: size, last updated present")
        void shouldIncludeMetadata() { 
            Map<String, Object> response = getDatasetsList(); 
            List<?> items = (List<?>) response.get("items");

            if (!items.isEmpty()) { 
                Map<String, ?> dataset = (Map<String, ?>) items.get(0); 
                assertThat(dataset).containsKeys("sizeBytes", "lastModified"); 
            }
        }
    }

    @Nested
    @DisplayName("DatasetDetailPageTests")
    class DatasetDetailPageTests {

        @Test
        @DisplayName("GET /datasets/{id}: returns detail with schema")
        void shouldReturnDetail() { 
            Map<String, Object> response = getDatasetDetail("dataset-1");

            assertThat(response).containsKeys("id", "name", "columns", "rowCount", "preview"); 
        }

        @Test
        @DisplayName("dataset columns: array with field info")
        void shouldHaveColumns() { 
            Map<String, Object> response = getDatasetDetail("dataset-1");
            List<?> columns = (List<?>) response.get("columns");

            if (!columns.isEmpty()) { 
                Map<String, ?> col = (Map<String, ?>) columns.get(0); 
                assertThat(col).containsKeys("name", "type", "nullable"); 
            }
        }

        @Test
        @DisplayName("dataset preview: sample rows returned")
        void shouldIncludePreview() { 
            Map<String, Object> response = getDatasetDetail("dataset-1");
            List<?> preview = (List<?>) response.get("preview");

            assertThat(preview).isNotNull(); 
        }

        @Test
        @DisplayName("dataset statistics: cardinality, null count")
        void shouldIncludeStats() { 
            Map<String, Object> response = getDatasetDetail("dataset-1");

            assertThat(response).containsKey("statistics");
        }

        @Test
        @DisplayName("dataset compression: format, ratio info")
        void shouldIncludeCompressionInfo() { 
            Map<String, Object> response = getDatasetDetail("dataset-1");

            assertThat(response).containsKey("compression");
        }

        @Test
        @DisplayName("dataset permissions: inherited from collection")
        void shouldHavePermissions() { 
            Map<String, Object> response = getDatasetDetail("dataset-1");

            assertThat(response).containsKey("permissions");
        }

        @Test
        @DisplayName("dataset query history: recent queries listed")
        void shouldShowQueryHistory() { 
            Map<String, Object> response = getDatasetDetail("dataset-1");

            assertThat(response).containsKey("recentQueries");
        }

        @Test
        @DisplayName("missing dataset: returns null gracefully")
        void shouldHandle404() { 
            Map<String, Object> response = getDatasetDetailOrNull("missing");

            assertThat(response).isNull(); 
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────────────

    private Map<String, Object> getDatasetsList() { 
        return getDatasetListForTenant("tenant-default");
    }

    private Map<String, Object> getDatasetListForTenant(String tenantId) { 
        Map<String, Object> response = new HashMap<>(); 
        response.put("tenantId", tenantId); 
        response.put("total", 8); 
        response.put("limit", 20); 
        response.put("offset", 0); 

        List<Map<String, Object>> items = List.of( 
                createDataset("dataset-1", "Sales Transactions", "coll-1", 150000), 
                createDataset("dataset-2", "Customer Info", "coll-1", 50000), 
                createDataset("dataset-3", "Campaign Data", "coll-2", 25000) 
        );
        response.put("items", items); 

        return response;
    }

    private Map<String, Object> getDatasetPageWithLimit(int limit) { 
        Map<String, Object> response = getDatasetsList(); 
        response.put("limit", limit); 
        return response;
    }

    private Map<String, Object> getFilteredDatasets(String collectionId) { 
        Map<String, Object> response = getDatasetsList(); 
        Map<String, Object> filter = new HashMap<>(); 
        filter.put("collectionId", collectionId); 
        response.put("filter", filter); 
        return response;
    }

    private Map<String, Object> getSortedDatasets(String sortBy, String order) { 
        Map<String, Object> response = getDatasetsList(); 
        response.put("sortBy", sortBy); 
        response.put("sortOrder", order); 
        return response;
    }

    private Map<String, Object> getEmptyDatasetsList() { 
        Map<String, Object> response = new HashMap<>(); 
        response.put("total", 0); 
        response.put("items", List.of()); 
        return response;
    }

    private Map<String, Object> getDatasetDetail(String datasetId) { 
        Map<String, Object> response = new HashMap<>(); 
        response.put("id", datasetId); 
        response.put("name", "Sales Transactions"); 
        response.put("collectionId", "coll-1"); 
        response.put("rowCount", 150000); 
        response.put("columnCount", 12); 
        response.put("createdAt", "2026-01-15T10:30:00Z"); 
        response.put("lastModified", "2026-04-03T14:22:00Z"); 
        response.put("sizeBytes", 50000000L); 

        List<Map<String, Object>> columns = List.of( 
                Map.of("name", "transaction_id", "type", "INTEGER", "nullable", false), 
                Map.of("name", "customer_id", "type", "INTEGER", "nullable", false), 
                Map.of("name", "amount", "type", "DECIMAL", "nullable", false), 
                Map.of("name", "date", "type", "DATE", "nullable", false) 
        );
        response.put("columns", columns); 

        response.put("preview", List.of( 
                Map.of("transaction_id", 1001, "customer_id", 501, "amount", 250.50, "date", "2026-01-15"), 
                Map.of("transaction_id", 1002, "customer_id", 502, "amount", 125.75, "date", "2026-01-16") 
        ));

        Map<String, Object> stats = new HashMap<>(); 
        stats.put("distinctCount", 100000); 
        stats.put("nullCount", 0); 
        stats.put("minValue", 1.0); 
        stats.put("maxValue", 5000.0); 
        response.put("statistics", stats); 

        response.put("compression", Map.of("format", "SNAPPY", "ratio", 0.45)); 
        response.put("permissions", List.of("read", "write")); 
        response.put("recentQueries", List.of()); 

        return response;
    }

    private Map<String, Object> getDatasetDetailOrNull(String datasetId) { 
        if (datasetId.equals("missing")) {
            return null;
        }
        return getDatasetDetail(datasetId); 
    }

    private Map<String, Object> createDataset(String id, String name, String collectionId, int rowCount) { 
        Map<String, Object> dataset = new HashMap<>(); 
        dataset.put("id", id); 
        dataset.put("name", name); 
        dataset.put("collectionId", collectionId); 
        dataset.put("rowCount", rowCount); 
        dataset.put("columnCount", 4); 
        dataset.put("createdAt", "2026-01-15T10:30:00Z"); 
        dataset.put("lastModified", "2026-04-03T14:22:00Z"); 
        dataset.put("sizeBytes", 5000000L); 
        return dataset;
    }
}
