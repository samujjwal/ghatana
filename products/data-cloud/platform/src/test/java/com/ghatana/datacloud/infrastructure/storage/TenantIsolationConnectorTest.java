/*
 * Copyright (c) 2026 Ghatana Inc.
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
@ExtendWith(MockitoExtension.class)
class TenantIsolationConnectorTest extends EventloopTestBase {

    private static final String TENANT_A = "tenant-alpha";
    private static final String TENANT_B = "tenant-beta";
    private static final UUID COLLECTION_ID = UUID.randomUUID();
    private static final String COLLECTION_NAME = COLLECTION_ID.toString();

    @Mock
    EntityRepository entityRepository;

    @Mock
    MetricsCollector metrics;

    @Mock
    DataCloudAuditLogger auditLogger;

    PostgresJsonbConnector connector;

    @BeforeEach
    void setUp() {
        lenient().doNothing().when(metrics).incrementCounter(anyString(), any(String[].class));
        lenient().doNothing().when(metrics).recordTimer(anyString(), anyLong(), any(String[].class));
        lenient().doNothing().when(auditLogger).logDataModification(any(), any(), any(), any(), anyBoolean());
        connector = new PostgresJsonbConnector(entityRepository, metrics, auditLogger);
    }

    // =========================================================================
    // Create — tenant ID from entity must propagate to repository
    // =========================================================================

    @Nested
    @DisplayName("Create: tenant propagation")
    class CreateTenantPropagation {

        @Test
        @DisplayName("create() passes entity's own tenantId to repository.save()")
        void create_passesEntityTenantToRepository() {
            Entity entityA = entityFor(TENANT_A);
            when(entityRepository.save(eq(TENANT_A), any(Entity.class)))
                    .thenReturn(Promise.of(entityA));

            runPromise(() -> connector.create(entityA));

            ArgumentCaptor<String> tenantCaptor = ArgumentCaptor.forClass(String.class);
            verify(entityRepository).save(tenantCaptor.capture(), any(Entity.class));
            assertThat(tenantCaptor.getValue()).isEqualTo(TENANT_A);
        }

        @Test
        @DisplayName("create() never passes tenant-B's ID when entity belongs to tenant-A")
        void create_neverUsesDifferentTenant() {
            Entity entityA = entityFor(TENANT_A);
            when(entityRepository.save(eq(TENANT_A), any(Entity.class)))
                    .thenReturn(Promise.of(entityA));

            runPromise(() -> connector.create(entityA));

            // repository must NOT be called with tenant-B
            verify(entityRepository, never()).save(eq(TENANT_B), any(Entity.class));
        }

        @Test
        @DisplayName("create() rejects entity with null tenantId before touching repository")
        void create_nullTenantId_rejectsBeforeRepository() {
            Entity noTenant = entityFor(null);

            assertThatNullPointerException()
                    .isThrownBy(() -> connector.create(noTenant));

            verifyNoInteractions(entityRepository);
        }

        @Test
        @DisplayName("create() rejects entity with blank tenantId before touching repository")
        void create_blankTenantId_rejectsBeforeRepository() {
            Entity blankTenant = Entity.builder()
                    .tenantId("  ")
                    .collectionName(COLLECTION_NAME)
                    .data(Map.of("k", "v"))
                    .build();

            // Either NPE or IllegalArgumentException — both signal early rejection
            assertThatException()
                    .isThrownBy(() -> connector.create(blankTenant));

            verifyNoInteractions(entityRepository);
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
        void read_passesCallerTenantToRepository() {
            UUID entityId = UUID.randomUUID();
            when(entityRepository.findById(TENANT_A, COLLECTION_NAME, entityId))
                    .thenReturn(Promise.of(Optional.of(entityFor(TENANT_A))));

            runPromise(() -> connector.read(COLLECTION_ID, TENANT_A, entityId));

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(entityRepository).findById(captor.capture(), eq(COLLECTION_NAME), eq(entityId));
            assertThat(captor.getValue()).isEqualTo(TENANT_A);
        }

        @Test
        @DisplayName("read() with tenant-B context never asks repository for tenant-A data")
        void read_tenantBContext_neverQueriesTenantA() {
            UUID entityId = UUID.randomUUID();
            when(entityRepository.findById(TENANT_B, COLLECTION_NAME, entityId))
                    .thenReturn(Promise.of(Optional.empty()));

            runPromise(() -> connector.read(COLLECTION_ID, TENANT_B, entityId));

            verify(entityRepository, never()).findById(eq(TENANT_A), any(), any(UUID.class));
        }

        @Test
        @DisplayName("cross-tenant read returns empty when repository returns empty")
        void read_crossTenant_returnsEmpty() {
            UUID entityId = UUID.randomUUID();
            // Repository simulates isolation: returns empty for wrong tenant
            when(entityRepository.findById(eq(TENANT_B), anyString(), eq(entityId)))
                    .thenReturn(Promise.of(Optional.empty()));

            Optional<Entity> result = runPromise(
                    () -> connector.read(COLLECTION_ID, TENANT_B, entityId));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("read() with null tenantId fails fast without repository call")
        void read_nullTenantId_failsFast() {
            UUID id = UUID.randomUUID();
            assertThatNullPointerException()
                    .isThrownBy(() -> connector.read(COLLECTION_ID, null, id));
            verifyNoInteractions(entityRepository);
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
        void update_passesEntityTenantToRepository() {
            Entity entityA = entityFor(TENANT_A);
            entityA.setId(UUID.randomUUID());
            when(entityRepository.save(eq(TENANT_A), any(Entity.class)))
                    .thenReturn(Promise.of(entityA));

            runPromise(() -> connector.update(entityA));

            ArgumentCaptor<String> tenantCaptor = ArgumentCaptor.forClass(String.class);
            verify(entityRepository).save(tenantCaptor.capture(), any(Entity.class));
            assertThat(tenantCaptor.getValue()).isEqualTo(TENANT_A);
        }

        @Test
        @DisplayName("update() never writes to tenant-B's scope when entity belongs to tenant-A")
        void update_entityA_neverWritesToTenantB() {
            Entity entityA = entityFor(TENANT_A);
            entityA.setId(UUID.randomUUID());
            when(entityRepository.save(eq(TENANT_A), any(Entity.class)))
                    .thenReturn(Promise.of(entityA));

            runPromise(() -> connector.update(entityA));

            verify(entityRepository, never()).save(eq(TENANT_B), any(Entity.class));
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
        void delete_passesCallerTenantToRepository() {
            UUID entityId = UUID.randomUUID();
            when(entityRepository.delete(TENANT_A, COLLECTION_NAME, entityId))
                    .thenReturn(Promise.of(null));

            runPromise(() -> connector.delete(COLLECTION_ID, TENANT_A, entityId));

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(entityRepository).delete(captor.capture(), eq(COLLECTION_NAME), eq(entityId));
            assertThat(captor.getValue()).isEqualTo(TENANT_A);
        }

        @Test
        @DisplayName("delete() with tenant-A context never deletes in tenant-B's namespace")
        void delete_tenantA_neverTouchtesTenantB() {
            UUID entityId = UUID.randomUUID();
            when(entityRepository.delete(TENANT_A, COLLECTION_NAME, entityId))
                    .thenReturn(Promise.of(null));

            runPromise(() -> connector.delete(COLLECTION_ID, TENANT_A, entityId));

            verify(entityRepository, never()).delete(eq(TENANT_B), any(), any(UUID.class));
        }

        @Test
        @DisplayName("delete() with null tenantId fails fast without repository call")
        void delete_nullTenantId_failsFast() {
            UUID id = UUID.randomUUID();
            assertThatNullPointerException()
                    .isThrownBy(() -> connector.delete(COLLECTION_ID, null, id));
            verifyNoInteractions(entityRepository);
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
        void query_passesCallerTenantToFindAll() {
            when(entityRepository.findByQuery(eq(TENANT_A), anyString(), any()))
                    .thenReturn(Promise.of(List.of()));
            when(entityRepository.count(eq(TENANT_A), anyString()))
                    .thenReturn(Promise.of(0L));

            QuerySpec spec = QuerySpec.builder().limit(10).offset(0).build();
            runPromise(() -> connector.query(COLLECTION_ID, TENANT_A, spec));

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(entityRepository).findByQuery(captor.capture(), anyString(), any());
            assertThat(captor.getValue()).isEqualTo(TENANT_A);
        }

        @Test
        @DisplayName("query() with tenant-B context never returns tenant-A data")
        void query_tenantBContext_neverReturnsTenantAEntities() {
            // Repository returns empty for tenant-B (correct scoping)
            when(entityRepository.findByQuery(eq(TENANT_B), anyString(), any()))
                    .thenReturn(Promise.of(List.of()));
            when(entityRepository.count(eq(TENANT_B), anyString()))
                    .thenReturn(Promise.of(0L));

            QuerySpec spec = QuerySpec.builder().limit(10).offset(0).build();
            StorageConnector.QueryResult result = runPromise(
                    () -> connector.query(COLLECTION_ID, TENANT_B, spec));

            assertThat(result.entities()).isEmpty();
            // Must never call findByQuery with tenant-A's ID
            verify(entityRepository, never()).findByQuery(eq(TENANT_A), anyString(), any());
        }

        @Test
        @DisplayName("query() with null tenantId fails fast without repository call")
        void query_nullTenantId_failsFast() {
            QuerySpec spec = QuerySpec.builder().limit(10).offset(0).build();
            assertThatNullPointerException()
                    .isThrownBy(() -> connector.query(COLLECTION_ID, null, spec));
            verifyNoInteractions(entityRepository);
        }

        @Test
        @DisplayName("query() results contain only entities for the requested tenant")
        void query_resultsContainOnlyRequestedTenant() {
            Entity entityA1 = entityFor(TENANT_A);
            Entity entityA2 = entityFor(TENANT_A);
            when(entityRepository.findByQuery(eq(TENANT_A), anyString(), any()))
                    .thenReturn(Promise.of(List.of(entityA1, entityA2)));
            when(entityRepository.count(eq(TENANT_A), anyString()))
                    .thenReturn(Promise.of(2L));

            QuerySpec spec = QuerySpec.builder().limit(10).offset(0).build();
            StorageConnector.QueryResult result = runPromise(
                    () -> connector.query(COLLECTION_ID, TENANT_A, spec));

            assertThat(result.entities())
                    .hasSize(2)
                    .extracting(Entity::getTenantId)
                    .containsOnly(TENANT_A);
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
        void create_reportsCorrectTenantToAuditLog() {
            Entity entityA = entityFor(TENANT_A);
            when(entityRepository.save(eq(TENANT_A), any(Entity.class)))
                    .thenReturn(Promise.of(entityA));

            runPromise(() -> connector.create(entityA));

            ArgumentCaptor<String> tenantCaptor = ArgumentCaptor.forClass(String.class);
            verify(auditLogger).logDataModification(
                    tenantCaptor.capture(), eq("CREATE"), anyString(), anyString(), eq(true));
            assertThat(tenantCaptor.getValue()).isEqualTo(TENANT_A);
        }

        @Test
        @DisplayName("delete() reports the caller's tenant to audit, not another tenant")
        void delete_reportsCorrectTenantToAuditLog() {
            UUID entityId = UUID.randomUUID();
            when(entityRepository.delete(TENANT_A, COLLECTION_NAME, entityId))
                    .thenReturn(Promise.of(null));

            runPromise(() -> connector.delete(COLLECTION_ID, TENANT_A, entityId));

            ArgumentCaptor<String> tenantCaptor = ArgumentCaptor.forClass(String.class);
            verify(auditLogger).logDataModification(
                    tenantCaptor.capture(), eq("DELETE"), anyString(), anyString(), eq(true));
            assertThat(tenantCaptor.getValue()).isEqualTo(TENANT_A);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Entity entityFor(String tenantId) {
        Entity e = Entity.builder()
                .tenantId(tenantId)
                .collectionName(COLLECTION_NAME)
                .data(Map.of("key", "value-" + UUID.randomUUID()))
                .build();
        e.setCreatedAt(Instant.now());
        e.setUpdatedAt(Instant.now());
        return e;
    }
}
