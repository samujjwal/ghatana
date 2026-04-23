/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.infrastructure.storage;

import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.EntityRepository;
import com.ghatana.datacloud.entity.storage.QuerySpec;
import com.ghatana.datacloud.entity.storage.StorageConnector;
import com.ghatana.datacloud.infrastructure.audit.DataCloudAuditLogger;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Connector-level tenant isolation tests for {@link PostgresJsonbConnector}.
 *
 * <p>These tests verify the storage connector's tenant-boundary contract: every
 * repository call must be scoped to the requesting tenant's ID. No operation may
 * accidentally read, write, or delete data belonging to a different tenant.
 *
 * <p>Scope: unit-level. The repository is mocked so tests assert that the
 * connector passes the <em>correct</em> tenant context to the persistence layer
 * — the repository itself enforces the actual DB-level isolation.
 *
 * @doc.type class
 * @doc.purpose Tenant isolation contract tests for storage connector layer
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Tenant Isolation — Storage Connector Layer")
@ExtendWith(MockitoExtension.class) // GH-90000
class TenantIsolationConnectorTest extends EventloopTestBase {

    private static final String TENANT_A = "tenant-alpha";
    private static final String TENANT_B = "tenant-beta";
    private static final UUID COLLECTION_ID = UUID.randomUUID(); // GH-90000
    private static final String COLLECTION_NAME = COLLECTION_ID.toString(); // GH-90000

    @Mock
    EntityRepository entityRepository;

    @Mock
    MetricsCollector metrics;

    @Mock
    DataCloudAuditLogger auditLogger;

    PostgresJsonbConnector connector;

    @BeforeEach
    void setUp() { // GH-90000
        lenient().doNothing().when(metrics).incrementCounter(anyString(), any(String[].class)); // GH-90000
        lenient().doNothing().when(metrics).recordTimer(anyString(), anyLong(), any(String[].class)); // GH-90000
        lenient().doNothing().when(auditLogger).logDataModification(any(), any(), any(), any(), anyBoolean()); // GH-90000
        connector = new PostgresJsonbConnector(entityRepository, metrics, auditLogger); // GH-90000
    }

    // =========================================================================
    // Create — tenant ID from entity must propagate to repository
    // =========================================================================

    @Nested
    @DisplayName("Create: tenant propagation")
    class CreateTenantPropagation {

        @Test
        @DisplayName("create() passes entity's own tenantId to repository.save()")
        void create_passesEntityTenantToRepository() { // GH-90000
            Entity entityA = entityFor(TENANT_A); // GH-90000
            when(entityRepository.save(eq(TENANT_A), any(Entity.class))) // GH-90000
                    .thenReturn(Promise.of(entityA)); // GH-90000

            runPromise(() -> connector.create(entityA)); // GH-90000

            ArgumentCaptor<String> tenantCaptor = ArgumentCaptor.forClass(String.class); // GH-90000
            verify(entityRepository).save(tenantCaptor.capture(), any(Entity.class)); // GH-90000
            assertThat(tenantCaptor.getValue()).isEqualTo(TENANT_A); // GH-90000
        }

        @Test
        @DisplayName("create() never passes tenant-B's ID when entity belongs to tenant-A")
        void create_neverUsesDifferentTenant() { // GH-90000
            Entity entityA = entityFor(TENANT_A); // GH-90000
            when(entityRepository.save(eq(TENANT_A), any(Entity.class))) // GH-90000
                    .thenReturn(Promise.of(entityA)); // GH-90000

            runPromise(() -> connector.create(entityA)); // GH-90000

            // repository must NOT be called with tenant-B
            verify(entityRepository, never()).save(eq(TENANT_B), any(Entity.class)); // GH-90000
        }

        @Test
        @DisplayName("create() rejects entity with null tenantId before touching repository")
        void create_nullTenantId_rejectsBeforeRepository() { // GH-90000
            Entity noTenant = entityFor(null); // GH-90000

            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> connector.create(noTenant)); // GH-90000

            verifyNoInteractions(entityRepository); // GH-90000
        }

