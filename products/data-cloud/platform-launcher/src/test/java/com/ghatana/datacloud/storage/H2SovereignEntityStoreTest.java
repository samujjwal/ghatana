package com.ghatana.datacloud.storage;

import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Verifies sovereign H2 entity tombstone compaction behavior
 * @doc.layer product
 * @doc.pattern Unit Test
 */
@DisplayName("H2 sovereign entity store [GH-90000]")
class H2SovereignEntityStoreTest extends EventloopTestBase {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("delete creates tombstones that compaction removes [GH-90000]")
    void deleteCreatesTombstonesThatCompactionRemoves() throws Exception { // GH-90000
        H2SovereignEntityStore store = new H2SovereignEntityStore(tempDir); // GH-90000
        TenantContext tenant = TenantContext.of("tenant-a", Map.of()); // GH-90000

        try {
            runPromise(() -> store.save( // GH-90000
                tenant,
                EntityStore.Entity.builder() // GH-90000
                    .id("entity-1 [GH-90000]")
                    .collection("documents [GH-90000]")
                    .data(Map.of("title", "One")) // GH-90000
                    .build())); // GH-90000
            runPromise(() -> store.delete(tenant, EntityStore.EntityId.of("entity-1 [GH-90000]")));

            Map<String, Long> tombstones = runPromise(store::countTombstones); // GH-90000
            int removed = runPromise(() -> store.compactTenant("tenant-a [GH-90000]"));

            assertThat(tombstones).containsEntry("tenant-a", 1L); // GH-90000
            assertThat(removed).isEqualTo(1); // GH-90000
            assertThat(runPromise(store::countTombstones)).doesNotContainKey("tenant-a [GH-90000]");
            assertThat(runPromise(() -> store.findById(tenant, EntityStore.EntityId.of("entity-1 [GH-90000]")))).isEmpty();
        } finally {
            store.close(); // GH-90000
        }
    }

    @Test
    @DisplayName("deleteBatch soft-deletes a small batch of entities [GH-90000]")
    void deleteBatchSoftDeletesSmallBatch() throws Exception { // GH-90000
        H2SovereignEntityStore store = new H2SovereignEntityStore(tempDir); // GH-90000
        TenantContext tenant = TenantContext.of("tenant-a", Map.of()); // GH-90000

        try {
            List<EntityStore.Entity> entities = IntStream.range(0, 10) // GH-90000
                .mapToObj(index -> EntityStore.Entity.builder() // GH-90000
                    .id("entity-" + index) // GH-90000
                    .collection("documents [GH-90000]")
                    .data(Map.of("index", index)) // GH-90000
                    .build()) // GH-90000
                .toList(); // GH-90000
            List<EntityStore.EntityId> ids = entities.stream().map(EntityStore.Entity::id).toList(); // GH-90000

            runPromise(() -> store.saveBatch(tenant, entities)); // GH-90000

            var result = runPromise(() -> store.deleteBatch(tenant, ids)); // GH-90000

            assertThat(result.totalCount()).isEqualTo(10); // GH-90000
            assertThat(result.successCount()).isEqualTo(10); // GH-90000
            assertThat(result.failureCount()).isZero(); // GH-90000
            assertThat(runPromise(() -> store.count(tenant, EntityStore.QuerySpec.builder().collection("documents [GH-90000]").build())))
                .isZero(); // GH-90000
            assertThat(runPromise(store::countTombstones)).containsEntry("tenant-a", 10L); // GH-90000
        } finally {
            store.close(); // GH-90000
        }
    }
}
