/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.launcher.store;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataCloudClient.Entity;
import com.ghatana.datacloud.DataCloudClient.Filter;
import com.ghatana.datacloud.DataCloudClient.Query;
import com.ghatana.pattern.api.model.PatternMetadata;
import com.ghatana.pattern.api.model.PatternSpecification;
import com.ghatana.pattern.api.model.PatternStatus;
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
 * Unit tests for {@link DataCloudPatternStore}.
 *
 * <p>All Data-Cloud I/O is replaced by Mockito mocks so tests run in-process
 * without requiring real storage. ActiveJ's synchronous {@link Promise#of(Object)}
 * factory is used to return stub values from mocks, so the tests can call
 * {@code .getResult()} directly (the promise is already resolved and never
 * touches an Eventloop).
 *
 * @doc.type class
 * @doc.purpose Unit tests for DataCloudPatternStore
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DataCloudPatternStore")
class DataCloudPatternStoreTest {

    private static final String TENANT = "tenant-alpha";

    @Mock
    private DataCloudClient client;

    private DataCloudPatternStore store;

    @BeforeEach
    void setUp() {
        store = new DataCloudPatternStore(client);
    }

    // =========================================================================
    // save
    // =========================================================================

    @Nested
    @DisplayName("save()")
    class SaveTests {

        @Test
        @DisplayName("save: assigns new UUID when spec.id is null")
        void save_whenSpecHasNoId_assignsUuid() {
            PatternSpecification spec = spec("Pattern A").build();
            Entity fakeEntity = entity(null);

            when(client.save(eq(TENANT), eq(DataCloudPatternStore.COLLECTION), anyMap()))
                    .thenReturn(Promise.of(fakeEntity));

            PatternMetadata result = store.save(spec).getResult();

            ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);
            verify(client).save(eq(TENANT), eq(DataCloudPatternStore.COLLECTION), dataCaptor.capture());
            assertThat(dataCaptor.getValue().get("id")).isNotNull();
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("save: preserves existing spec.id")
        void save_whenSpecHasId_keepsId() {
            UUID existingId = UUID.randomUUID();
            PatternSpecification spec = spec("Pattern B").id(existingId).build();
            Entity fakeEntity = entity(existingId.toString());

            when(client.save(eq(TENANT), eq(DataCloudPatternStore.COLLECTION), anyMap()))
                    .thenReturn(Promise.of(fakeEntity));

            store.save(spec).getResult();

            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(client).save(eq(TENANT), eq(DataCloudPatternStore.COLLECTION), captor.capture());
            assertThat(captor.getValue().get("id")).isEqualTo(existingId.toString());
        }

        @Test
        @DisplayName("save: propagates client exception as failed promise")
        void save_whenClientThrows_returnsFailedPromise() {
            PatternSpecification spec = spec("Pattern C").build();
            when(client.save(anyString(), anyString(), anyMap()))
                    .thenReturn(Promise.ofException(new RuntimeException("storage failure")));

            Promise<PatternMetadata> result = store.save(spec);

            assertThat(result.isException()).isTrue();
        }
    }

    // =========================================================================
    // findById
    // =========================================================================

    @Nested
    @DisplayName("findById()")
    class FindByIdTests {

