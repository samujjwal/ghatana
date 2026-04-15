package com.ghatana.datacloud.storage;

import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

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
            runPromise(() -> store.delete(tenant, EntityStore.EntityId.of("entity-1")));

            Map<String, Long> tombstones = runPromise(store::countTombstones);
            int removed = runPromise(() -> store.compactTenant("tenant-a"));

            assertThat(tombstones).containsEntry("tenant-a", 1L);
            assertThat(removed).isEqualTo(1);
            assertThat(runPromise(store::countTombstones)).doesNotContainKey("tenant-a");
            assertThat(runPromise(() -> store.findById(tenant, EntityStore.EntityId.of("entity-1")))).isEmpty();
        } finally {
            store.close();
        }
    }
}
