/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.launcher.store;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataCloudClient.Entity;
import com.ghatana.datacloud.DataCloudClient.Query;
import com.ghatana.pipeline.registry.model.Pipeline;
import com.ghatana.platform.core.common.pagination.Page;
import com.ghatana.platform.domain.auth.TenantId;
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
 * Unit tests for {@link DataCloudPipelineStore}.
 *
 * <p>Data-Cloud I/O is mocked via Mockito. The tests use ActiveJ's synchronous
 * {@link Promise#of(Object)} so no Eventloop is required.
 *
 * @doc.type class
 * @doc.purpose Unit tests for DataCloudPipelineStore
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DataCloudPipelineStore")
class DataCloudPipelineStoreTest {

    private static final TenantId TENANT = TenantId.of("tenant-beta");
    private static final String TENANT_STR = "tenant-beta";

    @Mock
    private DataCloudClient client;

    private DataCloudPipelineStore store;

    @BeforeEach
    void setUp() {
        store = new DataCloudPipelineStore(client);
    }

    // =========================================================================
    // save
    // =========================================================================

    @Nested
    @DisplayName("save()")
    class SaveTests {

        @Test
        @DisplayName("save: assigns UUID when pipeline.id is blank")
        void save_whenIdBlank_assignsUuid() {
            Pipeline p = pipeline(null);
            Entity saved = pipelineEntity(UUID.randomUUID().toString(), p);
            when(client.save(eq(TENANT_STR), eq(DataCloudPipelineStore.COLLECTION), anyMap()))
                    .thenReturn(Promise.of(saved));

            Pipeline result = store.save(p).getResult();

            ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
            verify(client).save(eq(TENANT_STR), eq(DataCloudPipelineStore.COLLECTION), cap.capture());
            assertThat(cap.getValue().get("id")).isNotNull().asString().isNotBlank();
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("save: preserves existing pipeline.id")
        void save_whenIdPresent_keepsId() {
            String id = UUID.randomUUID().toString();
            Pipeline p = pipeline(id);
            when(client.save(eq(TENANT_STR), eq(DataCloudPipelineStore.COLLECTION), anyMap()))
                    .thenReturn(Promise.of(pipelineEntity(id, p)));

            store.save(p).getResult();

            ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
            verify(client).save(eq(TENANT_STR), eq(DataCloudPipelineStore.COLLECTION), cap.capture());
            assertThat(cap.getValue().get("id")).isEqualTo(id);
        }
    }

    // =========================================================================
    // findById
    // =========================================================================

    @Nested
    @DisplayName("findById()")
    class FindByIdTests {

        @Test
        @DisplayName("findById: returns present Optional for same tenant entity")
        void findById_whenEntityBelongsToTenant_returnsPresent() {
            String id = UUID.randomUUID().toString();
            Pipeline source = pipeline(id);
            when(client.findById(TENANT_STR, DataCloudPipelineStore.COLLECTION, id))
                    .thenReturn(Promise.of(Optional.of(pipelineEntity(id, source))));

            Optional<Pipeline> result = store.findById(id, TENANT).getResult();

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("findById: returns empty when entity belongs to different tenant")
        void findById_whenEntityBelongsToDifferentTenant_returnsEmpty() {
            String id = UUID.randomUUID().toString();
            // Entity has tenantId "other-tenant" in data map
            Entity otherTenantEntity = Entity.of(id, DataCloudPipelineStore.COLLECTION,
                    Map.of("id", id, "tenantId", "other-tenant", "name", "P", "active", true,
                            "version", 1, "config", "{}", "createdAt", Instant.now().toString(),
                            "updatedAt", Instant.now().toString()));
            when(client.findById(TENANT_STR, DataCloudPipelineStore.COLLECTION, id))
                    .thenReturn(Promise.of(Optional.of(otherTenantEntity)));

            // The store filters by tenantId in the entity data
            Optional<Pipeline> result = store.findById(id, TENANT).getResult();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("findById: returns empty when not found")
        void findById_whenMissing_returnsEmpty() {
            String id = UUID.randomUUID().toString();
            when(client.findById(TENANT_STR, DataCloudPipelineStore.COLLECTION, id))
                    .thenReturn(Promise.of(Optional.empty()));

            Optional<Pipeline> result = store.findById(id, TENANT).getResult();

            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // findAll
    // =========================================================================

    @Nested
    @DisplayName("findAll()")
    class FindAllTests {

        @Test
        @DisplayName("findAll: pages results when count exceeds page size")
        void findAll_paginatesResults() {
            // Create 5 pipelines returned by DataCloud
            List<Entity> entities = java.util.stream.IntStream.range(0, 5)
                    .mapToObj(i -> {
                        String id = UUID.randomUUID().toString();
                        return pipelineEntity(id, pipelineNamed(id, "Pipeline-" + i));
                    })
                    .toList();
            when(client.query(eq(TENANT_STR), eq(DataCloudPipelineStore.COLLECTION), any(Query.class)))
                    .thenReturn(Promise.of(entities));

            // Page 1, size 3 → expect 3 items
            Page<Pipeline> page = store.findAll(TENANT, null, null, 1, 3).getResult();

            assertThat(page.content()).hasSize(3);
            assertThat(page.totalElements()).isEqualTo(5);
        }

        @Test
        @DisplayName("findAll: filters by name when nameFilter provided")
        void findAll_filtersbyName() {
            String matchId = UUID.randomUUID().toString();
            String noMatchId = UUID.randomUUID().toString();
            List<Entity> entities = List.of(
                    pipelineEntity(matchId, pipelineNamed(matchId, "fraud-detection")),
                    pipelineEntity(noMatchId, pipelineNamed(noMatchId, "click-stream"))
            );
            when(client.query(eq(TENANT_STR), eq(DataCloudPipelineStore.COLLECTION), any(Query.class)))
                    .thenReturn(Promise.of(entities));

            Page<Pipeline> page = store.findAll(TENANT, "fraud", null, 1, 10).getResult();

            assertThat(page.content()).hasSize(1);
            assertThat(page.content().get(0).getName()).contains("fraud");
        }
    }

    // =========================================================================
    // exists
    // =========================================================================

    @Test
    @DisplayName("exists: returns true when entity present")
    void exists_whenPresent_returnsTrue() {
        String id = UUID.randomUUID().toString();
        Pipeline p = pipeline(id);
        when(client.findById(TENANT_STR, DataCloudPipelineStore.COLLECTION, id))
                .thenReturn(Promise.of(Optional.of(pipelineEntity(id, p))));

        Boolean exists = store.exists(id, TENANT).getResult();

        assertThat(exists).isTrue();
    }

    // =========================================================================
    // nextVersion
    // =========================================================================

    @Test
    @DisplayName("nextVersion: returns 1 when no pipelines found")
    void nextVersion_whenNoPipelinesExist_returnsOne() {
        when(client.query(eq(TENANT_STR), eq(DataCloudPipelineStore.COLLECTION), any(Query.class)))
                .thenReturn(Promise.of(List.of()));

        Integer version = store.nextVersion("my-pipeline", TENANT).getResult();

        assertThat(version).isEqualTo(1);
    }

    @Test
    @DisplayName("nextVersion: returns max version + 1 when pipelines exist")
    void nextVersion_whenPipelinesExist_returnsMaxPlusOne() {
        String id1 = UUID.randomUUID().toString();
        String id2 = UUID.randomUUID().toString();
        Pipeline p1 = pipelineWithVersion(id1, 2);
        Pipeline p2 = pipelineWithVersion(id2, 5);
        when(client.query(eq(TENANT_STR), eq(DataCloudPipelineStore.COLLECTION), any(Query.class)))
                .thenReturn(Promise.of(List.of(pipelineEntity(id1, p1), pipelineEntity(id2, p2))));

        Integer version = store.nextVersion("my-pipeline", TENANT).getResult();

        assertThat(version).isEqualTo(6);
    }

    // =========================================================================
    // countLegacyConfigPipelines / countStructuredConfigPipelines
    // =========================================================================

    @Test
    @DisplayName("countLegacyConfigPipelines: counts pipelines without structuredConfig")
    void countLegacyConfigPipelines_countsLegacyOnes() {
        // Two legacy (config only, no structuredConfig) + one structured
        String id1 = UUID.randomUUID().toString();
        String id2 = UUID.randomUUID().toString();
        String id3 = UUID.randomUUID().toString();
        when(client.query(eq(TENANT_STR), eq(DataCloudPipelineStore.COLLECTION), any(Query.class)))
                .thenReturn(Promise.of(List.of(
                        pipelineEntity(id1, pipeline(id1)), // default: active, config set, no structuredConfig
                        pipelineEntity(id2, pipeline(id2)),
                        pipelineEntity(id3, pipeline(id3))
                )));

        // All three are legacy in our model (fromEntity never sets structuredConfig)
        Long count = store.countLegacyConfigPipelines(TENANT).getResult();

        assertThat(count).isEqualTo(3L);
    }

    @Test
    @DisplayName("countStructuredConfigPipelines: returns 0 when no structured pipelines")
    void countStructuredConfigPipelines_returnsZeroWhenNone() {
        when(client.query(eq(TENANT_STR), eq(DataCloudPipelineStore.COLLECTION), any(Query.class)))
                .thenReturn(Promise.of(List.of()));

        Long count = store.countStructuredConfigPipelines(TENANT).getResult();

        assertThat(count).isEqualTo(0L);
    }

    // =========================================================================
    // constructor guard
    // =========================================================================

    @Test
    @DisplayName("constructor: throws NullPointerException when client is null")
    void constructor_withNullClient_throwsNpe() {
        assertThatNullPointerException()
                .isThrownBy(() -> new DataCloudPipelineStore(null))
                .withMessageContaining("DataCloudClient");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static Pipeline pipeline(String id) {
        Pipeline p = new Pipeline();
        if (id != null) p.setId(id);
        p.setTenantId(TENANT);
        p.setName("test-pipeline");
        p.setVersion(1);
        p.setActive(true);
        p.setConfig("{\"steps\":[]}");
        p.setCreatedAt(Instant.now());
        p.setUpdatedAt(Instant.now());
        p.setCreatedBy("test");
        p.setUpdatedBy("test");
        return p;
    }

    private static Pipeline pipelineNamed(String id, String name) {
        Pipeline p = pipeline(id);
        p.setName(name);
        return p;
    }

    private static Pipeline pipelineWithVersion(String id, int version) {
        Pipeline p = pipeline(id);
        p.setVersion(version);
        return p;
    }

    private static Entity pipelineEntity(String id, Pipeline p) {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("id",          id);
        data.put("tenantId",    TENANT_STR);
        data.put("name",        p.getName() != null ? p.getName() : "test-pipeline");
        data.put("version",     p.getVersion());
        data.put("active",      p.isActive());
        data.put("config",      p.getConfig() != null ? p.getConfig() : "{}");
        data.put("createdAt",   Instant.now().toString());
        data.put("updatedAt",   Instant.now().toString());
        data.put("createdBy",   "test");
        data.put("updatedBy",   "test");
        return Entity.of(id, DataCloudPipelineStore.COLLECTION, data);
    }
}
