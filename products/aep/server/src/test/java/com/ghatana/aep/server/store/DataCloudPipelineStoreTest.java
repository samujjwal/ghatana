/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.server.store;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataCloudClient.Entity;
import com.ghatana.datacloud.DataCloudClient.Query;
import com.ghatana.pipeline.registry.model.Pipeline;
import com.ghatana.pipeline.registry.model.PipelineRegistration;
import com.ghatana.pipeline.registry.model.PipelineVersionStatus;
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
 * {@link Promise#of(Object)} so no Eventloop is required. // GH-90000
 *
 * @doc.type class
 * @doc.purpose Unit tests for DataCloudPipelineStore
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("DataCloudPipelineStore")
class DataCloudPipelineStoreTest {

    private static final TenantId TENANT = TenantId.of("tenant-beta");
    private static final String TENANT_STR = "tenant-beta";

    @Mock
    private DataCloudClient client;

    private DataCloudPipelineStore store;

    @BeforeEach
    void setUp() { // GH-90000
        store = new DataCloudPipelineStore(client); // GH-90000
    }

    // =========================================================================
    // save
    // =========================================================================

    @Nested
    @DisplayName("save()")
    class SaveTests {

        @Test
        @DisplayName("save: assigns UUID when pipeline.id is blank")
        void save_whenIdBlank_assignsUuid() { // GH-90000
            Pipeline p = pipeline(null); // GH-90000
            Entity saved = pipelineEntity(UUID.randomUUID().toString(), p); // GH-90000
            when(client.save(eq(TENANT_STR), eq(DataCloudPipelineStore.COLLECTION), anyMap())) // GH-90000
                    .thenReturn(Promise.of(saved)); // GH-90000

            Pipeline result = store.save(p).getResult(); // GH-90000

            ArgumentCaptor<Map<String, Object>> cap = mapCaptor(); // GH-90000
            verify(client).save(eq(TENANT_STR), eq(DataCloudPipelineStore.COLLECTION), cap.capture()); // GH-90000
            assertThat(cap.getValue().get("id")).isNotNull().asString().isNotBlank();
            assertThat(result).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("save: preserves existing pipeline.id")
        void save_whenIdPresent_keepsId() { // GH-90000
            String id = UUID.randomUUID().toString(); // GH-90000
            Pipeline p = pipeline(id); // GH-90000
            when(client.save(eq(TENANT_STR), eq(DataCloudPipelineStore.COLLECTION), anyMap())) // GH-90000
                    .thenReturn(Promise.of(pipelineEntity(id, p))); // GH-90000

            store.save(p).getResult(); // GH-90000

            ArgumentCaptor<Map<String, Object>> cap = mapCaptor(); // GH-90000
            verify(client).save(eq(TENANT_STR), eq(DataCloudPipelineStore.COLLECTION), cap.capture()); // GH-90000
            assertThat(cap.getValue().get("id")).isEqualTo(id);
        }

        @Test
        @DisplayName("save: persists version label and status metadata")
        void save_persistsVersionMetadata() { // GH-90000
            String id = UUID.randomUUID().toString(); // GH-90000
            Pipeline p = pipeline(id); // GH-90000
            p.setVersionLabel("release-2026-04");
            p.setVersionStatus(PipelineVersionStatus.PUBLISHED); // GH-90000
            p.setVersionControl(7L); // GH-90000
            when(client.save(eq(TENANT_STR), eq(DataCloudPipelineStore.COLLECTION), anyMap())) // GH-90000
                    .thenReturn(Promise.of(pipelineEntity(id, p))); // GH-90000

            store.save(p).getResult(); // GH-90000

            ArgumentCaptor<Map<String, Object>> cap = mapCaptor(); // GH-90000
            verify(client).save(eq(TENANT_STR), eq(DataCloudPipelineStore.COLLECTION), cap.capture()); // GH-90000
            assertThat(cap.getValue()) // GH-90000
                    .containsEntry("versionLabel", "release-2026-04") // GH-90000
                    .containsEntry("versionStatus", PipelineVersionStatus.PUBLISHED.name()) // GH-90000
                    .containsEntry("versionControl", 7L); // GH-90000
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
        void findById_whenEntityBelongsToTenant_returnsPresent() { // GH-90000
            String id = UUID.randomUUID().toString(); // GH-90000
            Pipeline source = pipeline(id); // GH-90000
            when(client.findById(TENANT_STR, DataCloudPipelineStore.COLLECTION, id)) // GH-90000
                    .thenReturn(Promise.of(Optional.of(pipelineEntity(id, source)))); // GH-90000

            Optional<Pipeline> result = store.findById(id, TENANT).getResult(); // GH-90000

            assertThat(result).isPresent(); // GH-90000
        }

        @Test
        @DisplayName("findById: returns empty when entity belongs to different tenant")
        void findById_whenEntityBelongsToDifferentTenant_returnsEmpty() { // GH-90000
            String id = UUID.randomUUID().toString(); // GH-90000
            // Entity has tenantId "other-tenant" in data map
            Entity otherTenantEntity = Entity.of(id, DataCloudPipelineStore.COLLECTION, // GH-90000
                    Map.of("id", id, "tenantId", "other-tenant", "name", "P", "active", true, // GH-90000
                            "version", 1, "config", "{}", "createdAt", Instant.now().toString(), // GH-90000
                            "updatedAt", Instant.now().toString())); // GH-90000
            when(client.findById(TENANT_STR, DataCloudPipelineStore.COLLECTION, id)) // GH-90000
                    .thenReturn(Promise.of(Optional.of(otherTenantEntity))); // GH-90000

            // The store filters by tenantId in the entity data
            Optional<Pipeline> result = store.findById(id, TENANT).getResult(); // GH-90000

            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("findById: returns empty when not found")
        void findById_whenMissing_returnsEmpty() { // GH-90000
            String id = UUID.randomUUID().toString(); // GH-90000
            when(client.findById(TENANT_STR, DataCloudPipelineStore.COLLECTION, id)) // GH-90000
                    .thenReturn(Promise.of(Optional.empty())); // GH-90000

            Optional<Pipeline> result = store.findById(id, TENANT).getResult(); // GH-90000

            assertThat(result).isEmpty(); // GH-90000
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
        void findAll_paginatesResults() { // GH-90000
            // Create 5 pipelines returned by DataCloud
            List<Entity> entities = java.util.stream.IntStream.range(0, 5) // GH-90000
                    .mapToObj(i -> { // GH-90000
                        String id = UUID.randomUUID().toString(); // GH-90000
                        return pipelineEntity(id, pipelineNamed(id, "Pipeline-" + i)); // GH-90000
                    })
                    .toList(); // GH-90000
            when(client.query(eq(TENANT_STR), eq(DataCloudPipelineStore.COLLECTION), any(Query.class))) // GH-90000
                    .thenReturn(Promise.of(entities)); // GH-90000

            // Page 1, size 3 → expect 3 items
            Page<Pipeline> page = store.findAll(TENANT, null, null, 1, 3).getResult(); // GH-90000

            assertThat(page.content()).hasSize(3); // GH-90000
            assertThat(page.totalElements()).isEqualTo(5); // GH-90000
        }

        @Test
        @DisplayName("findAll: filters by name when nameFilter provided")
        void findAll_filtersbyName() { // GH-90000
            String matchId = UUID.randomUUID().toString(); // GH-90000
            String noMatchId = UUID.randomUUID().toString(); // GH-90000
            List<Entity> entities = List.of( // GH-90000
                    pipelineEntity(matchId, pipelineNamed(matchId, "fraud-detection")), // GH-90000
                    pipelineEntity(noMatchId, pipelineNamed(noMatchId, "click-stream")) // GH-90000
            );
            when(client.query(eq(TENANT_STR), eq(DataCloudPipelineStore.COLLECTION), any(Query.class))) // GH-90000
                    .thenReturn(Promise.of(entities)); // GH-90000

            Page<Pipeline> page = store.findAll(TENANT, "fraud", null, 1, 10).getResult(); // GH-90000

            assertThat(page.content()).hasSize(1); // GH-90000
            assertThat(page.content().get(0).getName()).contains("fraud");
        }
    }

    // =========================================================================
    // exists
    // =========================================================================

    @Test
    @DisplayName("exists: returns true when entity present")
    void exists_whenPresent_returnsTrue() { // GH-90000
        String id = UUID.randomUUID().toString(); // GH-90000
        Pipeline p = pipeline(id); // GH-90000
        when(client.findById(TENANT_STR, DataCloudPipelineStore.COLLECTION, id)) // GH-90000
                .thenReturn(Promise.of(Optional.of(pipelineEntity(id, p)))); // GH-90000

        Boolean exists = store.exists(id, TENANT).getResult(); // GH-90000

        assertThat(exists).isTrue(); // GH-90000
    }

    // =========================================================================
    // nextVersion
    // =========================================================================

    @Test
    @DisplayName("nextVersion: returns 1 when no pipelines found")
    void nextVersion_whenNoPipelinesExist_returnsOne() { // GH-90000
        when(client.query(eq(TENANT_STR), eq(DataCloudPipelineStore.COLLECTION), any(Query.class))) // GH-90000
                .thenReturn(Promise.of(List.of())); // GH-90000

        Integer version = store.nextVersion("my-pipeline", TENANT).getResult(); // GH-90000

        assertThat(version).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("nextVersion: returns max version + 1 when pipelines exist")
    void nextVersion_whenPipelinesExist_returnsMaxPlusOne() { // GH-90000
        String id1 = UUID.randomUUID().toString(); // GH-90000
        String id2 = UUID.randomUUID().toString(); // GH-90000
        Pipeline p1 = pipelineWithVersion(id1, 2); // GH-90000
        Pipeline p2 = pipelineWithVersion(id2, 5); // GH-90000
        when(client.query(eq(TENANT_STR), eq(DataCloudPipelineStore.COLLECTION), any(Query.class))) // GH-90000
                .thenReturn(Promise.of(List.of(pipelineEntity(id1, p1), pipelineEntity(id2, p2)))); // GH-90000

        Integer version = store.nextVersion("my-pipeline", TENANT).getResult(); // GH-90000

        assertThat(version).isEqualTo(6); // GH-90000
    }

    // =========================================================================
    // countLegacyConfigPipelines / countStructuredConfigPipelines
    // =========================================================================

    @Test
    @DisplayName("countLegacyConfigPipelines: counts pipelines without structuredConfig")
    void countLegacyConfigPipelines_countsLegacyOnes() { // GH-90000
        // Two legacy (config only, no structuredConfig) + one structured // GH-90000
        String id1 = UUID.randomUUID().toString(); // GH-90000
        String id2 = UUID.randomUUID().toString(); // GH-90000
        String id3 = UUID.randomUUID().toString(); // GH-90000
        when(client.query(eq(TENANT_STR), eq(DataCloudPipelineStore.COLLECTION), any(Query.class))) // GH-90000
                .thenReturn(Promise.of(List.of( // GH-90000
                        pipelineEntity(id1, pipeline(id1)), // default: active, config set, no structuredConfig // GH-90000
                        pipelineEntity(id2, pipeline(id2)), // GH-90000
                        pipelineEntity(id3, pipeline(id3)) // GH-90000
                )));

        // All three are legacy in our model (fromEntity never sets structuredConfig) // GH-90000
        Long count = store.countLegacyConfigPipelines(TENANT).getResult(); // GH-90000

        assertThat(count).isEqualTo(3L); // GH-90000
    }

    @Test
    @DisplayName("countStructuredConfigPipelines: returns 0 when no structured pipelines")
    void countStructuredConfigPipelines_returnsZeroWhenNone() { // GH-90000
        when(client.query(eq(TENANT_STR), eq(DataCloudPipelineStore.COLLECTION), any(Query.class))) // GH-90000
                .thenReturn(Promise.of(List.of())); // GH-90000

        Long count = store.countStructuredConfigPipelines(TENANT).getResult(); // GH-90000

        assertThat(count).isEqualTo(0L); // GH-90000
    }

        // =========================================================================
        // Version snapshots
        // =========================================================================

        @Test
        @DisplayName("saveVersionSnapshot: writes snapshot into version collection with stable composite id")
        void saveVersionSnapshot_writesToVersionCollection() { // GH-90000
        String pipelineId = "pipeline-123";
        Pipeline snapshot = pipelineWithVersion(pipelineId, 3); // GH-90000
        snapshot.setVersionLabel("v3.0.0");
        snapshot.setVersionStatus(PipelineVersionStatus.PUBLISHED); // GH-90000
        when(client.save(eq(TENANT_STR), eq(DataCloudPipelineStore.VERSION_COLLECTION), anyMap())) // GH-90000
            .thenReturn(Promise.of(versionEntity(pipelineId, snapshot))); // GH-90000

        store.saveVersionSnapshot(pipelineId, snapshot).getResult(); // GH-90000

        ArgumentCaptor<Map<String, Object>> cap = mapCaptor(); // GH-90000
        verify(client).save(eq(TENANT_STR), eq(DataCloudPipelineStore.VERSION_COLLECTION), cap.capture()); // GH-90000
        assertThat(cap.getValue()) // GH-90000
            .containsEntry("id", pipelineId + ":v3") // GH-90000
            .containsEntry("pipelineId", pipelineId) // GH-90000
            .containsEntry("snapshotVersion", 3) // GH-90000
            .containsEntry("versionLabel", "v3.0.0") // GH-90000
            .containsEntry("versionStatus", PipelineVersionStatus.PUBLISHED.name()); // GH-90000
        }

        @Test
        @DisplayName("findVersionHistory: returns snapshots sorted by version")
        void findVersionHistory_returnsSortedSnapshots() { // GH-90000
        String pipelineId = "versioned-pipeline";
        Pipeline v3 = pipelineWithVersion(pipelineId, 3); // GH-90000
        v3.setVersionLabel("v3");
        Pipeline v1 = pipelineWithVersion(pipelineId, 1); // GH-90000
        v1.setVersionLabel("v1");
        when(client.query(eq(TENANT_STR), eq(DataCloudPipelineStore.VERSION_COLLECTION), any(Query.class))) // GH-90000
            .thenReturn(Promise.of(List.of(versionEntity(pipelineId, v3), versionEntity(pipelineId, v1)))); // GH-90000

        List<PipelineRegistration> history = store.findVersionHistory(pipelineId, TENANT_STR).getResult(); // GH-90000

        assertThat(history) // GH-90000
            .extracting(PipelineRegistration::getVersion) // GH-90000
            .containsExactly(1, 3); // GH-90000
        assertThat(history) // GH-90000
            .extracting(PipelineRegistration::getVersionLabel) // GH-90000
            .containsExactly("v1", "v3"); // GH-90000
        }

        @Test
        @DisplayName("findVersionSnapshot: returns snapshot with original pipeline id and version metadata")
        void findVersionSnapshot_returnsSnapshot() { // GH-90000
        String pipelineId = "pipeline-rollback";
        Pipeline snapshot = pipelineWithVersion(pipelineId, 4); // GH-90000
        snapshot.setVersionLabel("release-4");
        snapshot.setVersionStatus(PipelineVersionStatus.ARCHIVED); // GH-90000
        when(client.findById(TENANT_STR, DataCloudPipelineStore.VERSION_COLLECTION, pipelineId + ":v4")) // GH-90000
            .thenReturn(Promise.of(Optional.of(versionEntity(pipelineId, snapshot)))); // GH-90000

        Optional<PipelineRegistration> result = store.findVersionSnapshot(pipelineId, 4, TENANT_STR).getResult(); // GH-90000

        assertThat(result).isPresent(); // GH-90000
        assertThat(result.get().getId()).isEqualTo(pipelineId); // GH-90000
        assertThat(result.get().getVersion()).isEqualTo(4); // GH-90000
        assertThat(result.get().getVersionLabel()).isEqualTo("release-4");
        assertThat(result.get().getVersionStatus()).isEqualTo(PipelineVersionStatus.ARCHIVED); // GH-90000
        }

    // =========================================================================
    // constructor guard
    // =========================================================================

    @Test
    @DisplayName("constructor: throws NullPointerException when client is null")
    void constructor_withNullClient_throwsNpe() { // GH-90000
        assertThatNullPointerException() // GH-90000
                .isThrownBy(() -> new DataCloudPipelineStore(null)) // GH-90000
                .withMessageContaining("DataCloudClient");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    @SuppressWarnings({"unchecked", "rawtypes"}) // GH-90000
    private static ArgumentCaptor<Map<String, Object>> mapCaptor() { // GH-90000
        return (ArgumentCaptor) ArgumentCaptor.forClass(Map.class); // GH-90000
    }

    private static Pipeline pipeline(String id) { // GH-90000
        Pipeline p = new Pipeline(); // GH-90000
        if (id != null) p.setId(id); // GH-90000
        p.setTenantId(TENANT); // GH-90000
        p.setName("test-pipeline");
        p.setVersion(1); // GH-90000
        p.setActive(true); // GH-90000
        p.setConfig("{\"steps\":[]}"); // GH-90000
        p.setCreatedAt(Instant.now()); // GH-90000
        p.setUpdatedAt(Instant.now()); // GH-90000
        p.setCreatedBy("test");
        p.setUpdatedBy("test");
        p.setVersionLabel("");
        p.setVersionStatus(PipelineVersionStatus.DRAFT); // GH-90000
        p.setVersionControl(0L); // GH-90000
        return p;
    }

    private static Pipeline pipelineNamed(String id, String name) { // GH-90000
        Pipeline p = pipeline(id); // GH-90000
        p.setName(name); // GH-90000
        return p;
    }

    private static Pipeline pipelineWithVersion(String id, int version) { // GH-90000
        Pipeline p = pipeline(id); // GH-90000
        p.setVersion(version); // GH-90000
        return p;
    }

    private static Entity pipelineEntity(String id, Pipeline p) { // GH-90000
        Map<String, Object> data = new java.util.HashMap<>(); // GH-90000
        data.put("id",          id); // GH-90000
        data.put("tenantId",    TENANT_STR); // GH-90000
        data.put("name",        p.getName() != null ? p.getName() : "test-pipeline"); // GH-90000
        data.put("version",     p.getVersion()); // GH-90000
        data.put("active",      p.isActive()); // GH-90000
        data.put("config",      p.getConfig() != null ? p.getConfig() : "{}"); // GH-90000
        data.put("createdAt",   Instant.now().toString()); // GH-90000
        data.put("updatedAt",   Instant.now().toString()); // GH-90000
        data.put("createdBy",   "test"); // GH-90000
        data.put("updatedBy",   "test"); // GH-90000
        data.put("versionLabel", p.getVersionLabel() != null ? p.getVersionLabel() : ""); // GH-90000
        data.put("versionStatus", p.getVersionStatus() != null ? p.getVersionStatus().name() : PipelineVersionStatus.DRAFT.name()); // GH-90000
        data.put("versionControl", p.getVersionControl()); // GH-90000
        return Entity.of(id, DataCloudPipelineStore.COLLECTION, data); // GH-90000
    }

    private static Entity versionEntity(String pipelineId, Pipeline p) { // GH-90000
        Map<String, Object> data = new java.util.HashMap<>(); // GH-90000
        data.put("id", pipelineId + ":v" + p.getVersion()); // GH-90000
        data.put("pipelineId", pipelineId); // GH-90000
        data.put("tenantId", TENANT_STR); // GH-90000
        data.put("name", p.getName() != null ? p.getName() : "test-pipeline"); // GH-90000
        data.put("version", p.getVersion()); // GH-90000
        data.put("snapshotVersion", p.getVersion()); // GH-90000
        data.put("active", p.isActive()); // GH-90000
        data.put("config", p.getConfig() != null ? p.getConfig() : "{}"); // GH-90000
        data.put("createdAt", Instant.now().toString()); // GH-90000
        data.put("updatedAt", Instant.now().toString()); // GH-90000
        data.put("createdBy", "test"); // GH-90000
        data.put("updatedBy", "test"); // GH-90000
        data.put("versionLabel", p.getVersionLabel() != null ? p.getVersionLabel() : ""); // GH-90000
        data.put("versionStatus", p.getVersionStatus() != null ? p.getVersionStatus().name() : PipelineVersionStatus.DRAFT.name()); // GH-90000
        data.put("versionControl", p.getVersionControl()); // GH-90000
        return Entity.of(pipelineId + ":v" + p.getVersion(), DataCloudPipelineStore.VERSION_COLLECTION, data); // GH-90000
    }
}
