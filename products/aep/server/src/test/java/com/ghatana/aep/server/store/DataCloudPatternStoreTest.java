/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.server.store;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataCloudClient.Entity;
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
 * without requiring real storage. ActiveJ's synchronous {@link Promise#of(Object)} // GH-90000
 * factory is used to return stub values from mocks, so the tests can call
 * {@code .getResult()} directly (the promise is already resolved and never // GH-90000
 * touches an Eventloop).
 *
 * @doc.type class
 * @doc.purpose Unit tests for DataCloudPatternStore
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("DataCloudPatternStore [GH-90000]")
class DataCloudPatternStoreTest {

    private static final String TENANT = "tenant-alpha";

    @Mock
    private DataCloudClient client;

    private DataCloudPatternStore store;

    @BeforeEach
    void setUp() { // GH-90000
        store = new DataCloudPatternStore(client); // GH-90000
    }

    // =========================================================================
    // save
    // =========================================================================

    @Nested
    @DisplayName("save() [GH-90000]")
    class SaveTests {

        @Test
        @DisplayName("save: assigns new UUID when spec.id is null [GH-90000]")
        void save_whenSpecHasNoId_assignsUuid() { // GH-90000
            PatternSpecification spec = spec("Pattern A [GH-90000]").build();
            Entity fakeEntity = entity(null); // GH-90000

            when(client.save(eq(TENANT), eq(DataCloudPatternStore.COLLECTION), anyMap())) // GH-90000
                    .thenReturn(Promise.of(fakeEntity)); // GH-90000

            PatternMetadata result = store.save(spec).getResult(); // GH-90000

            ArgumentCaptor<Map<String, Object>> dataCaptor = mapCaptor(); // GH-90000
            verify(client).save(eq(TENANT), eq(DataCloudPatternStore.COLLECTION), dataCaptor.capture()); // GH-90000
            assertThat(dataCaptor.getValue().get("id [GH-90000]")).isNotNull();
            assertThat(result).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("save: preserves existing spec.id [GH-90000]")
        void save_whenSpecHasId_keepsId() { // GH-90000
            UUID existingId = UUID.randomUUID(); // GH-90000
            PatternSpecification spec = spec("Pattern B [GH-90000]").id(existingId).build();
            Entity fakeEntity = entity(existingId.toString()); // GH-90000

            when(client.save(eq(TENANT), eq(DataCloudPatternStore.COLLECTION), anyMap())) // GH-90000
                    .thenReturn(Promise.of(fakeEntity)); // GH-90000

            store.save(spec).getResult(); // GH-90000

            ArgumentCaptor<Map<String, Object>> captor = mapCaptor(); // GH-90000
            verify(client).save(eq(TENANT), eq(DataCloudPatternStore.COLLECTION), captor.capture()); // GH-90000
            assertThat(captor.getValue().get("id [GH-90000]")).isEqualTo(existingId.toString());
        }

        @Test
        @DisplayName("save: propagates client exception as failed promise [GH-90000]")
        void save_whenClientThrows_returnsFailedPromise() { // GH-90000
            PatternSpecification spec = spec("Pattern C [GH-90000]").build();
            when(client.save(anyString(), anyString(), anyMap())) // GH-90000
                    .thenReturn(Promise.ofException(new RuntimeException("storage failure [GH-90000]")));

            Promise<PatternMetadata> result = store.save(spec); // GH-90000

            assertThat(result.isException()).isTrue(); // GH-90000
        }
    }

    // =========================================================================
    // findById
    // =========================================================================

    @Nested
    @DisplayName("findById() [GH-90000]")
    class FindByIdTests {

        @Test
        @DisplayName("findById: returns populated Optional when entity exists [GH-90000]")
        void findById_whenEntityExists_returnsPresent() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            Entity e = entity(id.toString()); // GH-90000
            // The store's no-tenant findById uses "system" for cross-tenant reads
            when(client.findById("system", DataCloudPatternStore.COLLECTION, id.toString())) // GH-90000
                    .thenReturn(Promise.of(Optional.of(e))); // GH-90000

            Optional<PatternMetadata> result = store.findById(id).getResult(); // GH-90000

