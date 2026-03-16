package com.ghatana.appplatform.integration;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Slow dependency chaos test service (T-02).
 *              Scenarios: Toxiproxy-style 2s latency injection on inter-service calls;
 *              circuit breaker triggers before request queue builds; timeout triggers
 *              fail-fast; slow K-02 config service → other services use cached config;
 *              user-facing p99 stays bounded; no cascade latency.
 * @doc.layer   Integration Testing (T-02 Chaos)
 * @doc.pattern Port-Adapter; JDBC; Promise.ofBlocking; chaos-engineering
 *
 * STORY-T02-005: Implement slow dependency chaos tests
 */
public class SlowDependencyChaosTestService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface LatencyChaosPort {
        /** Inject fixed latency (ms) on calls between two services. Returns chaosId. */
        String injectLatency(String fromService, String toService, long latencyMs) throws Exception;
        /** Remove latency injection. */
        void removeLatency(String chaosId) throws Exception;
    }

    public interface CircuitBreakerPort {
        boolean isOpen(String service) throws Exception;
        int getRequestQueueDepth(String service) throws Exception;
    }

    public interface ResiliencePort {
        boolean hasFallbackActivated(String service) throws Exception;
        long measureUserFacingP99Ms(String operation) throws Exception;
        boolean isFailFastActive(String service) throws Exception;
        int getRetryCount(String service) throws Exception;
    }

    public interface ConfigCachePort {
        boolean isUsingCachedConfig(String service) throws Exception;
        boolean isConfigCurrent(String service) throws Exception;  // stale is OK as long as not crashed
    }

    public interface ApiGatewayPort {
        long sendRequestAndMeasureMs(String tenantId, String operation) throws Exception;
    }

    public interface AuditPort {
        void audit(String event, String detail) throws Exception;
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final long INJECTED_LATENCY_MS      = 2_000L;   // 2s injected
    private static final long CB_TRIP_THRESHOLD_MS     = 500L;     // CB trips at 500ms
    private static final long USER_FACING_P99_LIMIT_MS = 300L;     // user p99 < 300ms
    private static final int  MAX_QUEUE_DEPTH          = 10;       // CB should open before this

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final LatencyChaosPort latencyChaos;
    private final CircuitBreakerPort circuitBreaker;
    private final ResiliencePort resilience;
    private final ConfigCachePort configCache;
    private final ApiGatewayPort apiGateway;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter suitesPassed;
    private final Counter suitesFailed;
    private final Timer userFacingLatency;

    public SlowDependencyChaosTestService(
        javax.sql.DataSource ds,
        LatencyChaosPort latencyChaos,
        CircuitBreakerPort circuitBreaker,
        ResiliencePort resilience,
        ConfigCachePort configCache,
        ApiGatewayPort apiGateway,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds             = ds;
        this.latencyChaos   = latencyChaos;
        this.circuitBreaker = circuitBreaker;
        this.resilience     = resilience;
        this.configCache    = configCache;
        this.apiGateway     = apiGateway;
        this.audit          = audit;
        this.executor       = executor;
        this.suitesPassed   = Counter.builder("integration.chaos.slow_dep.suites_passed").register(registry);
        this.suitesFailed   = Counter.builder("integration.chaos.slow_dep.suites_failed").register(registry);
        this.userFacingLatency = Timer.builder("integration.chaos.slow_dep.user_facing_p99").register(registry);
    }

    public Promise<SuiteResult> runAll() {
        return Promise.ofBlocking(executor, () -> {
            List<ScenarioResult> results = new ArrayList<>();
            results.add(runScenario("circuit_breaker_500ms",  this::circuitBreaker500ms));
            results.add(runScenario("cache_config_fallback",  this::cacheConfigFallback));
            results.add(runScenario("latency_injection",      this::latencyInjection));
            results.add(runScenario("user_facing_p99",        this::userFacingP99));
            results.add(runScenario("fail_fast",              this::failFast));
            results.add(runScenario("no_cascade_latency",     this::noCascadeLatency));
            results.add(runScenario("toxiproxy_scenarios",    this::toxiproxyScenarios));

            long passed = results.stream().filter(r -> r.passed).count();
            long failed = results.size() - passed;
            if (failed == 0) suitesPassed.increment(); else suitesFailed.increment();
            audit.audit("SLOW_DEP_SUITE", "passed=" + passed + " failed=" + failed);
            return new SuiteResult("SlowDependencyChaos", results, passed, failed);
        });
    }

    /**
     * Injecting 2s latency on OMS→EMS calls should trigger the EMS circuit breaker
     * before the OMS request queue builds up beyond MAX_QUEUE_DEPTH.
     */
    private void circuitBreaker500ms(String runId) throws Exception {
        String chaosId = latencyChaos.injectLatency("oms", "ems", INJECTED_LATENCY_MS);
        try {
            // Send a few requests to trigger CB
            Thread.sleep(3000);
            boolean cbOpen = circuitBreaker.isOpen("ems");
            assertStep(runId, "cb_opens_on_slow_dep", "CB opens on EMS within 2s latency injection",
                "true", cbOpen, cbOpen);
            int queueDepth = circuitBreaker.getRequestQueueDepth("ems");
            assertStep(runId, "queue_depth_bounded", "request queue depth bounded (< " + MAX_QUEUE_DEPTH + ")",
                "< " + MAX_QUEUE_DEPTH, queueDepth < MAX_QUEUE_DEPTH, queueDepth);
        } finally { latencyChaos.removeLatency(chaosId); }
    }

    /** K-02 config service slow → other services use cached config safely. */
    private void cacheConfigFallback(String runId) throws Exception {
        String chaosId = latencyChaos.injectLatency("oms", "config-service", INJECTED_LATENCY_MS);
        try {
            Thread.sleep(2000);
            boolean cachedConfig = configCache.isUsingCachedConfig("oms");
            assertStep(runId, "oms_uses_cached_config", "OMS uses cached config when config-service is slow",
                "true", cachedConfig, cachedConfig);
        } finally { latencyChaos.removeLatency(chaosId); }
    }

    /** Latency injection is measurable and affects only the injected path. */
    private void latencyInjection(String runId) throws Exception {
        // Baseline measurement
        long baselineMs = apiGateway.sendRequestAndMeasureMs("tenant-001", "order_submit");

        String chaosId = latencyChaos.injectLatency("oms", "ems", INJECTED_LATENCY_MS);
        try {
            // Measure internal OMS→EMS path (injected)
            long injectedMs = apiGateway.sendRequestAndMeasureMs("tenant-001", "order_routing");
            // The injected path should be noticeably slower
            assertStep(runId, "latency_measurably_injected", "injected path slower than baseline",
                "increased", injectedMs > baselineMs, baselineMs + "ms baseline vs " + injectedMs + "ms);");
        } finally { latencyChaos.removeLatency(chaosId); }
    }

    /** User-facing p99 must remain below limit even with internal latency injection. */
    private void userFacingP99(String runId) throws Exception {
        String chaosId = latencyChaos.injectLatency("oms", "ems", INJECTED_LATENCY_MS);
        try {
            Thread.sleep(2000); // allow CB / fallback to activate
            long p99 = resilience.measureUserFacingP99Ms("order_submit");
            assertStep(runId, "user_facing_p99_bounded",
                "user-facing p99 < " + USER_FACING_P99_LIMIT_MS + "ms despite internal latency",
                "< " + USER_FACING_P99_LIMIT_MS, p99 < USER_FACING_P99_LIMIT_MS, p99 + "ms");
        } finally { latencyChaos.removeLatency(chaosId); }
    }

    /** Timeout must trigger fail-fast (not wait full 2s) when threshold exceeded. */
    private void failFast(String runId) throws Exception {
        String chaosId = latencyChaos.injectLatency("oms", "ems", INJECTED_LATENCY_MS);
        try {
            Thread.sleep(2000);
            boolean failFast = resilience.isFailFastActive("ems");
            assertStep(runId, "fail_fast_active", "fail-fast activated for EMS on latency injection",
                "true", failFast, failFast);
            // Fallback should also be available
            boolean fallback = resilience.hasFallbackActivated("ems");
            assertStep(runId, "fallback_active_on_slow_ems", "fallback activated after fail-fast",
                "true", fallback, fallback);
        } finally { latencyChaos.removeLatency(chaosId); }
    }

    /** Slow EMS should not cascade slowness to the Settlement service. */
    private void noCascadeLatency(String runId) throws Exception {
        String chaosId = latencyChaos.injectLatency("oms", "ems", INJECTED_LATENCY_MS);
        try {
            Thread.sleep(2000);
            // Settlement should respond quickly regardless
            long settlementMs = apiGateway.sendRequestAndMeasureMs("tenant-001", "settlement_status");
            assertStep(runId, "settlement_not_cascaded", "settlement p99 not cascaded by slow EMS",
                "< " + USER_FACING_P99_LIMIT_MS, settlementMs < USER_FACING_P99_LIMIT_MS, settlementMs + "ms");
        } finally { latencyChaos.removeLatency(chaosId); }
    }

    /**
     * Toxiproxy-style multi-scenario: inject latency on 3 different paths sequentially.
     * Each path should use CB or fallback and not propagate latency upstream.
     */
    private void toxiproxyScenarios(String runId) throws Exception {
        String[][] paths = {
            {"oms",    "ems"},
            {"ems",    "settlement"},
            {"api-gw", "config-service"}
        };
        for (String[] path : paths) {
            String from = path[0]; String to = path[1];
            String chaosId = latencyChaos.injectLatency(from, to, INJECTED_LATENCY_MS);
            try {
                Thread.sleep(2000);
                boolean cbOpen   = circuitBreaker.isOpen(to);
                boolean fallback = resilience.hasFallbackActivated(to);
                boolean protected_ = cbOpen || fallback;
                assertStep(runId, "toxiproxy_" + from + "_" + to,
                    from + "→" + to + " slow: CB or fallback active",
                    "CB or fallback", protected_, "CB=" + cbOpen + " fallback=" + fallback);
            } finally { latencyChaos.removeLatency(chaosId); }
        }
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
                 "INSERT INTO e2e_test_runs (suite_name,scenario) VALUES ('SlowDependencyChaos',?) RETURNING run_id")) {
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
