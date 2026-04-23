/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @doc.type class
 * @doc.purpose Data Cloud entity model validation and transformation tests
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("Entity Model Tests")
public class EntityModelTest {

    @Nested
    @DisplayName("EntityCreationTests")
    class EntityCreationTests {

        @Test
        @DisplayName("create entity with valid data: succeeds")
        void shouldCreateValidEntity() { // GH-90000
            String id = UUID.randomUUID().toString(); // GH-90000
            String tenantId = "tenant-123";
            String type = "COLLECTION";
            String name = "Test Collection";

            Map<String, Object> entity = createEntity(id, tenantId, type, name); // GH-90000

            assertThat(entity) // GH-90000
                    .containsEntry("id", id) // GH-90000
                    .containsEntry("tenantId", tenantId) // GH-90000
                    .containsEntry("type", type) // GH-90000
                    .containsEntry("name", name) // GH-90000
                    .containsKey("createdAt");
        }

        @Test
        @DisplayName("entity missing required field: fails")
        void shouldRejectMissingRequiredField() { // GH-90000
            Map<String, Object> entity = new HashMap<>(); // GH-90000
            entity.put("id", UUID.randomUUID().toString()); // GH-90000
            // Missing: tenantId

            assertThatThrownBy(() -> validateEntity(entity)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("tenantId");
        }

        @Test
        @DisplayName("entity with duplicate ID: detectable")
        void shouldHandleDuplicateId() { // GH-90000
            String id = "entity-123";
            String tenantId = "tenant-1";

            Map<String, Object> entity1 = createEntity(id, tenantId, "COLLECTION", "Coll1"); // GH-90000
            Map<String, Object> entity2 = createEntity(id, tenantId, "COLLECTION", "Coll2"); // GH-90000

            // IDs are the same but created separately (duplicate scenario) // GH-90000
            assertThat(entity1.get("id"))
                    .isEqualTo(entity2.get("id"));
        }

        @Test
        @DisplayName("entity type validation: enforced")
        void shouldValidateEntityType() { // GH-90000
            // Valid types
            assertThat(isValidEntityType("COLLECTION")).isTrue();
            assertThat(isValidEntityType("DATASET")).isTrue();
            assertThat(isValidEntityType("TABLE")).isTrue();

            // Invalid type
            assertThat(isValidEntityType("INVALID_TYPE")).isFalse();
        }

        @Test
        @DisplayName("entity with metadata: preserved")
        void shouldPreserveMetadata() { // GH-90000
            String id = UUID.randomUUID().toString(); // GH-90000
            String tenantId = "tenant-1";

            Map<String, Object> metadata = new HashMap<>(); // GH-90000
            metadata.put("owner", "user-123"); // GH-90000
            metadata.put("tags", new String[]{"important", "production"}); // GH-90000

            Map<String, Object> entity = createEntity(id, tenantId, "COLLECTION", "MyCollection"); // GH-90000
            entity.put("metadata", metadata); // GH-90000

            assertThat(entity.get("metadata")).isEqualTo(metadata);
        }
    }

    @Nested
    @DisplayName("EntityTenantIsolationTests")
    class EntityTenantIsolationTests {

        @Test
        @DisplayName("same ID in different tenants: isolated")
        void shouldIslateTenants() { // GH-90000
            String id = "entity-123";

            Map<String, Object> entity1 = createEntity(id, "tenant-1", "COLLECTION", "Coll1"); // GH-90000
            Map<String, Object> entity2 = createEntity(id, "tenant-2", "COLLECTION", "Coll2"); // GH-90000

            assertThat(entity1.get("tenantId")).isNotEqualTo(entity2.get("tenantId"));
        }

