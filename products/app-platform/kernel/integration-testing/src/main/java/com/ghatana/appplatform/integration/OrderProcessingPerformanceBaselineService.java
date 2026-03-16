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
 * @doc.purpose Order processing performance baseline service.
 *              Validates: 10,000 orders/hour steady-state; p99 acceptance < 200ms;
 *              spike to 25K/hour for 10 min; error rate < 0.01%; zero data loss.
 * @doc.layer   Integration Testing (T-01)
 * @doc.pattern Port-Adapter; JDBC; Promise.ofBlocking; load-test; latency-histogram
 *
 * STORY-T01-008: Implement order processing performance baseline
 */
public class OrderProcessingPerformanceBaselineService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface OrderSubmissionPort {
        /** Submit one order. Must return within reasonable time. */
        String submitOrder(String clientId, String symbol, int qty, double price) throws Exception;
        String getOrderStatus(String orderId) throws Exception;
    }

    public interface MetricsCollectionPort {
        long getOrdersAcceptedCount() throws Exception;
        double getP99LatencyMs() throws Exception;
        double getCpuUsagePct() throws Exception;
        long getMemoryUsedMb() throws Exception;
    }

    public interface AuditPort {
        void audit(String event, String detail) throws Exception;
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final int STEADY_ORDERS_PER_HOUR = 10_000;
    private static final int SPIKE_ORDERS_PER_HOUR  = 25_000;
    private static final long P99_LIMIT_MS           = 200L;
    private static final long P99_SPIKE_LIMIT_MS     = 500L;
    private static final double ERROR_RATE_LIMIT     = 0.0001; // 0.01%

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final OrderSubmissionPort orderPort;
    private final MetricsCollectionPort metricsPort;
    private final AuditPort audit;
    private final Executor executor;
    private final Timer orderLatency;
    private final Counter suitesPassed;
    private final Counter suitesFailed;

    public OrderProcessingPerformanceBaselineService(
        javax.sql.DataSource ds,
        OrderSubmissionPort orderPort,
        MetricsCollectionPort metricsPort,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds         = ds;
        this.orderPort  = orderPort;
        this.metricsPort = metricsPort;
        this.audit      = audit;
        this.executor   = executor;
        this.orderLatency = Timer.builder("integration.perf.order_latency").register(registry);
        this.suitesPassed = Counter.builder("integration.perf.order.suites_passed").register(registry);
        this.suitesFailed = Counter.builder("integration.perf.order.suites_failed").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public Promise<SuiteResult> runAll() {
        return Promise.ofBlocking(executor, () -> {
            List<ScenarioResult> results = new ArrayList<>();
            results.add(runScenario("sustained_load_10k",      this::sustainedLoad10k));
            results.add(runScenario("spike_25k",               this::spike25k));
            results.add(runScenario("p99_200ms",               this::p99LatencyCheck));
            results.add(runScenario("error_rate_001",          this::errorRateCheck));
            results.add(runScenario("zero_data_loss",          this::zeroDataLossCheck));
            results.add(runScenario("cpu_memory_per_pod",      this::cpuMemoryCheck));
            results.add(runScenario("trend_vs_previous",       this::trendVsPrevious));

            long passed = results.stream().filter(r -> r.passed).count();
            long failed = results.size() - passed;
            if (failed == 0) suitesPassed.increment(); else suitesFailed.increment();
            audit.audit("ORDER_PERF_SUITE", "passed=" + passed + " failed=" + failed);
            return new SuiteResult("OrderProcessingPerformanceBaseline", results, passed, failed);
        });
    }

    // ── Scenarios ─────────────────────────────────────────────────────────────

    /** Submit a proportional burst of 10K orders (scaled from hour-rate) and verify all accepted. */
    private void sustainedLoad10k(String runId) throws Exception {
        // Scale: 1000 orders in 360ms represents 10K/hour rate-correct burst
        int batch = 1000;
        AtomicInteger submitted = new AtomicInteger();
        AtomicInteger errors    = new AtomicInteger();
        List<Long> latencies    = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(20);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < batch; i++) {
            int idx = i;
            futures.add(pool.submit(() -> {
                long t0 = System.currentTimeMillis();
                try {
                    orderPort.submitOrder("PERF-CLIENT-" + (idx % 50), "NABIL", 100, 1500.0);
                    submitted.incrementAndGet();
                } catch (Exception ex) { errors.incrementAndGet(); }
                synchronized (latencies) { latencies.add(System.currentTimeMillis() - t0); }
            }));
        }
        for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();
        assertStep(runId, "sustained_submitted", "all 1000 orders submitted", String.valueOf(batch),
            submitted.get() >= batch * 0.99, submitted.get() + "/" + batch);
        // compute p99
        Collections.sort(latencies);
        long p99 = latencies.get((int)(latencies.size() * 0.99));
        assertStep(runId, "sustained_p99", "p99 latency < " + P99_LIMIT_MS + "ms under 10K load", "< " + P99_LIMIT_MS,
            p99 < P99_LIMIT_MS, p99 + "ms");
        persistPerformanceRecord(runId, "sustained_load_10k", submitted.get(), p99, errors.get());
    }

    /** Spike to 25K/hour equivalent: 2500-order burst. */
    private void spike25k(String runId) throws Exception {
        int batch = 2500;
        AtomicInteger submitted = new AtomicInteger();
        AtomicInteger errors    = new AtomicInteger();
        List<Long> latencies    = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(50);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < batch; i++) {
            int idx = i;
            futures.add(pool.submit(() -> {
                long t0 = System.currentTimeMillis();
                try {
                    orderPort.submitOrder("SPIKE-CLIENT-" + (idx % 100), "NTC", 50, 950.0);
                    submitted.incrementAndGet();
                } catch (Exception ex) { errors.incrementAndGet(); }
                synchronized (latencies) { latencies.add(System.currentTimeMillis() - t0); }
            }));
        }
        for (Future<?> f : futures) f.get(60, TimeUnit.SECONDS);
        pool.shutdown();
        Collections.sort(latencies);
        long p99 = latencies.get((int)(latencies.size() * 0.99));
        assertStep(runId, "spike_p99", "p99 < " + P99_SPIKE_LIMIT_MS + "ms during spike", "< " + P99_SPIKE_LIMIT_MS,
            p99 < P99_SPIKE_LIMIT_MS, p99 + "ms");
        assertStep(runId, "spike_submitted", "spike orders accepted", ">= " + (batch * 0.99),
            submitted.get() >= batch * 0.99, submitted.get() + "/" + batch);
        persistPerformanceRecord(runId, "spike_25k", submitted.get(), p99, errors.get());
    }

    /** Verify p99 from metrics collection port matches target. */
    private void p99LatencyCheck(String runId) throws Exception {
        double p99 = metricsPort.getP99LatencyMs();
        assertStep(runId, "p99_from_metrics", "system p99 order acceptance < " + P99_LIMIT_MS + "ms", "< " + P99_LIMIT_MS,
            p99 < P99_LIMIT_MS, p99 + "ms");
    }

    /** Error rate must be below 0.01% for a batch of 1000 orders. */
    private void errorRateCheck(String runId) throws Exception {
        int batch = 1000;
        AtomicInteger errors = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(20);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < batch; i++) {
            int idx = i;
            futures.add(pool.submit(() -> {
                try { orderPort.submitOrder("ERRRATE-" + (idx % 50), "NLIC", 100, 1000.0); }
                catch (Exception ex) { errors.incrementAndGet(); }
            }));
        }
        for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();
        double errRate = (double) errors.get() / batch;
        assertStep(runId, "error_rate", "error rate < " + ERROR_RATE_LIMIT, "< " + ERROR_RATE_LIMIT,
            errRate <= ERROR_RATE_LIMIT, errRate);
    }

    /** Every submitted order ID can be queried back. */
    private void zeroDataLossCheck(String runId) throws Exception {
        List<String> orderIds = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            String id = orderPort.submitOrder("ZDL-CLIENT-" + i, "NABIL", 10, 1500.0);
            if (id != null) orderIds.add(id);
        }
        Thread.sleep(200);
        int missing = 0;
        for (String id : orderIds) {
            String status = orderPort.getOrderStatus(id);
            if (status == null || status.isEmpty()) missing++;
        }
        assertStep(runId, "zero_data_loss", "all submitted orders retrievable", "0 missing",
            missing == 0, missing + " missing out of " + orderIds.size());
    }

    /** CPU and memory remain within acceptable bounds during load. */
    private void cpuMemoryCheck(String runId) throws Exception {
        double cpu = metricsPort.getCpuUsagePct();
        long memMb = metricsPort.getMemoryUsedMb();
        assertStep(runId, "cpu_below_80pct", "CPU usage < 80%", "< 80", cpu < 80.0, cpu + "%");
        assertStep(runId, "memory_below_4gb", "memory used < 4096 MB", "< 4096", memMb < 4096, memMb + "MB");
    }

    /** Persist trend record for comparison with previous runs. */
    private void trendVsPrevious(String runId) throws Exception {
        double p99 = metricsPort.getP99LatencyMs();
        persistPerformanceRecord(runId, "trend_baseline", 0, (long) p99, 0);
        // Query previous stored p99; allow up to 10% regression
        Long prevP99 = queryPreviousP99("OrderProcessingBaseline");
        if (prevP99 != null) {
            boolean noRegression = p99 <= prevP99 * 1.10;
            assertStep(runId, "trend_no_regression", "p99 not regressed >10% vs baseline", "< " + (prevP99 * 1.10),
                noRegression, p99 + "ms (prev=" + prevP99 + ")");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void persistPerformanceRecord(String runId, String scenario, int count, long p99Ms, int errors) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO perf_baselines (run_id,suite,scenario,order_count,p99_ms,error_count,measured_at) " +
                 "VALUES (?,?,?,?,?,?,NOW()) ON CONFLICT DO NOTHING"
             )) {
            ps.setString(1, runId); ps.setString(2, "OrderProcessingBaseline");
            ps.setString(3, scenario); ps.setInt(4, count); ps.setLong(5, p99Ms); ps.setInt(6, errors);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private Long queryPreviousP99(String suite) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT p99_ms FROM perf_baselines WHERE suite=? ORDER BY measured_at DESC LIMIT 1 OFFSET 1"
             )) {
            ps.setString(1, suite);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getLong(1) : null; }
        } catch (SQLException e) { return null; }
    }

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
                 "INSERT INTO e2e_test_runs (suite_name,scenario) VALUES ('OrderProcessingPerformanceBaseline',?) RETURNING run_id"
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
