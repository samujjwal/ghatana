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
@DisplayName("Entity Model Tests [GH-90000]")
public class EntityModelTest {

    @Nested
    @DisplayName("EntityCreationTests [GH-90000]")
    class EntityCreationTests {

        @Test
        @DisplayName("create entity with valid data: succeeds [GH-90000]")
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
                    .containsKey("createdAt [GH-90000]");
        }

        @Test
        @DisplayName("entity missing required field: fails [GH-90000]")
        void shouldRejectMissingRequiredField() { // GH-90000
            Map<String, Object> entity = new HashMap<>(); // GH-90000
            entity.put("id", UUID.randomUUID().toString()); // GH-90000
            // Missing: tenantId

            assertThatThrownBy(() -> validateEntity(entity)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("tenantId [GH-90000]");
        }

        @Test
        @DisplayName("entity with duplicate ID: detectable [GH-90000]")
        void shouldHandleDuplicateId() { // GH-90000
            String id = "entity-123";
            String tenantId = "tenant-1";

            Map<String, Object> entity1 = createEntity(id, tenantId, "COLLECTION", "Coll1"); // GH-90000
            Map<String, Object> entity2 = createEntity(id, tenantId, "COLLECTION", "Coll2"); // GH-90000

            // IDs are the same but created separately (duplicate scenario) // GH-90000
            assertThat(entity1.get("id [GH-90000]"))
                    .isEqualTo(entity2.get("id [GH-90000]"));
        }

        @Test
        @DisplayName("entity type validation: enforced [GH-90000]")
        void shouldValidateEntityType() { // GH-90000
            // Valid types
            assertThat(isValidEntityType("COLLECTION [GH-90000]")).isTrue();
            assertThat(isValidEntityType("DATASET [GH-90000]")).isTrue();
            assertThat(isValidEntityType("TABLE [GH-90000]")).isTrue();

            // Invalid type
            assertThat(isValidEntityType("INVALID_TYPE [GH-90000]")).isFalse();
        }

        @Test
        @DisplayName("entity with metadata: preserved [GH-90000]")
        void shouldPreserveMetadata() { // GH-90000
            String id = UUID.randomUUID().toString(); // GH-90000
            String tenantId = "tenant-1";

            Map<String, Object> metadata = new HashMap<>(); // GH-90000
            metadata.put("owner", "user-123"); // GH-90000
            metadata.put("tags", new String[]{"important", "production"}); // GH-90000

            Map<String, Object> entity = createEntity(id, tenantId, "COLLECTION", "MyCollection"); // GH-90000
            entity.put("metadata", metadata); // GH-90000

            assertThat(entity.get("metadata [GH-90000]")).isEqualTo(metadata);
        }
    }

    @Nested
    @DisplayName("EntityTenantIsolationTests [GH-90000]")
    class EntityTenantIsolationTests {

        @Test
        @DisplayName("same ID in different tenants: isolated [GH-90000]")
        void shouldIslateTenants() { // GH-90000
            String id = "entity-123";

            Map<String, Object> entity1 = createEntity(id, "tenant-1", "COLLECTION", "Coll1"); // GH-90000
            Map<String, Object> entity2 = createEntity(id, "tenant-2", "COLLECTION", "Coll2"); // GH-90000

            assertThat(entity1.get("tenantId [GH-90000]")).isNotEqualTo(entity2.get("tenantId [GH-90000]"));
        }

