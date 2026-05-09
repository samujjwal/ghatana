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
@DisplayName("H2 sovereign entity store")
class H2SovereignEntityStoreTest extends EventloopTestBase {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("delete creates tombstones that compaction removes")
    void deleteCreatesTombstonesThatCompactionRemoves() throws Exception { 
        H2SovereignEntityStore store = new H2SovereignEntityStore(tempDir); 
        TenantContext tenant = TenantContext.of("tenant-a", Map.of()); 

        try {
            runPromise(() -> store.save( 
                tenant,
                EntityStore.Entity.builder() 
                    .id("entity-1")
                    .collection("documents")
                    .data(Map.of("title", "One")) 
                    .build())); 
            runPromise(() -> store.deleteByRef(
                tenant,
                EntityStore.EntityRef.of("documents", "entity-1")));

            Map<String, Long> tombstones = runPromise(store::countTombstones); 
            int removed = runPromise(() -> store.compactTenant("tenant-a"));

            assertThat(tombstones).containsEntry("tenant-a", 1L); 
            assertThat(removed).isEqualTo(1); 
            assertThat(runPromise(store::countTombstones)).doesNotContainKey("tenant-a");
            assertThat(runPromise(() -> store.findByRef(tenant, EntityStore.EntityRef.of("documents", "entity-1"))))
                .isEmpty();
        } finally {
            store.close(); 
        }
    }

    @Test
    @DisplayName("deleteBatch soft-deletes a small batch of entities")
    void deleteBatchSoftDeletesSmallBatch() throws Exception { 
        H2SovereignEntityStore store = new H2SovereignEntityStore(tempDir); 
        TenantContext tenant = TenantContext.of("tenant-a", Map.of()); 

        try {
            List<EntityStore.Entity> entities = IntStream.range(0, 10) 
                .mapToObj(index -> EntityStore.Entity.builder() 
                    .id("entity-" + index) 
                    .collection("documents")
                    .data(Map.of("index", index)) 
                    .build()) 
                .toList(); 
            List<EntityStore.EntityRef> refs = entities.stream()
                .map(entity -> EntityStore.EntityRef.of("documents", entity.id().value()))
                .toList();

            runPromise(() -> store.saveBatch(tenant, entities)); 

            var result = runPromise(() -> store.deleteByRefs(tenant, refs)); 

            assertThat(result.totalCount()).isEqualTo(10); 
            assertThat(result.successCount()).isEqualTo(10); 
            assertThat(result.failureCount()).isZero(); 
            assertThat(runPromise(() -> store.count(tenant, EntityStore.QuerySpec.builder().collection("documents").build())))
                .isZero(); 
            assertThat(runPromise(store::countTombstones)).containsEntry("tenant-a", 10L); 
        } finally {
            store.close(); 
        }
    }
}
