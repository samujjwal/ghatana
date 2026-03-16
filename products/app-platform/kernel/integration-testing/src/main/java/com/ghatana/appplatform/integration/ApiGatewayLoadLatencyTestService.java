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
 * @doc.purpose API Gateway (K-11) load and latency test service.
 *              Validates: 5000 concurrent sessions across 50 tenants; p99 gateway overhead < 10ms;
 *              rate limiting fires at tenant quota; SSL termination latency < 5ms;
 *              session isolation (no cross-tenant contamination).
 * @doc.layer   Integration Testing (T-01)
 * @doc.pattern Port-Adapter; JDBC; Promise.ofBlocking; load-test; latency
 *
 * STORY-T01-010: Implement API gateway load and latency test
 */
public class ApiGatewayLoadLatencyTestService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface ApiGatewayPort {
        /** Send an authenticated HTTP request through the gateway. Returns latency-breakdown in ms. */
        GatewayResponse sendRequest(String tenantToken, String endpoint, String method) throws Exception;
        /** Measure SSL handshake latency to gateway. */
        double measureSslHandshakeMs(String tenantToken) throws Exception;
        /** Check if rate-limit response returned for given tenant. */
        boolean isRateLimited(String tenantToken) throws Exception;
        /** Get session data associated with token (for isolation check). */
        String getSessionData(String tenantToken) throws Exception;
        /** Gateway overhead latency (excluding backend time) in ms for last N requests. */
        double getGatewayOverheadP99Ms() throws Exception;
    }

    public record GatewayResponse(int statusCode, long gatewayOverheadMs, long totalMs, String tenantId) {}

    public interface TenantTokenPort {
        /** Obtain a valid auth token for tenantId. */
        String getToken(String tenantId) throws Exception;
        /** Obtain a token for a tenant that has exhausted quota. */
        String getQuotaExhaustedToken() throws Exception;
    }

    public interface AuditPort {
        void audit(String event, String detail) throws Exception;
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final int    TENANT_COUNT          = 50;
    private static final int    SESSIONS_PER_TENANT   = 100; // 50 × 100 = 5000 concurrent
    private static final long   GATEWAY_OVERHEAD_LIMIT_MS = 10L;
    private static final double SSL_LATENCY_LIMIT_MS  = 5.0;

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final ApiGatewayPort gateway;
    private final TenantTokenPort tokenPort;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter suitesPassed;
    private final Counter suitesFailed;

    public ApiGatewayLoadLatencyTestService(
        javax.sql.DataSource ds,
        ApiGatewayPort gateway,
        TenantTokenPort tokenPort,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds          = ds;
        this.gateway     = gateway;
        this.tokenPort   = tokenPort;
        this.audit       = audit;
        this.executor    = executor;
        this.suitesPassed = Counter.builder("integration.perf.gateway.suites_passed").register(registry);
        this.suitesFailed = Counter.builder("integration.perf.gateway.suites_failed").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public Promise<SuiteResult> runAll() {
        return Promise.ofBlocking(executor, () -> {
            List<ScenarioResult> results = new ArrayList<>();
            results.add(runScenario("concurrent_sessions",    this::concurrentSessionsLoad));
            results.add(runScenario("session_isolation",      this::sessionIsolation));
            results.add(runScenario("gateway_overhead_p99",   this::gatewayOverheadP99));
            results.add(runScenario("rate_limit_precision",   this::rateLimitPrecision));
            results.add(runScenario("ssl_latency",            this::sslLatencyCheck));
            results.add(runScenario("tenant_auth_overhead",   this::tenantAuthOverhead));
            results.add(runScenario("read_write_mix",         this::readWriteMix));

            long passed = results.stream().filter(r -> r.passed).count();
            long failed = results.size() - passed;
            if (failed == 0) suitesPassed.increment(); else suitesFailed.increment();
            audit.audit("GATEWAY_LOAD_SUITE", "passed=" + passed + " failed=" + failed);
            return new SuiteResult("ApiGatewayLoadLatency", results, passed, failed);
        });
    }

    // ── Scenarios ─────────────────────────────────────────────────────────────

    /** 5000 concurrent requests across 50 tenants; all succeed with 2xx. */
    private void concurrentSessionsLoad(String runId) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(200);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger errors  = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < TENANT_COUNT; t++) {
            String token = tokenPort.getToken("LOAD-TENANT-" + t);
            for (int s = 0; s < SESSIONS_PER_TENANT; s++) {
                futures.add(pool.submit(() -> {
                    try {
                        GatewayResponse r = gateway.sendRequest(token, "/api/orders", "GET");
                        if (r.statusCode() >= 200 && r.statusCode() < 300) success.incrementAndGet();
                        else errors.incrementAndGet();
                    } catch (Exception ex) { errors.incrementAndGet(); }
                }));
            }
        }
        for (Future<?> f : futures) f.get(60, TimeUnit.SECONDS);
        pool.shutdown();
        int total = success.get() + errors.get();
        assertStep(runId, "concurrent_sessions_success", "≥99% of 5000 sessions succeed",
            ">= 4950", success.get() >= 4950, success.get() + "/" + total);
    }

    /** Requests from tenant A must not see tenant B session data. */
    private void sessionIsolation(String runId) throws Exception {
        String tokenA = tokenPort.getToken("ISO-TENANT-A");
        String tokenB = tokenPort.getToken("ISO-TENANT-B");
        gateway.sendRequest(tokenA, "/api/orders", "GET");
        gateway.sendRequest(tokenB, "/api/orders", "GET");
        String sessionA = gateway.getSessionData(tokenA);
        String sessionB = gateway.getSessionData(tokenB);
        boolean isolated = !sessionA.contains("ISO-TENANT-B") && !sessionB.contains("ISO-TENANT-A");
        assertStep(runId, "session_isolation", "tenant sessions are isolated", "true", isolated, isolated);
    }

    /** Gateway overhead p99 < 10ms. */
    private void gatewayOverheadP99(String runId) throws Exception {
        // Warm up
        String token = tokenPort.getToken("OVERHEAD-TENANT");
        for (int i = 0; i < 50; i++) gateway.sendRequest(token, "/api/health", "GET");
        double p99 = gateway.getGatewayOverheadP99Ms();
        assertStep(runId, "gateway_overhead_p99", "gateway overhead p99 < " + GATEWAY_OVERHEAD_LIMIT_MS + "ms",
            "< " + GATEWAY_OVERHEAD_LIMIT_MS, p99 < GATEWAY_OVERHEAD_LIMIT_MS, p99 + "ms");
    }

    /** Rate limiting fires when tenant quota is exceeded. */
    private void rateLimitPrecision(String runId) throws Exception {
        String exhaustedToken = tokenPort.getQuotaExhaustedToken();
        boolean rateLimited = gateway.isRateLimited(exhaustedToken);
        assertStep(runId, "rate_limit_fires", "rate limit response for quota-exhausted tenant", "true",
            rateLimited, rateLimited);
        // Normal tenant must still work
        String normalToken = tokenPort.getToken("RATELIMIT-NORMAL-TENANT");
        GatewayResponse r = gateway.sendRequest(normalToken, "/api/orders", "GET");
        assertStep(runId, "normal_tenant_unaffected", "normal tenant unaffected by peer's rate limit", "2xx",
            r.statusCode() >= 200 && r.statusCode() < 300, r.statusCode());
    }

    /** SSL handshake < 5ms. */
    private void sslLatencyCheck(String runId) throws Exception {
        String token = tokenPort.getToken("SSL-TENANT");
        double latency = gateway.measureSslHandshakeMs(token);
        assertStep(runId, "ssl_latency", "SSL handshake < " + SSL_LATENCY_LIMIT_MS + "ms",
            "< " + SSL_LATENCY_LIMIT_MS, latency < SSL_LATENCY_LIMIT_MS, latency + "ms");
    }

    /** Auth token validation overhead is within 10ms additional overhead. */
    private void tenantAuthOverhead(String runId) throws Exception {
        String token = tokenPort.getToken("AUTH-OVERHEAD-TENANT");
        GatewayResponse resp = gateway.sendRequest(token, "/api/orders", "GET");
        assertStep(runId, "auth_overhead", "gateway overhead (incl auth) < " + GATEWAY_OVERHEAD_LIMIT_MS + "ms",
            "< " + GATEWAY_OVERHEAD_LIMIT_MS, resp.gatewayOverheadMs() < GATEWAY_OVERHEAD_LIMIT_MS,
            resp.gatewayOverheadMs() + "ms");
    }

    /** 70% read / 30% write mix all succeed under concurrent load. */
    private void readWriteMix(String runId) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(50);
        AtomicInteger readOk = new AtomicInteger(), writeOk = new AtomicInteger(), errs = new AtomicInteger();
        String token = tokenPort.getToken("MIX-TENANT");
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            boolean isRead = i % 10 < 7; // 70/30 split
            futures.add(pool.submit(() -> {
                try {
                    GatewayResponse r = gateway.sendRequest(token, "/api/orders", isRead ? "GET" : "POST");
                    if (r.statusCode() >= 200 && r.statusCode() < 300) {
                        if (isRead) readOk.incrementAndGet(); else writeOk.incrementAndGet();
                    } else { errs.incrementAndGet(); }
                } catch (Exception ex) { errs.incrementAndGet(); }
            }));
        }
        for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();
        assertStep(runId, "read_write_mix_ok", "70/30 read/write mix succeeds", "errs=0",
            errs.get() == 0, "reads=" + readOk + " writes=" + writeOk + " errs=" + errs);
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
                 "INSERT INTO e2e_test_runs (suite_name,scenario) VALUES ('ApiGatewayLoadLatency',?) RETURNING run_id"
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
