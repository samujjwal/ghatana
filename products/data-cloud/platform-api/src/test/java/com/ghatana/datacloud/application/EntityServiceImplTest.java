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
import java.util.Optional;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * EntityServiceImpl Tests - 100% Coverage
 *
 * @doc.type class
 * @doc.purpose Comprehensive tests for EntityServiceImpl
 * @doc.layer test
 * @doc.pattern Unit Test
 */
@DisplayName("EntityServiceImpl Tests")
@ExtendWith(MockitoExtension.class) // GH-90000
class EntityServiceImplTest extends EventloopTestBase {

    @Mock
    private EntityRepository repository;

    @Mock
    private MetricsCollector metrics;

    private EntityServiceImpl service;

    @BeforeEach
    void setUp() { // GH-90000
        service = new EntityServiceImpl(repository, metrics); // GH-90000
    }

    @Nested
    @DisplayName("Create Entity")
    class CreateEntityTests {

        @Test
        @DisplayName("[TEST-001]: createEntity_successfully_creates_entity")
        void createEntitySuccessfully() { // GH-90000
            // Given
            String tenantId = "tenant-alpha";
            String collectionName = "customers";
            String userId = "user-123";
            Map<String, Object> data = Map.of("name", "John", "email", "john@example.com"); // GH-90000

            Entity savedEntity = Entity.builder() // GH-90000
                .id(UUID.randomUUID()) // GH-90000
                .tenantId(tenantId) // GH-90000
                .collectionName(collectionName) // GH-90000
                .data(data) // GH-90000
                .createdBy(userId) // GH-90000
                .updatedBy(userId) // GH-90000
                .createdAt(Instant.now()) // GH-90000
                .updatedAt(Instant.now()) // GH-90000
                .version(1) // GH-90000
                .build(); // GH-90000

            when(repository.save(eq(tenantId), any(Entity.class))).thenReturn(Promise.of(savedEntity)); // GH-90000

            // When
            Entity result = runPromise(() -> service.createEntity(tenantId, collectionName, data, userId)); // GH-90000

            // Then
            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.getTenantId()).isEqualTo(tenantId); // GH-90000
            assertThat(result.getCollectionName()).isEqualTo(collectionName); // GH-90000
            assertThat(result.getData()).isEqualTo(data); // GH-90000
            assertThat(result.getCreatedBy()).isEqualTo(userId); // GH-90000
            assertThat(result.getVersion()).isEqualTo(1); // GH-90000

            verify(metrics).incrementCounter("entity.create.success", "tenant", tenantId, "collection", collectionName); // GH-90000
        }

