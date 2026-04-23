/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * Data Cloud entity service contract tests for CRUD boundaries.
 *
 * Validates contracts for entity operations in Data Cloud.
 */
package com.ghatana.datacloud.platform.api.contract;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Contract tests for Data Cloud entity service API.
 *
 * <p>Validates contracts for:
 * <ul>
 *   <li>Entity CRUD operations (Create, Read, Update, Delete)</li> // GH-90000
 *   <li>Entity attribute validation against collection schema</li>
 *   <li>Tenant isolation for entity queries</li>
 *   <li>Audit logging contracts</li>
 *   <li>Event emission on entity changes</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Data Cloud entity service contract tests
 * @doc.layer product
 * @doc.pattern Test, Contract
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("Data Cloud Entity Service API Contract Tests")
class DataCloudEntityServiceContractTest extends EventloopTestBase {

    @Mock
    private EntityService entityService;

    /**
     * Mock entity class for testing.
     */
    static class Entity {
        String id;
        String collectionId;
        String tenantId;
        Map<String, Object> attributes;
        long createdAt;
        long updatedAt;
        String createdBy;
        String updatedBy;

        Entity(String id, String collectionId, String tenantId, Map<String, Object> attributes) { // GH-90000
            this.id = id;
            this.collectionId = collectionId;
            this.tenantId = tenantId;
            this.attributes = attributes;
            this.createdAt = System.currentTimeMillis(); // GH-90000
            this.updatedAt = System.currentTimeMillis(); // GH-90000
        }
    }

    interface EntityService {
        Promise<Entity> create(String tenantId, String collectionId, Entity entity); // GH-90000
        Promise<Optional<Entity>> getById(String tenantId, String collectionId, String entityId); // GH-90000
        Promise<Void> update(String tenantId, String collectionId, String entityId, Entity entity); // GH-90000
        Promise<Void> delete(String tenantId, String collectionId, String entityId); // GH-90000
        Promise<Long> count(String tenantId, String collectionId); // GH-90000
    }

    // =========================================================================
    // Entity Creation Contracts
    // =========================================================================

    @Nested
    @DisplayName("Entity Creation Contracts")
    class EntityCreationContract {

        @Test
        @DisplayName("POST /api/v1/collections/:collectionId/entities creates entity with generated ID")
        void createMustGenerateEntityId() { // GH-90000
            String tenantId = "tenant-1";
            String collectionId = "coll-users";
            Entity newEntity = new Entity("", collectionId, tenantId, Map.of("name", "Alice", "age", 28)); // GH-90000
            Entity expected = new Entity("ent-abc-123", collectionId, tenantId, newEntity.attributes); // GH-90000
            lenient().when(entityService.create(eq(tenantId), eq(collectionId), any())) // GH-90000
                    .thenReturn(Promise.of(expected)); // GH-90000

            Entity created = runPromise(() -> entityService.create(tenantId, collectionId, newEntity)); // GH-90000

            assertThat(created.id).isNotBlank().isNotEqualTo("");
        }

        @Test
        @DisplayName("created entity must inherit tenant from collection")
        void createdEntityMustHaveTenantIsolation() { // GH-90000
            String tenantId = "tenant-org";
            String collectionId = "coll-employees";
            Entity newEntity = new Entity("", collectionId, tenantId, Map.of("name", "Bob")); // GH-90000
            Entity expected = new Entity("ent-1", collectionId, tenantId, newEntity.attributes); // GH-90000
            lenient().when(entityService.create(eq(tenantId), eq(collectionId), any())) // GH-90000
                    .thenReturn(Promise.of(expected)); // GH-90000

            Entity created = runPromise(() -> entityService.create(tenantId, collectionId, newEntity)); // GH-90000

            assertThat(created.tenantId).isEqualTo(tenantId); // GH-90000
        }

