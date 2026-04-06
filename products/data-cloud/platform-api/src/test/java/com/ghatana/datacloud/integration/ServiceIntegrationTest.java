/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.integration;

import com.ghatana.datacloud.application.*;
import com.ghatana.datacloud.entity.*;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.*;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Service Integration Test - End-to-End Usage
 * 
 * @doc.type class
 * @doc.purpose Integration tests showing service usage patterns
 * @doc.layer test
 * @doc.pattern Integration Test
 */
@DisplayName("Service Integration Tests")
class ServiceIntegrationTest extends EventloopTestBase {

    @Mock
    private EntityRepository entityRepository;

    @Mock
    private MetricsCollector metrics;

    private EntityServiceImpl entityService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        entityService = new EntityServiceImpl(entityRepository, metrics);
    }

    @Test
    @DisplayName("[INTEGRATION-001]: entity_creation_emits_event")
    void entityCreationEmitsEvent() {
        // Given - Setup entity repository to return saved entity
        String tenantId = "tenant-alpha";
        String collectionName = "customers";
        String userId = "user-123";
        Map<String, Object> data = Map.of("name", "John Doe", "email", "john@example.com");

        UUID entityId = UUID.randomUUID();
        Entity savedEntity = Entity.builder()
            .id(entityId)
            .tenantId(tenantId)
            .collectionName(collectionName)
            .data(data)
            .createdBy(userId)
            .updatedBy(userId)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .version(1)
            .build();

        when(entityRepository.save(eq(tenantId), any(Entity.class))).thenReturn(Promise.of(savedEntity));

        // When - Create entity through service
        Entity result = runPromise(() -> entityService.createEntity(tenantId, collectionName, data, userId));

        // Then - Verify entity was created with correct data
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(entityId);
        assertThat(result.getTenantId()).isEqualTo(tenantId);
        assertThat(result.getCollectionName()).isEqualTo(collectionName);
        assertThat(result.getData()).isEqualTo(data);
        assertThat(result.getVersion()).isEqualTo(1);

        System.out.println("[INTEGRATION] Entity created: " + result.getId());
    }

    @Test
    @DisplayName("[INTEGRATION-002]: entity_update_increments_version")
    void entityUpdateIncrementsVersion() {
        // Given - Existing entity
        String tenantId = "tenant-alpha";
        String collectionName = "customers";
        UUID entityId = UUID.randomUUID();
        String userId = "user-123";

        Entity existingEntity = Entity.builder()
            .id(entityId)
            .tenantId(tenantId)
            .collectionName(collectionName)
            .data(Map.of("name", "Old Name"))
            .createdBy("user-1")
            .createdAt(Instant.now())
            .updatedBy("user-1")
            .updatedAt(Instant.now())
            .version(1)
            .build();

        when(entityRepository.findById(tenantId, collectionName, entityId))
            .thenReturn(Promise.of(Optional.of(existingEntity)));
        when(entityRepository.save(eq(tenantId), any(Entity.class)))
            .thenAnswer(invocation -> Promise.of(invocation.getArgument(1)));

        // When - Update entity
        Map<String, Object> newData = Map.of("name", "New Name");
        Entity result = runPromise(() -> entityService.updateEntity(tenantId, collectionName, entityId, newData, userId));

        // Then - Version should be incremented
        assertThat(result.getVersion()).isEqualTo(2);
        assertThat(result.getData()).isEqualTo(newData);
        assertThat(result.getUpdatedBy()).isEqualTo(userId);

        System.out.println("[INTEGRATION] Entity updated from v1 to v" + result.getVersion());
    }

    @Test
    @DisplayName("[INTEGRATION-003]: full_entity_lifecycle")
    void fullEntityLifecycle() {
        // Given
        String tenantId = "tenant-lifecycle";
        String collectionName = "products";
        String userId = "admin-1";

        UUID entityId = UUID.randomUUID();

        // Step 1: Create
        Map<String, Object> createData = Map.of(
            "name", "Product A",
            "price", 99.99,
            "category", "electronics"
        );

        Entity createdEntity = Entity.builder()
            .id(entityId)
            .tenantId(tenantId)
            .collectionName(collectionName)
            .data(createData)
            .createdBy(userId)
            .updatedBy(userId)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .version(1)
            .build();

        when(entityRepository.save(eq(tenantId), any(Entity.class))).thenReturn(Promise.of(createdEntity));

        Entity createResult = runPromise(() -> 
            entityService.createEntity(tenantId, collectionName, createData, userId));

        assertThat(createResult.getVersion()).isEqualTo(1);
        System.out.println("[INTEGRATION] Created: v" + createResult.getVersion());

        // Step 2: Update
        when(entityRepository.findById(tenantId, collectionName, entityId))
            .thenReturn(Promise.of(Optional.of(createdEntity)));
        when(entityRepository.save(eq(tenantId), any(Entity.class)))
            .thenAnswer(invocation -> Promise.of(invocation.getArgument(1)));

        Map<String, Object> updateData = Map.of(
            "name", "Product A Updated",
            "price", 89.99,
            "category", "electronics"
        );

        Entity updateResult = runPromise(() -> 
            entityService.updateEntity(tenantId, collectionName, entityId, updateData, userId));

        assertThat(updateResult.getVersion()).isEqualTo(2);
        System.out.println("[INTEGRATION] Updated: v" + updateResult.getVersion());

        // Step 3: Get
        when(entityRepository.findById(tenantId, collectionName, entityId))
            .thenReturn(Promise.of(Optional.of(updateResult)));

        Entity getResult = runPromise(() -> entityService.getEntity(tenantId, collectionName, entityId));

        assertThat(getResult).isNotNull();
        assertThat(getResult.getData().get("price")).isEqualTo(89.99);
        System.out.println("[INTEGRATION] Retrieved: price = " + getResult.getData().get("price"));

        // Step 4: Delete
        when(entityRepository.delete(tenantId, collectionName, entityId))
            .thenReturn(Promise.of(null));

        runPromise(() -> entityService.deleteEntity(tenantId, collectionName, entityId, userId));
        System.out.println("[INTEGRATION] Deleted successfully");
    }

    @Test
    @DisplayName("[INTEGRATION-004]: multi_tenant_isolation")
    void multiTenantIsolation() {
        // Given - Two tenants with same entity ID
        String tenantA = "tenant-alpha";
        String tenantB = "tenant-beta";
        String collectionName = "shared-collection";
        UUID entityId = UUID.randomUUID();

        Entity entityA = Entity.builder()
            .id(entityId)
            .tenantId(tenantA)
            .collectionName(collectionName)
            .data(Map.of("tenant", "A"))
            .build();

        Entity entityB = Entity.builder()
            .id(entityId)
            .tenantId(tenantB)
            .collectionName(collectionName)
            .data(Map.of("tenant", "B"))
            .build();

        when(entityRepository.findById(tenantA, collectionName, entityId))
            .thenReturn(Promise.of(Optional.of(entityA)));
        when(entityRepository.findById(tenantB, collectionName, entityId))
            .thenReturn(Promise.of(Optional.of(entityB)));

        // When - Get from both tenants
        Entity resultA = runPromise(() -> entityService.getEntity(tenantA, collectionName, entityId));
        Entity resultB = runPromise(() -> entityService.getEntity(tenantB, collectionName, entityId));

        // Then - Each tenant sees their own data
        assertThat(resultA.getData().get("tenant")).isEqualTo("A");
        assertThat(resultB.getData().get("tenant")).isEqualTo("B");

        System.out.println("[INTEGRATION] Tenant A sees: " + resultA.getData().get("tenant"));
        System.out.println("[INTEGRATION] Tenant B sees: " + resultB.getData().get("tenant"));
    }
}
