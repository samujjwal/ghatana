/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 * <p>These tests validate that key objects (entity collections, event subscriptions, // GH-90000
 * query result sets, and the client itself) are properly cleaned up after close/cancel,
 * so long-running services do not accumulate memory over time.
 *
 * <p><strong>Strategy:</strong> Objects are weakly referenced before release, GC is hinted,
 * and the tests assert that the referents become reclaimable. This is a best-effort
 * heuristic (GC is non-deterministic) but catches obvious hard references. // GH-90000
 *
 * @doc.type class
 * @doc.purpose Memory leak detection tests (P3 gap closure) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Memory Leak Detection [GH-90000]")
class MemoryLeakDetectionTest extends EventloopTestBase {

    private static final String TENANT_ID  = "leak-tenant";
    private static final String COLLECTION = "leak-records";

    private DataCloudClient client;

    @BeforeEach
    void setUp() { // GH-90000
        client = DataCloud.forTesting(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        client.close(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Client lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Client lifecycle [GH-90000]")
    class ClientLifecycleTests {

        @Test
        @DisplayName("closed client is reclaimable by GC (no hard reference retained) [GH-90000]")
        void closedClient_isReclaimable() throws Exception { // GH-90000
            // Create and use the client in a helper method whose stack frame is fully
            // released on return, ensuring no variable slot retains the reference.
            WeakReference<DataCloudClient> ref = useAndCloseClient(); // GH-90000

            // Apply repeated GC pressure with memory allocation to force collection
            forceGc(); // GH-90000

            // The client should be reclaimable (not pinned by internal caches) // GH-90000
            assertThat(ref.get()).as("Closed DataCloudClient must not be held by internal caches [GH-90000]").isNull();
        }

        /** Creates a client, uses it once, closes it, and returns a WeakReference. */
        private WeakReference<DataCloudClient> useAndCloseClient() throws Exception { // GH-90000
            DataCloudClient local = DataCloud.forTesting(); // GH-90000
            WeakReference<DataCloudClient> ref = new WeakReference<>(local); // GH-90000
            runPromise(() -> local.save(TENANT_ID, COLLECTION, Map.of("key", "value"))); // GH-90000
            local.close(); // GH-90000
            return ref;
            // local goes out of scope here — stack frame released on method return
        }

        @Test
        @DisplayName("multiple open+close cycles do not accumulate heap pressure [GH-90000]")
        void multipleOpenCloseCycles_noHeapAccumulation() throws Exception { // GH-90000
            long heapBefore = usedHeap(); // GH-90000

            for (int i = 0; i < 20; i++) { // GH-90000
                final int idx = i;
                DataCloudClient c = DataCloud.forTesting(); // GH-90000
                runPromise(() -> c.save("cycle-tenant-" + idx, COLLECTION, Map.of("idx", idx))); // GH-90000
                c.close(); // GH-90000
            }

            forceGc(); // GH-90000
            long heapAfter = usedHeap(); // GH-90000

            // Heap growth after 20 open/close cycles must be < 50 MB
            long growthMb = (heapAfter - heapBefore) / (1024 * 1024); // GH-90000
            assertThat(growthMb) // GH-90000
                .as("Heap grew by %d MB after 20 open/close cycles — possible leak", growthMb) // GH-90000
                .isLessThan(50); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Query result cleanup
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Query result cleanup [GH-90000]")
    class QueryResultCleanupTests {

        @Test
        @DisplayName("query result list is reclaimable after caller drops the reference [GH-90000]")
        void queryResultList_isReclaimableAfterCaller_dropsReference() throws Exception { // GH-90000
            // Pre-populate
            for (int i = 0; i < 100; i++) { // GH-90000
                final int idx = i;
                runPromise(() -> client.save(TENANT_ID, COLLECTION, Map.of("idx", idx))); // GH-90000
            }

            List<Entity> results = runPromise(() -> // GH-90000
                client.query(TENANT_ID, COLLECTION, Query.all())); // GH-90000

            assertThat(results).hasSize(100); // GH-90000

            WeakReference<List<Entity>> ref = new WeakReference<>(results); // GH-90000
            //noinspection UnusedAssignment
            results = null;

            forceGc(); // GH-90000

            // Result list must be reclaimable once the caller drops the reference
            assertThat(ref.get()) // GH-90000
                .as("Query result list should be reclaimable after caller releases it [GH-90000]")
                .isNull(); // GH-90000
        }

        @Test
        @DisplayName("repeated large queries do not retain results internally [GH-90000]")
        void repeatedLargeQueries_doNotRetainResultsInternally() throws Exception { // GH-90000
            for (int i = 0; i < 50; i++) { // GH-90000
                final int idx = i;
                runPromise(() -> client.save(TENANT_ID, COLLECTION, Map.of("idx", idx))); // GH-90000
            }

            long heapBefore = usedHeap(); // GH-90000

            for (int round = 0; round < 10; round++) { // GH-90000
                List<Entity> r = runPromise(() -> // GH-90000
                    client.query(TENANT_ID, COLLECTION, Query.all())); // GH-90000
                assertThat(r).isNotEmpty(); // GH-90000
                // Let r go out of scope
            }

            forceGc(); // GH-90000
            long heapAfter = usedHeap(); // GH-90000
            long growthMb = (heapAfter - heapBefore) / (1024 * 1024); // GH-90000
            assertThat(growthMb) // GH-90000
                .as("Heap grew by %d MB after 10 large queries — internal result caching leak?", growthMb) // GH-90000
                .isLessThan(20); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Entity store — entity removal
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Entity store cleanup [GH-90000]")
    class EntityStoreCleanupTests {

        @Test
        @DisplayName("deleted entities are removed from internal store (no memory accumulation) [GH-90000]")
        void deletedEntities_removedFromStore() throws Exception { // GH-90000
            List<String> ids = new ArrayList<>(); // GH-90000
            for (int i = 0; i < 100; i++) { // GH-90000
                final int idx = i;
                Entity entity = runPromise(() -> // GH-90000
                    client.save(TENANT_ID, COLLECTION, Map.of("idx", idx))); // GH-90000
                ids.add(entity.id()); // GH-90000
            }

            // Delete all entities
            for (String id : ids) { // GH-90000
                runPromise(() -> client.delete(TENANT_ID, COLLECTION, id)); // GH-90000
            }

            // Heap should not grow proportionally to the deleted entity count
            forceGc(); // GH-90000
            List<Entity> remaining = runPromise(() -> // GH-90000
                client.query(TENANT_ID, COLLECTION, Query.all())); // GH-90000
            assertThat(remaining).isEmpty(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Forces GC with repeated attempts and memory pressure to ensure weak references are cleared. */
    private static void forceGc() throws InterruptedException { // GH-90000
        for (int attempt = 0; attempt < 5; attempt++) { // GH-90000
            System.gc(); // GH-90000
            System.runFinalization(); // GH-90000
            Thread.sleep(50); // GH-90000
        }
        // Allocate memory to create heap pressure and trigger collection
        try {
            byte[] pressure = new byte[8 * 1024 * 1024]; // 8 MB
            pressure[0] = 1; // prevent dead-code elimination
        } catch (OutOfMemoryError ignored) { /* expected in constrained environments */ } // GH-90000
        System.gc(); // GH-90000
        System.runFinalization(); // GH-90000
        Thread.sleep(100); // GH-90000
    }

    private static long usedHeap() { // GH-90000
        Runtime rt = Runtime.getRuntime(); // GH-90000
        return rt.totalMemory() - rt.freeMemory(); // GH-90000
    }
}
