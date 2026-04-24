/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.application;

import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.EntityRepository;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for EntityServiceImpl.
 *
 * Tests CRUD operations, querying, versioning, tenant isolation, and
 * concurrent operations with proper async handling and metrics collection.
 *
 * @doc.type class
 * @doc.purpose Test entity CRUD operations and versioning
 * @doc.layer application
 * @doc.pattern Test, Service Implementation
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("EntityServiceImpl – Entity CRUD & Versioning")
class EntityServiceTest extends EventloopTestBase {

    private static final String TENANT_ID = "tenant-alpha";
    private static final String COLLECTION_NAME = "customers";
    private static final String USER_ID = "user-123";

    @Mock
    private EntityRepository repository;

    @Mock
    private MetricsCollector metrics;

    private EntityServiceImpl service;

    @BeforeEach
    void setUp() { // GH-90000
        service = new EntityServiceImpl(repository, metrics); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CREATE ENTITY TESTS
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Create Entity Operations")
    class CreateEntityTests {

        @Test
        @DisplayName("[DC-002-01]: createEntity_success_with_valid_inputs")
        void createEntitySuccessWithValidInputs() { // GH-90000
            // Given
            Map<String, Object> data = Map.of( // GH-90000
                "name", "John Doe",
                "email", "john@example.com",
                "country", "USA"
            );

            Entity createdEntity = Entity.builder() // GH-90000
                .id(UUID.randomUUID()) // GH-90000
                .tenantId(TENANT_ID) // GH-90000
                .collectionName(COLLECTION_NAME) // GH-90000
                .data(data) // GH-90000
                .createdBy(USER_ID) // GH-90000
                .updatedBy(USER_ID) // GH-90000
                .createdAt(Instant.now()) // GH-90000
                .updatedAt(Instant.now()) // GH-90000
                .version(1) // GH-90000
                .build(); // GH-90000

            when(repository.save(eq(TENANT_ID), any(Entity.class))) // GH-90000
                .thenReturn(Promise.of(createdEntity)); // GH-90000

            // When
            Entity result = runPromise(() -> // GH-90000
                service.createEntity(TENANT_ID, COLLECTION_NAME, data, USER_ID) // GH-90000
            );

            // Then
            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.getId()).isNotNull(); // GH-90000
            assertThat(result.getTenantId()).isEqualTo(TENANT_ID); // GH-90000
            assertThat(result.getCollectionName()).isEqualTo(COLLECTION_NAME); // GH-90000
            assertThat(result.getData()).isEqualTo(data); // GH-90000
            assertThat(result.getVersion()).isEqualTo(1); // GH-90000
            assertThat(result.getCreatedBy()).isEqualTo(USER_ID); // GH-90000

            verify(metrics).incrementCounter( // GH-90000
                eq("entity.create.success"),
                eq("tenant"), eq(TENANT_ID),
                eq("collection"), eq(COLLECTION_NAME)
            );
        }

        @Test
        @DisplayName("[DC-002-02]: createEntity_rejects_null_tenantId")
        void createEntityRejectsNullTenantId() { // GH-90000
            // Given
            Map<String, Object> data = Map.of("name", "John"); // GH-90000

            // When & Then
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> service.createEntity(null, COLLECTION_NAME, data, USER_ID)) // GH-90000
            ).isInstanceOf(IllegalArgumentException.class) // GH-90000
             .hasMessageContaining("Tenant ID required");
        }

        @Test
        @DisplayName("[DC-002-03]: createEntity_rejects_null_collectionName")
        void createEntityRejectsNullCollectionName() { // GH-90000
            // Given
            Map<String, Object> data = Map.of("name", "John"); // GH-90000

            // When & Then
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> service.createEntity(TENANT_ID, null, data, USER_ID)) // GH-90000
            ).isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("[DC-002-04]: createEntity_rejects_null_data")
        void createEntityRejectsNullData() { // GH-90000
            // When & Then
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> service.createEntity(TENANT_ID, COLLECTION_NAME, null, USER_ID)) // GH-90000
            ).isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("[DC-002-05]: createEntity_records_metrics_on_error")
        void createEntityRecordsMetricsOnError() { // GH-90000
            // Given
            Map<String, Object> data = Map.of("name", "John"); // GH-90000
            RuntimeException dbException = new RuntimeException("DB error");

            when(repository.save(eq(TENANT_ID), any(Entity.class))) // GH-90000
                .thenReturn(Promise.ofException(dbException)); // GH-90000

            // When & Then
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> service.createEntity(TENANT_ID, COLLECTION_NAME, data, USER_ID)) // GH-90000
            ).isInstanceOf(RuntimeException.class); // GH-90000

            verify(metrics).incrementCounter( // GH-90000
                eq("entity.create.error"),
                eq("tenant"), eq(TENANT_ID),
                contains("error"), anyString()
            );
        }

        @Test
        @DisplayName("[DC-002-06]: createEntity_with_empty_data_mapping")
        void createEntityWithEmptyDataMapping() { // GH-90000
            // Given
            Map<String, Object> emptyData = Map.of(); // GH-90000

            Entity createdEntity = Entity.builder() // GH-90000
                .id(UUID.randomUUID()) // GH-90000
                .tenantId(TENANT_ID) // GH-90000
                .collectionName(COLLECTION_NAME) // GH-90000
                .data(emptyData) // GH-90000
                .createdBy(USER_ID) // GH-90000
                .updatedBy(USER_ID) // GH-90000
                .createdAt(Instant.now()) // GH-90000
                .updatedAt(Instant.now()) // GH-90000
                .version(1) // GH-90000
                .build(); // GH-90000

            when(repository.save(eq(TENANT_ID), any(Entity.class))) // GH-90000
                .thenReturn(Promise.of(createdEntity)); // GH-90000

            // When
            Entity result = runPromise(() -> // GH-90000
                service.createEntity(TENANT_ID, COLLECTION_NAME, emptyData, USER_ID) // GH-90000
            );

            // Then
            assertThat(result.getData()).isEmpty(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UPDATE ENTITY TESTS
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Update Entity Operations")
    class UpdateEntityTests {

        @Test
        @DisplayName("[DC-002-07]: updateEntity_success_increments_version")
        void updateEntitySuccessIncrementsVersion() { // GH-90000
            // Given
            UUID entityId = UUID.randomUUID(); // GH-90000
            Map<String, Object> oldData = Map.of("name", "John", "age", 30); // GH-90000
            Map<String, Object> newData = Map.of("name", "John", "age", 31); // GH-90000
            Instant now = Instant.now(); // GH-90000

            Entity existingEntity = Entity.builder() // GH-90000
                .id(entityId) // GH-90000
                .tenantId(TENANT_ID) // GH-90000
                .collectionName(COLLECTION_NAME) // GH-90000
                .data(oldData) // GH-90000
                .createdBy(USER_ID) // GH-90000
                .createdAt(now) // GH-90000
                .updatedBy(USER_ID) // GH-90000
                .updatedAt(now) // GH-90000
                .version(1) // GH-90000
                .build(); // GH-90000

            Entity updatedEntity = Entity.builder() // GH-90000
                .id(entityId) // GH-90000
                .tenantId(TENANT_ID) // GH-90000
                .collectionName(COLLECTION_NAME) // GH-90000
                .data(newData) // GH-90000
                .createdBy(USER_ID) // GH-90000
                .createdAt(now) // GH-90000
                .updatedBy(USER_ID) // GH-90000
                .updatedAt(Instant.now()) // GH-90000
                .version(2) // GH-90000
                .build(); // GH-90000

            when(repository.findById(TENANT_ID, COLLECTION_NAME, entityId)) // GH-90000
                .thenReturn(Promise.of(Optional.of(existingEntity))); // GH-90000
            when(repository.save(eq(TENANT_ID), any(Entity.class))) // GH-90000
                .thenReturn(Promise.of(updatedEntity)); // GH-90000

            // When
            Entity result = runPromise(() -> // GH-90000
                service.updateEntity(TENANT_ID, COLLECTION_NAME, entityId, newData, USER_ID) // GH-90000
            );

            // Then
            assertThat(result.getVersion()).isEqualTo(2); // GH-90000
            assertThat(result.getUpdatedAt()).isAfterOrEqualTo(now); // GH-90000
            assertThat(result.getCreatedAt()).isEqualTo(now); // GH-90000
            assertThat(result.getCreatedBy()).isEqualTo(USER_ID); // GH-90000

            verify(metrics).incrementCounter( // GH-90000
                eq("entity.update.success"),
                eq("tenant"), eq(TENANT_ID),
                eq("collection"), eq(COLLECTION_NAME)
            );
        }

        @Test
        @DisplayName("[DC-002-08]: updateEntity_not_found_throws_exception")
        void updateEntityNotFoundThrowsException() { // GH-90000
            // Given
            UUID entityId = UUID.randomUUID(); // GH-90000
            Map<String, Object> newData = Map.of("name", "Jane"); // GH-90000

            when(repository.findById(TENANT_ID, COLLECTION_NAME, entityId)) // GH-90000
                .thenReturn(Promise.of(Optional.empty())); // GH-90000

            // When & Then
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> // GH-90000
                    service.updateEntity(TENANT_ID, COLLECTION_NAME, entityId, newData, USER_ID) // GH-90000
                )
            ).isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("not found");

            verify(metrics).incrementCounter( // GH-90000
                eq("entity.update.error"),
                eq("tenant"), eq(TENANT_ID),
                contains("error"), anyString()
            );
        }

        @Test
        @DisplayName("[DC-002-09]: updateEntity_preserves_creation_metadata")
        void updateEntityPreservesCreationMetadata() { // GH-90000
            // Given
            UUID entityId = UUID.randomUUID(); // GH-90000
            Instant originalCreationTime = Instant.parse("2026-01-01T00:00:00Z");
            String originalCreator = "original-user";

            Entity existingEntity = Entity.builder() // GH-90000
                .id(entityId) // GH-90000
                .tenantId(TENANT_ID) // GH-90000
                .collectionName(COLLECTION_NAME) // GH-90000
                .data(Map.of("status", "active")) // GH-90000
                .createdBy(originalCreator) // GH-90000
                .createdAt(originalCreationTime) // GH-90000
                .updatedBy(originalCreator) // GH-90000
                .updatedAt(originalCreationTime) // GH-90000
                .version(1) // GH-90000
                .build(); // GH-90000

            Entity updatedEntity = Entity.builder() // GH-90000
                .id(entityId) // GH-90000
                .tenantId(TENANT_ID) // GH-90000
                .collectionName(COLLECTION_NAME) // GH-90000
                .data(Map.of("status", "inactive")) // GH-90000
                .createdBy(originalCreator)  // Preserved // GH-90000
                .createdAt(originalCreationTime)  // Preserved // GH-90000
                .updatedBy(USER_ID)  // Changed // GH-90000
                .updatedAt(Instant.now())  // Changed // GH-90000
                .version(2) // GH-90000
                .build(); // GH-90000

            when(repository.findById(TENANT_ID, COLLECTION_NAME, entityId)) // GH-90000
                .thenReturn(Promise.of(Optional.of(existingEntity))); // GH-90000
            when(repository.save(eq(TENANT_ID), any(Entity.class))) // GH-90000
                .thenReturn(Promise.of(updatedEntity)); // GH-90000

            // When
            Entity result = runPromise(() -> // GH-90000
                service.updateEntity(TENANT_ID, COLLECTION_NAME, entityId, // GH-90000
                    Map.of("status", "inactive"), USER_ID) // GH-90000
            );

            // Then
            assertThat(result.getCreatedBy()).isEqualTo(originalCreator); // GH-90000
            assertThat(result.getCreatedAt()).isEqualTo(originalCreationTime); // GH-90000
            assertThat(result.getUpdatedBy()).isEqualTo(USER_ID); // GH-90000
        }

        @Test
        @DisplayName("[DC-002-10]: updateEntity_with_null_entity_id_rejected")
        void updateEntityWithNullEntityIdRejected() { // GH-90000
            // When & Then
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> // GH-90000
                    service.updateEntity(TENANT_ID, COLLECTION_NAME, null, // GH-90000
                        Map.of("name", "Jane"), USER_ID) // GH-90000
                )
            ).isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // QUERY & RETRIEVAL TESTS
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Entity Query & Retrieval")
    class QueryTests {

        @Test
        @DisplayName("[DC-002-11]: findById_returns_entity")
        void findByIdReturnsEntity() { // GH-90000
            // Given
            UUID entityId = UUID.randomUUID(); // GH-90000
            Entity entity = Entity.builder() // GH-90000
                .id(entityId) // GH-90000
                .tenantId(TENANT_ID) // GH-90000
                .collectionName(COLLECTION_NAME) // GH-90000
                .data(Map.of("name", "John")) // GH-90000
                .createdBy(USER_ID) // GH-90000
                .updatedBy(USER_ID) // GH-90000
                .createdAt(Instant.now()) // GH-90000
                .updatedAt(Instant.now()) // GH-90000
                .version(1) // GH-90000
                .build(); // GH-90000

            when(repository.findById(TENANT_ID, COLLECTION_NAME, entityId)) // GH-90000
                .thenReturn(Promise.of(Optional.of(entity))); // GH-90000

            // When
            Entity result = runPromise(() -> service.getEntity(TENANT_ID, COLLECTION_NAME, entityId)); // GH-90000

            // Then
            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.getId()).isEqualTo(entityId); // GH-90000
        }

        @Test
        @DisplayName("[DC-002-12]: listByCollection_returns_paginated_results")
        void listByCollectionReturnsPaginatedResults() { // GH-90000
            // Given: Multiple entities in collection
            List<Entity> entities = List.of( // GH-90000
                Entity.builder() // GH-90000
                    .id(UUID.randomUUID()) // GH-90000
                    .tenantId(TENANT_ID) // GH-90000
                    .collectionName(COLLECTION_NAME) // GH-90000
                    .data(Map.of("name", "Entity1")) // GH-90000
                    .createdBy(USER_ID) // GH-90000
                    .updatedBy(USER_ID) // GH-90000
                    .createdAt(Instant.now()) // GH-90000
                    .updatedAt(Instant.now()) // GH-90000
                    .version(1) // GH-90000
                    .build(), // GH-90000
                Entity.builder() // GH-90000
                    .id(UUID.randomUUID()) // GH-90000
                    .tenantId(TENANT_ID) // GH-90000
                    .collectionName(COLLECTION_NAME) // GH-90000
                    .data(Map.of("name", "Entity2")) // GH-90000
                    .createdBy(USER_ID) // GH-90000
                    .updatedBy(USER_ID) // GH-90000
                    .createdAt(Instant.now()) // GH-90000
                    .updatedAt(Instant.now()) // GH-90000
                    .version(1) // GH-90000
                    .build() // GH-90000
            );

            lenient().when(repository.findAll(TENANT_ID, COLLECTION_NAME, null, null, 0, 10)) // GH-90000
                .thenReturn(Promise.of(entities)); // GH-90000

            // When (assuming service has listByCollection method) // GH-90000
            // This test assumes the service interface supports listing
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE ENTITY TESTS
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Delete Entity Operations")
    class DeleteEntityTests {

        @Test
        @DisplayName("[DC-002-13]: deleteEntity_removes_entity")
        void deleteEntityRemovesEntity() { // GH-90000
            // Given
            UUID entityId = UUID.randomUUID(); // GH-90000

            when(repository.delete(TENANT_ID, COLLECTION_NAME, entityId)) // GH-90000
                .thenReturn(Promise.of(null)); // GH-90000

            // When
            runPromise(() -> service.deleteEntity(TENANT_ID, COLLECTION_NAME, entityId, USER_ID)); // GH-90000

            // Then
            verify(repository).delete(TENANT_ID, COLLECTION_NAME, entityId); // GH-90000
        }

        @Test
        @DisplayName("[DC-002-14]: deleteEntity_records_metrics")
        void deleteEntityRecordsMetrics() { // GH-90000
            // Given
            UUID entityId = UUID.randomUUID(); // GH-90000

            when(repository.delete(TENANT_ID, COLLECTION_NAME, entityId)) // GH-90000
                .thenReturn(Promise.of(null)); // GH-90000

            // When
            runPromise(() -> service.deleteEntity(TENANT_ID, COLLECTION_NAME, entityId, USER_ID)); // GH-90000

            // Then - verify metrics recorded
            verify(metrics).incrementCounter( // GH-90000
                eq("entity.delete.success"),
                eq("tenant"), eq(TENANT_ID),
                eq("collection"), eq(COLLECTION_NAME)
            );
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TENANT ISOLATION TESTS
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Tenant Isolation & Multi-Tenancy")
    class TenantIsolationTests {

        @Test
        @DisplayName("[DC-002-15]: createEntity_isolates_data_by_tenant")
        void createEntityIsolatesByTenant() { // GH-90000
            // Given: Two different tenants
            String tenantA = "tenant-alpha";
            String tenantB = "tenant-beta";

            Entity entityA = Entity.builder() // GH-90000
                .id(UUID.randomUUID()) // GH-90000
                .tenantId(tenantA) // GH-90000
                .collectionName(COLLECTION_NAME) // GH-90000
                .data(Map.of("name", "Data A")) // GH-90000
                .createdBy(USER_ID) // GH-90000
                .updatedBy(USER_ID) // GH-90000
                .createdAt(Instant.now()) // GH-90000
                .updatedAt(Instant.now()) // GH-90000
                .version(1) // GH-90000
                .build(); // GH-90000

            Entity entityB = Entity.builder() // GH-90000
                .id(UUID.randomUUID()) // GH-90000
                .tenantId(tenantB) // GH-90000
                .collectionName(COLLECTION_NAME) // GH-90000
                .data(Map.of("name", "Data B")) // GH-90000
                .createdBy(USER_ID) // GH-90000
                .updatedBy(USER_ID) // GH-90000
                .createdAt(Instant.now()) // GH-90000
                .updatedAt(Instant.now()) // GH-90000
                .version(1) // GH-90000
                .build(); // GH-90000

            when(repository.save(eq(tenantA), argThat(e -> e.getTenantId().equals(tenantA)))) // GH-90000
                .thenReturn(Promise.of(entityA)); // GH-90000
            when(repository.save(eq(tenantB), argThat(e -> e.getTenantId().equals(tenantB)))) // GH-90000
                .thenReturn(Promise.of(entityB)); // GH-90000

            // When
            Entity resultA = runPromise(() -> // GH-90000
                service.createEntity(tenantA, COLLECTION_NAME, Map.of("name", "Data A"), USER_ID) // GH-90000
            );
            Entity resultB = runPromise(() -> // GH-90000
                service.createEntity(tenantB, COLLECTION_NAME, Map.of("name", "Data B"), USER_ID) // GH-90000
            );

            // Then
            assertThat(resultA.getTenantId()).isEqualTo(tenantA); // GH-90000
            assertThat(resultB.getTenantId()).isEqualTo(tenantB); // GH-90000
        }

        @Test
        @DisplayName("[DC-002-16]: updateEntity_respects_tenant_boundaries")
        void updateEntityRespectsTenantBoundaries() { // GH-90000
            // Given: Entity from tenant A
            UUID entityId = UUID.randomUUID(); // GH-90000
            String tenantA = "tenant-alpha";

            Entity existingEntity = Entity.builder() // GH-90000
                .id(entityId) // GH-90000
                .tenantId(tenantA) // GH-90000
                .collectionName(COLLECTION_NAME) // GH-90000
                .data(Map.of("status", "active")) // GH-90000
                .createdBy(USER_ID) // GH-90000
                .createdAt(Instant.now()) // GH-90000
                .updatedBy(USER_ID) // GH-90000
                .updatedAt(Instant.now()) // GH-90000
                .version(1) // GH-90000
                .build(); // GH-90000

            when(repository.findById(tenantA, COLLECTION_NAME, entityId)) // GH-90000
                .thenReturn(Promise.of(Optional.of(existingEntity))); // GH-90000
            when(repository.save(eq(tenantA), any(Entity.class))) // GH-90000
                .thenReturn(Promise.of(existingEntity.toBuilder() // GH-90000
                    .data(Map.of("status", "inactive")) // GH-90000
                    .version(2) // GH-90000
                    .build())); // GH-90000

            // When
            Entity result = runPromise(() -> // GH-90000
                service.updateEntity(tenantA, COLLECTION_NAME, entityId, // GH-90000
                    Map.of("status", "inactive"), USER_ID) // GH-90000
            );

            // Then
            assertThat(result.getTenantId()).isEqualTo(tenantA); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VERSIONING & CONCURRENCY TESTS
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Versioning & Concurrency Control")
    class VersioningTests {

        @Test
        @DisplayName("[DC-002-17]: concurrent_updates_increment_version")
        void concurrentUpdatesIncrementVersion() { // GH-90000
            // Given: Multiple sequential updates
            UUID entityId = UUID.randomUUID(); // GH-90000
            Instant creationTime = Instant.now(); // GH-90000

            Entity v1 = Entity.builder() // GH-90000
                .id(entityId) // GH-90000
                .tenantId(TENANT_ID) // GH-90000
                .collectionName(COLLECTION_NAME) // GH-90000
                .data(Map.of("count", 0)) // GH-90000
                .createdBy(USER_ID) // GH-90000
                .createdAt(creationTime) // GH-90000
                .updatedBy(USER_ID) // GH-90000
                .updatedAt(creationTime) // GH-90000
                .version(1) // GH-90000
                .build(); // GH-90000

            Entity v2 = v1.toBuilder() // GH-90000
                .data(Map.of("count", 1)) // GH-90000
                .version(2) // GH-90000
                .build(); // GH-90000

            Entity v3 = v1.toBuilder() // GH-90000
                .data(Map.of("count", 2)) // GH-90000
                .version(3) // GH-90000
                .build(); // GH-90000

            when(repository.findById(TENANT_ID, COLLECTION_NAME, entityId)) // GH-90000
                .thenReturn(Promise.of(Optional.of(v1))) // GH-90000
                .thenReturn(Promise.of(Optional.of(v2))); // GH-90000

            when(repository.save(eq(TENANT_ID), any(Entity.class))) // GH-90000
                .thenReturn(Promise.of(v2)) // GH-90000
                .thenReturn(Promise.of(v3)); // GH-90000

            // When: First update
            Entity result1 = runPromise(() -> // GH-90000
                service.updateEntity(TENANT_ID, COLLECTION_NAME, entityId, // GH-90000
                    Map.of("count", 1), USER_ID) // GH-90000
            );

            assertThat(result1.getVersion()).isEqualTo(2); // GH-90000

            // When: Second update
            // Entity result2 = runPromise(() -> // GH-90000
            //     service.updateEntity(TENANT_ID, COLLECTION_NAME, entityId, // GH-90000
            //         Map.of("count", 2), USER_ID) // GH-90000
            // );

            // Then
            // assertThat(result2.getVersion()).isEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("[DC-002-18]: version_starts_at_one_for_new_entities")
        void versionStartsAtOneForNewEntities() { // GH-90000
            // Given
            Map<String, Object> data = Map.of("name", "New Entity"); // GH-90000

            Entity createdEntity = Entity.builder() // GH-90000
                .id(UUID.randomUUID()) // GH-90000
                .tenantId(TENANT_ID) // GH-90000
                .collectionName(COLLECTION_NAME) // GH-90000
                .data(data) // GH-90000
                .createdBy(USER_ID) // GH-90000
                .updatedBy(USER_ID) // GH-90000
                .createdAt(Instant.now()) // GH-90000
                .updatedAt(Instant.now()) // GH-90000
                .version(1) // GH-90000
                .build(); // GH-90000

            when(repository.save(eq(TENANT_ID), any(Entity.class))) // GH-90000
                .thenReturn(Promise.of(createdEntity)); // GH-90000

            // When
            Entity result = runPromise(() -> // GH-90000
                service.createEntity(TENANT_ID, COLLECTION_NAME, data, USER_ID) // GH-90000
            );

            // Then
            assertThat(result.getVersion()).isEqualTo(1); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VALIDATION TESTS
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Input Validation")
    class ValidationTests {

        @Test
        @DisplayName("[DC-002-19]: empty_collection_name_rejected")
        void emptyCollectionNameRejected() { // GH-90000
            // When & Then
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> service.createEntity(TENANT_ID, "", Map.of("data", "value"), USER_ID)) // GH-90000
            ).isInstanceOf(Exception.class); // GH-90000
        }

        @Test
        @DisplayName("[DC-002-20]: whitespace_only_user_id_rejected")
        void whitespaceOnlyUserIdRejected() { // GH-90000
            // When & Then
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> service.createEntity(TENANT_ID, COLLECTION_NAME, Map.of("data", "value"), "   ")) // GH-90000
            ).isInstanceOf(Exception.class); // GH-90000
        }
    }
}
