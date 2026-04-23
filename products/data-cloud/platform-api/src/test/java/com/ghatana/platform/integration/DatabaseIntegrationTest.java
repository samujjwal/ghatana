/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.integration;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

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
@Timeout(value = 30, unit = TimeUnit.SECONDS) // GH-90000
class DatabaseIntegrationTest extends EventloopTestBase {

    private InMemoryDatabase db;

    @BeforeEach
    void setUp() { // GH-90000
        db = new InMemoryDatabase(5); // GH-90000
    }

    // ── Connection management ─────────────────────────────────────────────────

    @Test
    @DisplayName("acquire returns a valid connection when pool has idle connections")
    void acquireReturnsValidConnectionWhenPoolHasIdleConnections() { // GH-90000
        InMemoryDatabase.Connection conn = db.acquire(); // GH-90000
        assertThat(conn).isNotNull(); // GH-90000
        assertThat(conn.isOpen()).isTrue(); // GH-90000
        conn.release(); // GH-90000
    }

    @Test
    @DisplayName("acquiring all pool connections then one more blocks until one is released")
    void acquiringBeyondPoolLimitBlocksUntilRelease() throws Exception { // GH-90000
        List<InMemoryDatabase.Connection> conns = new ArrayList<>(); // GH-90000
        for (int i = 0; i < 5; i++) conns.add(db.acquire()); // GH-90000

        AtomicInteger acquired = new AtomicInteger(0); // GH-90000
        Thread waiter = Thread.ofVirtual().start(() -> { // GH-90000
            InMemoryDatabase.Connection c = db.acquire(); // GH-90000
            acquired.incrementAndGet(); // GH-90000
            c.release(); // GH-90000
        });

        // Before releasing, waiter should still be waiting
        Thread.sleep(50); // GH-90000
        assertThat(acquired.get()).isZero(); // GH-90000

        // Release one — now waiter should proceed
        conns.get(0).release(); // GH-90000
        waiter.join(1000); // GH-90000
        assertThat(acquired.get()).isEqualTo(1); // GH-90000

        conns.subList(1, 5).forEach(InMemoryDatabase.Connection::release); // GH-90000
    }

    @Test
    @DisplayName("released connections free capacity for another acquire")
    void releasedConnectionsFreeCapacityForAnotherAcquire() { // GH-90000
        InMemoryDatabase.Connection c1 = db.acquire(); // GH-90000
        c1.release(); // GH-90000

        InMemoryDatabase.Connection c2 = db.acquire(); // GH-90000
        assertThat(c2.isOpen()).isTrue(); // GH-90000
        c2.release(); // GH-90000
    }

    // ── CRUD correctness ──────────────────────────────────────────────────────

    @Test
    @DisplayName("insert and select-all round-trip returns the stored row")
    void insertAndSelectAllRoundTrip() { // GH-90000
        InMemoryDatabase.Connection conn = db.acquire(); // GH-90000
        conn.execute("INSERT", "users", Map.of("id", "u1", "name", "Alice")); // GH-90000

        List<Map<String, Object>> rows = conn.query("SELECT", "users"); // GH-90000
        assertThat(rows).hasSize(1); // GH-90000
        assertThat(rows.get(0).get("name")).isEqualTo("Alice");
        conn.release(); // GH-90000
    }

    @Test
    @DisplayName("update modifies only the targeted row")
    void updateModifiesOnlyTargetedRow() { // GH-90000
        InMemoryDatabase.Connection conn = db.acquire(); // GH-90000
        conn.execute("INSERT", "users", Map.of("id", "u-upd", "name", "Bob")); // GH-90000
        conn.execute("UPDATE", "users", Map.of("id", "u-upd", "name", "Robert")); // GH-90000

        List<Map<String, Object>> rows = conn.query("SELECT", "users"); // GH-90000
        assertThat(rows).hasSize(1); // GH-90000
        assertThat(rows.get(0).get("name")).isEqualTo("Robert");
        conn.release(); // GH-90000
    }

