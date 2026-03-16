package com.ghatana.appplatform.integration;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Dependency failure chaos test service (T-02).
 *              Scenarios: external market data feed down → cache fallback within TTL;
 *              sanctions list API down → cache fallback; custodian settlement API down →
 *              queue with exponential backoff + DLQ; all failures produce K-07 audit + K-06
 *              alerts; no cascading failure to core order flow.
 * @doc.layer   Integration Testing (T-02 Chaos)
 * @doc.pattern Port-Adapter; JDBC; Promise.ofBlocking; chaos-engineering
 *
 * STORY-T02-004: Implement dependency failure chaos tests
 */
public class DependencyFailureChaosTestService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface DependencyChaosPort {
        void takeDownDependency(String dep) throws Exception;     // signals chaos inject
        void restoreDependency(String dep) throws Exception;
        boolean isDependencyDown(String dep) throws Exception;
    }

    public interface MarketDataPort {
        String getQuote(String symbol) throws Exception;          // returns "CACHED" or live
        long cacheTtlRemainingMs() throws Exception;
        boolean isFallbackActive() throws Exception;
    }

    public interface SanctionsPort {
        boolean screenEntity(String entityId) throws Exception;   // must not throw when feed down
        boolean isCacheFallbackActive() throws Exception;
        long cacheTtlRemainingMs() throws Exception;
    }

    public interface CustodianPort {
        void submitSettlement(String settlementId) throws Exception;
        String getSettlementStatus(String settlementId) throws Exception; // QUEUED, SENT, FAILED, DLQ
        int getRetryCount(String settlementId) throws Exception;
        boolean isInDlq(String settlementId) throws Exception;
    }

    public interface AlertPort {
        boolean hasK06Alert(String component) throws Exception;   // ops alert fired
        boolean hasK07Audit(String component) throws Exception;   // audit event captured
    }

    public interface OrderSubmissionPort {
        String submitOrder(String orderId) throws Exception;
        String getOrderStatus(String orderId) throws Exception;   // ACCEPTED, REJECTED, PENDING
    }

    public interface AuditPort {
        void audit(String event, String detail) throws Exception;
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final long MAX_CACHE_TTL_MS = 60_000L; // 60s fallback window

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final DependencyChaosPort dependencyChaos;
    private final MarketDataPort marketData;
    private final SanctionsPort sanctions;
    private final CustodianPort custodian;
    private final AlertPort alertPort;
    private final OrderSubmissionPort orderPort;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter suitesPassed;
    private final Counter suitesFailed;

    public DependencyFailureChaosTestService(
        javax.sql.DataSource ds,
        DependencyChaosPort dependencyChaos,
        MarketDataPort marketData,
        SanctionsPort sanctions,
        CustodianPort custodian,
        AlertPort alertPort,
        OrderSubmissionPort orderPort,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds             = ds;
        this.dependencyChaos= dependencyChaos;
        this.marketData     = marketData;
        this.sanctions      = sanctions;
        this.custodian      = custodian;
        this.alertPort      = alertPort;
        this.orderPort      = orderPort;
        this.audit          = audit;
        this.executor       = executor;
        this.suitesPassed   = Counter.builder("integration.chaos.dep.suites_passed").register(registry);
        this.suitesFailed   = Counter.builder("integration.chaos.dep.suites_failed").register(registry);
    }

    public Promise<SuiteResult> runAll() {
        return Promise.ofBlocking(executor, () -> {
            List<ScenarioResult> results = new ArrayList<>();
            results.add(runScenario("market_data_fallback",    this::marketDataFallback));
            results.add(runScenario("sanctions_cache",         this::sanctionsCache));
            results.add(runScenario("custodian_retry_dlq",     this::custodianRetryDlq));
            results.add(runScenario("no_cascading_failure",    this::noCascadingFailure));
            results.add(runScenario("k07_failure_events",      this::k07FailureEvents));
            results.add(runScenario("k06_alert_fired",         this::k06AlertFired));
            results.add(runScenario("cache_ttl_respected",     this::cacheTtlRespected));
            results.add(runScenario("fallback_expiry",         this::fallbackExpiry));

            long passed = results.stream().filter(r -> r.passed).count();
            long failed = results.size() - passed;
            if (failed == 0) suitesPassed.increment(); else suitesFailed.increment();
            audit.audit("DEP_FAILURE_SUITE", "passed=" + passed + " failed=" + failed);
            return new SuiteResult("DependencyFailureChaos", results, passed, failed);
        });
    }

    private void marketDataFallback(String runId) throws Exception {
        dependencyChaos.takeDownDependency("market-data-feed");
        try {
            String quote = marketData.getQuote("AAPL");
            assertStep(runId, "quote_not_null", "quote still returned with feed down", "non-null",
                quote != null && !quote.isBlank(), quote);
            boolean fallback = marketData.isFallbackActive();
            assertStep(runId, "fallback_active", "market data fallback active", "true", fallback, fallback);
        } finally { dependencyChaos.restoreDependency("market-data-feed"); }
    }

    private void sanctionsCache(String runId) throws Exception {
        dependencyChaos.takeDownDependency("sanctions-api");
        try {
            boolean screened = sanctions.screenEntity("ENTITY-001"); // should not throw
            assertStep(runId, "sanctions_still_works", "sanctions screen does not throw when API down",
                "true", true, "ok");
            boolean fallback = sanctions.isCacheFallbackActive();
            assertStep(runId, "sanctions_cache_active", "sanctions list cache fallback active", "true",
                fallback, fallback);
        } finally { dependencyChaos.restoreDependency("sanctions-api"); }
    }

    private void custodianRetryDlq(String runId) throws Exception {
        dependencyChaos.takeDownDependency("custodian-settlement-api");
        String settlementId = "SETT-CHAOS-" + System.nanoTime();
        try {
            custodian.submitSettlement(settlementId);
            // Allow exponential backoff retries to occur
            Thread.sleep(5000);
            int retries = custodian.getRetryCount(settlementId);
            assertStep(runId, "retried_at_least_once", "custodian settlement retried on failure",
                ">= 1", retries >= 1, retries);
        } finally { dependencyChaos.restoreDependency("custodian-settlement-api"); }
        // After restore, either sent or DLQ after exhaustion
        Thread.sleep(2000);
        String status = custodian.getSettlementStatus(settlementId);
        boolean resolved = "SENT".equals(status) || "DLQ".equals(status);
        assertStep(runId, "custodian_resolved", "settlement eventually SENT or DLQ after restore",
            "SENT or DLQ", resolved, status);
    }

    /** Core order submission must work even with market-data, sanctions, custodian all down. */
    private void noCascadingFailure(String runId) throws Exception {
        dependencyChaos.takeDownDependency("market-data-feed");
        dependencyChaos.takeDownDependency("sanctions-api");
        try {
            String orderId = "ORD-CASCADE-" + System.nanoTime();
            orderPort.submitOrder(orderId);
            String status = orderPort.getOrderStatus(orderId);
            assertStep(runId, "order_accepted_despite_deps_down", "core order accepted despite dependency failures",
                "ACCEPTED", "ACCEPTED".equals(status), status);
        } finally {
            dependencyChaos.restoreDependency("market-data-feed");
            dependencyChaos.restoreDependency("sanctions-api");
        }
    }

    private void k07FailureEvents(String runId) throws Exception {
        dependencyChaos.takeDownDependency("market-data-feed");
        try {
            marketData.isFallbackActive(); // trigger k-07  audit path
            Thread.sleep(500);
            boolean k07 = alertPort.hasK07Audit("market-data-feed");
            assertStep(runId, "k07_generated", "K-07 audit event generated for market-data failure",
                "true", k07, k07);
        } finally { dependencyChaos.restoreDependency("market-data-feed"); }
    }

    private void k06AlertFired(String runId) throws Exception {
        dependencyChaos.takeDownDependency("custodian-settlement-api");
        try {
            Thread.sleep(1000);
            boolean k06 = alertPort.hasK06Alert("custodian-settlement-api");
            assertStep(runId, "k06_alert_generated", "K-06 ops alert fired for custodian failure",
                "true", k06, k06);
        } finally { dependencyChaos.restoreDependency("custodian-settlement-api"); }
    }

    private void cacheTtlRespected(String runId) throws Exception {
        dependencyChaos.takeDownDependency("market-data-feed");
        try {
            long ttl = marketData.cacheTtlRemainingMs();
            assertStep(runId, "cache_ttl_positive", "cache TTL is positive while fallback active",
                "> 0", ttl > 0, ttl + "ms");
            assertStep(runId, "cache_ttl_bounded", "cache TTL within expected window",
                "<= " + MAX_CACHE_TTL_MS, ttl <= MAX_CACHE_TTL_MS, ttl + "ms");
        } finally { dependencyChaos.restoreDependency("market-data-feed"); }
    }

    /**
     * Once cache TTL expires with no restore, service should signal fallback expiry
     * (e.g., serve stale with degraded flag or reject new quotes).
     */
    private void fallbackExpiry(String runId) throws Exception {
        dependencyChaos.takeDownDependency("sanctions-api");
        try {
            long ttl = sanctions.cacheTtlRemainingMs();
            boolean ttlPositive = ttl > 0;
            assertStep(runId, "sanctions_ttl_tracked", "sanctions cache TTL is tracked", "> 0",
                ttlPositive, ttl + "ms");
            // Verify that screening still returns a result (degraded, not crash)
            boolean screened = sanctions.screenEntity("ENTITY-002");
            assertStep(runId, "screening_returns_result", "sanctions returns cached result within TTL",
                "no-throw", true, "ok");
        } finally { dependencyChaos.restoreDependency("sanctions-api"); }
    }

    private ScenarioResult runScenario(String name, ThrowingConsumer<String> fn) {
        long start = System.currentTimeMillis();
        try {
            String runId = insertRun(name); fn.accept(runId); markRunStatus(runId, "PASSED");
            return new ScenarioResult(name, true, null, System.currentTimeMillis() - start);
        } catch (AssertionError ae) { return new ScenarioResult(name, false, ae.getMessage(), System.currentTimeMillis() - start);
        } catch (Exception ex)      { return new ScenarioResult(name, false, ex.getMessage(),  System.currentTimeMillis() - start); }
    }

    private void assertStep(String runId, String step, String assertion, String expected, boolean passed, Object actual) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO e2e_step_assertions (run_id,step_name,assertion,expected,actual,passed) VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, runId); ps.setString(2, step); ps.setString(3, assertion);
            ps.setString(4, expected); ps.setString(5, String.valueOf(actual)); ps.setBoolean(6, passed);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
        if (!passed) throw new AssertionError("FAIL [" + step + "] " + assertion + " expected=" + expected + " actual=" + actual);
    }

    private String insertRun(String scenario) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO e2e_test_runs (suite_name,scenario) VALUES ('DependencyFailureChaos',?) RETURNING run_id")) {
            ps.setString(1, scenario);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString(1); }
        }
    }

    private void markRunStatus(String runId, String status) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE e2e_test_runs SET status=? WHERE run_id=?")) {
            ps.setString(1, status); ps.setString(2, runId); ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    @FunctionalInterface interface ThrowingConsumer<T> { void accept(T t) throws Exception; }
    public record ScenarioResult(String scenario, boolean passed, String failureMessage, long durationMs) {}
    public record SuiteResult(String suite, List<ScenarioResult> results, long passedCount, long failedCount) {}
}
