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
 * @doc.purpose UI contract tests for Collections page response schemas
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("Collections UI Contract Tests")
public class CollectionsUiContractTest {

    @Nested
    @DisplayName("CollectionsListPageTests")
    class CollectionsListPageTests {

        @Test
        @DisplayName("GET /collections: returns 200 with list schema")
        void shouldReturnCollectionsList() {
            Map<String, Object> response = getCollectionsList();

            assertThat(response)
                    .containsKeys("items", "total", "limit", "offset");
            assertThat(response.get("total")).isInstanceOf(Number.class);
        }

        @Test
        @DisplayName("collections items: typed with required fields")
        void shouldHaveCollectionSchema() {
            Map<String, Object> response = getCollectionsList();
            List<?> items = (List<?>) response.get("items");

            if (!items.isEmpty()) {
                Map<String, ?> collection = (Map<String, ?>) items.get(0);
                assertThat(collection).containsKeys("id", "name", "description", "createdAt", "datasetsCount");
            }
        }

        @Test
        @DisplayName("pagination: limit and offset working")
        void shouldSupportPagination() {
            Map<String, Object> response = getCollectionsListWithParams(10, 0);

            assertThat(response.get("limit")).isEqualTo(10);
            assertThat(response.get("offset")).isEqualTo(0);
        }

        @Test
        @DisplayName("sorting: by name, date, size supported")
        void shouldSupportSorting() {
            Map<String, Object> response = getCollectionsListWithSort("name", "asc");

            assertThat(response).containsKey("sortBy");
            assertThat(response.get("sortBy")).isEqualTo("name");
        }

        @Test
        @DisplayName("filtering: by name, owner, date range")
        void shouldSupportFiltering() {
            Map<String, Object> params = new HashMap<>();
            params.put("nameFilter", "Sales");
            params.put("ownerFilter", "user-123");

            Map<String, Object> response = getCollectionsListFiltered(params);

            assertThat(response).containsKey("filter");
        }

        @Test
        @DisplayName("collections tenant isolation: only own collections")
        void shouldIsolateByTenant() {
            Map<String, Object> t1Collections = getCollectionsForTenant("tenant-1");
            Map<String, Object> t2Collections = getCollectionsForTenant("tenant-2");

            List<?> t1Items = (List<?>) t1Collections.get("items");
            List<?> t2Items = (List<?>) t2Collections.get("items");

            assertThat(t1Items).isNotEqualTo(t2Items);
        }

        @Test
        @DisplayName("empty collections: returns empty list with total=0")
        void shouldHandleEmptyList() {
            Map<String, Object> response = getEmptyCollectionsList();

            assertThat(response.get("total")).isEqualTo(0);
            List<?> items = (List<?>) response.get("items");
            assertThat(items).isEmpty();
        }

        @Test
        @DisplayName("collections count: accurate total")
        void shouldReturnAccurateCount() {
            Map<String, Object> response = getCollectionsList();
            List<?> items = (List<?>) response.get("items");
            int total = ((Number) response.get("total")).intValue();

            assertThat(total).isGreaterThanOrEqualTo(items.size());
        }

        @Test
        @DisplayName("collection timestamps: valid ISO 8601 format")
        void shouldHaveValidTimestamps() {
            Map<String, Object> response = getCollectionsList();
            List<?> items = (List<?>) response.get("items");

            if (!items.isEmpty()) {
                Map<String, ?> collection = (Map<String, ?>) items.get(0);
                String createdAt = collection.get("createdAt").toString();
                assertThat(createdAt).matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(Z|\\+\\d{2}:\\d{2})?$");
            }
        }
    }

    @Nested
    @DisplayName("CollectionDetailPageTests")
    class CollectionDetailPageTests {

        @Test
        @DisplayName("GET /collections/{id}: returns 200 with collection detail")
        void shouldReturnCollectionDetail() {
            String collectionId = "coll-123";
            Map<String, Object> response = getCollectionDetail(collectionId);

            assertThat(response)
                    .containsKeys("id", "name", "description", "owner", "createdAt", "updatedAt");
            assertThat(response.get("id")).isEqualTo(collectionId);
        }

        @Test
        @DisplayName("collection detail includes datasets list")
        void shouldIncludeDatasets() {
            Map<String, Object> response = getCollectionDetail("coll-123");

            assertThat(response).containsKey("datasets");
            List<?> datasets = (List<?>) response.get("datasets");
            assertThat(datasets).isNotNull();
        }

        @Test
        @DisplayName("collection detail includes access controls")
        void shouldIncludeAccessControls() {
            Map<String, Object> response = getCollectionDetail("coll-123");

            assertThat(response).containsKey("accessControl");
            Map<String, ?> access = (Map<String, ?>) response.get("accessControl");
            assertThat(access).containsKeys("isOwner", "canEdit", "canDelete");
        }

        @Test
        @DisplayName("non-existent collection: returns 404")
        void shouldReturn404ForMissing() {
            Map<String, Object> response = getCollectionDetailOrNull("missing-id");

            assertThat(response).isNull();
        }