    @Test
    @DisplayName("delete removes the targeted row and count becomes zero")
    void deleteRemovesTargetedRowAndCountBecomesZero() { // GH-90000
        InMemoryDatabase.Connection conn = db.acquire(); // GH-90000
        conn.execute("INSERT", "items", Map.of("id", "item-1", "value", "x")); // GH-90000
        conn.execute("DELETE", "items", Map.of("id", "item-1")); // GH-90000

        List<Map<String, Object>> rows = conn.query("SELECT", "items"); // GH-90000
        assertThat(rows).isEmpty(); // GH-90000
        conn.release(); // GH-90000
    }

    // ── Transaction isolation ─────────────────────────────────────────────────

    @Test
    @DisplayName("uncommitted transaction changes are not visible outside the transaction")
    void uncommittedChangesAreNotVisibleOutsideTransaction() { // GH-90000
        InMemoryDatabase.Connection conn = db.acquire(); // GH-90000
        InMemoryDatabase.Transaction tx = conn.beginTransaction(); // GH-90000
        tx.execute("INSERT", "accounts", Map.of("id", "acc-1", "balance", 100)); // GH-90000

        // Another connection should not see uncommitted data
        InMemoryDatabase.Connection conn2 = db.acquire(); // GH-90000
        List<Map<String, Object>> rows = conn2.query("SELECT", "accounts"); // GH-90000
        assertThat(rows).isEmpty(); // GH-90000

        tx.rollback(); // GH-90000
        conn.release(); // GH-90000
        conn2.release(); // GH-90000
    }

    @Test
    @DisplayName("committed transaction changes are visible from other connections")
    void committedChangesAreVisibleFromOtherConnections() { // GH-90000
        InMemoryDatabase.Connection conn = db.acquire(); // GH-90000
        InMemoryDatabase.Transaction tx = conn.beginTransaction(); // GH-90000
        tx.execute("INSERT", "accounts", Map.of("id", "acc-2", "balance", 200)); // GH-90000
        tx.commit(); // GH-90000
        conn.release(); // GH-90000

        InMemoryDatabase.Connection conn2 = db.acquire(); // GH-90000
        List<Map<String, Object>> rows = conn2.query("SELECT", "accounts"); // GH-90000
        assertThat(rows).anyMatch(r -> "acc-2".equals(r.get("id")));
        conn2.release(); // GH-90000
    }

    @Test
    @DisplayName("rolled-back transaction leaves the table unchanged")
    void rolledBackTransactionLeavesTableUnchanged() { // GH-90000
        InMemoryDatabase.Connection conn = db.acquire(); // GH-90000
        InMemoryDatabase.Transaction tx = conn.beginTransaction(); // GH-90000
        tx.execute("INSERT", "rollback_test", Map.of("id", "rb-1")); // GH-90000
        tx.rollback(); // GH-90000

        List<Map<String, Object>> rows = conn.query("SELECT", "rollback_test"); // GH-90000
        assertThat(rows).isEmpty(); // GH-90000
        conn.release(); // GH-90000
    }

    // ── Concurrent writes ─────────────────────────────────────────────────────

    @Test
    @DisplayName("concurrent inserts from multiple connections are all persisted")
    void concurrentInsertsAreAllPersisted() throws Exception { // GH-90000
        int writers = 20;
        CyclicBarrier barrier = new CyclicBarrier(writers); // GH-90000
        Thread[] threads = new Thread[writers];
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>(); // GH-90000

        for (int i = 0; i < writers; i++) { // GH-90000
            final int idx = i;
            threads[i] = Thread.ofVirtual().start(() -> { // GH-90000
                try {
                    barrier.await(); // GH-90000
                    InMemoryDatabase.Connection c = db.acquire(); // GH-90000
                    c.execute("INSERT", "concurrent_table", // GH-90000
                            Map.of("id", "row-" + idx, "value", idx)); // GH-90000
                    c.release(); // GH-90000
                } catch (InterruptedException e) { // GH-90000
                    Thread.currentThread().interrupt(); // GH-90000
                    failures.add(e); // GH-90000
                } catch (BrokenBarrierException | RuntimeException e) { // GH-90000
                    failures.add(e); // GH-90000
                }
            });
        }
        for (Thread t : threads) { // GH-90000
            t.join(2000); // GH-90000
            assertThat(t.isAlive()).isFalse(); // GH-90000
        }
        assertThat(failures).isEmpty(); // GH-90000

        InMemoryDatabase.Connection readConn = db.acquire(); // GH-90000
        assertThat(readConn.query("SELECT", "concurrent_table")).hasSize(writers); // GH-90000
        readConn.release(); // GH-90000
    }

