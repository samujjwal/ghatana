/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@ExtendWith(MockitoExtension.class) 
class EntityServiceImplTest extends EventloopTestBase {

    @Mock
    private EntityRepository repository;

    @Mock
    private MetricsCollector metrics;

    private EntityServiceImpl service;

    @BeforeEach
    void setUp() { 
        service = new EntityServiceImpl(repository, metrics); 
    }

    @Nested
    @DisplayName("Create Entity")
    class CreateEntityTests {

        @Test
        @DisplayName("[TEST-001]: createEntity_successfully_creates_entity")
        void createEntitySuccessfully() { 
            // Given
            String tenantId = "tenant-alpha";
            String collectionName = "customers";
            String userId = "user-123";
            Map<String, Object> data = Map.of("name", "John", "email", "john@example.com"); 

            Entity savedEntity = Entity.builder() 
                .id(UUID.randomUUID()) 
                .tenantId(tenantId) 
                .collectionName(collectionName) 
                .data(data) 
                .createdBy(userId) 
                .updatedBy(userId) 
                .createdAt(Instant.now()) 
                .updatedAt(Instant.now()) 
                .version(1) 
                .build(); 

            when(repository.save(eq(tenantId), any(Entity.class))).thenReturn(Promise.of(savedEntity)); 

            // When
            Entity result = runPromise(() -> service.createEntity(tenantId, collectionName, data, userId)); 

            // Then
            assertThat(result).isNotNull(); 
            assertThat(result.getTenantId()).isEqualTo(tenantId); 
            assertThat(result.getCollectionName()).isEqualTo(collectionName); 
            assertThat(result.getData()).isEqualTo(data); 
            assertThat(result.getCreatedBy()).isEqualTo(userId); 
            assertThat(result.getVersion()).isEqualTo(1); 

            verify(metrics).incrementCounter("entity.create.success", "tenant", tenantId, "collection", collectionName); 
        }

        @Test
        @DisplayName("[TEST-001]: createEntity_with_null_tenantId_throws_exception")
        void createEntityNullTenantId() { 
            // Given
            String tenantId = null;
            String collectionName = "customers";
            String userId = "user-123";
            Map<String, Object> data = Map.of("name", "John"); 

            // When/Then
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> { 
                service.createEntity(tenantId, collectionName, data, userId); 
            });
        }

