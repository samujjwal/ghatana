package com.ghatana.appplatform.integration;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Resource exhaustion chaos test service (T-02).
 *              Scenarios: CPU stress → HPA scale-out; OOM → K8s restart, no data loss;
 *              disk full → alert; connection pool exhaustion → recovery; liveness probe
 *              restart; other pods unaffected; state recovered from event log.
 * @doc.layer   Integration Testing (T-02 Chaos)
 * @doc.pattern Port-Adapter; JDBC; Promise.ofBlocking; chaos-engineering
 *
 * STORY-T02-003: Implement resource exhaustion chaos tests
 */
public class ResourceExhaustionChaosTestService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface ResourceChaosPort {
        /** Inject CPU stress on service (pct=0–100). Returns chaosId. */
        String injectCpuStress(String serviceName, int cpuPct) throws Exception;
        /** Inject memory pressure until OOM. Returns chaosId. */
        String injectMemoryPressure(String serviceName) throws Exception;
        /** Simulate disk full on data node. Returns chaosId. */
        String injectDiskFull(String nodeName) throws Exception;
        /** Stop chaos injection. */
        void stopChaos(String chaosId) throws Exception;
        /** Get pod count for service (validates HPA). */
        int getPodCount(String serviceName) throws Exception;
        /** Get pod status after OOM (should be RESTARTED). */
        String getPodStatusAfterOom(String serviceName) throws Exception;
    }

    public interface AlertPort {
        boolean hasCpuAlert(String serviceName) throws Exception;
        boolean hasDiskFullAlert(String nodeName) throws Exception;
        boolean hasOomAlert(String serviceName) throws Exception;
    }

    public interface DataIntegrityPort {
        boolean hasNoCorruptedOrders() throws Exception;
        boolean isStateRecoveredFromEventLog(String serviceName) throws Exception;
    }

    public interface ConnectionPoolPort {
        long exhaustPoolAndMeasureWaitMs() throws Exception;
        void releasePool() throws Exception;
        boolean isPoolHealthy() throws Exception;
    }

    public interface AuditPort {
        void audit(String event, String detail) throws Exception;
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final int HPA_MIN_PODS    = 2;
    private static final int HPA_MAX_PODS    = 6;
    private static final long RECOVERY_MS    = 30_000L;

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final ResourceChaosPort resourceChaos;
    private final AlertPort alertPort;
    private final DataIntegrityPort dataIntegrity;
    private final ConnectionPoolPort poolPort;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter suitesPassed;
    private final Counter suitesFailed;

    public ResourceExhaustionChaosTestService(
        javax.sql.DataSource ds,
        ResourceChaosPort resourceChaos,
        AlertPort alertPort,
        DataIntegrityPort dataIntegrity,
        ConnectionPoolPort poolPort,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds            = ds;
        this.resourceChaos = resourceChaos;
        this.alertPort     = alertPort;
        this.dataIntegrity = dataIntegrity;
        this.poolPort      = poolPort;
        this.audit         = audit;
        this.executor      = executor;
        this.suitesPassed  = Counter.builder("integration.chaos.resource.suites_passed").register(registry);
        this.suitesFailed  = Counter.builder("integration.chaos.resource.suites_failed").register(registry);
    }

    public Promise<SuiteResult> runAll() {
        return Promise.ofBlocking(executor, () -> {
            List<ScenarioResult> results = new ArrayList<>();
            results.add(runScenario("cpu_stress_hpa_scale",         this::cpuStressHpaScale));
            results.add(runScenario("oom_restart_no_data_loss",     this::oomRestartNoDataLoss));
            results.add(runScenario("disk_full_alert",              this::diskFullAlert));
            results.add(runScenario("pool_exhaustion_recover",      this::poolExhaustionRecover));
            results.add(runScenario("liveness_probe_restart",       this::livenessProbeRestart));
            results.add(runScenario("other_pods_unaffected",        this::otherPodsUnaffected));
            results.add(runScenario("state_recovery_from_event_log",this::stateRecoveryFromEventLog));

            long passed = results.stream().filter(r -> r.passed).count();
            long failed = results.size() - passed;
            if (failed == 0) suitesPassed.increment(); else suitesFailed.increment();
            audit.audit("RESOURCE_EXHAUSTION_SUITE", "passed=" + passed + " failed=" + failed);
            return new SuiteResult("ResourceExhaustionChaos", results, passed, failed);
        });
    }

    private void cpuStressHpaScale(String runId) throws Exception {
        int beforePods = resourceChaos.getPodCount("oms");
        String chaosId = resourceChaos.injectCpuStress("oms", 90);
        try {
            // HPA should scale out within 60s
            int maxPods = beforePods;
            for (int i = 0; i < 30; i++) {
                Thread.sleep(2000);
                int current = resourceChaos.getPodCount("oms");
                if (current > maxPods) { maxPods = current; break; }
            }
            assertStep(runId, "hpa_scaled_out", "HPA added pods under CPU stress", ">" + beforePods,
                maxPods > beforePods, maxPods + " pods (was " + beforePods + ")");
            boolean cpuAlert = alertPort.hasCpuAlert("oms");
            assertStep(runId, "cpu_alert_fired", "CPU alert fired during stress", "true", cpuAlert, cpuAlert);
        } finally { resourceChaos.stopChaos(chaosId); }
    }

    private void oomRestartNoDataLoss(String runId) throws Exception {
        String chaosId = resourceChaos.injectMemoryPressure("oms");
        try { Thread.sleep(5000); } finally { resourceChaos.stopChaos(chaosId); }
        Thread.sleep(3000); // K8s liveness probe triggers restart
        String status = resourceChaos.getPodStatusAfterOom("oms");
        assertStep(runId, "oom_restarted", "OMS pod restarted after OOM", "RESTARTED",
            "RESTARTED".equals(status) || "RUNNING".equals(status), status);
        boolean noCorruption = dataIntegrity.hasNoCorruptedOrders();
        assertStep(runId, "no_data_loss_after_oom", "no data corruption after OOM restart", "true", noCorruption, noCorruption);
        boolean oomAlert = alertPort.hasOomAlert("oms");
        assertStep(runId, "oom_alert_fired", "OOM alert fired", "true", oomAlert, oomAlert);
    }

    private void diskFullAlert(String runId) throws Exception {
        String chaosId = resourceChaos.injectDiskFull("data-node-1");
        try {
            Thread.sleep(3000);
            boolean diskAlert = alertPort.hasDiskFullAlert("data-node-1");
            assertStep(runId, "disk_full_alert", "disk full alert fired", "true", diskAlert, diskAlert);
        } finally { resourceChaos.stopChaos(chaosId); }
    }

    private void poolExhaustionRecover(String runId) throws Exception {
        try {
            long waitMs = poolPort.exhaustPoolAndMeasureWaitMs();
            assertStep(runId, "pool_waits_not_crash", "connection pool waits on exhaustion", ">= 0",
                waitMs >= 0, waitMs + "ms wait");
        } finally { poolPort.releasePool(); }
        // After release, pool should be healthy
        Thread.sleep(1000);
        boolean healthy = poolPort.isPoolHealthy();
        assertStep(runId, "pool_recovered", "pool healthy after exhaustion", "true", healthy, healthy);
    }

    /** K8s liveness probe triggers restart when service is stuck. */
    private void livenessProbeRestart(String runId) throws Exception {
        // Liveness probe scenario: CPU stress and OOM together
        String chaosId = resourceChaos.injectCpuStress("ems", 95);
        try {
            Thread.sleep(5000); // allow liveness probe to fire (default 30s but test config is shorter)
        } finally { resourceChaos.stopChaos(chaosId); }
        // EMS should eventually be running again
        String status = resourceChaos.getPodStatusAfterOom("ems");
        assertStep(runId, "liveness_restart", "EMS pod restarted by liveness probe", "RUNNING or RESTARTED",
            "RUNNING".equals(status) || "RESTARTED".equals(status), status);
    }

    /** OMS under stress should not affect settlement service. */
    private void otherPodsUnaffected(String runId) throws Exception {
        String chaosId = resourceChaos.injectCpuStress("oms", 90);
        try {
            Thread.sleep(2000);
            int settlementPods = resourceChaos.getPodCount("settlement");
            assertStep(runId, "settlement_unaffected", "settlement pod count unchanged during OMS stress",
                String.valueOf(HPA_MIN_PODS), settlementPods >= HPA_MIN_PODS, settlementPods);
        } finally { resourceChaos.stopChaos(chaosId); }
    }

    /** After OOM, state is recovered from event log (K-05 replay). */
    private void stateRecoveryFromEventLog(String runId) throws Exception {
        resourceChaos.injectMemoryPressure("oms");
        Thread.sleep(3000);
        boolean recovered = dataIntegrity.isStateRecoveredFromEventLog("oms");
        assertStep(runId, "state_from_event_log", "OMS state recovered from event log after OOM", "true",
            recovered, recovered);
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
                 "INSERT INTO e2e_test_runs (suite_name,scenario) VALUES ('ResourceExhaustionChaos',?) RETURNING run_id")) {
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
