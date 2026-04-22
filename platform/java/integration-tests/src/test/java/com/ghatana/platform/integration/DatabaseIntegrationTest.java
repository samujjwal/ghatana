/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.integration;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Platform database integration tests.
 *
 * <p>Verifies connection management, CRUD correctness, transaction isolation,
 * and connection-pool behaviour using a lightweight in-memory simulation.
 *
 * @doc.type    class
 * @doc.purpose Platform database integration: connection pool, CRUD, transactions
 * @doc.layer   platform
 * @doc.pattern IntegrationTest
 */
@DisplayName("Platform Database Integration Tests [GH-90000]")
@Tag("integration [GH-90000]")
class DatabaseIntegrationTest extends EventloopTestBase {

    private InMemoryDatabase db;

    @BeforeEach
    void setUp() { // GH-90000
        db = new InMemoryDatabase(5); // GH-90000
    }

    // ── Connection management ─────────────────────────────────────────────────

    @Test
    @DisplayName("acquire returns a valid connection when pool has idle connections [GH-90000]")
    void acquireReturnsValidConnectionWhenPoolHasIdleConnections() { // GH-90000
        InMemoryDatabase.Connection conn = db.acquire(); // GH-90000
        assertThat(conn).isNotNull(); // GH-90000
        assertThat(conn.isOpen()).isTrue(); // GH-90000
        conn.release(); // GH-90000
    }

    @Test
    @DisplayName("released connections are returned to the pool for reuse [GH-90000]")
    void releasedConnectionsReturnedToPool() { // GH-90000
        InMemoryDatabase.Connection c1 = db.acquire(); // GH-90000
        c1.release(); // GH-90000
        InMemoryDatabase.Connection c2 = db.acquire(); // GH-90000
        assertThat(c2.isOpen()).isTrue(); // GH-90000
        c2.release(); // GH-90000
    }

    @Test
    @DisplayName("pool enforces max connections — extra acquires block until a slot is freed [GH-90000]")
    void poolEnforcesMaxConnections() throws Exception { // GH-90000
        List<InMemoryDatabase.Connection> held = new ArrayList<>(); // GH-90000
        for (int i = 0; i < 5; i++) held.add(db.acquire()); // GH-90000

        AtomicInteger acquired = new AtomicInteger(0); // GH-90000
        Thread waiter = Thread.ofVirtual().start(() -> { // GH-90000
            InMemoryDatabase.Connection c = db.acquire(); // GH-90000
            acquired.incrementAndGet(); // GH-90000
            c.release(); // GH-90000
        });

        Thread.sleep(50); // GH-90000
        assertThat(acquired.get()).isZero(); // still waiting // GH-90000

        held.get(0).release(); // GH-90000
        waiter.join(1000); // GH-90000
        assertThat(acquired.get()).isEqualTo(1); // GH-90000

        held.subList(1, 5).forEach(InMemoryDatabase.Connection::release); // GH-90000
    }

    // ── CRUD correctness ──────────────────────────────────────────────────────

    @Test
    @DisplayName("insert and select round-trip returns the stored row [GH-90000]")
    void insertAndSelectRoundTrip() { // GH-90000
        InMemoryDatabase.Connection conn = db.acquire(); // GH-90000
        conn.insert("users", Map.of("id", "u1", "name", "Alice")); // GH-90000
        List<Map<String, Object>> rows = conn.selectAll("users [GH-90000]");

        assertThat(rows).hasSize(1); // GH-90000
        assertThat(rows.get(0).get("name [GH-90000]")).isEqualTo("Alice [GH-90000]");
        conn.release(); // GH-90000
    }

    @Test
    @DisplayName("update modifies only the targeted row identified by id [GH-90000]")
    void updateModifiesOnlyTargetedRow() { // GH-90000
        InMemoryDatabase.Connection conn = db.acquire(); // GH-90000
        conn.insert("users", Map.of("id", "u-upd", "name", "Bob")); // GH-90000
        conn.update("users", "u-upd", Map.of("name", "Robert")); // GH-90000

        List<Map<String, Object>> rows = conn.selectAll("users [GH-90000]");
        assertThat(rows).hasSize(1); // GH-90000
        assertThat(rows.get(0).get("name [GH-90000]")).isEqualTo("Robert [GH-90000]");
        conn.release(); // GH-90000
    }

    @Test
    @DisplayName("delete removes the targeted row so the table becomes empty [GH-90000]")
    void deleteRemovesTargetedRow() { // GH-90000
        InMemoryDatabase.Connection conn = db.acquire(); // GH-90000
        conn.insert("items", Map.of("id", "item-1", "value", "x")); // GH-90000
        conn.delete("items", "item-1"); // GH-90000

        assertThat(conn.selectAll("items [GH-90000]")).isEmpty();
        conn.release(); // GH-90000
    }

    // ── Transaction isolation ─────────────────────────────────────────────────

    @Test
    @DisplayName("uncommitted transaction changes are not visible to other connections [GH-90000]")
    void uncommittedChangesAreNotVisibleToOthers() { // GH-90000
        InMemoryDatabase.Connection conn = db.acquire(); // GH-90000
        InMemoryDatabase.Transaction tx = conn.beginTransaction(); // GH-90000
        tx.insert("accounts", Map.of("id", "acc-u", "balance", 100)); // GH-90000

        InMemoryDatabase.Connection conn2 = db.acquire(); // GH-90000
        List<Map<String, Object>> rows = conn2.selectAll("accounts [GH-90000]");
        assertThat(rows).isEmpty(); // GH-90000

        tx.rollback(); // GH-90000
        conn.release(); // GH-90000
        conn2.release(); // GH-90000
    }