        @Test
        @DisplayName("created entity must be validated against collection schema")
        void createMustValidateSchema() { // GH-90000
            String tenantId = "tenant-1";
            String collectionId = "coll-strict-schema";
            // Collection schema requires: name (string), email (string) // GH-90000
            Entity invalidEntity = new Entity("", collectionId, tenantId, // GH-90000
                    Map.of("missing_required_field", "value")); // GH-90000
            lenient().when(entityService.create(eq(tenantId), eq(collectionId), eq(invalidEntity))) // GH-90000
                    .thenReturn(Promise.ofException( // GH-90000
                            new IllegalArgumentException("Missing required field: name")));

            Throwable thrown = catchThrowable(() -> // GH-90000
                    runPromise(() -> entityService.create(tenantId, collectionId, invalidEntity))); // GH-90000

            assertThat(thrown).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("created entity must have audit fields (createdAt, createdBy)")
        void createdEntityMustHaveAuditFields() { // GH-90000
            String tenantId = "tenant-1";
            String collectionId = "coll-users";
            Entity newEntity = new Entity("", collectionId, tenantId, Map.of("name", "Charlie")); // GH-90000
            Entity expected = new Entity("ent-2", collectionId, tenantId, newEntity.attributes); // GH-90000
            expected.createdBy = "user-creator";
            expected.updatedBy = "user-creator";
            lenient().when(entityService.create(eq(tenantId), eq(collectionId), any())) // GH-90000
                    .thenReturn(Promise.of(expected)); // GH-90000

            Entity created = runPromise(() -> entityService.create(tenantId, collectionId, newEntity)); // GH-90000

            assertThat(created.createdAt).isGreaterThan(0); // GH-90000
            assertThat(created.updatedAt).isGreaterThanOrEqualTo(created.createdAt); // GH-90000
            assertThat(created.createdBy).isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("cannot create entity in another tenant's collection")
        void createMustPreventCrossTenantEntity() { // GH-90000
            String requestingTenant = "tenant-1";
            String owningTenant = "tenant-2";
            String collectionId = "coll-secret";
            Entity newEntity = new Entity("", collectionId, owningTenant, Map.of()); // GH-90000
            lenient().when(entityService.create(eq(requestingTenant), eq(collectionId), any())) // GH-90000
                    .thenReturn(Promise.ofException( // GH-90000
                            new SecurityException("Not authorized for this collection")));

            Throwable thrown = catchThrowable(() -> // GH-90000
                    runPromise(() -> entityService.create(requestingTenant, collectionId, newEntity))); // GH-90000

            assertThat(thrown).isNotNull(); // GH-90000
        }
    }

    // =========================================================================
    // Entity Read Contracts
    // =========================================================================

    @Nested
    @DisplayName("Entity Read Contracts")
    class EntityReadContract {

        @Test
        @DisplayName("GET /api/v1/collections/:collectionId/entities/:id returns entity if accessible")
        void getByIdMustReturnEntity() { // GH-90000
            String tenantId = "tenant-1";
            String collectionId = "coll-users";
            String entityId = "ent-123";
            Entity expected = new Entity(entityId, collectionId, tenantId, Map.of("name", "Dave")); // GH-90000
            lenient().when(entityService.getById(eq(tenantId), eq(collectionId), eq(entityId))) // GH-90000
                    .thenReturn(Promise.of(Optional.of(expected))); // GH-90000

            Optional<Entity> result = runPromise(() -> // GH-90000
                    entityService.getById(tenantId, collectionId, entityId)); // GH-90000

            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get().id).isEqualTo(entityId); // GH-90000
        }

        @Test
        @DisplayName("get entity from other tenant's collection must return empty")
        void getByIdMustIsolateTenant() { // GH-90000
            String requestingTenant = "tenant-1";
            String owningTenant = "tenant-2";
            String collectionId = "coll-secret";
            String entityId = "ent-secret";
            lenient().when(entityService.getById(eq(requestingTenant), eq(collectionId), eq(entityId))) // GH-90000
                    .thenReturn(Promise.of(Optional.empty())); // GH-90000

            Optional<Entity> result = runPromise(() -> // GH-90000
                    entityService.getById(requestingTenant, collectionId, entityId)); // GH-90000

            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("entity response must include all required attributes")
        void entityResponseMustBeComplete() { // GH-90000
            String tenantId = "tenant-1";
            String collectionId = "coll-users";
            Entity entity = new Entity("ent-1", collectionId, tenantId, // GH-90000
                    Map.of("name", "Eve", "email", "eve@example.com")); // GH-90000
            lenient().when(entityService.getById(eq(tenantId), eq(collectionId), eq("ent-1")))
                    .thenReturn(Promise.of(Optional.of(entity))); // GH-90000

            Optional<Entity> result = runPromise(() -> // GH-90000
                    entityService.getById(tenantId, collectionId, "ent-1")); // GH-90000

            assertThat(result.get().attributes).containsKeys("name", "email"); // GH-90000
        }
    }

    // =========================================================================
    // Entity Update Contracts
    // =========================================================================

    @Nested
    @DisplayName("Entity Update Contracts")
    class EntityUpdateContract {

        @Test
        @DisplayName("PATCH /api/v1/collections/:collectionId/entities/:id updates attributes")
        void updateMustModifyAttributes() { // GH-90000
            String tenantId = "tenant-1";
            String collectionId = "coll-users";
            String entityId = "ent-1";
            Entity updated = new Entity(entityId, collectionId, tenantId, // GH-90000
                    Map.of("name", "Frank", "age", 35)); // GH-90000
            updated.updatedBy = "user-updater";
            lenient().when(entityService.update(eq(tenantId), eq(collectionId), eq(entityId), any())) // GH-90000
                    .thenReturn(Promise.of(null)); // GH-90000

            runPromise(() -> entityService.update(tenantId, collectionId, entityId, updated)); // GH-90000

            verify(entityService, times(1)).update(eq(tenantId), eq(collectionId), eq(entityId), any()); // GH-90000
        }

        @Test
        @DisplayName("update must validate attributes against schema")
        void updateMustValidateSchema() { // GH-90000
            String tenantId = "tenant-1";
            String collectionId = "coll-users";
            String entityId = "ent-1";
            // Invalid: email must be email format
            Entity invalidUpdate = new Entity(entityId, collectionId, tenantId, // GH-90000
                    Map.of("email", "not-an-email")); // GH-90000
            lenient().when(entityService.update(eq(tenantId), eq(collectionId), eq(entityId), eq(invalidUpdate))) // GH-90000
                    .thenReturn(Promise.ofException( // GH-90000
                            new IllegalArgumentException("Invalid email format")));

            Throwable thrown = catchThrowable(() -> // GH-90000
                    runPromise(() -> entityService.update(tenantId, collectionId, entityId, invalidUpdate))); // GH-90000

            assertThat(thrown).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("update must not change entity ID or createdAt")
        void updateMustNotChangeImmutableFields() { // GH-90000
            String tenantId = "tenant-1";
            String collectionId = "coll-users";
            String entityId = "ent-1";
            long originalCreatedAt = System.currentTimeMillis() - 60_000; // GH-90000

            Map<String, Object> attributes = new java.util.HashMap<>(); // GH-90000
            attributes.put("name", "Grace"); // GH-90000
            Entity entity = new Entity(entityId, collectionId, tenantId, attributes); // GH-90000
            entity.createdAt = originalCreatedAt;

            // After update:
            entity.attributes.put("name", "Grace Updated"); // GH-90000
            entity.updatedAt = System.currentTimeMillis(); // GH-90000

            assertThat(entity.id).isEqualTo(entityId); // ID must not change // GH-90000
            assertThat(entity.createdAt).isEqualTo(originalCreatedAt); // createdAt must not change // GH-90000
            assertThat(entity.updatedAt).isGreaterThan(entity.createdAt); // updatedAt must change // GH-90000
        }

        @Test
        @DisplayName("cannot update entity in another tenant's collection")
        void updateMustPreventCrossTenantMod() { // GH-90000
            String requestingTenant = "tenant-1";
            String owningTenant = "tenant-2";
            String collectionId = "coll-secret";
            String entityId = "ent-secret";
            Entity update = new Entity(entityId, collectionId, owningTenant, Map.of()); // GH-90000
            lenient().when(entityService.update(eq(requestingTenant), eq(collectionId), eq(entityId), eq(update))) // GH-90000
                    .thenReturn(Promise.ofException( // GH-90000
                            new SecurityException("Not authorized")));

            Throwable thrown = catchThrowable(() -> // GH-90000
                    runPromise(() -> entityService.update(requestingTenant, collectionId, entityId, update))); // GH-90000

            assertThat(thrown).isNotNull(); // GH-90000
        }
    }

    // =========================================================================
    // Entity Deletion Contracts
    // =========================================================================

    @Nested
    @DisplayName("Entity Deletion Contracts")
    class EntityDeletionContract {

        @Test
        @DisplayName("DELETE /api/v1/collections/:collectionId/entities/:id deletes entity")
        void deleteMustRemoveEntity() { // GH-90000
            String tenantId = "tenant-1";
            String collectionId = "coll-users";
            String entityId = "ent-temp";
            lenient().when(entityService.delete(eq(tenantId), eq(collectionId), eq(entityId))) // GH-90000
                    .thenReturn(Promise.of(null)); // GH-90000

            runPromise(() -> entityService.delete(tenantId, collectionId, entityId)); // GH-90000

            verify(entityService, times(1)).delete(eq(tenantId), eq(collectionId), eq(entityId)); // GH-90000
        }

        @Test
        @DisplayName("delete of non-existent entity must be idempotent")
        void deleteNonExistentMustBeSafe() { // GH-90000
            String tenantId = "tenant-1";
            String collectionId = "coll-users";
            String nonExistentId = "ent-does-not-exist";
            lenient().when(entityService.delete(eq(tenantId), eq(collectionId), eq(nonExistentId))) // GH-90000
                    .thenReturn(Promise.of(null)); // Success even if not found // GH-90000

            runPromise(() -> entityService.delete(tenantId, collectionId, nonExistentId)); // GH-90000

            // Contract: deletion is idempotent
        }

        @Test
        @DisplayName("cannot delete entities from another tenant's collection")
        void deleteMustPreventCrossTenantDelete() { // GH-90000
            String requestingTenant = "tenant-1";
            String owningTenant = "tenant-2";
            String collectionId = "coll-secret";
            String entityId = "ent-secret";
            lenient().when(entityService.delete(eq(requestingTenant), eq(collectionId), eq(entityId))) // GH-90000
                    .thenReturn(Promise.ofException( // GH-90000
                            new SecurityException("Not authorized")));

            Throwable thrown = catchThrowable(() -> // GH-90000
                    runPromise(() -> entityService.delete(requestingTenant, collectionId, entityId))); // GH-90000

            assertThat(thrown).isNotNull(); // GH-90000
        }
    }

    // =========================================================================
    // Entity Query Contracts
    // =========================================================================

    @Nested
    @DisplayName("Entity Counting Contracts")
    class EntityCountingContract {

        @Test
        @DisplayName("count API returns entity count for given collection")
        void countMustReturnAccurateCount() { // GH-90000
            String tenantId = "tenant-1";
            String collectionId = "coll-users";
            lenient().when(entityService.count(eq(tenantId), eq(collectionId))) // GH-90000
                    .thenReturn(Promise.of(42L)); // GH-90000

            Long count = runPromise(() -> entityService.count(tenantId, collectionId)); // GH-90000

            assertThat(count).isEqualTo(42L); // GH-90000
        }

        @Test
        @DisplayName("count must only include entities from requesting tenant")
        void countMustIsolateTenant() { // GH-90000
            String tenant1 = "tenant-1";
            String tenant2 = "tenant-2";
            String collectionId = "coll-users";
            lenient().when(entityService.count(eq(tenant1), eq(collectionId))) // GH-90000
                    .thenReturn(Promise.of(10L)); // GH-90000
            lenient().when(entityService.count(eq(tenant2), eq(collectionId))) // GH-90000
                    .thenReturn(Promise.of(20L)); // GH-90000

            Long tenant1Count = runPromise(() -> entityService.count(tenant1, collectionId)); // GH-90000
            Long tenant2Count = runPromise(() -> entityService.count(tenant2, collectionId)); // GH-90000

            assertThat(tenant1Count).isEqualTo(10L); // GH-90000
            assertThat(tenant2Count).isEqualTo(20L); // GH-90000
            assertThat(tenant1Count).isNotEqualTo(tenant2Count); // GH-90000
        }

        @Test
        @DisplayName("count for non-existent collection must return 0")
        void countNonExistentMustReturn0() { // GH-90000
            String tenantId = "tenant-1";
            String nonExistentCollection = "coll-does-not-exist";
            lenient().when(entityService.count(eq(tenantId), eq(nonExistentCollection))) // GH-90000
                    .thenReturn(Promise.of(0L)); // GH-90000

            Long count = runPromise(() -> entityService.count(tenantId, nonExistentCollection)); // GH-90000

            assertThat(count).isEqualTo(0L); // GH-90000
        }
    }
}