        @Test
        @DisplayName("[TEST-001]: createEntity_with_empty_tenantId_throws_exception")
        void createEntityEmptyTenantId() { 
            // Given
            String tenantId = "";
            String collectionName = "customers";
            String userId = "user-123";
            Map<String, Object> data = Map.of("name", "John"); 

            // When/Then
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> { 
                service.createEntity(tenantId, collectionName, data, userId); 
            });
        }

        @Test
        @DisplayName("[TEST-001]: createEntity_repository_error_increments_error_metric")
        void createEntityRepositoryError() { 
            // Given
            String tenantId = "tenant-alpha";
            String collectionName = "customers";
            String userId = "user-123";
            Map<String, Object> data = Map.of("name", "John"); 

            when(repository.save(eq(tenantId), any(Entity.class))).thenReturn(Promise.ofException(new RuntimeException("DB error")));

            // When
            clearFatalError(); 
            try {
                runPromise(() -> service.createEntity(tenantId, collectionName, data, userId)); 
            } catch (Exception e) { 
                // Expected
            }

            // Then
            verify(metrics).incrementCounter("entity.create.error", "tenant", tenantId, "error", "RuntimeException"); 
        }
    }

    @Nested
    @DisplayName("Get Entity")
    class GetEntityTests {

        @Test
        @DisplayName("[TEST-002]: getEntity_returns_entity_when_found")
        void getEntityReturnsEntity() { 
            // Given
            String tenantId = "tenant-alpha";
            String collectionName = "customers";
            UUID entityId = UUID.randomUUID(); 

            Entity entity = Entity.builder() 
                .id(entityId) 
                .tenantId(tenantId) 
                .collectionName(collectionName) 
                .data(Map.of("name", "John")) 
                .build(); 

            when(repository.findById(tenantId, collectionName, entityId)).thenReturn(Promise.of(Optional.of(entity))); 

            // When
            Entity result = runPromise(() -> service.getEntity(tenantId, collectionName, entityId)); 

            // Then
            assertThat(result).isNotNull(); 
            assertThat(result.getId()).isEqualTo(entityId); 
            verify(metrics).incrementCounter("entity.get.success", "tenant", tenantId, "collection", collectionName); 
        }

        @Test
        @DisplayName("[TEST-002]: getEntity_returns_null_when_not_found")
        void getEntityNotFound() { 
            // Given
            String tenantId = "tenant-alpha";
            String collectionName = "customers";
            UUID entityId = UUID.randomUUID(); 

            when(repository.findById(tenantId, collectionName, entityId)).thenReturn(Promise.of(Optional.empty())); 

            // When
            Entity result = runPromise(() -> service.getEntity(tenantId, collectionName, entityId)); 

            // Then
            assertThat(result).isNull(); 
            verify(metrics).incrementCounter("entity.get.not_found", "tenant", tenantId, "collection", collectionName); 
        }
    }

    @Nested
    @DisplayName("Update Entity")
    class UpdateEntityTests {

        @Test
        @DisplayName("[TEST-003]: updateEntity_successfully_updates_entity")
        void updateEntitySuccessfully() { 
            // Given
            String tenantId = "tenant-alpha";
            String collectionName = "customers";
            UUID entityId = UUID.randomUUID(); 
            String userId = "user-123";

            Entity existingEntity = Entity.builder() 
                .id(entityId) 
                .tenantId(tenantId) 
                .collectionName(collectionName) 
                .data(Map.of("name", "John")) 
                .createdBy("user-1")
                .createdAt(Instant.now()) 
                .updatedBy("user-1")
                .updatedAt(Instant.now()) 
                .version(1) 
                .build(); 

            Map<String, Object> newData = Map.of("name", "John Updated"); 

            when(repository.findById(tenantId, collectionName, entityId)).thenReturn(Promise.of(Optional.of(existingEntity))); 
            when(repository.save(eq(tenantId), any(Entity.class))).thenAnswer(invocation -> { 
                Entity arg = invocation.getArgument(1); 
                return Promise.of(arg); 
            });

            // When
            Entity result = runPromise(() -> service.updateEntity(tenantId, collectionName, entityId, newData, userId)); 

            // Then
            assertThat(result).isNotNull(); 
            assertThat(result.getVersion()).isEqualTo(2); 
            assertThat(result.getUpdatedBy()).isEqualTo(userId); 
            assertThat(result.getData()).isEqualTo(newData); 
            verify(metrics).incrementCounter("entity.update.success", "tenant", tenantId, "collection", collectionName); 
        }

        @Test
        @DisplayName("[TEST-003]: updateEntity_throws_when_entity_not_found")
        void updateEntityNotFound() { 
            // Given
            String tenantId = "tenant-alpha";
            String collectionName = "customers";
            UUID entityId = UUID.randomUUID(); 
            String userId = "user-123";
            Map<String, Object> data = Map.of("name", "John"); 

            when(repository.findById(tenantId, collectionName, entityId)).thenReturn(Promise.of(Optional.empty())); 

            // When
            clearFatalError(); 
            try {
                runPromise(() -> service.updateEntity(tenantId, collectionName, entityId, data, userId)); 
            } catch (Exception e) { 
                // Expected
            }

            // Then
            verify(metrics).incrementCounter("entity.update.error", "tenant", tenantId, "error", "IllegalArgumentException"); 
        }
    }

    @Nested
    @DisplayName("Delete Entity")
    class DeleteEntityTests {

        @Test
        @DisplayName("[TEST-004]: deleteEntity_successfully_deletes_entity")
        void deleteEntitySuccessfully() { 
            // Given
            String tenantId = "tenant-alpha";
            String collectionName = "customers";
            UUID entityId = UUID.randomUUID(); 
            String userId = "user-123";

            when(repository.delete(tenantId, collectionName, entityId)).thenReturn(Promise.of(null)); 

            // When
            runPromise(() -> service.deleteEntity(tenantId, collectionName, entityId, userId)); 

            // Then
            verify(repository).delete(tenantId, collectionName, entityId); 
            verify(metrics).incrementCounter("entity.delete.success", "tenant", tenantId, "collection", collectionName); 
        }

        @Test
        @DisplayName("[TEST-004]: deleteEntity_increments_error_metric_on_failure")
        void deleteEntityError() { 
            // Given
            String tenantId = "tenant-alpha";
            String collectionName = "customers";
            UUID entityId = UUID.randomUUID(); 
            String userId = "user-123";

            when(repository.delete(tenantId, collectionName, entityId)) 
                .thenReturn(Promise.ofException(new RuntimeException("Delete failed")));

            // When
            clearFatalError(); 
            try {
                runPromise(() -> service.deleteEntity(tenantId, collectionName, entityId, userId)); 
            } catch (Exception e) { 
                // Expected
            }

            // Then
            verify(metrics).incrementCounter("entity.delete.error", "tenant", tenantId, "error", "RuntimeException"); 
        }
    }
}