    @Test
    @DisplayName("committed changes are visible from other connections [GH-90000]")
    void committedChangesAreVisibleFromOtherConnections() { // GH-90000
        InMemoryDatabase.Connection conn = db.acquire(); // GH-90000
        InMemoryDatabase.Transaction tx = conn.beginTransaction(); // GH-90000
        tx.insert("accounts", Map.of("id", "acc-c", "balance", 200)); // GH-90000
        tx.commit(); // GH-90000
        conn.release(); // GH-90000

        InMemoryDatabase.Connection conn2 = db.acquire(); // GH-90000
        List<Map<String, Object>> rows = conn2.selectAll("accounts [GH-90000]");
        assertThat(rows).anyMatch(r -> "acc-c".equals(r.get("id [GH-90000]")));
        conn2.release(); // GH-90000
    }

    @Test
    @DisplayName("rolled-back transaction leaves the table unchanged [GH-90000]")
    void rolledBackTransactionLeavesTableUnchanged() { // GH-90000
        InMemoryDatabase.Connection conn = db.acquire(); // GH-90000
        InMemoryDatabase.Transaction tx = conn.beginTransaction(); // GH-90000
        tx.insert("rollback_test", Map.of("id", "rb-1")); // GH-90000
        tx.rollback(); // GH-90000

        assertThat(conn.selectAll("rollback_test [GH-90000]")).isEmpty();
        conn.release(); // GH-90000
    }

    // ── Concurrent writes ─────────────────────────────────────────────────────

    @Test
    @DisplayName("concurrent inserts from multiple connections are all durably persisted [GH-90000]")
    void concurrentInsertsAreAllPersisted() throws Exception { // GH-90000
        int writers = 20;
        CyclicBarrier barrier = new CyclicBarrier(writers); // GH-90000
        Thread[] threads = new Thread[writers];

        for (int i = 0; i < writers; i++) { // GH-90000
            final int idx = i;
            threads[i] = Thread.ofVirtual().start(() -> { // GH-90000
                try {
                    barrier.await(); // GH-90000
                    InMemoryDatabase.Connection c = db.acquire(); // GH-90000
                    c.insert("concurrent_table", Map.of("id", "row-" + idx, "value", idx)); // GH-90000
                    c.release(); // GH-90000
                } catch (Exception ignored) {} // GH-90000
            });
        }
        for (Thread t : threads) t.join(); // GH-90000

        InMemoryDatabase.Connection readConn = db.acquire(); // GH-90000
        assertThat(readConn.selectAll("concurrent_table [GH-90000]")).hasSize(writers);
        readConn.release(); // GH-90000
    }

    // ── In-memory database simulation (for tests) ───────────────────────────── // GH-90000

    static class InMemoryDatabase {
        private final Semaphore semaphore;
        private final AtomicInteger idCounter = new AtomicInteger(0); // GH-90000
        private final ConcurrentHashMap<String, CopyOnWriteArrayList<Map<String, Object>>> tables
                = new ConcurrentHashMap<>(); // GH-90000

        InMemoryDatabase(int maxConnections) { // GH-90000
            this.semaphore = new Semaphore(maxConnections); // GH-90000
        }

        Connection acquire() { // GH-90000
            try { semaphore.acquire(); } catch (InterruptedException e) { // GH-90000
                Thread.currentThread().interrupt(); throw new RuntimeException(e); // GH-90000
            }
            return new Connection(idCounter.incrementAndGet(), this); // GH-90000
        }

        void doRelease() { semaphore.release(); } // GH-90000

        CopyOnWriteArrayList<Map<String, Object>> table(String name) { // GH-90000
            return tables.computeIfAbsent(name, k -> new CopyOnWriteArrayList<>()); // GH-90000
        }

        static class Connection {
            private final int connId;
            private final InMemoryDatabase db;
            private boolean open = true;

            Connection(int id, InMemoryDatabase db) { this.connId = id; this.db = db; } // GH-90000
            int connectionId() { return connId; } // GH-90000
            boolean isOpen() { return open; } // GH-90000

            void insert(String table, Map<String, Object> row) { // GH-90000
                db.table(table).add(new HashMap<>(row)); // GH-90000
            }

            void update(String table, String id, Map<String, Object> patch) { // GH-90000
                db.table(table).replaceAll(r -> id.equals(r.get("id [GH-90000]")) ? mergedWith(r, patch) : r);
            }

            void delete(String table, String id) { // GH-90000
                db.table(table).removeIf(r -> id.equals(r.get("id [GH-90000]")));
            }

            List<Map<String, Object>> selectAll(String table) { // GH-90000
                return List.copyOf(db.table(table)); // GH-90000
            }

            Transaction beginTransaction() { return new Transaction(db); } // GH-90000

            void release() { open = false; db.doRelease(); } // GH-90000

            private Map<String, Object> mergedWith(Map<String, Object> row, Map<String, Object> patch) { // GH-90000
                Map<String, Object> merged = new HashMap<>(row); // GH-90000
                merged.putAll(patch); // GH-90000
                return merged;
            }
        }

        static class Transaction {
            private final InMemoryDatabase db;
            private final List<Runnable> ops = new ArrayList<>(); // GH-90000

            Transaction(InMemoryDatabase db) { this.db = db; } // GH-90000

            void insert(String table, Map<String, Object> row) { // GH-90000
                ops.add(() -> db.table(table).add(new HashMap<>(row))); // GH-90000
            }

            void commit() { ops.forEach(Runnable::run); ops.clear(); } // GH-90000
            void rollback() { ops.clear(); } // GH-90000
        }
    }
}
