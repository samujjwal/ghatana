package com.ghatana.appplatform.integration;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose End-to-end plugin lifecycle test suite (T1/T2/T3 plugins).
 *              Verifies: plugin installed → activated → executed in core workflow →
 *              metrics collected → uninstalled cleanly. Edge cases: plugin crash
 *              isolation (core unaffected), audit trail, quota metering, timeout.
 * @doc.layer   Integration Testing (T-01)
 * @doc.pattern Port-Adapter; JDBC; Promise.ofBlocking; scenario-execution; assertion
 *
 * STORY-T01-003: Implement e2e plugin execution test suite
 */
public class PluginExecutionE2eTestSuiteService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface PluginRegistryPort {
        String installPlugin(String pluginId, String tier, String rulePayload) throws Exception;
        void activatePlugin(String pluginId) throws Exception;
        void deactivatePlugin(String pluginId) throws Exception;
        void uninstallPlugin(String pluginId) throws Exception;
        String getPluginStatus(String pluginId) throws Exception; // ACTIVE|INACTIVE|INSTALLED|UNINSTALLED
    }

    /** T1 – pre-order risk rule. */
    public interface OrderRuleEnginePort {
        /** Submit order through plugin-enriched pipeline. Returns orderId or throws if blocked. */
        String submitOrderWithPlugins(String clientId, String symbol, int qty, double price) throws Exception;
        String getOrderStatus(String orderId) throws Exception;
    }

    /** T2 – post-trade report generator. */
    public interface PluginReportPort {
        boolean hasReportGeneratedForTrade(String tradeId, String pluginId) throws Exception;
        String getLastReportId(String tradeId, String pluginId) throws Exception;
    }

    /** T3 – algo trading plugin (submits child orders). */
    public interface AlgoPluginPort {
        String triggerAlgo(String pluginId, String parentOrderId) throws Exception;
        List<String> getChildOrders(String algoRunId) throws Exception;
        String getAlgoStatus(String algoRunId) throws Exception;
    }

    public interface PluginMetricsPort {
        long getPluginExecutionCount(String pluginId) throws Exception;
        long getPluginQuotaConsumed(String pluginId) throws Exception;
        boolean isQuotaExceeded(String pluginId) throws Exception;
    }

    public interface AuditPort {
        boolean hasAuditEvent(String eventType, String entityId) throws Exception;
        void audit(String event, String detail) throws Exception;
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final PluginRegistryPort registry;
    private final OrderRuleEnginePort orderEngine;
    private final PluginReportPort reportPort;
    private final AlgoPluginPort algoPort;
    private final PluginMetricsPort metricsPort;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter suitesPassed;
    private final Counter suitesFailed;

    public PluginExecutionE2eTestSuiteService(
        javax.sql.DataSource ds,
        PluginRegistryPort registry,
        OrderRuleEnginePort orderEngine,
        PluginReportPort reportPort,
        AlgoPluginPort algoPort,
        PluginMetricsPort metricsPort,
        AuditPort audit,
        MeterRegistry registry2,
        Executor executor
    ) {
        this.ds          = ds;
        this.registry    = registry;
        this.orderEngine = orderEngine;
        this.reportPort  = reportPort;
        this.algoPort    = algoPort;
        this.metricsPort = metricsPort;
        this.audit       = audit;
        this.executor    = executor;
        this.suitesPassed = Counter.builder("integration.e2e.plugin.suites_passed").register(registry2);
        this.suitesFailed = Counter.builder("integration.e2e.plugin.suites_failed").register(registry2);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public Promise<SuiteResult> runAll() {
        return Promise.ofBlocking(executor, () -> {
            List<ScenarioResult> results = new ArrayList<>();
            results.add(runScenario("t1_plugin_rule",       this::t1PluginRuleScenario));
            results.add(runScenario("t2_plugin_report",     this::t2PluginReportScenario));
            results.add(runScenario("t3_plugin_algo",       this::t3PluginAlgoScenario));
            results.add(runScenario("t3_crash_isolation",   this::t3CrashIsolationScenario));
            results.add(runScenario("audit_on_execute",     this::auditOnExecuteScenario));
            results.add(runScenario("quota_metering",       this::quotaMeteringScenario));
            results.add(runScenario("uninstall_cleanup",    this::uninstallCleanupScenario));
            results.add(runScenario("plugin_timeout",       this::pluginTimeoutScenario));

            long passed = results.stream().filter(r -> r.passed).count();
            long failed = results.size() - passed;
            if (failed == 0) suitesPassed.increment(); else suitesFailed.increment();
            audit.audit("PLUGIN_E2E_SUITE", "passed=" + passed + " failed=" + failed);
            return new SuiteResult("PluginExecutionE2e", results, passed, failed);
        });
    }

    // ── Scenarios ─────────────────────────────────────────────────────────────

    /** T1: custom risk rule plugin blocks high-qty orders. */
    private void t1PluginRuleScenario(String runId) throws Exception {
        String pluginId = "T1-RISK-RULE-001";
        // install a rule blocking qty > 50000
        registry.installPlugin(pluginId, "T1", "{\"maxQty\":50000}");
        registry.activatePlugin(pluginId);
        assertStep(runId, "t1_active", "T1 plugin ACTIVE after install", "ACTIVE",
            "ACTIVE".equals(registry.getPluginStatus(pluginId)), registry.getPluginStatus(pluginId));
        try {
            // Normal order: qty 1000 should pass
            String orderId = orderEngine.submitOrderWithPlugins("CLIENT-001", "NABIL", 1000, 1500.0);
            assertStep(runId, "t1_allow_normal", "T1 allows normal order", "not null", orderId != null, orderId);
            // Big order: qty 100000 should be blocked
            boolean blocked = false;
            try { orderEngine.submitOrderWithPlugins("CLIENT-001", "NABIL", 100000, 1500.0); }
            catch (Exception ex) { blocked = true; }
            assertStep(runId, "t1_block_large", "T1 blocks order exceeding maxQty", "true", blocked, blocked);
        } finally {
            registry.deactivatePlugin(pluginId);
            registry.uninstallPlugin(pluginId);
        }
    }

    /** T2: custom report generator plugin runs post-trade. */
    private void t2PluginReportScenario(String runId) throws Exception {
        String pluginId = "T2-REPORT-GEN-001";
        registry.installPlugin(pluginId, "T2", "{\"reportFormat\":\"PDF\"}");
        registry.activatePlugin(pluginId);
        try {
            String orderId = orderEngine.submitOrderWithPlugins("CLIENT-002", "NTC", 500, 950.0);
            Thread.sleep(300); // let post-trade hook fire
            boolean reported = reportPort.hasReportGeneratedForTrade(orderId, pluginId);
            assertStep(runId, "t2_report_generated", "T2 generates post-trade report", "true", reported, reported);
            String reportId = reportPort.getLastReportId(orderId, pluginId);
            assertStep(runId, "t2_report_id", "T2 report has valid ID", "not null", reportId != null && !reportId.isEmpty(), reportId);
        } finally {
            registry.deactivatePlugin(pluginId);
            registry.uninstallPlugin(pluginId);
        }
    }

    /** T3: algo plugin submits child orders autonomously. */
    private void t3PluginAlgoScenario(String runId) throws Exception {
        String pluginId = "T3-ALGO-001";
        registry.installPlugin(pluginId, "T3", "{\"sliceCount\":3}");
        registry.activatePlugin(pluginId);
        try {
            String parentOrderId = orderEngine.submitOrderWithPlugins("CLIENT-003", "NLIC", 3000, 1000.0);
            String algoRunId = algoPort.triggerAlgo(pluginId, parentOrderId);
            Thread.sleep(500);
            String algoStatus = algoPort.getAlgoStatus(algoRunId);
            assertStep(runId, "t3_algo_complete", "T3 algo run completes", "COMPLETE", "COMPLETE".equals(algoStatus), algoStatus);
            List<String> childOrders = algoPort.getChildOrders(algoRunId);
            assertStep(runId, "t3_child_orders", "T3 produces 3 child orders", "3", childOrders.size(), childOrders.size());
        } finally {
            registry.deactivatePlugin(pluginId);
            registry.uninstallPlugin(pluginId);
        }
    }

    /** T3 crash isolation: crashing plugin must not affect core. */
    private void t3CrashIsolationScenario(String runId) throws Exception {
        String pluginId = "T3-CRASH-SIM-001";
        registry.installPlugin(pluginId, "T3", "{\"crashOnTrigger\":true}");
        registry.activatePlugin(pluginId);
        try {
            // Core order submission should succeed even if algo plugin crashes
            String orderId = orderEngine.submitOrderWithPlugins("CLIENT-004", "NIMB", 100, 800.0);
            assertStep(runId, "t3_crash_core_unaffected", "core order succeeds despite T3 crash", "not null", orderId != null, orderId);
            // Plugin should be in error/isolated state, not active
            String status = registry.getPluginStatus(pluginId);
            boolean isolated = !"ACTIVE".equals(status); // CRASHED or SUSPENDED
            assertStep(runId, "t3_plugin_isolated", "crashed T3 plugin is isolated", "not ACTIVE", isolated, status);
        } finally {
            try { registry.deactivatePlugin(pluginId); } catch (Exception ignored) {}
            try { registry.uninstallPlugin(pluginId);  } catch (Exception ignored) {}
        }
    }

    /** Audit events emitted on plugin execute. */
    private void auditOnExecuteScenario(String runId) throws Exception {
        String pluginId = "T1-AUDIT-TEST-001";
        registry.installPlugin(pluginId, "T1", "{\"maxQty\":999999}");
        registry.activatePlugin(pluginId);
        try {
            String orderId = orderEngine.submitOrderWithPlugins("CLIENT-005", "NABIL", 100, 1500.0);
            Thread.sleep(200);
            boolean hasAudit = audit.hasAuditEvent("PLUGIN_EXECUTED", pluginId);
            assertStep(runId, "audit_on_execute", "K-07 audit event emitted for plugin execution", "true", hasAudit, hasAudit);
        } finally {
            registry.deactivatePlugin(pluginId);
            registry.uninstallPlugin(pluginId);
        }
    }

    /** Quota consumed increments per execution. */
    private void quotaMeteringScenario(String runId) throws Exception {
        String pluginId = "T1-QUOTA-TEST-001";
        registry.installPlugin(pluginId, "T1", "{\"maxQty\":999999}");
        registry.activatePlugin(pluginId);
        try {
            int executions = 5;
            for (int i = 0; i < executions; i++) {
                orderEngine.submitOrderWithPlugins("CLIENT-006", "NTC", 100, 950.0);
            }
            long quota = metricsPort.getPluginQuotaConsumed(pluginId);
            assertStep(runId, "quota_consumed", "quota consumed = executions", String.valueOf(executions),
                quota >= executions, String.valueOf(quota));
        } finally {
            registry.deactivatePlugin(pluginId);
            registry.uninstallPlugin(pluginId);
        }
    }

    /** Uninstall removes plugin, no references remain. */
    private void uninstallCleanupScenario(String runId) throws Exception {
        String pluginId = "T2-CLEANUP-001";
        registry.installPlugin(pluginId, "T2", "{\"reportFormat\":\"CSV\"}");
        registry.activatePlugin(pluginId);
        registry.deactivatePlugin(pluginId);
        registry.uninstallPlugin(pluginId);
        assertStep(runId, "uninstall_status", "plugin UNINSTALLED after removal", "UNINSTALLED",
            "UNINSTALLED".equals(registry.getPluginStatus(pluginId)), registry.getPluginStatus(pluginId));
    }

    /** Plugin that exceeds 2s execution budget should time out gracefully. */
    private void pluginTimeoutScenario(String runId) throws Exception {
        String pluginId = "T1-SLOW-001";
        registry.installPlugin(pluginId, "T1", "{\"sleepMs\":3000}"); // exceeds 2s budget
        registry.activatePlugin(pluginId);
        long start = System.currentTimeMillis();
        try {
            // Core order should still complete within reasonable time via timeout gate
            String orderId = orderEngine.submitOrderWithPlugins("CLIENT-007", "NABIL", 100, 1500.0);
            long duration = System.currentTimeMillis() - start;
            assertStep(runId, "timeout_gate", "order completes within 5s despite slow plugin", "< 5000ms",
                duration < 5000, duration + "ms");
        } finally {
            try { registry.deactivatePlugin(pluginId); } catch (Exception ignored) {}
            try { registry.uninstallPlugin(pluginId);  } catch (Exception ignored) {}
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
                 "INSERT INTO e2e_test_runs (suite_name,scenario) VALUES ('PluginExecutionE2e',?) RETURNING run_id"
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
