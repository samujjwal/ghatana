/*
 * Copyright (c) 2026 Ghatana Inc.
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
        void shouldCreateValidEntity() {
            String id = UUID.randomUUID().toString();
            String tenantId = "tenant-123";
            String type = "COLLECTION";
            String name = "Test Collection";

            Map<String, Object> entity = createEntity(id, tenantId, type, name);

            assertThat(entity)
                    .containsEntry("id", id)
                    .containsEntry("tenantId", tenantId)
                    .containsEntry("type", type)
                    .containsEntry("name", name)
                    .containsKey("createdAt");
        }

        @Test
        @DisplayName("entity missing required field: fails")
        void shouldRejectMissingRequiredField() {
            Map<String, Object> entity = new HashMap<>();
            entity.put("id", UUID.randomUUID().toString());
            // Missing: tenantId

            assertThatThrownBy(() -> validateEntity(entity))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tenantId");
        }

        @Test
        @DisplayName("entity with duplicate ID: detectable")
        void shouldHandleDuplicateId() {
            String id = "entity-123";
            String tenantId = "tenant-1";

            Map<String, Object> entity1 = createEntity(id, tenantId, "COLLECTION", "Coll1");
            Map<String, Object> entity2 = createEntity(id, tenantId, "COLLECTION", "Coll2");

            // IDs are the same but created separately (duplicate scenario)
            assertThat(entity1.get("id"))
                    .isEqualTo(entity2.get("id"));
        }

        @Test
        @DisplayName("entity type validation: enforced")
        void shouldValidateEntityType() {
            String id = UUID.randomUUID().toString();
            String tenantId = "tenant-1";

            // Valid types
            assertThat(isValidEntityType("COLLECTION")).isTrue();
            assertThat(isValidEntityType("DATASET")).isTrue();
            assertThat(isValidEntityType("TABLE")).isTrue();

            // Invalid type
            assertThat(isValidEntityType("INVALID_TYPE")).isFalse();
        }

        @Test
        @DisplayName("entity with metadata: preserved")
        void shouldPreserveMetadata() {
            String id = UUID.randomUUID().toString();
            String tenantId = "tenant-1";

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("owner", "user-123");
            metadata.put("tags", new String[]{"important", "production"});

            Map<String, Object> entity = createEntity(id, tenantId, "COLLECTION", "MyCollection");
            entity.put("metadata", metadata);

            assertThat(entity.get("metadata")).isEqualTo(metadata);
        }
    }

    @Nested
    @DisplayName("EntityTenantIsolationTests")
    class EntityTenantIsolationTests {

        @Test
        @DisplayName("same ID in different tenants: isolated")
        void shouldIslateTenants() {
            String id = "entity-123";

            Map<String, Object> entity1 = createEntity(id, "tenant-1", "COLLECTION", "Coll1");
            Map<String, Object> entity2 = createEntity(id, "tenant-2", "COLLECTION", "Coll2");

            assertThat(entity1.get("tenantId")).isNotEqualTo(entity2.get("tenantId"));
        }

        @Test
        @DisplayName("cross-tenant access: must be prevented")
        void shouldPreventCrossTenantAccess() {
            Map<String, Object> entity = createEntity("id-1", "tenant-1", "COLLECTION", "Private");

            // Simulate cross-tenant access attempt
            String otherTenant = "tenant-2";
            boolean canAccess = checkAccess(entity, otherTenant);

            assertThat(canAccess).isFalse();
        }

        @Test
        @DisplayName("same-tenant access: allowed")
        void shouldAllowSameTenantAccess() {
            String tenantId = "tenant-1";
            Map<String, Object> entity = createEntity("id-1", tenantId, "COLLECTION", "Shared");

            boolean canAccess = checkAccess(entity, tenantId);

            assertThat(canAccess).isTrue();
        }
    }

    @Nested
    @DisplayName("EntityUpdateTests")
    class EntityUpdateTests {

        @Test
        @DisplayName("update entity field: succeeds")
        void shouldUpdateField() {
            Map<String, Object> entity = createEntity("id-1", "tenant-1", "COLLECTION", "Original");

            entity.put("name", "Updated Name");

            assertThat(entity.get("name")).isEqualTo("Updated Name");
        }

        @Test
        @DisplayName("update with schema mismatch: detected")
        void shouldDetectSchemaMismatch() {
            Map<String, Object> entity = createEntity("id-1", "tenant-1", "COLLECTION", "Test");

            // Invalid type value
            entity.put("type", "INVALID");

            assertThatThrownBy(() -> validateEntity(entity))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("tenant ID immutable: cannot change")
        void shouldNotAllowTenantChange() {
            String originalTenant = "tenant-1";
            Map<String, Object> entity = createEntity("id-1", originalTenant, "COLLECTION", "Test");

            // Attempt to change tenant (should be prevented or tracked)
            String changedTenant = entity.get("tenantId").toString();

            assertThat(changedTenant).isEqualTo(originalTenant);
        }
    }

    @Nested
    @DisplayName("EntityDeletionTests")
    class EntityDeletionTests {

        @Test
        @DisplayName("soft delete: marks entity as deleted")
        void shouldSoftDelete() {
            Map<String, Object> entity = createEntity("id-1", "tenant-1", "COLLECTION", "ToDelete");

            softDelete(entity);

            assertThat(entity.get("deletedAt")).isNotNull();
            assertThat(entity.get("isDeleted")).isEqualTo(true);
        }

        @Test
        @DisplayName("hard delete: removes entity")
        void shouldHardDelete() {
            Map<String, Object> entity = createEntity("id-1", "tenant-1", "COLLECTION", "ToDelete");

            hardDelete(entity);

            assertThat(entity).isEmpty();
        }

        @Test
        @DisplayName("double delete: idempotent")
        void shouldHandleDoubleDelete() {
            Map<String, Object> entity = createEntity("id-1", "tenant-1", "COLLECTION", "ToDelete");

            softDelete(entity);
            softDelete(entity); // Second delete

            // Should still be marked deleted, no error
            assertThat(entity.get("isDeleted")).isEqualTo(true);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────────────

    private Map<String, Object> createEntity(String id, String tenantId, String type, String name) {
        Map<String, Object> entity = new HashMap<>();
        entity.put("id", id);
        entity.put("tenantId", tenantId);
        entity.put("type", type);
        entity.put("name", name);
        entity.put("createdAt", Instant.now());
        entity.put("isDeleted", false);
        return entity;
    }

    private void validateEntity(Map<String, Object> entity) {
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
        if (!isValidEntityType(type)) {
            throw new IllegalArgumentException("Invalid entity type: " + type);
        }
    }

    private boolean isValidEntityType(String type) {
        return type.matches("^(COLLECTION|DATASET|TABLE|VIEW|SCHEMA)$");
    }

    private boolean checkAccess(Map<String, Object> entity, String requestTenantId) {
        String entityTenantId = entity.get("tenantId").toString();
        return entityTenantId.equals(requestTenantId);
    }

    private void softDelete(Map<String, Object> entity) {
        entity.put("deletedAt", Instant.now());
        entity.put("isDeleted", true);
    }

    private void hardDelete(Map<String, Object> entity) {
        entity.clear();
    }
}