        @Test
        @DisplayName("[TEST-001]: createEntity_with_null_tenantId_throws_exception")
        void createEntityNullTenantId() { // GH-90000
            // Given
            String tenantId = null;
            String collectionName = "customers";
            String userId = "user-123";
            Map<String, Object> data = Map.of("name", "John"); // GH-90000

            // When/Then
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> { // GH-90000
                service.createEntity(tenantId, collectionName, data, userId); // GH-90000
            });
        }

        @Test
        @DisplayName("[TEST-001]: createEntity_with_empty_tenantId_throws_exception")
        void createEntityEmptyTenantId() { // GH-90000
            // Given
            String tenantId = "";
            String collectionName = "customers";
            String userId = "user-123";
            Map<String, Object> data = Map.of("name", "John"); // GH-90000

            // When/Then
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> { // GH-90000
                service.createEntity(tenantId, collectionName, data, userId); // GH-90000
            });
        }

        @Test
        @DisplayName("[TEST-001]: createEntity_repository_error_increments_error_metric")
        void createEntityRepositoryError() { // GH-90000
            // Given
            String tenantId = "tenant-alpha";
            String collectionName = "customers";
            String userId = "user-123";
            Map<String, Object> data = Map.of("name", "John"); // GH-90000

            when(repository.save(eq(tenantId), any(Entity.class))).thenReturn(Promise.ofException(new RuntimeException("DB error")));

            // When
            clearFatalError(); // GH-90000
            try {
                runPromise(() -> service.createEntity(tenantId, collectionName, data, userId)); // GH-90000
            } catch (Exception e) { // GH-90000
                // Expected
            }

            // Then
            verify(metrics).incrementCounter("entity.create.error", "tenant", tenantId, "error", "RuntimeException"); // GH-90000
        }
    }

    @Nested
    @DisplayName("Get Entity")
    class GetEntityTests {

        @Test
        @DisplayName("[TEST-002]: getEntity_returns_entity_when_found")
        void getEntityReturnsEntity() { // GH-90000
            // Given
            String tenantId = "tenant-alpha";
            String collectionName = "customers";
            UUID entityId = UUID.randomUUID(); // GH-90000

            Entity entity = Entity.builder() // GH-90000
                .id(entityId) // GH-90000
                .tenantId(tenantId) // GH-90000
                .collectionName(collectionName) // GH-90000
                .data(Map.of("name", "John")) // GH-90000
                .build(); // GH-90000

            when(repository.findById(tenantId, collectionName, entityId)).thenReturn(Promise.of(Optional.of(entity))); // GH-90000

            // When
            Entity result = runPromise(() -> service.getEntity(tenantId, collectionName, entityId)); // GH-90000

            // Then
            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.getId()).isEqualTo(entityId); // GH-90000
            verify(metrics).incrementCounter("entity.get.success", "tenant", tenantId, "collection", collectionName); // GH-90000
        }

        @Test
        @DisplayName("[TEST-002]: getEntity_returns_null_when_not_found")
        void getEntityNotFound() { // GH-90000
            // Given
            String tenantId = "tenant-alpha";
            String collectionName = "customers";
            UUID entityId = UUID.randomUUID(); // GH-90000

            when(repository.findById(tenantId, collectionName, entityId)).thenReturn(Promise.of(Optional.empty())); // GH-90000

            // When
            Entity result = runPromise(() -> service.getEntity(tenantId, collectionName, entityId)); // GH-90000

            // Then
            assertThat(result).isNull(); // GH-90000
            verify(metrics).incrementCounter("entity.get.not_found", "tenant", tenantId, "collection", collectionName); // GH-90000
        }
    }

    @Nested
    @DisplayName("Update Entity")
    class UpdateEntityTests {

        @Test
        @DisplayName("[TEST-003]: updateEntity_successfully_updates_entity")
        void updateEntitySuccessfully() { // GH-90000
            // Given
            String tenantId = "tenant-alpha";
            String collectionName = "customers";
            UUID entityId = UUID.randomUUID(); // GH-90000
            String userId = "user-123";

            Entity existingEntity = Entity.builder() // GH-90000
                .id(entityId) // GH-90000
                .tenantId(tenantId) // GH-90000
                .collectionName(collectionName) // GH-90000
                .data(Map.of("name", "John")) // GH-90000
                .createdBy("user-1")
                .createdAt(Instant.now()) // GH-90000
                .updatedBy("user-1")
                .updatedAt(Instant.now()) // GH-90000
                .version(1) // GH-90000
                .build(); // GH-90000

            Map<String, Object> newData = Map.of("name", "John Updated"); // GH-90000

            when(repository.findById(tenantId, collectionName, entityId)).thenReturn(Promise.of(Optional.of(existingEntity))); // GH-90000
            when(repository.save(eq(tenantId), any(Entity.class))).thenAnswer(invocation -> { // GH-90000
                Entity arg = invocation.getArgument(1); // GH-90000
                return Promise.of(arg); // GH-90000
            });

            // When
            Entity result = runPromise(() -> service.updateEntity(tenantId, collectionName, entityId, newData, userId)); // GH-90000

            // Then
            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.getVersion()).isEqualTo(2); // GH-90000
            assertThat(result.getUpdatedBy()).isEqualTo(userId); // GH-90000
            assertThat(result.getData()).isEqualTo(newData); // GH-90000
            verify(metrics).incrementCounter("entity.update.success", "tenant", tenantId, "collection", collectionName); // GH-90000
        }

        @Test
        @DisplayName("[TEST-003]: updateEntity_throws_when_entity_not_found")
        void updateEntityNotFound() { // GH-90000
            // Given
            String tenantId = "tenant-alpha";
            String collectionName = "customers";
            UUID entityId = UUID.randomUUID(); // GH-90000
            String userId = "user-123";
            Map<String, Object> data = Map.of("name", "John"); // GH-90000

            when(repository.findById(tenantId, collectionName, entityId)).thenReturn(Promise.of(Optional.empty())); // GH-90000

            // When
            clearFatalError(); // GH-90000
            try {
                runPromise(() -> service.updateEntity(tenantId, collectionName, entityId, data, userId)); // GH-90000
            } catch (Exception e) { // GH-90000
                // Expected
            }

            // Then
            verify(metrics).incrementCounter("entity.update.error", "tenant", tenantId, "error", "IllegalArgumentException"); // GH-90000
        }
    }

    @Nested
    @DisplayName("Delete Entity")
    class DeleteEntityTests {

        @Test
        @DisplayName("[TEST-004]: deleteEntity_successfully_deletes_entity")
        void deleteEntitySuccessfully() { // GH-90000
            // Given
            String tenantId = "tenant-alpha";
            String collectionName = "customers";
            UUID entityId = UUID.randomUUID(); // GH-90000
            String userId = "user-123";

            when(repository.delete(tenantId, collectionName, entityId)).thenReturn(Promise.of(null)); // GH-90000

            // When
            runPromise(() -> service.deleteEntity(tenantId, collectionName, entityId, userId)); // GH-90000

            // Then
            verify(repository).delete(tenantId, collectionName, entityId); // GH-90000
            verify(metrics).incrementCounter("entity.delete.success", "tenant", tenantId, "collection", collectionName); // GH-90000
        }

        @Test
        @DisplayName("[TEST-004]: deleteEntity_increments_error_metric_on_failure")
        void deleteEntityError() { // GH-90000
            // Given
            String tenantId = "tenant-alpha";
            String collectionName = "customers";
            UUID entityId = UUID.randomUUID(); // GH-90000
            String userId = "user-123";

            when(repository.delete(tenantId, collectionName, entityId)) // GH-90000
                .thenReturn(Promise.ofException(new RuntimeException("Delete failed")));

            // When
            clearFatalError(); // GH-90000
            try {
                runPromise(() -> service.deleteEntity(tenantId, collectionName, entityId, userId)); // GH-90000
            } catch (Exception e) { // GH-90000
                // Expected
            }

            // Then
            verify(metrics).incrementCounter("entity.delete.error", "tenant", tenantId, "error", "RuntimeException"); // GH-90000
        }
    }
}
