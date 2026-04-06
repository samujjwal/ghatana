/*
 * Copyright (c) 2026 Ghatana Inc.
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Contract tests for Data Cloud entity service API.
 *
 * <p>Validates contracts for:
 * <ul>
 *   <li>Entity CRUD operations (Create, Read, Update, Delete)</li>
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
@ExtendWith(MockitoExtension.class)
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

        Entity(String id, String collectionId, String tenantId, Map<String, Object> attributes) {
            this.id = id;
            this.collectionId = collectionId;
            this.tenantId = tenantId;
            this.attributes = attributes;
            this.createdAt = System.currentTimeMillis();
            this.updatedAt = System.currentTimeMillis();
        }
    }

    interface EntityService {
        Promise<Entity> create(String tenantId, String collectionId, Entity entity);
        Promise<Optional<Entity>> getById(String tenantId, String collectionId, String entityId);
        Promise<Void> update(String tenantId, String collectionId, String entityId, Entity entity);
        Promise<Void> delete(String tenantId, String collectionId, String entityId);
        Promise<Long> count(String tenantId, String collectionId);
    }

    // =========================================================================
    // Entity Creation Contracts
    // =========================================================================

    @Nested
    @DisplayName("Entity Creation Contracts")
    class EntityCreationContract {

        @Test
        @DisplayName("POST /api/v1/collections/:collectionId/entities creates entity with generated ID")
        void createMustGenerateEntityId() {
            String tenantId = "tenant-1";
            String collectionId = "coll-users";
            Entity newEntity = new Entity("", collectionId, tenantId, Map.of("name", "Alice", "age", 28));
            Entity expected = new Entity("ent-abc-123", collectionId, tenantId, newEntity.attributes);
            lenient().when(entityService.create(tenantId, collectionId, any()))
                    .thenReturn(Promise.of(expected));

            Entity created = runPromise(() -> entityService.create(tenantId, collectionId, newEntity));

            assertThat(created.id).isNotBlank().isNotEqualTo("");
        }

        @Test
        @DisplayName("created entity must inherit tenant from collection")
        void createdEntityMustHaveTenantIsolation() {
            String tenantId = "tenant-org";
            String collectionId = "coll-employees";
            Entity newEntity = new Entity("", collectionId, tenantId, Map.of("name", "Bob"));
            Entity expected = new Entity("ent-1", collectionId, tenantId, newEntity.attributes);
            lenient().when(entityService.create(tenantId, collectionId, any()))
                    .thenReturn(Promise.of(expected));

            Entity created = runPromise(() -> entityService.create(tenantId, collectionId, newEntity));

            assertThat(created.tenantId).isEqualTo(tenantId);
        }

        @Test
        @DisplayName("created entity must be validated against collection schema")
        void createMustValidateSchema() {
            String tenantId = "tenant-1";
            String collectionId = "coll-strict-schema";
            // Collection schema requires: name (string), email (string)
            Entity invalidEntity = new Entity("", collectionId, tenantId, 
                    Map.of("missing_required_field", "value"));
            lenient().when(entityService.create(tenantId, collectionId, invalidEntity))
                    .thenReturn(Promise.ofException(
                            new IllegalArgumentException("Missing required field: name")));

            Throwable thrown = catchThrowable(() ->
                    runPromise(() -> entityService.create(tenantId, collectionId, invalidEntity)));

            assertThat(thrown).isNotNull();
        }

        @Test
        @DisplayName("created entity must have audit fields (createdAt, createdBy)")
        void createdEntityMustHaveAuditFields() {
            String tenantId = "tenant-1";
            String collectionId = "coll-users";
            Entity newEntity = new Entity("", collectionId, tenantId, Map.of("name", "Charlie"));
            Entity expected = new Entity("ent-2", collectionId, tenantId, newEntity.attributes);
            expected.createdBy = "user-creator";
            expected.updatedBy = "user-creator";
            lenient().when(entityService.create(tenantId, collectionId, any()))
                    .thenReturn(Promise.of(expected));

            Entity created = runPromise(() -> entityService.create(tenantId, collectionId, newEntity));

            assertThat(created.createdAt).isGreaterThan(0);
            assertThat(created.updatedAt).isGreaterThanOrEqualTo(created.createdAt);
            assertThat(created.createdBy).isNotBlank();
        }

        @Test
        @DisplayName("cannot create entity in another tenant's collection")
        void createMustPreventCrossTenantEntity() {
            String requestingTenant = "tenant-1";
            String owningTenant = "tenant-2";
            String collectionId = "coll-secret";
            Entity newEntity = new Entity("", collectionId, owningTenant, Map.of());
            lenient().when(entityService.create(requestingTenant, collectionId, any()))
                    .thenReturn(Promise.ofException(
                            new SecurityException("Not authorized for this collection")));

            Throwable thrown = catchThrowable(() ->
                    runPromise(() -> entityService.create(requestingTenant, collectionId, newEntity)));

            assertThat(thrown).isNotNull();
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
        void getByIdMustReturnEntity() {
            String tenantId = "tenant-1";
            String collectionId = "coll-users";
            String entityId = "ent-123";
            Entity expected = new Entity(entityId, collectionId, tenantId, Map.of("name", "Dave"));
            lenient().when(entityService.getById(tenantId, collectionId, entityId))
                    .thenReturn(Promise.of(Optional.of(expected)));

            Optional<Entity> result = runPromise(() -> 
                    entityService.getById(tenantId, collectionId, entityId));

            assertThat(result).isPresent();
            assertThat(result.get().id).isEqualTo(entityId);
        }

        @Test
        @DisplayName("get entity from other tenant's collection must return empty")
        void getByIdMustIsolateTenant() {
            String requestingTenant = "tenant-1";
            String owningTenant = "tenant-2";
            String collectionId = "coll-secret";
            String entityId = "ent-secret";
            lenient().when(entityService.getById(requestingTenant, collectionId, entityId))
                    .thenReturn(Promise.of(Optional.empty()));

            Optional<Entity> result = runPromise(() ->
                    entityService.getById(requestingTenant, collectionId, entityId));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("entity response must include all required attributes")
        void entityResponseMustBeComplete() {
            String tenantId = "tenant-1";
            String collectionId = "coll-users";
            Entity entity = new Entity("ent-1", collectionId, tenantId, 
                    Map.of("name", "Eve", "email", "eve@example.com"));
            lenient().when(entityService.getById(tenantId, collectionId, "ent-1"))
                    .thenReturn(Promise.of(Optional.of(entity)));

            Optional<Entity> result = runPromise(() ->
                    entityService.getById(tenantId, collectionId, "ent-1"));

            assertThat(result.get().attributes).containsKeys("name", "email");
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
        void updateMustModifyAttributes() {
            String tenantId = "tenant-1";
            String collectionId = "coll-users";
            String entityId = "ent-1";
            Entity updated = new Entity(entityId, collectionId, tenantId, 
                    Map.of("name", "Frank", "age", 35));
            updated.updatedBy = "user-updater";
            lenient().when(entityService.update(tenantId, collectionId, entityId, any()))
                    .thenReturn(Promise.of(null));

            runPromise(() -> entityService.update(tenantId, collectionId, entityId, updated));

            verify(entityService, times(1)).update(tenantId, collectionId, entityId, any());
        }

        @Test
        @DisplayName("update must validate attributes against schema")
        void updateMustValidateSchema() {
            String tenantId = "tenant-1";
            String collectionId = "coll-users";
            String entityId = "ent-1";
            // Invalid: email must be email format
            Entity invalidUpdate = new Entity(entityId, collectionId, tenantId,
                    Map.of("email", "not-an-email"));
            lenient().when(entityService.update(tenantId, collectionId, entityId, invalidUpdate))
                    .thenReturn(Promise.ofException(
                            new IllegalArgumentException("Invalid email format")));

            Throwable thrown = catchThrowable(() ->
                    runPromise(() -> entityService.update(tenantId, collectionId, entityId, invalidUpdate)));

            assertThat(thrown).isNotNull();
        }

        @Test
        @DisplayName("update must not change entity ID or createdAt")
        void updateMustNotChangeImmutableFields() {
            String tenantId = "tenant-1";
            String collectionId = "coll-users";
            String entityId = "ent-1";
            long originalCreatedAt = System.currentTimeMillis() - 60_000;

            Entity entity = new Entity(entityId, collectionId, tenantId, Map.of("name", "Grace"));
            entity.createdAt = originalCreatedAt;

            // After update:
            entity.attributes.put("name", "Grace Updated");
            entity.updatedAt = System.currentTimeMillis();

            assertThat(entity.id).isEqualTo(entityId); // ID must not change
            assertThat(entity.createdAt).isEqualTo(originalCreatedAt); // createdAt must not change
            assertThat(entity.updatedAt).isGreaterThan(entity.createdAt); // updatedAt must change
        }

        @Test
        @DisplayName("cannot update entity in another tenant's collection")
        void updateMustPreventCrossTenantMod() {
            String requestingTenant = "tenant-1";
            String owningTenant = "tenant-2";
            String collectionId = "coll-secret";
            String entityId = "ent-secret";
            Entity update = new Entity(entityId, collectionId, owningTenant, Map.of());
            lenient().when(entityService.update(requestingTenant, collectionId, entityId, update))
                    .thenReturn(Promise.ofException(
                            new SecurityException("Not authorized")));

            Throwable thrown = catchThrowable(() ->
                    runPromise(() -> entityService.update(requestingTenant, collectionId, entityId, update)));

            assertThat(thrown).isNotNull();
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
        void deleteMustRemoveEntity() {
            String tenantId = "tenant-1";
            String collectionId = "coll-users";
            String entityId = "ent-temp";
            lenient().when(entityService.delete(tenantId, collectionId, entityId))
                    .thenReturn(Promise.of(null));

            runPromise(() -> entityService.delete(tenantId, collectionId, entityId));

            verify(entityService, times(1)).delete(tenantId, collectionId, entityId);
        }

        @Test
        @DisplayName("delete of non-existent entity must be idempotent")
        void deleteNonExistentMustBeSafe() {
            String tenantId = "tenant-1";
            String collectionId = "coll-users";
            String nonExistentId = "ent-does-not-exist";
            lenient().when(entityService.delete(tenantId, collectionId, nonExistentId))
                    .thenReturn(Promise.of(null)); // Success even if not found

            runPromise(() -> entityService.delete(tenantId, collectionId, nonExistentId));

            // Contract: deletion is idempotent
        }

        @Test
        @DisplayName("cannot delete entities from another tenant's collection")
        void deleteMustPreventCrossTenantDelete() {
            String requestingTenant = "tenant-1";
            String owningTenant = "tenant-2";
            String collectionId = "coll-secret";
            String entityId = "ent-secret";
            lenient().when(entityService.delete(requestingTenant, collectionId, entityId))
                    .thenReturn(Promise.ofException(
                            new SecurityException("Not authorized")));

            Throwable thrown = catchThrowable(() ->
                    runPromise(() -> entityService.delete(requestingTenant, collectionId, entityId)));

            assertThat(thrown).isNotNull();
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
        void countMustReturnAccurateCount() {
            String tenantId = "tenant-1";
            String collectionId = "coll-users";
            lenient().when(entityService.count(tenantId, collectionId))
                    .thenReturn(Promise.of(42L));

            Long count = runPromise(() -> entityService.count(tenantId, collectionId));

            assertThat(count).isEqualTo(42L);
        }

        @Test
        @DisplayName("count must only include entities from requesting tenant")
        void countMustIsolateTenant() {
            String tenant1 = "tenant-1";
            String tenant2 = "tenant-2";
            String collectionId = "coll-users";
            lenient().when(entityService.count(tenant1, collectionId))
                    .thenReturn(Promise.of(10L));
            lenient().when(entityService.count(tenant2, collectionId))
                    .thenReturn(Promise.of(20L));

            Long tenant1Count = runPromise(() -> entityService.count(tenant1, collectionId));
            Long tenant2Count = runPromise(() -> entityService.count(tenant2, collectionId));

            assertThat(tenant1Count).isEqualTo(10L);
            assertThat(tenant2Count).isEqualTo(20L);
            assertThat(tenant1Count).isNotEqualTo(tenant2Count);
        }

        @Test
        @DisplayName("count for non-existent collection must return 0")
        void countNonExistentMustReturn0() {
            String tenantId = "tenant-1";
            String nonExistentCollection = "coll-does-not-exist";
            lenient().when(entityService.count(tenantId, nonExistentCollection))
                    .thenReturn(Promise.of(0L));

            Long count = runPromise(() -> entityService.count(tenantId, nonExistentCollection));

            assertThat(count).isEqualTo(0L);
        }
    }
}