        @Test
        @DisplayName("create() rejects entity with blank tenantId before touching repository")
        void create_blankTenantId_rejectsBeforeRepository() { // GH-90000
            Entity blankTenant = Entity.builder() // GH-90000
                    .tenantId("  ")
                    .collectionName(COLLECTION_NAME) // GH-90000
                    .data(Map.of("k", "v")) // GH-90000
                    .build(); // GH-90000

            // Either NPE or IllegalArgumentException — both signal early rejection
            assertThatException() // GH-90000
                    .isThrownBy(() -> connector.create(blankTenant)); // GH-90000

            verifyNoInteractions(entityRepository); // GH-90000
        }
    }

    // =========================================================================
    // Read — tenant passed on read must be forwarded verbatim
    // =========================================================================

    @Nested
    @DisplayName("Read: tenant propagation and cross-tenant opacity")
    class ReadTenantPropagation {

        @Test
        @DisplayName("read() passes the calling tenant's ID to repository.findById()")
        void read_passesCallerTenantToRepository() { // GH-90000
            UUID entityId = UUID.randomUUID(); // GH-90000
            when(entityRepository.findById(TENANT_A, COLLECTION_NAME, entityId)) // GH-90000
                    .thenReturn(Promise.of(Optional.of(entityFor(TENANT_A)))); // GH-90000

            runPromise(() -> connector.read(COLLECTION_ID, TENANT_A, entityId)); // GH-90000

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class); // GH-90000
            verify(entityRepository).findById(captor.capture(), eq(COLLECTION_NAME), eq(entityId)); // GH-90000
            assertThat(captor.getValue()).isEqualTo(TENANT_A); // GH-90000
        }

        @Test
        @DisplayName("read() with tenant-B context never asks repository for tenant-A data")
        void read_tenantBContext_neverQueriesTenantA() { // GH-90000
            UUID entityId = UUID.randomUUID(); // GH-90000
            when(entityRepository.findById(TENANT_B, COLLECTION_NAME, entityId)) // GH-90000
                    .thenReturn(Promise.of(Optional.empty())); // GH-90000

            runPromise(() -> connector.read(COLLECTION_ID, TENANT_B, entityId)); // GH-90000

            verify(entityRepository, never()).findById(eq(TENANT_A), any(), any(UUID.class)); // GH-90000
        }

        @Test
        @DisplayName("cross-tenant read returns empty when repository returns empty")
        void read_crossTenant_returnsEmpty() { // GH-90000
            UUID entityId = UUID.randomUUID(); // GH-90000
            // Repository simulates isolation: returns empty for wrong tenant
            when(entityRepository.findById(eq(TENANT_B), anyString(), eq(entityId))) // GH-90000
                    .thenReturn(Promise.of(Optional.empty())); // GH-90000

            Optional<Entity> result = runPromise( // GH-90000
                    () -> connector.read(COLLECTION_ID, TENANT_B, entityId)); // GH-90000

            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("read() with null tenantId fails fast without repository call")
        void read_nullTenantId_failsFast() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> connector.read(COLLECTION_ID, null, id)); // GH-90000
            verifyNoInteractions(entityRepository); // GH-90000
        }
    }

    // =========================================================================
    // Update — entity's own tenant must be used, not any substitute
    // =========================================================================

    @Nested
    @DisplayName("Update: tenant propagation")
    class UpdateTenantPropagation {

        @Test
        @DisplayName("update() calls repository.save() with entity's tenantId")
        void update_passesEntityTenantToRepository() { // GH-90000
            Entity entityA = entityFor(TENANT_A); // GH-90000
            entityA.setId(UUID.randomUUID()); // GH-90000
            when(entityRepository.save(eq(TENANT_A), any(Entity.class))) // GH-90000
                    .thenReturn(Promise.of(entityA)); // GH-90000

            runPromise(() -> connector.update(entityA)); // GH-90000

            ArgumentCaptor<String> tenantCaptor = ArgumentCaptor.forClass(String.class); // GH-90000
            verify(entityRepository).save(tenantCaptor.capture(), any(Entity.class)); // GH-90000
            assertThat(tenantCaptor.getValue()).isEqualTo(TENANT_A); // GH-90000
        }

        @Test
        @DisplayName("update() never writes to tenant-B's scope when entity belongs to tenant-A")
        void update_entityA_neverWritesToTenantB() { // GH-90000
            Entity entityA = entityFor(TENANT_A); // GH-90000
            entityA.setId(UUID.randomUUID()); // GH-90000
            when(entityRepository.save(eq(TENANT_A), any(Entity.class))) // GH-90000
                    .thenReturn(Promise.of(entityA)); // GH-90000

            runPromise(() -> connector.update(entityA)); // GH-90000

            verify(entityRepository, never()).save(eq(TENANT_B), any(Entity.class)); // GH-90000
        }
    }

    // =========================================================================
    // Delete — caller's tenantId is forwarded; cross-tenant delete impossible
    // =========================================================================

    @Nested
    @DisplayName("Delete: tenant scoping")
    class DeleteTenantScoping {

        @Test
        @DisplayName("delete() passes caller's tenantId to repository.delete()")
        void delete_passesCallerTenantToRepository() { // GH-90000
            UUID entityId = UUID.randomUUID(); // GH-90000
            when(entityRepository.delete(TENANT_A, COLLECTION_NAME, entityId)) // GH-90000
                    .thenReturn(Promise.of(null)); // GH-90000

            runPromise(() -> connector.delete(COLLECTION_ID, TENANT_A, entityId)); // GH-90000

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class); // GH-90000
            verify(entityRepository).delete(captor.capture(), eq(COLLECTION_NAME), eq(entityId)); // GH-90000
            assertThat(captor.getValue()).isEqualTo(TENANT_A); // GH-90000
        }

        @Test
        @DisplayName("delete() with tenant-A context never deletes in tenant-B's namespace")
        void delete_tenantA_neverTouchtesTenantB() { // GH-90000
            UUID entityId = UUID.randomUUID(); // GH-90000
            when(entityRepository.delete(TENANT_A, COLLECTION_NAME, entityId)) // GH-90000
                    .thenReturn(Promise.of(null)); // GH-90000

            runPromise(() -> connector.delete(COLLECTION_ID, TENANT_A, entityId)); // GH-90000

            verify(entityRepository, never()).delete(eq(TENANT_B), any(), any(UUID.class)); // GH-90000
        }

        @Test
        @DisplayName("delete() with null tenantId fails fast without repository call")
        void delete_nullTenantId_failsFast() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> connector.delete(COLLECTION_ID, null, id)); // GH-90000
            verifyNoInteractions(entityRepository); // GH-90000
        }
    }

    // =========================================================================
    // Query — tenant scope enforced in every query call
    // =========================================================================

    @Nested
    @DisplayName("Query: tenant scoping")
    class QueryTenantScoping {

        @Test
        @DisplayName("query() passes caller's tenantId to repository.findByQuery()")
        void query_passesCallerTenantToFindAll() { // GH-90000
            when(entityRepository.findByQuery(eq(TENANT_A), anyString(), any())) // GH-90000
                    .thenReturn(Promise.of(List.of())); // GH-90000
            when(entityRepository.count(eq(TENANT_A), anyString())) // GH-90000
                    .thenReturn(Promise.of(0L)); // GH-90000

            QuerySpec spec = QuerySpec.builder().limit(10).offset(0).build(); // GH-90000
            runPromise(() -> connector.query(COLLECTION_ID, TENANT_A, spec)); // GH-90000

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class); // GH-90000
            verify(entityRepository).findByQuery(captor.capture(), anyString(), any()); // GH-90000
            assertThat(captor.getValue()).isEqualTo(TENANT_A); // GH-90000
        }

        @Test
        @DisplayName("query() with tenant-B context never returns tenant-A data")
        void query_tenantBContext_neverReturnsTenantAEntities() { // GH-90000
            // Repository returns empty for tenant-B (correct scoping) // GH-90000
            when(entityRepository.findByQuery(eq(TENANT_B), anyString(), any())) // GH-90000
                    .thenReturn(Promise.of(List.of())); // GH-90000
            when(entityRepository.count(eq(TENANT_B), anyString())) // GH-90000
                    .thenReturn(Promise.of(0L)); // GH-90000

            QuerySpec spec = QuerySpec.builder().limit(10).offset(0).build(); // GH-90000
            StorageConnector.QueryResult result = runPromise( // GH-90000
                    () -> connector.query(COLLECTION_ID, TENANT_B, spec)); // GH-90000

            assertThat(result.entities()).isEmpty(); // GH-90000
            // Must never call findByQuery with tenant-A's ID
            verify(entityRepository, never()).findByQuery(eq(TENANT_A), anyString(), any()); // GH-90000
        }

        @Test
        @DisplayName("query() with null tenantId fails fast without repository call")
        void query_nullTenantId_failsFast() { // GH-90000
            QuerySpec spec = QuerySpec.builder().limit(10).offset(0).build(); // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> connector.query(COLLECTION_ID, null, spec)); // GH-90000
            verifyNoInteractions(entityRepository); // GH-90000
        }

        @Test
        @DisplayName("query() results contain only entities for the requested tenant")
        void query_resultsContainOnlyRequestedTenant() { // GH-90000
            Entity entityA1 = entityFor(TENANT_A); // GH-90000
            Entity entityA2 = entityFor(TENANT_A); // GH-90000
            when(entityRepository.findByQuery(eq(TENANT_A), anyString(), any())) // GH-90000
                    .thenReturn(Promise.of(List.of(entityA1, entityA2))); // GH-90000
            when(entityRepository.count(eq(TENANT_A), anyString())) // GH-90000
                    .thenReturn(Promise.of(2L)); // GH-90000

            QuerySpec spec = QuerySpec.builder().limit(10).offset(0).build(); // GH-90000
            StorageConnector.QueryResult result = runPromise( // GH-90000
                    () -> connector.query(COLLECTION_ID, TENANT_A, spec)); // GH-90000

            assertThat(result.entities()) // GH-90000
                    .hasSize(2) // GH-90000
                    .extracting(Entity::getTenantId) // GH-90000
                    .containsOnly(TENANT_A); // GH-90000
        }
    }

    // =========================================================================
    // Audit log — tenant reported to audit must match the actual caller
    // =========================================================================

    @Nested
    @DisplayName("Audit: tenant reported correctly")
    class AuditTenantCorrectness {

        @Test
        @DisplayName("create() reports tenant-A to audit log, not tenant-B")
        void create_reportsCorrectTenantToAuditLog() { // GH-90000
            Entity entityA = entityFor(TENANT_A); // GH-90000
            when(entityRepository.save(eq(TENANT_A), any(Entity.class))) // GH-90000
                    .thenReturn(Promise.of(entityA)); // GH-90000

            runPromise(() -> connector.create(entityA)); // GH-90000

            ArgumentCaptor<String> tenantCaptor = ArgumentCaptor.forClass(String.class); // GH-90000
            verify(auditLogger).logDataModification( // GH-90000
                    tenantCaptor.capture(), eq("CREATE"), anyString(), anyString(), eq(true));
            assertThat(tenantCaptor.getValue()).isEqualTo(TENANT_A); // GH-90000
        }

        @Test
        @DisplayName("delete() reports the caller's tenant to audit, not another tenant")
        void delete_reportsCorrectTenantToAuditLog() { // GH-90000
            UUID entityId = UUID.randomUUID(); // GH-90000
            when(entityRepository.delete(TENANT_A, COLLECTION_NAME, entityId)) // GH-90000
                    .thenReturn(Promise.of(null)); // GH-90000

            runPromise(() -> connector.delete(COLLECTION_ID, TENANT_A, entityId)); // GH-90000

            ArgumentCaptor<String> tenantCaptor = ArgumentCaptor.forClass(String.class); // GH-90000
            verify(auditLogger).logDataModification( // GH-90000
                    tenantCaptor.capture(), eq("DELETE"), anyString(), anyString(), eq(true));
            assertThat(tenantCaptor.getValue()).isEqualTo(TENANT_A); // GH-90000
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Entity entityFor(String tenantId) { // GH-90000
        Entity e = Entity.builder() // GH-90000
                .tenantId(tenantId) // GH-90000
                .collectionName(COLLECTION_NAME) // GH-90000
                .data(Map.of("key", "value-" + UUID.randomUUID())) // GH-90000
                .build(); // GH-90000
        e.setCreatedAt(Instant.now()); // GH-90000
        e.setUpdatedAt(Instant.now()); // GH-90000
        return e;
    }
}
