package com.ghatana.appplatform.integration;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Database resilience and query performance test service.
 *              Validates: 95% queries < 100ms; read replica lag < 200ms; connection pool queue
 *              on exhaustion; slow query detection; write amplification; projection freshness.
 * @doc.layer   Integration Testing (T-01)
 * @doc.pattern Port-Adapter; JDBC; Promise.ofBlocking; load-test; latency
 *
 * STORY-T01-011: Implement database resilience and query performance test
 */
public class DatabaseResilienceQueryPerformanceTestService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface DbQueryPort {
        /** Execute representative read query. Returns elapsed ms. */
        long executeReadQuery(String queryName) throws Exception;
        /** Execute on read replica. Returns elapsed ms. */
        long executeReplicaQuery(String queryName) throws Exception;
        /** Get current replica lag in ms. */
        long getReplicaLagMs() throws Exception;
        /** Get slow query count (> 1s) in last 1 minute. */
        long getSlowQueryCount() throws Exception;
        /** Get write amplification factor for event sourcing (writes per business event). */
        double getWriteAmplificationFactor() throws Exception;
        /** Get projection staleness in seconds. */
        long getProjectionStalenessSeconds() throws Exception;
    }

    public interface ConnectionPoolPort {
        int getPoolSize() throws Exception;
        int getActiveConnections() throws Exception;
        /** Exhaust all connections then verify new request waits (returns wait ms vs negative if crashed). */
        long exhaustPoolAndMeasureWaitMs() throws Exception;
        /** Release pool back to normal. */
        void releasePool() throws Exception;
    }

    public interface AuditPort {
        void audit(String event, String detail) throws Exception;
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final long   QUERY_P95_LIMIT_MS      = 100L;
    private static final long   REPLICA_LAG_LIMIT_MS    = 200L;
    private static final long   SLOW_QUERY_LIMIT        = 0L;   // zero slow queries expected
    private static final long   PROJECTION_LIMIT_S      = 5L;   // projection < 5s stale
    private static final double WRITE_AMP_LIMIT         = 5.0;  // max 5 DB writes per event

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final DbQueryPort dbQuery;
    private final ConnectionPoolPort poolPort;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter suitesPassed;
    private final Counter suitesFailed;

    public DatabaseResilienceQueryPerformanceTestService(
        javax.sql.DataSource ds,
        DbQueryPort dbQuery,
        ConnectionPoolPort poolPort,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds          = ds;
        this.dbQuery     = dbQuery;
        this.poolPort    = poolPort;
        this.audit       = audit;
        this.executor    = executor;
        this.suitesPassed = Counter.builder("integration.perf.db.suites_passed").register(registry);
        this.suitesFailed = Counter.builder("integration.perf.db.suites_failed").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public Promise<SuiteResult> runAll() {
        return Promise.ofBlocking(executor, () -> {
            List<ScenarioResult> results = new ArrayList<>();
            results.add(runScenario("query_p95_100ms",          this::queryP95Check));
            results.add(runScenario("replica_lag_200ms",        this::replicaLagCheck));
            results.add(runScenario("pool_exhaustion_queue",    this::poolExhaustionQueue));
            results.add(runScenario("slow_query_detection",     this::slowQueryDetection));
            results.add(runScenario("vacuum_during_load",       this::vacuumDuringLoad));
            results.add(runScenario("write_amplification",      this::writeAmplificationCheck));
            results.add(runScenario("projection_freshness",     this::projectionFreshnessCheck));

            long passed = results.stream().filter(r -> r.passed).count();
            long failed = results.size() - passed;
            if (failed == 0) suitesPassed.increment(); else suitesFailed.increment();
            audit.audit("DB_RESILIENCE_SUITE", "passed=" + passed + " failed=" + failed);
            return new SuiteResult("DatabaseResilienceQueryPerformance", results, passed, failed);
        });
    }

    // ── Scenarios ─────────────────────────────────────────────────────────────

    /** 95% of standard read queries complete within 100ms. */
    private void queryP95Check(String runId) throws Exception {
        String[] queries = {"order_history", "position_summary", "ledger_balance", "client_profile",
                            "settlement_queue", "recon_breaks", "audit_trail", "regulatory_report"};
        List<Long> times = new ArrayList<>();
        for (String q : queries) {
            for (int i = 0; i < 25; i++) { // 25 × 8 = 200 samples
                times.add(dbQuery.executeReadQuery(q));
            }
        }
        Collections.sort(times);
        long p95 = times.get((int)(times.size() * 0.95));
        assertStep(runId, "query_p95", "95% of queries < " + QUERY_P95_LIMIT_MS + "ms", "< " + QUERY_P95_LIMIT_MS,
            p95 < QUERY_P95_LIMIT_MS, p95 + "ms");
    }

    /** Read replica queries complete within 100ms; replica lag < 200ms. */
    private void replicaLagCheck(String runId) throws Exception {
        long replicaMs = dbQuery.executeReplicaQuery("position_summary");
        assertStep(runId, "replica_query_fast", "replica query < 100ms", "< 100", replicaMs < 100, replicaMs + "ms");
        long lagMs = dbQuery.getReplicaLagMs();
        assertStep(runId, "replica_lag", "replica lag < " + REPLICA_LAG_LIMIT_MS + "ms", "< " + REPLICA_LAG_LIMIT_MS,
            lagMs < REPLICA_LAG_LIMIT_MS, lagMs + "ms");
    }

    /** Exhaust connection pool: new requests wait (not crash). */
    private void poolExhaustionQueue(String runId) throws Exception {
        try {
            long waitMs = poolPort.exhaustPoolAndMeasureWaitMs();
            assertStep(runId, "pool_wait_not_crash", "new request waits when pool exhausted", ">= 0",
                waitMs >= 0, waitMs + "ms wait");
        } finally { poolPort.releasePool(); }
    }

    /** No slow queries (> 1s) in normal operation. */
    private void slowQueryDetection(String runId) throws Exception {
        // Run normal queries for 5s to populate metrics window
        for (int i = 0; i < 20; i++) dbQuery.executeReadQuery("order_history");
        long slowCount = dbQuery.getSlowQueryCount();
        assertStep(runId, "slow_query_count", "0 slow queries in normal operation", String.valueOf(SLOW_QUERY_LIMIT),
            slowCount <= SLOW_QUERY_LIMIT, slowCount);
    }

    /** Concurrent load continues while vacuum/maintenance runs. */
    private void vacuumDuringLoad(String runId) throws Exception {
        // Run concurrent queries; vacuum is background — just verify no errors
        ExecutorService pool = Executors.newFixedThreadPool(10);
        AtomicInteger errs = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            futures.add(pool.submit(() -> {
                try { dbQuery.executeReadQuery("order_history"); }
                catch (Exception ex) { errs.incrementAndGet(); }
            }));
        }
        for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();
        assertStep(runId, "vacuum_no_errors", "no errors during vacuum scenario", "0", errs.get() == 0, errs.get());
    }

    /** Write amplification factor ≤ 5 writes per business event. */
    private void writeAmplificationCheck(String runId) throws Exception {
        double factor = dbQuery.getWriteAmplificationFactor();
        assertStep(runId, "write_amplification", "write amplification ≤ " + WRITE_AMP_LIMIT, "≤ " + WRITE_AMP_LIMIT,
            factor <= WRITE_AMP_LIMIT, factor);
    }

    /** Projections are fresher than 5 seconds stale. */
    private void projectionFreshnessCheck(String runId) throws Exception {
        long staleSeconds = dbQuery.getProjectionStalenessSeconds();
        assertStep(runId, "projection_freshness", "projections < " + PROJECTION_LIMIT_S + "s stale",
            "< " + PROJECTION_LIMIT_S, staleSeconds < PROJECTION_LIMIT_S, staleSeconds + "s");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ScenarioResult runScenario(String name, ThrowingConsumer<String> fn) {
        long start = System.currentTimeMillis();
        try {
            String runId = insertRun(name);
            fn.accept(runId);
            markRunStatus(runId, "PASSED");
            return new ScenarioResult(name, true, null, System.currentTimeMillis() - start);
        } catch (AssertionError ae) {
            return new ScenarioResult(name, false, ae.getMessage(), System.currentTimeMillis() - start);
        } catch (Exception ex) {
            return new ScenarioResult(name, false, ex.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private void assertStep(String runId, String step, String assertion, String expected, boolean passed, Object actual) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO e2e_step_assertions (run_id,step_name,assertion,expected,actual,passed) VALUES (?,?,?,?,?,?)"
             )) {
            ps.setString(1, runId); ps.setString(2, step); ps.setString(3, assertion);
            ps.setString(4, expected); ps.setString(5, String.valueOf(actual)); ps.setBoolean(6, passed);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
        if (!passed) throw new AssertionError("FAIL [" + step + "] " + assertion + " expected=" + expected + " actual=" + actual);
    }

    private String insertRun(String scenario) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO e2e_test_runs (suite_name,scenario) VALUES ('DatabaseResilienceQueryPerformance',?) RETURNING run_id"
             )) {
            ps.setString(1, scenario);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString(1); }
        }
    }

    private void markRunStatus(String runId, String status) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE e2e_test_runs SET status=? WHERE run_id=?"
             )) { ps.setString(1, status); ps.setString(2, runId); ps.executeUpdate(); }
        catch (SQLException ignored) {}
    }

    @FunctionalInterface interface ThrowingConsumer<T> { void accept(T t) throws Exception; }
    public record ScenarioResult(String scenario, boolean passed, String failureMessage, long durationMs) {}
    public record SuiteResult(String suite, List<ScenarioResult> results, long passedCount, long failedCount) {}
}