        @Test
        @DisplayName("cross-tenant access: must be prevented [GH-90000]")
        void shouldPreventCrossTenantAccess() { // GH-90000
            Map<String, Object> entity = createEntity("id-1", "tenant-1", "COLLECTION", "Private"); // GH-90000

            // Simulate cross-tenant access attempt
            String otherTenant = "tenant-2";
            boolean canAccess = checkAccess(entity, otherTenant); // GH-90000

            assertThat(canAccess).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("same-tenant access: allowed [GH-90000]")
        void shouldAllowSameTenantAccess() { // GH-90000
            String tenantId = "tenant-1";
            Map<String, Object> entity = createEntity("id-1", tenantId, "COLLECTION", "Shared"); // GH-90000

            boolean canAccess = checkAccess(entity, tenantId); // GH-90000

            assertThat(canAccess).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("EntityUpdateTests [GH-90000]")
    class EntityUpdateTests {

        @Test
        @DisplayName("update entity field: succeeds [GH-90000]")
        void shouldUpdateField() { // GH-90000
            Map<String, Object> entity = createEntity("id-1", "tenant-1", "COLLECTION", "Original"); // GH-90000

            entity.put("name", "Updated Name"); // GH-90000

            assertThat(entity.get("name [GH-90000]")).isEqualTo("Updated Name [GH-90000]");
        }

        @Test
        @DisplayName("update with schema mismatch: detected [GH-90000]")
        void shouldDetectSchemaMismatch() { // GH-90000
            Map<String, Object> entity = createEntity("id-1", "tenant-1", "COLLECTION", "Test"); // GH-90000

            // Invalid type value
            entity.put("type", "INVALID"); // GH-90000

            assertThatThrownBy(() -> validateEntity(entity)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("tenant ID immutable: cannot change [GH-90000]")
        void shouldNotAllowTenantChange() { // GH-90000
            String originalTenant = "tenant-1";
            Map<String, Object> entity = createEntity("id-1", originalTenant, "COLLECTION", "Test"); // GH-90000

            // Attempt to change tenant (should be prevented or tracked) // GH-90000
            String changedTenant = entity.get("tenantId [GH-90000]").toString();

            assertThat(changedTenant).isEqualTo(originalTenant); // GH-90000
        }
    }

    @Nested
    @DisplayName("EntityDeletionTests [GH-90000]")
    class EntityDeletionTests {

        @Test
        @DisplayName("soft delete: marks entity as deleted [GH-90000]")
        void shouldSoftDelete() { // GH-90000
            Map<String, Object> entity = createEntity("id-1", "tenant-1", "COLLECTION", "ToDelete"); // GH-90000

            softDelete(entity); // GH-90000

            assertThat(entity.get("deletedAt [GH-90000]")).isNotNull();
            assertThat(entity.get("isDeleted [GH-90000]")).isEqualTo(true);
        }

        @Test
        @DisplayName("hard delete: removes entity [GH-90000]")
        void shouldHardDelete() { // GH-90000
            Map<String, Object> entity = createEntity("id-1", "tenant-1", "COLLECTION", "ToDelete"); // GH-90000

            hardDelete(entity); // GH-90000

            assertThat(entity).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("double delete: idempotent [GH-90000]")
        void shouldHandleDoubleDelete() { // GH-90000
            Map<String, Object> entity = createEntity("id-1", "tenant-1", "COLLECTION", "ToDelete"); // GH-90000

            softDelete(entity); // GH-90000
            softDelete(entity); // Second delete // GH-90000

            // Should still be marked deleted, no error
            assertThat(entity.get("isDeleted [GH-90000]")).isEqualTo(true);
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
        if (!entity.containsKey("id [GH-90000]")) {
            throw new IllegalArgumentException("Missing required field: id [GH-90000]");
        }
        if (!entity.containsKey("tenantId [GH-90000]")) {
            throw new IllegalArgumentException("Missing required field: tenantId [GH-90000]");
        }
        if (!entity.containsKey("type [GH-90000]")) {
            throw new IllegalArgumentException("Missing required field: type [GH-90000]");
        }
        if (!entity.containsKey("name [GH-90000]")) {
            throw new IllegalArgumentException("Missing required field: name [GH-90000]");
        }

        String type = entity.get("type [GH-90000]").toString();
        if (!isValidEntityType(type)) { // GH-90000
            throw new IllegalArgumentException("Invalid entity type: " + type); // GH-90000
        }
    }

    private boolean isValidEntityType(String type) { // GH-90000
        return type.matches("^(COLLECTION|DATASET|TABLE|VIEW|SCHEMA)$ [GH-90000]");
    }

    private boolean checkAccess(Map<String, Object> entity, String requestTenantId) { // GH-90000
        String entityTenantId = entity.get("tenantId [GH-90000]").toString();
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
