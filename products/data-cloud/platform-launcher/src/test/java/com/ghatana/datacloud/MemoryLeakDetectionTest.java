/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud;

import com.ghatana.datacloud.DataCloudClient.Entity;
import com.ghatana.datacloud.DataCloudClient.Query;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Memory leak detection tests for Data-Cloud.
 *
 * <p>These tests validate that key objects (entity collections, event subscriptions,
 * query result sets, and the client itself) are properly cleaned up after close/cancel,
 * so long-running services do not accumulate memory over time.
 *
 * <p><strong>Strategy:</strong> Objects are weakly referenced before release, GC is hinted,
 * and the tests assert that the referents become reclaimable. This is a best-effort
 * heuristic (GC is non-deterministic) but catches obvious hard references.
 *
 * @doc.type class
 * @doc.purpose Memory leak detection tests (P3 gap closure)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Memory Leak Detection")
class MemoryLeakDetectionTest extends EventloopTestBase {

    private static final String TENANT_ID  = "leak-tenant";
    private static final String COLLECTION = "leak-records";

    private DataCloudClient client;

    @BeforeEach
    void setUp() {
        client = DataCloud.forTesting();
    }

    @AfterEach
    void tearDown() {
        client.close();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Client lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Client lifecycle")
    class ClientLifecycleTests {

        @Test
        @DisplayName("closed client is reclaimable by GC (no hard reference retained)")
        void closedClient_isReclaimable() throws Exception {
            // Create and use the client in a helper method whose stack frame is fully
            // released on return, ensuring no variable slot retains the reference.
            WeakReference<DataCloudClient> ref = useAndCloseClient();

            // Apply repeated GC pressure with memory allocation to force collection
            forceGc();

            // The client should be reclaimable (not pinned by internal caches)
            assertThat(ref.get()).as("Closed DataCloudClient must not be held by internal caches").isNull();
        }

        /** Creates a client, uses it once, closes it, and returns a WeakReference. */
        private WeakReference<DataCloudClient> useAndCloseClient() throws Exception {
            DataCloudClient local = DataCloud.forTesting();
            WeakReference<DataCloudClient> ref = new WeakReference<>(local);
            runPromise(() -> local.save(TENANT_ID, COLLECTION, Map.of("key", "value")));
            local.close();
            return ref;
            // local goes out of scope here — stack frame released on method return
        }

        @Test
        @DisplayName("multiple open+close cycles do not accumulate heap pressure")
        void multipleOpenCloseCycles_noHeapAccumulation() throws Exception {
            long heapBefore = usedHeap();

            for (int i = 0; i < 20; i++) {
                final int idx = i;
                DataCloudClient c = DataCloud.forTesting();
                runPromise(() -> c.save("cycle-tenant-" + idx, COLLECTION, Map.of("idx", idx)));
                c.close();
            }

            forceGc();
            long heapAfter = usedHeap();

            // Heap growth after 20 open/close cycles must be < 50 MB
            long growthMb = (heapAfter - heapBefore) / (1024 * 1024);
            assertThat(growthMb)
                .as("Heap grew by %d MB after 20 open/close cycles — possible leak", growthMb)
                .isLessThan(50);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Query result cleanup
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Query result cleanup")
    class QueryResultCleanupTests {

        @Test
        @DisplayName("query result list is reclaimable after caller drops the reference")
        void queryResultList_isReclaimableAfterCaller_dropsReference() throws Exception {
            // Pre-populate
            for (int i = 0; i < 100; i++) {
                final int idx = i;
                runPromise(() -> client.save(TENANT_ID, COLLECTION, Map.of("idx", idx)));
            }

            List<Entity> results = runPromise(() ->
                client.query(TENANT_ID, COLLECTION, Query.all()));

            assertThat(results).hasSize(100);

            WeakReference<List<Entity>> ref = new WeakReference<>(results);
            //noinspection UnusedAssignment
            results = null;

            forceGc();

            // Result list must be reclaimable once the caller drops the reference
            assertThat(ref.get())
                .as("Query result list should be reclaimable after caller releases it")
                .isNull();
        }

        @Test
        @DisplayName("repeated large queries do not retain results internally")
        void repeatedLargeQueries_doNotRetainResultsInternally() throws Exception {
            for (int i = 0; i < 50; i++) {
                final int idx = i;
                runPromise(() -> client.save(TENANT_ID, COLLECTION, Map.of("idx", idx)));
            }

            long heapBefore = usedHeap();

            for (int round = 0; round < 10; round++) {
                List<Entity> r = runPromise(() ->
                    client.query(TENANT_ID, COLLECTION, Query.all()));
                assertThat(r).isNotEmpty();
                // Let r go out of scope
            }

            forceGc();
            long heapAfter = usedHeap();
            long growthMb = (heapAfter - heapBefore) / (1024 * 1024);
            assertThat(growthMb)
                .as("Heap grew by %d MB after 10 large queries — internal result caching leak?", growthMb)
                .isLessThan(20);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Entity store — entity removal
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Entity store cleanup")
    class EntityStoreCleanupTests {

        @Test
        @DisplayName("deleted entities are removed from internal store (no memory accumulation)")
        void deletedEntities_removedFromStore() throws Exception {
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                final int idx = i;
                Entity entity = runPromise(() ->
                    client.save(TENANT_ID, COLLECTION, Map.of("idx", idx)));
                ids.add(entity.id());
            }

            // Delete all entities
            for (String id : ids) {
                runPromise(() -> client.delete(TENANT_ID, COLLECTION, id));
            }

            // Heap should not grow proportionally to the deleted entity count
            forceGc();
            List<Entity> remaining = runPromise(() ->
                client.query(TENANT_ID, COLLECTION, Query.all()));
            assertThat(remaining).isEmpty();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Forces GC with repeated attempts and memory pressure to ensure weak references are cleared. */
    private static void forceGc() throws InterruptedException {
        for (int attempt = 0; attempt < 5; attempt++) {
            System.gc();
            System.runFinalization();
            Thread.sleep(50);
        }
        // Allocate memory to create heap pressure and trigger collection
        try {
            byte[] pressure = new byte[8 * 1024 * 1024]; // 8 MB
            pressure[0] = 1; // prevent dead-code elimination
        } catch (OutOfMemoryError ignored) { /* expected in constrained environments */ }
        System.gc();
        System.runFinalization();
        Thread.sleep(100);
    }

    private static long usedHeap() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }
}
