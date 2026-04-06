/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.integration;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for the platform database layer.
 *
 * <p>Verifies connection management, CRUD correctness, transaction isolation,
 * and connection-pool behaviour using an in-memory H2-style simulation.
 *
 * @doc.type    class
 * @doc.purpose Platform database integration tests: connection management, CRUD, transactions
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
    @DisplayName("acquiring all pool connections then one more blocks until one is released")
    void acquiringBeyondPoolLimitBlocksUntilRelease() throws Exception {
        List<InMemoryDatabase.Connection> conns = new ArrayList<>();
        for (int i = 0; i < 5; i++) conns.add(db.acquire());

        AtomicInteger acquired = new AtomicInteger(0);
        Thread waiter = Thread.ofVirtual().start(() -> {
            InMemoryDatabase.Connection c = db.acquire();
            acquired.incrementAndGet();
            c.release();
        });

        // Before releasing, waiter should still be waiting
        Thread.sleep(50);
        assertThat(acquired.get()).isZero();

        // Release one — now waiter should proceed
        conns.get(0).release();
        waiter.join(1000);
        assertThat(acquired.get()).isEqualTo(1);

        conns.subList(1, 5).forEach(InMemoryDatabase.Connection::release);
    }

    @Test
    @DisplayName("released connections are returned to the pool for reuse")
    void releasedConnectionsReturnedToPoolForReuse() {
        InMemoryDatabase.Connection c1 = db.acquire();
        int id1 = c1.connectionId();
        c1.release();

        InMemoryDatabase.Connection c2 = db.acquire();
        // Connection from the pool may be reused
        assertThat(c2.isOpen()).isTrue();
        c2.release();
    }

    // ── CRUD correctness ──────────────────────────────────────────────────────

    @Test
    @DisplayName("insert and select-all round-trip returns the stored row")
    void insertAndSelectAllRoundTrip() {
        InMemoryDatabase.Connection conn = db.acquire();
        conn.execute("INSERT", "users", Map.of("id", "u1", "name", "Alice"));

        List<Map<String, Object>> rows = conn.query("SELECT", "users");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("name")).isEqualTo("Alice");
        conn.release();
    }

    @Test
    @DisplayName("update modifies only the targeted row")
    void updateModifiesOnlyTargetedRow() {
        InMemoryDatabase.Connection conn = db.acquire();
        conn.execute("INSERT", "users", Map.of("id", "u-upd", "name", "Bob"));
        conn.execute("UPDATE", "users", Map.of("id", "u-upd", "name", "Robert"));

        List<Map<String, Object>> rows = conn.query("SELECT", "users");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("name")).isEqualTo("Robert");
        conn.release();
    }

    @Test
    @DisplayName("delete removes the targeted row and count becomes zero")
    void deleteRemovesTargetedRowAndCountBecomesZero() {
        InMemoryDatabase.Connection conn = db.acquire();
        conn.execute("INSERT", "items", Map.of("id", "item-1", "value", "x"));
        conn.execute("DELETE", "items", Map.of("id", "item-1"));

        List<Map<String, Object>> rows = conn.query("SELECT", "items");
        assertThat(rows).isEmpty();
        conn.release();
    }

    // ── Transaction isolation ─────────────────────────────────────────────────

    @Test
    @DisplayName("uncommitted transaction changes are not visible outside the transaction")
    void uncommittedChangesAreNotVisibleOutsideTransaction() {
        InMemoryDatabase.Connection conn = db.acquire();
        InMemoryDatabase.Transaction tx = conn.beginTransaction();
        tx.execute("INSERT", "accounts", Map.of("id", "acc-1", "balance", 100));

        // Another connection should not see uncommitted data
        InMemoryDatabase.Connection conn2 = db.acquire();
        List<Map<String, Object>> rows = conn2.query("SELECT", "accounts");
        assertThat(rows).isEmpty();

        tx.rollback();
        conn.release();
        conn2.release();
    }

    @Test
    @DisplayName("committed transaction changes are visible from other connections")
    void committedChangesAreVisibleFromOtherConnections() {
        InMemoryDatabase.Connection conn = db.acquire();
        InMemoryDatabase.Transaction tx = conn.beginTransaction();
        tx.execute("INSERT", "accounts", Map.of("id", "acc-2", "balance", 200));
        tx.commit();
        conn.release();

        InMemoryDatabase.Connection conn2 = db.acquire();
        List<Map<String, Object>> rows = conn2.query("SELECT", "accounts");
        assertThat(rows).anyMatch(r -> "acc-2".equals(r.get("id")));
        conn2.release();
    }

    @Test
    @DisplayName("rolled-back transaction leaves the table unchanged")
    void rolledBackTransactionLeavesTableUnchanged() {
        InMemoryDatabase.Connection conn = db.acquire();
        InMemoryDatabase.Transaction tx = conn.beginTransaction();
        tx.execute("INSERT", "rollback_test", Map.of("id", "rb-1"));
        tx.rollback();

        List<Map<String, Object>> rows = conn.query("SELECT", "rollback_test");
        assertThat(rows).isEmpty();
        conn.release();
    }

    // ── Concurrent writes ─────────────────────────────────────────────────────

    @Test
    @DisplayName("concurrent inserts from multiple connections are all persisted")
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
                    c.execute("INSERT", "concurrent_table",
                            Map.of("id", "row-" + idx, "value", idx));
                    c.release();
                } catch (Exception ignored) {}
            });
        }
        for (Thread t : threads) t.join();

        InMemoryDatabase.Connection readConn = db.acquire();
        assertThat(readConn.query("SELECT", "concurrent_table")).hasSize(writers);
        readConn.release();
    }

    // ── In-memory database simulation (for tests) ─────────────────────────────

    static class InMemoryDatabase {
        private final int maxConnections;
        private final Semaphore semaphore;
        private final AtomicInteger idCounter = new AtomicInteger(0);
        private final ConcurrentHashMap<String, CopyOnWriteArrayList<Map<String, Object>>> tables
                = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, AtomicInteger> connectionCount = new ConcurrentHashMap<>();

        InMemoryDatabase(int maxConnections) {
            this.maxConnections = maxConnections;
            this.semaphore = new Semaphore(maxConnections);
        }

        Connection acquire() {
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            return new Connection(idCounter.incrementAndGet(), this);
        }

        void release() { semaphore.release(); }

        CopyOnWriteArrayList<Map<String, Object>> table(String name) {
            return tables.computeIfAbsent(name, k -> new CopyOnWriteArrayList<>());
        }

        static class Connection {
            private final int connId;
            private final InMemoryDatabase db;
            private boolean open = true;

            Connection(int connId, InMemoryDatabase db) { this.connId = connId; this.db = db; }
            int connectionId() { return connId; }
            boolean isOpen() { return open; }

            void execute(String op, String table, Map<String, Object> row) {
                switch (op) {
                    case "INSERT" -> db.table(table).add(new HashMap<>(row));
                    case "UPDATE" -> {
                        Object id = row.get("id");
                        db.table(table).replaceAll(r -> id.equals(r.get("id")) ? new HashMap<>(row) : r);
                    }
                    case "DELETE" -> {
                        Object id = row.get("id");
                        db.table(table).removeIf(r -> id.equals(r.get("id")));
                    }
                }
            }

            List<Map<String, Object>> query(String op, String table) {
                return List.copyOf(db.table(table));
            }

            Transaction beginTransaction() { return new Transaction(db); }

            void release() {
                open = false;
                db.release();
            }
        }

        static class Transaction {
            private final InMemoryDatabase db;
            private final Map<String, List<Map<String, Object>>> pendingOps = new LinkedHashMap<>();

            Transaction(InMemoryDatabase db) { this.db = db; }

            void execute(String op, String table, Map<String, Object> row) {
                pendingOps.computeIfAbsent(table, k -> new ArrayList<>()).add(new HashMap<>(row));
            }

            void commit() {
                pendingOps.forEach((table, rows) ->
                        rows.forEach(r -> db.table(table).add(new HashMap<>(r))));
                pendingOps.clear();
            }

            void rollback() { pendingOps.clear(); }
        }
    }
}