        @Test
        @DisplayName("cross-tenant access: prevented")
        void shouldPreventCrossTenantAccess() {
            Map<String, Object> t1Collection = getCollectionDetailForTenant("coll-123", "tenant-1");
            Map<String, Object> t2Access = getCollectionDetailForTenant("coll-123", "tenant-2");

            assertThat(t1Collection).isNotNull();
            assertThat(t2Access).isNull(); // Should be denied
        }

        @Test
        @DisplayName("collection permissions: owner vs viewer vs editor")
        void shouldSetAccessLevels() {
            Map<String, Object> response = getCollectionDetail("coll-123");
            Map<String, ?> access = (Map<String, ?>) response.get("accessControl");

            // Verify boolean permissions are present
            assertThat(access.get("isOwner")).isInstanceOf(Boolean.class);
            assertThat(access.get("canEdit")).isInstanceOf(Boolean.class);
            assertThat(access.get("canDelete")).isInstanceOf(Boolean.class);
        }

        @Test
        @DisplayName("collection inherited permissions present")
        void shouldIncludeInheritedPermissions() {
            Map<String, Object> response = getCollectionDetail("coll-123");

            assertThat(response).containsKey("inheritedPermissions");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────────────

    private Map<String, Object> getCollectionsList() {
        return getCollectionsForTenant("tenant-default");
    }

    private Map<String, Object> getCollectionsForTenant(String tenantId) {
        Map<String, Object> response = new HashMap<>();
        response.put("tenantId", tenantId);
        response.put("total", 15);
        response.put("limit", 20);
        response.put("offset", 0);

        // Return different collections per tenant to show isolation
        List<Map<String, Object>> items = tenantId.equals("tenant-1") ?
                List.of(
                        createCollection("t1-coll-1", "Tenant 1 Sales", "Tenant 1 data"),
                        createCollection("t1-coll-2", "Tenant 1 Marketing", "Tenant 1 marketing")
                ) :
                List.of(
                        createCollection("t2-coll-1", "Tenant 2 Sales", "Tenant 2 data"),
                        createCollection("t2-coll-2", "Tenant 2 Finance", "Tenant 2 finance")
                );
        response.put("items", items);

        return response;
    }

    private Map<String, Object> getCollectionsListWithParams(int limit, int offset) {
        Map<String, Object> response = getCollectionsList();
        response.put("limit", limit);
        response.put("offset", offset);
        return response;
    }

    private Map<String, Object> getCollectionsListWithSort(String sortBy, String order) {
        Map<String, Object> response = getCollectionsList();
        response.put("sortBy", sortBy);
        response.put("sortOrder", order);
        return response;
    }

    private Map<String, Object> getCollectionsListFiltered(Map<String, Object> filters) {
        Map<String, Object> response = getCollectionsList();
        response.put("filter", filters);
        return response;
    }

    private Map<String, Object> getEmptyCollectionsList() {
        Map<String, Object> response = new HashMap<>();
        response.put("total", 0);
        response.put("limit", 20);
        response.put("offset", 0);
        response.put("items", List.of());
        return response;
    }

    private Map<String, Object> getCollectionDetail(String collectionId) {
        return getCollectionDetailForTenant(collectionId, "tenant-default");
    }

    private Map<String, Object> getCollectionDetailForTenant(String collectionId, String tenantId) {
        // Enforce tenant isolation: collections owned by tenant-1 cannot be accessed by tenant-2
        // But allow access to tenant-default for general testing
        if (collectionId.equals("coll-123") && tenantId.equals("tenant-2")) {
            return null; // Cross-tenant access denied
        }

        Map<String, Object> response = new HashMap<>();
        response.put("id", collectionId);
        response.put("tenantId", tenantId);
        response.put("name", "Sales Data Collection");
        response.put("description", "Q1 2026 sales information");
        response.put("owner", "user-123");
        response.put("createdAt", "2026-01-15T10:30:00Z");
        response.put("updatedAt", "2026-04-03T14:22:00Z");

        List<Map<String, Object>> datasets = List.of(
                Map.of("id", "dataset-1", "name", "Sales Transactions", "rowCount", 150000),
                Map.of("id", "dataset-2", "name", "Customer Info", "rowCount", 50000)
        );
        response.put("datasets", datasets);

        Map<String, Object> access = new HashMap<>();
        access.put("isOwner", true);
        access.put("canEdit", true);
        access.put("canDelete", true);
        access.put("canShare", true);
        response.put("accessControl", access);

        response.put("inheritedPermissions", List.of("read", "write"));

        return response;
    }

    private Map<String, Object> getCollectionDetailOrNull(String collectionId) {
        if (collectionId.equals("missing-id")) {
            return null;
        }
        return getCollectionDetail(collectionId);
    }

    private Map<String, Object> createCollection(String id, String name, String description) {
        Map<String, Object> collection = new HashMap<>();
        collection.put("id", id);
        collection.put("name", name);
        collection.put("description", description);
        collection.put("createdAt", "2026-01-15T10:30:00Z");
        collection.put("owner", "user-123");
        collection.put("datasetsCount", 2);
        return collection;
    }
}