            assertThat(result).isPresent(); // GH-90000
        }

        @Test
        @DisplayName("findById: returns empty Optional when entity not found [GH-90000]")
        void findById_whenEntityMissing_returnsEmpty() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            // The store's no-tenant findById uses "system" for cross-tenant reads
            when(client.findById("system", DataCloudPatternStore.COLLECTION, id.toString())) // GH-90000
                    .thenReturn(Promise.of(Optional.empty())); // GH-90000

            Optional<PatternMetadata> result = store.findById(id).getResult(); // GH-90000

            assertThat(result).isEmpty(); // GH-90000
        }
    }

    // =========================================================================
    // findByTenant
    // =========================================================================

    @Nested
    @DisplayName("findByTenant() [GH-90000]")
    class FindByTenantTests {

        @Test
        @DisplayName("findByTenant: returns all matching entities for tenant [GH-90000]")
        void findByTenant_returnsMatchingPatterns() { // GH-90000
            Entity e1 = entity(UUID.randomUUID().toString()); // GH-90000
            Entity e2 = entity(UUID.randomUUID().toString()); // GH-90000
            when(client.query(eq(TENANT), eq(DataCloudPatternStore.COLLECTION), any(Query.class))) // GH-90000
                    .thenReturn(Promise.of(List.of(e1, e2))); // GH-90000

            List<PatternMetadata> result = store.findByTenant(TENANT, PatternStatus.ACTIVE).getResult(); // GH-90000

            assertThat(result).hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("findByTenant: null status skips status filter [GH-90000]")
        void findByTenant_withNullStatus_queriesWithoutStatusFilter() { // GH-90000
            when(client.query(eq(TENANT), eq(DataCloudPatternStore.COLLECTION), any(Query.class))) // GH-90000
                    .thenReturn(Promise.of(List.of())); // GH-90000

            List<PatternMetadata> result = store.findByTenant(TENANT, null).getResult(); // GH-90000

            assertThat(result).isEmpty(); // GH-90000
            verify(client).query(eq(TENANT), eq(DataCloudPatternStore.COLLECTION), any(Query.class)); // GH-90000
        }
    }

    // =========================================================================
    // updateStatus
    // =========================================================================

    @Nested
    @DisplayName("updateStatus() [GH-90000]")
    class UpdateStatusTests {

        @Test
        @DisplayName("updateStatus: read-modify-write updates status field [GH-90000]")
        void updateStatus_whenEntityExists_updatesStatusField() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            Entity existing = entityWithData(id.toString(), Map.of( // GH-90000
                    "id", id.toString(), "tenantId", TENANT, "name", "P", // GH-90000
                    "status", "ACTIVE"));

            // The store uses "system" for the cross-tenant findById read
            when(client.findById("system", DataCloudPatternStore.COLLECTION, id.toString())) // GH-90000
                    .thenReturn(Promise.of(Optional.of(existing))); // GH-90000
            // Save uses the tenantId found in the entity's data (TENANT) // GH-90000
            when(client.save(eq(TENANT), eq(DataCloudPatternStore.COLLECTION), anyMap())) // GH-90000
                    .thenReturn(Promise.of(existing)); // GH-90000

            store.updateStatus(id, PatternStatus.INACTIVE).getResult(); // GH-90000

            ArgumentCaptor<Map<String, Object>> captor = mapCaptor(); // GH-90000
            verify(client).save(eq(TENANT), eq(DataCloudPatternStore.COLLECTION), captor.capture()); // GH-90000
            assertThat(captor.getValue().get("status [GH-90000]")).isEqualTo("INACTIVE [GH-90000]");
        }

        @Test
        @DisplayName("updateStatus: fails with exception when entity not found [GH-90000]")
        void updateStatus_whenEntityMissing_returnsException() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            // The store uses "system" for the cross-tenant findById read
            when(client.findById("system", DataCloudPatternStore.COLLECTION, id.toString())) // GH-90000
                    .thenReturn(Promise.of(Optional.empty())); // GH-90000

            Promise<Void> result = store.updateStatus(id, PatternStatus.INACTIVE); // GH-90000

            assertThat(result.isException()).isTrue(); // GH-90000
        }
    }

    // =========================================================================
    // delete
    // =========================================================================

    @Nested
    @DisplayName("delete() [GH-90000]")
    class DeleteTests {

        @Test
        @DisplayName("delete: calls DataCloudClient.delete with correct args [GH-90000]")
        void delete_callsClientDelete() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            // The store uses "system" as the tenant for cross-store deletes
            when(client.delete("system", DataCloudPatternStore.COLLECTION, id.toString())) // GH-90000
                    .thenReturn(Promise.of((Void) null)); // GH-90000

            store.delete(id).getResult(); // GH-90000

            verify(client).delete("system", DataCloudPatternStore.COLLECTION, id.toString()); // GH-90000
        }

        @Test
        @DisplayName("delete(tenant,id): uses tenant-scoped delete for persisted patterns [GH-90000]")
        void deleteWithTenant_callsTenantScopedDelete() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            when(client.delete(TENANT, DataCloudPatternStore.COLLECTION, id.toString())) // GH-90000
                    .thenReturn(Promise.of((Void) null)); // GH-90000

            store.delete(TENANT, id).getResult(); // GH-90000

            verify(client).delete(TENANT, DataCloudPatternStore.COLLECTION, id.toString()); // GH-90000
        }
    }

    // =========================================================================
    // exists
    // =========================================================================

    @Nested
    @DisplayName("exists() [GH-90000]")
    class ExistsTests {

        @Test
        @DisplayName("exists: returns true when entity present [GH-90000]")
        void exists_whenEntityPresent_returnsTrue() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            // The store uses "system" for the cross-tenant exists check
            when(client.findById("system", DataCloudPatternStore.COLLECTION, id.toString())) // GH-90000
                    .thenReturn(Promise.of(Optional.of(entity(id.toString())))); // GH-90000

            Boolean exists = store.exists(id).getResult(); // GH-90000

            assertThat(exists).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("exists: returns false when entity absent [GH-90000]")
        void exists_whenEntityAbsent_returnsFalse() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            // The store uses "system" for the cross-tenant exists check
            when(client.findById("system", DataCloudPatternStore.COLLECTION, id.toString())) // GH-90000
                    .thenReturn(Promise.of(Optional.empty())); // GH-90000

            Boolean exists = store.exists(id).getResult(); // GH-90000

            assertThat(exists).isFalse(); // GH-90000
        }
    }

    // =========================================================================
    // constructor guard
    // =========================================================================

    @Test
    @DisplayName("constructor: throws NullPointerException when client is null [GH-90000]")
    void constructor_withNullClient_throwsNpe() { // GH-90000
        assertThatNullPointerException() // GH-90000
                .isThrownBy(() -> new DataCloudPatternStore(null)) // GH-90000
                .withMessageContaining("DataCloudClient [GH-90000]");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static PatternSpecification.Builder spec(String name) { // GH-90000
        return new PatternSpecification.Builder() // GH-90000
                .tenantId(TENANT) // GH-90000
                .name(name) // GH-90000
                .status(PatternStatus.ACTIVE); // GH-90000
    }

    @SuppressWarnings({"unchecked", "rawtypes"}) // GH-90000
    private static ArgumentCaptor<Map<String, Object>> mapCaptor() { // GH-90000
        return (ArgumentCaptor) ArgumentCaptor.forClass(Map.class); // GH-90000
    }

    private static Entity entity(String id) { // GH-90000
        String resolvedId = id != null ? id : UUID.randomUUID().toString(); // GH-90000
        return entityWithData(resolvedId, Map.of( // GH-90000
                "id",       resolvedId,
                "tenantId", TENANT,
                "name",     "Test Pattern",
                "status",   "ACTIVE",
                "createdAt", Instant.now().toString(), // GH-90000
                "updatedAt", Instant.now().toString() // GH-90000
        ));
    }

    private static Entity entityWithData(String id, Map<String, Object> data) { // GH-90000
        return Entity.of(id, DataCloudPatternStore.COLLECTION, data); // GH-90000
    }
}
