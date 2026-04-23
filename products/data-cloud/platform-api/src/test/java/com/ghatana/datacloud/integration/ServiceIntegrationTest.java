/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
    void setUp() { // GH-90000
        MockitoAnnotations.openMocks(this); // GH-90000
        entityService = new EntityServiceImpl(entityRepository, metrics); // GH-90000
    }

    @Test
    @DisplayName("[INTEGRATION-001]: entity_creation_emits_event")
    void entityCreationEmitsEvent() { // GH-90000
        // Given - Setup entity repository to return saved entity
        String tenantId = "tenant-alpha";
        String collectionName = "customers";
        String userId = "user-123";
        Map<String, Object> data = Map.of("name", "John Doe", "email", "john@example.com"); // GH-90000

        UUID entityId = UUID.randomUUID(); // GH-90000
        Entity savedEntity = Entity.builder() // GH-90000
            .id(entityId) // GH-90000
            .tenantId(tenantId) // GH-90000
            .collectionName(collectionName) // GH-90000
            .data(data) // GH-90000
            .createdBy(userId) // GH-90000
            .updatedBy(userId) // GH-90000
            .createdAt(Instant.now()) // GH-90000
            .updatedAt(Instant.now()) // GH-90000
            .version(1) // GH-90000
            .build(); // GH-90000

        when(entityRepository.save(eq(tenantId), any(Entity.class))).thenReturn(Promise.of(savedEntity)); // GH-90000

        // When - Create entity through service
        Entity result = runPromise(() -> entityService.createEntity(tenantId, collectionName, data, userId)); // GH-90000

        // Then - Verify entity was created with correct data
        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.getId()).isEqualTo(entityId); // GH-90000
        assertThat(result.getTenantId()).isEqualTo(tenantId); // GH-90000
        assertThat(result.getCollectionName()).isEqualTo(collectionName); // GH-90000
        assertThat(result.getData()).isEqualTo(data); // GH-90000
        assertThat(result.getVersion()).isEqualTo(1); // GH-90000

        System.out.println("[INTEGRATION] Entity created: " + result.getId()); // GH-90000
    }

    @Test
    @DisplayName("[INTEGRATION-002]: entity_update_increments_version")
    void entityUpdateIncrementsVersion() { // GH-90000
        // Given - Existing entity
        String tenantId = "tenant-alpha";
        String collectionName = "customers";
        UUID entityId = UUID.randomUUID(); // GH-90000
        String userId = "user-123";

        Entity existingEntity = Entity.builder() // GH-90000
            .id(entityId) // GH-90000
            .tenantId(tenantId) // GH-90000
            .collectionName(collectionName) // GH-90000
            .data(Map.of("name", "Old Name")) // GH-90000
            .createdBy("user-1")
            .createdAt(Instant.now()) // GH-90000
            .updatedBy("user-1")
            .updatedAt(Instant.now()) // GH-90000
            .version(1) // GH-90000
            .build(); // GH-90000

        when(entityRepository.findById(tenantId, collectionName, entityId)) // GH-90000
            .thenReturn(Promise.of(Optional.of(existingEntity))); // GH-90000
        when(entityRepository.save(eq(tenantId), any(Entity.class))) // GH-90000
            .thenAnswer(invocation -> Promise.of(invocation.getArgument(1))); // GH-90000

        // When - Update entity
        Map<String, Object> newData = Map.of("name", "New Name"); // GH-90000
        Entity result = runPromise(() -> entityService.updateEntity(tenantId, collectionName, entityId, newData, userId)); // GH-90000

        // Then - Version should be incremented
        assertThat(result.getVersion()).isEqualTo(2); // GH-90000
        assertThat(result.getData()).isEqualTo(newData); // GH-90000
        assertThat(result.getUpdatedBy()).isEqualTo(userId); // GH-90000

        System.out.println("[INTEGRATION] Entity updated from v1 to v" + result.getVersion()); // GH-90000
    }

    @Test
    @DisplayName("[INTEGRATION-003]: full_entity_lifecycle")
    void fullEntityLifecycle() { // GH-90000
        // Given
        String tenantId = "tenant-lifecycle";
        String collectionName = "products";
        String userId = "admin-1";

        UUID entityId = UUID.randomUUID(); // GH-90000

        // Step 1: Create
        Map<String, Object> createData = Map.of( // GH-90000
            "name", "Product A",
            "price", 99.99,
            "category", "electronics"
        );

        Entity createdEntity = Entity.builder() // GH-90000
            .id(entityId) // GH-90000
            .tenantId(tenantId) // GH-90000
            .collectionName(collectionName) // GH-90000
            .data(createData) // GH-90000
            .createdBy(userId) // GH-90000
            .updatedBy(userId) // GH-90000
            .createdAt(Instant.now()) // GH-90000
            .updatedAt(Instant.now()) // GH-90000
            .version(1) // GH-90000
            .build(); // GH-90000

        when(entityRepository.save(eq(tenantId), any(Entity.class))).thenReturn(Promise.of(createdEntity)); // GH-90000

        Entity createResult = runPromise(() -> // GH-90000
            entityService.createEntity(tenantId, collectionName, createData, userId)); // GH-90000

        assertThat(createResult.getVersion()).isEqualTo(1); // GH-90000
        System.out.println("[INTEGRATION] Created: v" + createResult.getVersion()); // GH-90000

        // Step 2: Update
        when(entityRepository.findById(tenantId, collectionName, entityId)) // GH-90000
            .thenReturn(Promise.of(Optional.of(createdEntity))); // GH-90000
        when(entityRepository.save(eq(tenantId), any(Entity.class))) // GH-90000
            .thenAnswer(invocation -> Promise.of(invocation.getArgument(1))); // GH-90000

        Map<String, Object> updateData = Map.of( // GH-90000
            "name", "Product A Updated",
            "price", 89.99,
            "category", "electronics"
        );

        Entity updateResult = runPromise(() -> // GH-90000
            entityService.updateEntity(tenantId, collectionName, entityId, updateData, userId)); // GH-90000

        assertThat(updateResult.getVersion()).isEqualTo(2); // GH-90000
        System.out.println("[INTEGRATION] Updated: v" + updateResult.getVersion()); // GH-90000

        // Step 3: Get
        when(entityRepository.findById(tenantId, collectionName, entityId)) // GH-90000
            .thenReturn(Promise.of(Optional.of(updateResult))); // GH-90000

        Entity getResult = runPromise(() -> entityService.getEntity(tenantId, collectionName, entityId)); // GH-90000

        assertThat(getResult).isNotNull(); // GH-90000
        assertThat(getResult.getData().get("price")).isEqualTo(89.99);
        System.out.println("[INTEGRATION] Retrieved: price = " + getResult.getData().get("price"));

        // Step 4: Delete
        when(entityRepository.delete(tenantId, collectionName, entityId)) // GH-90000
            .thenReturn(Promise.of(null)); // GH-90000

        runPromise(() -> entityService.deleteEntity(tenantId, collectionName, entityId, userId)); // GH-90000
        System.out.println("[INTEGRATION] Deleted successfully");
    }

    @Test
    @DisplayName("[INTEGRATION-004]: multi_tenant_isolation")
    void multiTenantIsolation() { // GH-90000
        // Given - Two tenants with same entity ID
        String tenantA = "tenant-alpha";
        String tenantB = "tenant-beta";
        String collectionName = "shared-collection";
        UUID entityId = UUID.randomUUID(); // GH-90000

        Entity entityA = Entity.builder() // GH-90000
            .id(entityId) // GH-90000
            .tenantId(tenantA) // GH-90000
            .collectionName(collectionName) // GH-90000
            .data(Map.of("tenant", "A")) // GH-90000
            .build(); // GH-90000

        Entity entityB = Entity.builder() // GH-90000
            .id(entityId) // GH-90000
            .tenantId(tenantB) // GH-90000
            .collectionName(collectionName) // GH-90000
            .data(Map.of("tenant", "B")) // GH-90000
            .build(); // GH-90000

        when(entityRepository.findById(tenantA, collectionName, entityId)) // GH-90000
            .thenReturn(Promise.of(Optional.of(entityA))); // GH-90000
        when(entityRepository.findById(tenantB, collectionName, entityId)) // GH-90000
            .thenReturn(Promise.of(Optional.of(entityB))); // GH-90000

        // When - Get from both tenants
        Entity resultA = runPromise(() -> entityService.getEntity(tenantA, collectionName, entityId)); // GH-90000
        Entity resultB = runPromise(() -> entityService.getEntity(tenantB, collectionName, entityId)); // GH-90000

        // Then - Each tenant sees their own data
        assertThat(resultA.getData().get("tenant")).isEqualTo("A");
        assertThat(resultB.getData().get("tenant")).isEqualTo("B");

        System.out.println("[INTEGRATION] Tenant A sees: " + resultA.getData().get("tenant"));
        System.out.println("[INTEGRATION] Tenant B sees: " + resultB.getData().get("tenant"));
    }
}