        @Test
        @DisplayName("findById: returns populated Optional when entity exists")
        void findById_whenEntityExists_returnsPresent() {
            UUID id = UUID.randomUUID();
            Entity e = entity(id.toString());
            // The store's no-tenant findById uses "system" for cross-tenant reads
            when(client.findById("system", DataCloudPatternStore.COLLECTION, id.toString()))
                    .thenReturn(Promise.of(Optional.of(e)));

            Optional<PatternMetadata> result = store.findById(id).getResult();

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("findById: returns empty Optional when entity not found")
        void findById_whenEntityMissing_returnsEmpty() {
            UUID id = UUID.randomUUID();
            // The store's no-tenant findById uses "system" for cross-tenant reads
            when(client.findById("system", DataCloudPatternStore.COLLECTION, id.toString()))
                    .thenReturn(Promise.of(Optional.empty()));

            Optional<PatternMetadata> result = store.findById(id).getResult();

            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // findByTenant
    // =========================================================================

    @Nested
    @DisplayName("findByTenant()")
    class FindByTenantTests {

        @Test
        @DisplayName("findByTenant: returns all matching entities for tenant")
        void findByTenant_returnsMatchingPatterns() {
            Entity e1 = entity(UUID.randomUUID().toString());
            Entity e2 = entity(UUID.randomUUID().toString());
            when(client.query(eq(TENANT), eq(DataCloudPatternStore.COLLECTION), any(Query.class)))
                    .thenReturn(Promise.of(List.of(e1, e2)));

            List<PatternMetadata> result = store.findByTenant(TENANT, PatternStatus.ACTIVE).getResult();

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("findByTenant: null status skips status filter")
        void findByTenant_withNullStatus_queriesWithoutStatusFilter() {
            when(client.query(eq(TENANT), eq(DataCloudPatternStore.COLLECTION), any(Query.class)))
                    .thenReturn(Promise.of(List.of()));

            List<PatternMetadata> result = store.findByTenant(TENANT, null).getResult();

            assertThat(result).isEmpty();
            verify(client).query(eq(TENANT), eq(DataCloudPatternStore.COLLECTION), any(Query.class));
        }
    }

    // =========================================================================
    // updateStatus
    // =========================================================================

    @Nested
    @DisplayName("updateStatus()")
    class UpdateStatusTests {

        @Test
        @DisplayName("updateStatus: read-modify-write updates status field")
        void updateStatus_whenEntityExists_updatesStatusField() {
            UUID id = UUID.randomUUID();
            Entity existing = entityWithData(id.toString(), Map.of(
                    "id", id.toString(), "tenantId", TENANT, "name", "P",
                    "status", "ACTIVE"));

            // The store uses "system" for the cross-tenant findById read
            when(client.findById("system", DataCloudPatternStore.COLLECTION, id.toString()))
                    .thenReturn(Promise.of(Optional.of(existing)));
            // Save uses the tenantId found in the entity's data (TENANT)
            when(client.save(eq(TENANT), eq(DataCloudPatternStore.COLLECTION), anyMap()))
                    .thenReturn(Promise.of(existing));

            store.updateStatus(id, PatternStatus.INACTIVE).getResult();

            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(client).save(eq(TENANT), eq(DataCloudPatternStore.COLLECTION), captor.capture());
            assertThat(captor.getValue().get("status")).isEqualTo("INACTIVE");
        }

        @Test
        @DisplayName("updateStatus: fails with exception when entity not found")
        void updateStatus_whenEntityMissing_returnsException() {
            UUID id = UUID.randomUUID();
            // The store uses "system" for the cross-tenant findById read
            when(client.findById("system", DataCloudPatternStore.COLLECTION, id.toString()))
                    .thenReturn(Promise.of(Optional.empty()));

            Promise<Void> result = store.updateStatus(id, PatternStatus.INACTIVE);

            assertThat(result.isException()).isTrue();
        }
    }

    // =========================================================================
    // delete
    // =========================================================================

    @Nested
    @DisplayName("delete()")
    class DeleteTests {

        @Test
        @DisplayName("delete: calls DataCloudClient.delete with correct args")
        void delete_callsClientDelete() {
            UUID id = UUID.randomUUID();
            // The store uses "system" as the tenant for cross-store deletes
            when(client.delete("system", DataCloudPatternStore.COLLECTION, id.toString()))
                    .thenReturn(Promise.of((Void) null));

            store.delete(id).getResult();

            verify(client).delete("system", DataCloudPatternStore.COLLECTION, id.toString());
        }
    }

    // =========================================================================
    // exists
    // =========================================================================

    @Nested
    @DisplayName("exists()")
    class ExistsTests {

        @Test
        @DisplayName("exists: returns true when entity present")
        void exists_whenEntityPresent_returnsTrue() {
            UUID id = UUID.randomUUID();
            // The store uses "system" for the cross-tenant exists check
            when(client.findById("system", DataCloudPatternStore.COLLECTION, id.toString()))
                    .thenReturn(Promise.of(Optional.of(entity(id.toString()))));

            Boolean exists = store.exists(id).getResult();

            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("exists: returns false when entity absent")
        void exists_whenEntityAbsent_returnsFalse() {
            UUID id = UUID.randomUUID();
            // The store uses "system" for the cross-tenant exists check
            when(client.findById("system", DataCloudPatternStore.COLLECTION, id.toString()))
                    .thenReturn(Promise.of(Optional.empty()));

            Boolean exists = store.exists(id).getResult();

            assertThat(exists).isFalse();
        }
    }

    // =========================================================================
    // constructor guard
    // =========================================================================

    @Test
    @DisplayName("constructor: throws NullPointerException when client is null")
    void constructor_withNullClient_throwsNpe() {
        assertThatNullPointerException()
                .isThrownBy(() -> new DataCloudPatternStore(null))
                .withMessageContaining("DataCloudClient");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static PatternSpecification.Builder spec(String name) {
        return new PatternSpecification.Builder()
                .tenantId(TENANT)
                .name(name)
                .status(PatternStatus.ACTIVE);
    }

    private static Entity entity(String id) {
        String resolvedId = id != null ? id : UUID.randomUUID().toString();
        return entityWithData(resolvedId, Map.of(
                "id",       resolvedId,
                "tenantId", TENANT,
                "name",     "Test Pattern",
                "status",   "ACTIVE",
                "createdAt", Instant.now().toString(),
                "updatedAt", Instant.now().toString()
        ));
    }

    private static Entity entityWithData(String id, Map<String, Object> data) {
        return Entity.of(id, DataCloudPatternStore.COLLECTION, data);
    }
}