    // ── In-memory database simulation (for tests) ───────────────────────────── // GH-90000

    static class InMemoryDatabase {
        private final int maxConnections;
        private final Semaphore semaphore;
        private final AtomicInteger idCounter = new AtomicInteger(0); // GH-90000
        private final ConcurrentHashMap<String, CopyOnWriteArrayList<Map<String, Object>>> tables
                = new ConcurrentHashMap<>(); // GH-90000
        private final ConcurrentHashMap<String, AtomicInteger> connectionCount = new ConcurrentHashMap<>(); // GH-90000

        InMemoryDatabase(int maxConnections) { // GH-90000
            this.maxConnections = maxConnections;
            this.semaphore = new Semaphore(maxConnections); // GH-90000
        }

        Connection acquire() { // GH-90000
            try {
                semaphore.acquire(); // GH-90000
            } catch (InterruptedException e) { // GH-90000
                Thread.currentThread().interrupt(); // GH-90000
                throw new RuntimeException(e); // GH-90000
            }
            return new Connection(idCounter.incrementAndGet(), this); // GH-90000
        }

        void release() { semaphore.release(); } // GH-90000

        CopyOnWriteArrayList<Map<String, Object>> table(String name) { // GH-90000
            return tables.computeIfAbsent(name, k -> new CopyOnWriteArrayList<>()); // GH-90000
        }

        static class Connection {
            private final int connId;
            private final InMemoryDatabase db;
            private boolean open = true;

            Connection(int connId, InMemoryDatabase db) { this.connId = connId; this.db = db; } // GH-90000
            int connectionId() { return connId; } // GH-90000
            boolean isOpen() { return open; } // GH-90000

            void execute(String op, String table, Map<String, Object> row) { // GH-90000
                switch (op) { // GH-90000
                    case "INSERT" -> db.table(table).add(new HashMap<>(row)); // GH-90000
                    case "UPDATE" -> {
                        Object id = row.get("id");
                        db.table(table).replaceAll(r -> id.equals(r.get("id")) ? new HashMap<>(row) : r);
                    }
                    case "DELETE" -> {
                        Object id = row.get("id");
                        db.table(table).removeIf(r -> id.equals(r.get("id")));
                    }
                    default -> throw new IllegalArgumentException("Unsupported operation: " + op); // GH-90000
                }
            }

            List<Map<String, Object>> query(String op, String table) { // GH-90000
                return List.copyOf(db.table(table)); // GH-90000
            }

            Transaction beginTransaction() { return new Transaction(db); } // GH-90000

            void release() { // GH-90000
                open = false;
                db.release(); // GH-90000
            }
        }

        static class Transaction {
            private final InMemoryDatabase db;
            private final Map<String, List<Map<String, Object>>> pendingOps = new LinkedHashMap<>(); // GH-90000

            Transaction(InMemoryDatabase db) { this.db = db; } // GH-90000

            void execute(String op, String table, Map<String, Object> row) { // GH-90000
                pendingOps.computeIfAbsent(table, k -> new ArrayList<>()).add(new HashMap<>(row)); // GH-90000
            }

            void commit() { // GH-90000
                pendingOps.forEach((table, rows) -> // GH-90000
                        rows.forEach(r -> db.table(table).add(new HashMap<>(r)))); // GH-90000
                pendingOps.clear(); // GH-90000
            }

            void rollback() { pendingOps.clear(); } // GH-90000
        }
    }
}
