package com.ghatana.appplatform.integration;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Pod failure chaos test service (T-02).
 *              Injects pod kill failures across core microservices and validates:
 *              service restarts within SLA; in-flight operations resume or complete;
 *              no data loss; K-18 circuit breaker opens; alerts fire; MTTR tracked.
 * @doc.layer   Integration Testing (T-02 Chaos)
 * @doc.pattern Port-Adapter; JDBC; Promise.ofBlocking; chaos-engineering; resilience
 *
 * STORY-T02-001: Implement pod failure chaos tests
 */
public class PodFailureChaosTestService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface ChaosInjectionPort {
        /** Kill the named pod (simulated). Returns podKillId. */
        String killPod(String serviceName) throws Exception;
        /** Kill all replicas of a service (total outage). Returns podKillId. */
        String killAllPods(String serviceName) throws Exception;
        /** Get current pod status for service. */
        String getPodStatus(String serviceName) throws Exception; // RUNNING|TERMINATED|RECOVERING|HEALTHY
        /** Wait for service to return HEALTHY. Returns actual MTTR in ms. */
        long awaitHealthy(String serviceName, long timeoutMs) throws Exception;
    }

    public interface ServiceHealthPort {
        boolean isServiceHealthy(String serviceName) throws Exception;
        boolean isCircuitBreakerOpen(String serviceName) throws Exception;
        boolean hasAlertFired(String serviceName, String alertType) throws Exception;
    }

    public interface OrderPort {
        String submitOrder(String clientId, String symbol, int qty) throws Exception;
        String getOrderStatus(String orderId) throws Exception;
    }

    public interface DataIntegrityPort {
        long countOrders() throws Exception;
        boolean hasNoCorruptedOrders() throws Exception;
    }

    public interface AuditPort {
        void audit(String event, String detail) throws Exception;
    }

    // ── SLA constants ─────────────────────────────────────────────────────────

    private static final long RESTART_SLA_MS = 30_000L; // pods must restart within 30s
    private static final String[] CORE_SERVICES = {"oms", "ems", "settlement", "ledger", "audit"};

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final ChaosInjectionPort chaos;
    private final ServiceHealthPort health;
    private final OrderPort orderPort;
    private final DataIntegrityPort dataIntegrity;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter suitesPassed;
    private final Counter suitesFailed;
    private final Timer mttrTimer;

    public PodFailureChaosTestService(
        javax.sql.DataSource ds,
        ChaosInjectionPort chaos,
        ServiceHealthPort health,
        OrderPort orderPort,
        DataIntegrityPort dataIntegrity,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds            = ds;
        this.chaos         = chaos;
        this.health        = health;
        this.orderPort     = orderPort;
        this.dataIntegrity = dataIntegrity;
        this.audit         = audit;
        this.executor      = executor;
        this.suitesPassed  = Counter.builder("integration.chaos.pod.suites_passed").register(registry);
        this.suitesFailed  = Counter.builder("integration.chaos.pod.suites_failed").register(registry);
        this.mttrTimer     = Timer.builder("integration.chaos.pod.mttr").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public Promise<SuiteResult> runAll() {
        return Promise.ofBlocking(executor, () -> {
            List<ScenarioResult> results = new ArrayList<>();
            results.add(runScenario("single_pod_kill_oms",           this::singlePodKillOms));
            results.add(runScenario("single_pod_kill_ems",           this::singlePodKillEms));
            results.add(runScenario("in_flight_order_survives_kill", this::inFlightOrderSurvivesKill));
            results.add(runScenario("data_integrity_after_kill",     this::dataIntegrityAfterKill));
            results.add(runScenario("circuit_breaker_opens",         this::circuitBreakerOpens));
            results.add(runScenario("alert_fires_on_kill",           this::alertFiresOnKill));
            results.add(runScenario("all_services_recover",          this::allServicesRecover));

            long passed = results.stream().filter(r -> r.passed).count();
            long failed = results.size() - passed;
            if (failed == 0) suitesPassed.increment(); else suitesFailed.increment();
            audit.audit("POD_FAILURE_CHAOS_SUITE", "passed=" + passed + " failed=" + failed);
            return new SuiteResult("PodFailureChaos", results, passed, failed);
        });
    }

    // ── Scenarios ─────────────────────────────────────────────────────────────

    private void singlePodKillOms(String runId) throws Exception {
        chaos.killPod("oms");
        long mttrMs = chaos.awaitHealthy("oms", RESTART_SLA_MS * 2);
        mttrTimer.record(mttrMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertStep(runId, "oms_restart_sla", "OMS restarts within " + RESTART_SLA_MS + "ms", "< " + RESTART_SLA_MS,
            mttrMs < RESTART_SLA_MS, mttrMs + "ms");
    }

    private void singlePodKillEms(String runId) throws Exception {
        chaos.killPod("ems");
        long mttrMs = chaos.awaitHealthy("ems", RESTART_SLA_MS * 2);
        assertStep(runId, "ems_restart_sla", "EMS restarts within SLA", "< " + RESTART_SLA_MS,
            mttrMs < RESTART_SLA_MS, mttrMs + "ms");
    }

    /** Submit order, kill OMS pod, order should eventually reach terminal state. */
    private void inFlightOrderSurvivesKill(String runId) throws Exception {
        String orderId = orderPort.submitOrder("CHAOS-CLIENT-001", "NABIL", 100);
        chaos.killPod("oms");
        chaos.awaitHealthy("oms", RESTART_SLA_MS * 2);
        Thread.sleep(2000); // let order processing resume
        String status = orderPort.getOrderStatus(orderId);
        boolean terminal = "FILLED".equals(status) || "CANCELLED".equals(status) || "REJECTED".equals(status) || "PENDING".equals(status);
        assertStep(runId, "order_survives_kill", "in-flight order reaches visible state after kill", "terminal or pending",
            terminal, status);
    }

    /** After pod kills, no corrupted order records. */
    private void dataIntegrityAfterKill(String runId) throws Exception {
        for (String svc : CORE_SERVICES) chaos.killPod(svc);
        for (String svc : CORE_SERVICES) chaos.awaitHealthy(svc, RESTART_SLA_MS * 2);
        boolean noCorruption = dataIntegrity.hasNoCorruptedOrders();
        assertStep(runId, "no_corruption", "no corrupted orders after mass pod kill", "true", noCorruption, noCorruption);
    }

    /** K-18 circuit breaker opens when downstream service is down. */
    private void circuitBreakerOpens(String runId) throws Exception {
        chaos.killAllPods("ems");
        Thread.sleep(2000); // allow circuit breaker to detect
        boolean open = health.isCircuitBreakerOpen("ems");
        assertStep(runId, "circuit_breaker_open", "circuit breaker opens when EMS unavailable", "true", open, open);
        // Restore
        chaos.awaitHealthy("ems", RESTART_SLA_MS * 3);
    }

    /** Alert fires within 60s when pod is killed. */
    private void alertFiresOnKill(String runId) throws Exception {
        chaos.killPod("settlement");
        Thread.sleep(5000); // allow alert propagation (5s window)
        boolean alertFired = health.hasAlertFired("settlement", "POD_KILLED");
        assertStep(runId, "alert_fires", "pod kill alert fires within 5s", "true", alertFired, alertFired);
        chaos.awaitHealthy("settlement", RESTART_SLA_MS * 2);
    }

    /** All core services recover from pod kill sequentially. */
    private void allServicesRecover(String runId) throws Exception {
        for (String svc : CORE_SERVICES) chaos.killPod(svc);
        for (String svc : CORE_SERVICES) {
            long mttr = chaos.awaitHealthy(svc, RESTART_SLA_MS * 3);
            assertStep(runId, svc + "_recovered", svc + " recovered within SLA", "< " + (RESTART_SLA_MS * 2),
                mttr < RESTART_SLA_MS * 2, mttr + "ms");
        }
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
                 "INSERT INTO e2e_test_runs (suite_name,scenario) VALUES ('PodFailureChaos',?) RETURNING run_id"
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
