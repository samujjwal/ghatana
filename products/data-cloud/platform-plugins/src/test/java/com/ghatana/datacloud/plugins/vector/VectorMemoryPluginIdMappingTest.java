/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.plugins.vector;

import com.ghatana.datacloud.DataRecord;
import com.ghatana.datacloud.EntityRecord;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Explicit tests for the VectorMemoryPlugin ID key contract.
 *
 * <p>The plugin stores records keyed by {@code record.getId().toString()} (the DataRecord UUID),
 * NOT by any external entity string ID. These tests document and guard that contract so that
 * callers (e.g., SemanticSearchHandler) must translate external entity IDs to DataRecord UUIDs
 * before performing retrieve, delete, or findSimilar operations.
 *
 * @doc.type class
 * @doc.purpose Guard the VectorMemoryPlugin ID key contract (DataRecord UUID, not external entity ID)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("VectorMemoryPlugin – ID key mapping contract")
class VectorMemoryPluginIdMappingTest extends EventloopTestBase {

    private VectorMemoryPlugin plugin;

    @BeforeEach
    void setUp() {
        plugin = new VectorMemoryPlugin(); // default: dim=384, cosine distance
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private EntityRecord recordWithId(UUID id, Map<String, Object> metadata) {
        return EntityRecord.builder()
                .id(id)
                .tenantId("tenant-dc-l2")
                .collectionName("test-collection")
                .metadata(new HashMap<>(metadata))
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Store / Retrieve
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("retrieve by DataRecord UUID succeeds after store")
    void retrieveByRecordUuidSucceedsAfterStore() { // GH-90000
        UUID recordId = UUID.randomUUID();
        EntityRecord record = recordWithId(recordId, Map.of("sourceEntityId", "ext-abc"));

        runPromise(() -> plugin.store(record, "tenant-dc-l2"));

        Optional<DataRecord> result = runPromise(() -> plugin.retrieve(recordId.toString(), "tenant-dc-l2"));

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(recordId);
    }

    @Test
    @DisplayName("retrieve by external string entity ID returns empty – ID translation is required")
    void retrieveByExternalStringIdReturnsEmpty() { // GH-90000
        UUID recordId = UUID.randomUUID();
        String externalEntityId = "ext-entity-abc-123";
        EntityRecord record = recordWithId(recordId, Map.of("sourceEntityId", externalEntityId));

        runPromise(() -> plugin.store(record, "tenant-dc-l2"));

        // Caller must translate external ID to DataRecord UUID before lookup.
        // Passing the raw external string returns empty because the key does not exist.
        Optional<DataRecord> byExternalId = runPromise(
                () -> plugin.retrieve(externalEntityId, "tenant-dc-l2"));

        assertThat(byExternalId).isEmpty();
    }

    @Test
    @DisplayName("sourceEntityId preserved in record metadata is recoverable after store")
    void sourceEntityIdPreservedInMetadataAfterStore() { // GH-90000
        UUID recordId = UUID.randomUUID();
        String externalId = "preserve-me-007";
        EntityRecord record = recordWithId(recordId, Map.of("sourceEntityId", externalId));

        runPromise(() -> plugin.store(record, "tenant-dc-l2"));

        Optional<DataRecord> retrieved = runPromise(
                () -> plugin.retrieve(recordId.toString(), "tenant-dc-l2"));

        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getMetadata()).containsEntry("sourceEntityId", externalId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Delete
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete by DataRecord UUID removes the stored record")
    void deleteByRecordUuidRemovesRecord() { // GH-90000
        UUID recordId = UUID.randomUUID();
        runPromise(() -> plugin.store(recordWithId(recordId, Map.of()), "tenant-dc-l2"));

        boolean deleted = runPromise(() -> plugin.delete(recordId.toString(), "tenant-dc-l2"));
        Optional<DataRecord> after = runPromise(
                () -> plugin.retrieve(recordId.toString(), "tenant-dc-l2"));

        assertThat(deleted).isTrue();
        assertThat(after).isEmpty();
    }

    @Test
    @DisplayName("delete by external entity string ID is a no-op – record remains under its UUID key")
    void deleteByExternalEntityIdLeavesRecordIntact() { // GH-90000
        UUID recordId = UUID.randomUUID();
        String externalEntityId = "ext-entity-xyz";
        runPromise(() -> plugin.store(
                recordWithId(recordId, Map.of("sourceEntityId", externalEntityId)),
                "tenant-dc-l2"));

        // Caller using an untranslated external ID misses the storage key.
        boolean deletedByExternal = runPromise(
                () -> plugin.delete(externalEntityId, "tenant-dc-l2"));
        Optional<DataRecord> still = runPromise(
                () -> plugin.retrieve(recordId.toString(), "tenant-dc-l2"));

        assertThat(deletedByExternal).isFalse();
        assertThat(still).isPresent();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // findSimilar
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findSimilar by DataRecord UUID locates near neighbors")
    void findSimilarByRecordUuidLocatesNeighbors() { // GH-90000
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        float[] embA = {1f, 0f, 0f};
        float[] embB = {0.99f, 0.1f, 0f};

        runPromise(() -> plugin.storeWithEmbedding(recordWithId(idA, Map.of()), "tenant-dc-l2", embA));
        runPromise(() -> plugin.storeWithEmbedding(recordWithId(idB, Map.of()), "tenant-dc-l2", embB));

        SimilaritySearch.SearchResults results = runPromise(
                () -> plugin.findSimilar(idA.toString(), 1, true, "tenant-dc-l2"));

        assertThat(results.getResults()).hasSize(1);
        assertThat(results.getResults().get(0).getRecord().id()).isEqualTo(idB.toString());
    }

    @Test
    @DisplayName("findSimilar by external entity string ID returns empty – ID translation required")
    void findSimilarByExternalEntityIdReturnsEmpty() { // GH-90000
        UUID recordId = UUID.randomUUID();
        String externalId = "ext-sim-99";

        runPromise(() -> plugin.storeWithEmbedding(
                recordWithId(recordId, Map.of("sourceEntityId", externalId)),
                "tenant-dc-l2", new float[]{1f, 0f, 0f}));

        // Passing the external string directly to findSimilar finds no source record
        // and must return an empty result set rather than throwing.
        SimilaritySearch.SearchResults results = runPromise(
                () -> plugin.findSimilar(externalId, 5, false, "tenant-dc-l2"));

        assertThat(results.getResults()).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tenant isolation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("same DataRecord UUID in different tenants is independently stored and isolated")
    void sameUuidAcrossTenantsIsIsolated() { // GH-90000
        UUID sharedId = UUID.randomUUID();
        EntityRecord t1Record = recordWithId(sharedId, Map.of("sourceEntityId", "ent-t1"));
        EntityRecord t2Record = recordWithId(sharedId, Map.of("sourceEntityId", "ent-t2"));

        runPromise(() -> plugin.store(t1Record, "tenant-1"));
        runPromise(() -> plugin.store(t2Record, "tenant-2"));

        Optional<DataRecord> t1Result = runPromise(
                () -> plugin.retrieve(sharedId.toString(), "tenant-1"));
        Optional<DataRecord> t2Result = runPromise(
                () -> plugin.retrieve(sharedId.toString(), "tenant-2"));

        assertThat(t1Result).isPresent();
        assertThat(t1Result.get().getMetadata()).containsEntry("sourceEntityId", "ent-t1");
        assertThat(t2Result).isPresent();
        assertThat(t2Result.get().getMetadata()).containsEntry("sourceEntityId", "ent-t2");
    }
}