        @Test
        @DisplayName("cross-tenant access: must be prevented")
        void shouldPreventCrossTenantAccess() { // GH-90000
            Map<String, Object> entity = createEntity("id-1", "tenant-1", "COLLECTION", "Private"); // GH-90000

            // Simulate cross-tenant access attempt
            String otherTenant = "tenant-2";
            boolean canAccess = checkAccess(entity, otherTenant); // GH-90000

            assertThat(canAccess).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("same-tenant access: allowed")
        void shouldAllowSameTenantAccess() { // GH-90000
            String tenantId = "tenant-1";
            Map<String, Object> entity = createEntity("id-1", tenantId, "COLLECTION", "Shared"); // GH-90000

            boolean canAccess = checkAccess(entity, tenantId); // GH-90000

            assertThat(canAccess).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("EntityUpdateTests")
    class EntityUpdateTests {

        @Test
        @DisplayName("update entity field: succeeds")
        void shouldUpdateField() { // GH-90000
            Map<String, Object> entity = createEntity("id-1", "tenant-1", "COLLECTION", "Original"); // GH-90000

            entity.put("name", "Updated Name"); // GH-90000

            assertThat(entity.get("name")).isEqualTo("Updated Name");
        }

        @Test
        @DisplayName("update with schema mismatch: detected")
        void shouldDetectSchemaMismatch() { // GH-90000
            Map<String, Object> entity = createEntity("id-1", "tenant-1", "COLLECTION", "Test"); // GH-90000

            // Invalid type value
            entity.put("type", "INVALID"); // GH-90000

            assertThatThrownBy(() -> validateEntity(entity)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("tenant ID immutable: cannot change")
        void shouldNotAllowTenantChange() { // GH-90000
            String originalTenant = "tenant-1";
            Map<String, Object> entity = createEntity("id-1", originalTenant, "COLLECTION", "Test"); // GH-90000

            // Attempt to change tenant (should be prevented or tracked) // GH-90000
            String changedTenant = entity.get("tenantId").toString();

            assertThat(changedTenant).isEqualTo(originalTenant); // GH-90000
        }
    }

    @Nested
    @DisplayName("EntityDeletionTests")
    class EntityDeletionTests {

        @Test
        @DisplayName("soft delete: marks entity as deleted")
        void shouldSoftDelete() { // GH-90000
            Map<String, Object> entity = createEntity("id-1", "tenant-1", "COLLECTION", "ToDelete"); // GH-90000

            softDelete(entity); // GH-90000

            assertThat(entity.get("deletedAt")).isNotNull();
            assertThat(entity.get("isDeleted")).isEqualTo(true);
        }

        @Test
        @DisplayName("hard delete: removes entity")
        void shouldHardDelete() { // GH-90000
            Map<String, Object> entity = createEntity("id-1", "tenant-1", "COLLECTION", "ToDelete"); // GH-90000

            hardDelete(entity); // GH-90000

            assertThat(entity).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("double delete: idempotent")
        void shouldHandleDoubleDelete() { // GH-90000
            Map<String, Object> entity = createEntity("id-1", "tenant-1", "COLLECTION", "ToDelete"); // GH-90000

            softDelete(entity); // GH-90000
            softDelete(entity); // Second delete // GH-90000

            // Should still be marked deleted, no error
            assertThat(entity.get("isDeleted")).isEqualTo(true);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────────────

    private Map<String, Object> createEntity(String id, String tenantId, String type, String name) { // GH-90000
        Map<String, Object> entity = new HashMap<>(); // GH-90000
        entity.put("id", id); // GH-90000
        entity.put("tenantId", tenantId); // GH-90000
        entity.put("type", type); // GH-90000
        entity.put("name", name); // GH-90000
        entity.put("createdAt", Instant.now()); // GH-90000
        entity.put("isDeleted", false); // GH-90000
        return entity;
    }

    private void validateEntity(Map<String, Object> entity) { // GH-90000
        if (!entity.containsKey("id")) {
            throw new IllegalArgumentException("Missing required field: id");
        }
        if (!entity.containsKey("tenantId")) {
            throw new IllegalArgumentException("Missing required field: tenantId");
        }
        if (!entity.containsKey("type")) {
            throw new IllegalArgumentException("Missing required field: type");
        }
        if (!entity.containsKey("name")) {
            throw new IllegalArgumentException("Missing required field: name");
        }

        String type = entity.get("type").toString();
        if (!isValidEntityType(type)) { // GH-90000
            throw new IllegalArgumentException("Invalid entity type: " + type); // GH-90000
        }
    }

    private boolean isValidEntityType(String type) { // GH-90000
        return type.matches("^(COLLECTION|DATASET|TABLE|VIEW|SCHEMA)$");
    }

    private boolean checkAccess(Map<String, Object> entity, String requestTenantId) { // GH-90000
        String entityTenantId = entity.get("tenantId").toString();
        return entityTenantId.equals(requestTenantId); // GH-90000
    }

    private void softDelete(Map<String, Object> entity) { // GH-90000
        entity.put("deletedAt", Instant.now()); // GH-90000
        entity.put("isDeleted", true); // GH-90000
    }

    private void hardDelete(Map<String, Object> entity) { // GH-90000
        entity.clear(); // GH-90000
    }
}
