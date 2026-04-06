/*
 * Copyright (c) 2026 Ghatana Inc.
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
@DisplayName("Platform Database Integration Tests")
@Tag("integration")
class DatabaseIntegrationTest extends EventloopTestBase {

    private InMemoryDatabase db;

    @BeforeEach
    void setUp() {
        db = new InMemoryDatabase(5);
    }

    // ── Connection management ─────────────────────────────────────────────────

    @Test
    @DisplayName("acquire returns a valid connection when pool has idle connections")
    void acquireReturnsValidConnectionWhenPoolHasIdleConnections() {
        InMemoryDatabase.Connection conn = db.acquire();
        assertThat(conn).isNotNull();
        assertThat(conn.isOpen()).isTrue();
        conn.release();
    }

    @Test
    @DisplayName("released connections are returned to the pool for reuse")
    void releasedConnectionsReturnedToPool() {
        InMemoryDatabase.Connection c1 = db.acquire();
        c1.release();
        InMemoryDatabase.Connection c2 = db.acquire();
        assertThat(c2.isOpen()).isTrue();
        c2.release();
    }

    @Test
    @DisplayName("pool enforces max connections — extra acquires block until a slot is freed")
    void poolEnforcesMaxConnections() throws Exception {
        List<InMemoryDatabase.Connection> held = new ArrayList<>();
        for (int i = 0; i < 5; i++) held.add(db.acquire());

        AtomicInteger acquired = new AtomicInteger(0);
        Thread waiter = Thread.ofVirtual().start(() -> {
            InMemoryDatabase.Connection c = db.acquire();
            acquired.incrementAndGet();
            c.release();
        });

        Thread.sleep(50);
        assertThat(acquired.get()).isZero(); // still waiting

        held.get(0).release();
        waiter.join(1000);
        assertThat(acquired.get()).isEqualTo(1);

        held.subList(1, 5).forEach(InMemoryDatabase.Connection::release);
    }

    // ── CRUD correctness ──────────────────────────────────────────────────────

    @Test
    @DisplayName("insert and select round-trip returns the stored row")
    void insertAndSelectRoundTrip() {
        InMemoryDatabase.Connection conn = db.acquire();
        conn.insert("users", Map.of("id", "u1", "name", "Alice"));
        List<Map<String, Object>> rows = conn.selectAll("users");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("name")).isEqualTo("Alice");
        conn.release();
    }

    @Test
    @DisplayName("update modifies only the targeted row identified by id")
    void updateModifiesOnlyTargetedRow() {
        InMemoryDatabase.Connection conn = db.acquire();
        conn.insert("users", Map.of("id", "u-upd", "name", "Bob"));
        conn.update("users", "u-upd", Map.of("name", "Robert"));

        List<Map<String, Object>> rows = conn.selectAll("users");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("name")).isEqualTo("Robert");
        conn.release();
    }

    @Test
    @DisplayName("delete removes the targeted row so the table becomes empty")
    void deleteRemovesTargetedRow() {
        InMemoryDatabase.Connection conn = db.acquire();
        conn.insert("items", Map.of("id", "item-1", "value", "x"));
        conn.delete("items", "item-1");

        assertThat(conn.selectAll("items")).isEmpty();
        conn.release();
    }

    // ── Transaction isolation ─────────────────────────────────────────────────

    @Test
    @DisplayName("uncommitted transaction changes are not visible to other connections")
    void uncommittedChangesAreNotVisibleToOthers() {
        InMemoryDatabase.Connection conn = db.acquire();
        InMemoryDatabase.Transaction tx = conn.beginTransaction();
        tx.insert("accounts", Map.of("id", "acc-u", "balance", 100));

        InMemoryDatabase.Connection conn2 = db.acquire();
        List<Map<String, Object>> rows = conn2.selectAll("accounts");
        assertThat(rows).isEmpty();

        tx.rollback();
        conn.release();
        conn2.release();
    }

    @Test
    @DisplayName("committed changes are visible from other connections")
    void committedChangesAreVisibleFromOtherConnections() {
        InMemoryDatabase.Connection conn = db.acquire();
        InMemoryDatabase.Transaction tx = conn.beginTransaction();
        tx.insert("accounts", Map.of("id", "acc-c", "balance", 200));
        tx.commit();
        conn.release();

        InMemoryDatabase.Connection conn2 = db.acquire();
        List<Map<String, Object>> rows = conn2.selectAll("accounts");
        assertThat(rows).anyMatch(r -> "acc-c".equals(r.get("id")));
        conn2.release();
    }

    @Test
    @DisplayName("rolled-back transaction leaves the table unchanged")
    void rolledBackTransactionLeavesTableUnchanged() {
        InMemoryDatabase.Connection conn = db.acquire();
        InMemoryDatabase.Transaction tx = conn.beginTransaction();
        tx.insert("rollback_test", Map.of("id", "rb-1"));
        tx.rollback();

        assertThat(conn.selectAll("rollback_test")).isEmpty();
        conn.release();
    }

    // ── Concurrent writes ─────────────────────────────────────────────────────

    @Test
    @DisplayName("concurrent inserts from multiple connections are all durably persisted")
    void concurrentInsertsAreAllPersisted() throws Exception {
        int writers = 20;
        CyclicBarrier barrier = new CyclicBarrier(writers);
        Thread[] threads = new Thread[writers];

        for (int i = 0; i < writers; i++) {
            final int idx = i;
            threads[i] = Thread.ofVirtual().start(() -> {
                try {
                    barrier.await();
                    InMemoryDatabase.Connection c = db.acquire();
                    c.insert("concurrent_table", Map.of("id", "row-" + idx, "value", idx));
                    c.release();
                } catch (Exception ignored) {}
            });
        }
        for (Thread t : threads) t.join();

        InMemoryDatabase.Connection readConn = db.acquire();
        assertThat(readConn.selectAll("concurrent_table")).hasSize(writers);
        readConn.release();
    }

    // ── In-memory database simulation (for tests) ─────────────────────────────

    static class InMemoryDatabase {
        private final Semaphore semaphore;
        private final AtomicInteger idCounter = new AtomicInteger(0);
        private final ConcurrentHashMap<String, CopyOnWriteArrayList<Map<String, Object>>> tables
                = new ConcurrentHashMap<>();

        InMemoryDatabase(int maxConnections) {
            this.semaphore = new Semaphore(maxConnections);
        }

        Connection acquire() {
            try { semaphore.acquire(); } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); throw new RuntimeException(e);
            }
            return new Connection(idCounter.incrementAndGet(), this);
        }

        void doRelease() { semaphore.release(); }

        CopyOnWriteArrayList<Map<String, Object>> table(String name) {
            return tables.computeIfAbsent(name, k -> new CopyOnWriteArrayList<>());
        }

        static class Connection {
            private final int connId;
            private final InMemoryDatabase db;
            private boolean open = true;

            Connection(int id, InMemoryDatabase db) { this.connId = id; this.db = db; }
            int connectionId() { return connId; }
            boolean isOpen() { return open; }

            void insert(String table, Map<String, Object> row) {
                db.table(table).add(new HashMap<>(row));
            }

            void update(String table, String id, Map<String, Object> patch) {
                db.table(table).replaceAll(r -> id.equals(r.get("id")) ? mergedWith(r, patch) : r);
            }

            void delete(String table, String id) {
                db.table(table).removeIf(r -> id.equals(r.get("id")));
            }

            List<Map<String, Object>> selectAll(String table) {
                return List.copyOf(db.table(table));
            }

            Transaction beginTransaction() { return new Transaction(db); }

            void release() { open = false; db.doRelease(); }

            private Map<String, Object> mergedWith(Map<String, Object> row, Map<String, Object> patch) {
                Map<String, Object> merged = new HashMap<>(row);
                merged.putAll(patch);
                return merged;
            }
        }

        static class Transaction {
            private final InMemoryDatabase db;
            private final List<Runnable> ops = new ArrayList<>();

            Transaction(InMemoryDatabase db) { this.db = db; }

            void insert(String table, Map<String, Object> row) {
                ops.add(() -> db.table(table).add(new HashMap<>(row)));
            }

            void commit() { ops.forEach(Runnable::run); ops.clear(); }
            void rollback() { ops.clear(); }
        }
    }
}
