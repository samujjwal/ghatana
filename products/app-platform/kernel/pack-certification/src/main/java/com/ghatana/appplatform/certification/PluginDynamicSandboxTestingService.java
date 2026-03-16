package com.ghatana.appplatform.certification;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Dynamic security testing: run a plugin in an instrumented K-04 sandbox and
 *              attempt to trigger policy violations. Test scenarios: network access, filesystem
 *              access, CPU exhaustion (DoS), memory exhaustion, infinite loop.
 *              All violations recorded with evidence. Plugin must complete without crashing
 *              the sandbox or causing unrecovered resource exhaustion.
 *              Test report stored; CRITICAL outcomes block certification.
 * @doc.layer   Pack Certification (P-01)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer
 *
 * STORY-P01-005: Dynamic sandbox security testing
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS plugin_dynamic_test_reports (
 *   report_id      TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   plugin_id      TEXT NOT NULL,
 *   version        TEXT NOT NULL,
 *   sandbox_id     TEXT NOT NULL,
 *   overall_outcome TEXT NOT NULL,  -- PASS | FAIL
 *   scenarios      JSONB NOT NULL DEFAULT '[]',
 *   test_duration_ms BIGINT,
 *   tested_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * </pre>
 */
public class PluginDynamicSandboxTestingService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface SandboxRunnerPort {
        /** Launch plugin in isolated K-04 sandbox. Returns sandboxId. */
        String launchSandbox(String pluginId, String version) throws Exception;
        /** Inject a test scenario into a running sandbox. Returns scenario result. */
        ScenarioResult runScenario(String sandboxId, String scenario, Map<String, Object> params) throws Exception;
        /** Terminate sandbox and free resources. */
        void terminateSandbox(String sandboxId) throws Exception;
        /** Check if sandbox is still alive. */
        boolean isSandboxHealthy(String sandboxId) throws Exception;
    }

    public interface AuditPort {
        void record(String actorId, String action, String detail) throws Exception;
    }

    // ── Value types ───────────────────────────────────────────────────────────

    public record ScenarioResult(String scenario, String outcome, String evidence, long durationMs) {}

    public record DynamicTestReport(
        String reportId, String pluginId, String version,
        String overallOutcome, List<ScenarioResult> scenarios
    ) {}

    // ── Scenarios ─────────────────────────────────────────────────────────────

    private static final List<String> SCENARIOS = List.of(
        "NETWORK_ACCESS_ATTEMPT",
        "FILESYSTEM_ACCESS_ATTEMPT",
        "CPU_EXHAUSTION_ATTEMPT",
        "MEMORY_EXHAUSTION_ATTEMPT",
        "INFINITE_LOOP_ATTEMPT"
    );

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final SandboxRunnerPort sandbox;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter testsPassedCounter;
    private final Counter testsFailedCounter;

    public PluginDynamicSandboxTestingService(
        javax.sql.DataSource ds,
        SandboxRunnerPort sandbox,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds                 = ds;
        this.sandbox            = sandbox;
        this.audit              = audit;
        this.executor           = executor;
        this.testsPassedCounter = Counter.builder("certification.dynamic_test.passed").register(registry);
        this.testsFailedCounter = Counter.builder("certification.dynamic_test.failed").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Run the full dynamic security test suite for a plugin.
     * Launches sandbox, runs all scenarios, terminates sandbox, persists report.
     */
    public Promise<DynamicTestReport> runTestSuite(String pluginId, String version) {
        return Promise.ofBlocking(executor, () -> {
            long suiteStart = System.currentTimeMillis();
            String sandboxId = sandbox.launchSandbox(pluginId, version);
            List<ScenarioResult> results = new ArrayList<>();
            boolean overallPass = true;

            try {
                for (String scenario : SCENARIOS) {
                    ScenarioResult result = sandbox.runScenario(sandboxId, scenario, Map.of());
                    results.add(result);
                    if ("FAIL".equals(result.outcome())) overallPass = false;

                    // Verify sandbox health after each scenario
                    if (!sandbox.isSandboxHealthy(sandboxId)) {
                        results.add(new ScenarioResult("SANDBOX_HEALTH_CHECK", "FAIL",
                            "Sandbox crashed after scenario " + scenario, 0L));
                        overallPass = false;
                        break;
                    }
                }
            } finally {
                sandbox.terminateSandbox(sandboxId);
            }

            String outcome = overallPass ? "PASS" : "FAIL";
            long totalMs = System.currentTimeMillis() - suiteStart;
            String reportId = persistReport(pluginId, version, sandboxId, outcome, results, totalMs);

            if (overallPass) testsPassedCounter.increment(); else testsFailedCounter.increment();
            audit.record("system", "DYNAMIC_TEST_COMPLETED",
                "pluginId=" + pluginId + " version=" + version + " outcome=" + outcome);
            return new DynamicTestReport(reportId, pluginId, version, outcome, results);
        });
    }

    /** Check if latest dynamic test for a plugin passed. Used as certification gate. */
    public Promise<Boolean> hasPassedDynamicTest(String pluginId, String version) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT overall_outcome FROM plugin_dynamic_test_reports " +
                     "WHERE plugin_id=? AND version=? ORDER BY tested_at DESC LIMIT 1"
                 )) {
                ps.setString(1, pluginId); ps.setString(2, version);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && "PASS".equals(rs.getString("overall_outcome"));
                }
            }
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String persistReport(String pluginId, String version, String sandboxId,
                                  String outcome, List<ScenarioResult> results, long totalMs) throws SQLException {
        String scenariosJson = scenariosToJson(results);
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO plugin_dynamic_test_reports (plugin_id, version, sandbox_id, overall_outcome, scenarios, test_duration_ms) " +
                 "VALUES (?,?,?,?,?::jsonb,?) RETURNING report_id"
             )) {
            ps.setString(1, pluginId); ps.setString(2, version); ps.setString(3, sandboxId);
            ps.setString(4, outcome); ps.setString(5, scenariosJson); ps.setLong(6, totalMs);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString("report_id"); }
        }
    }

    private String scenariosToJson(List<ScenarioResult> results) {
        if (results.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (ScenarioResult r : results) {
            sb.append("{\"scenario\":\"").append(r.scenario())
              .append("\",\"outcome\":\"").append(r.outcome())
              .append("\",\"evidence\":\"").append(r.evidence().replace("\"", "'"))
              .append("\",\"durationMs\":").append(r.durationMs()).append("},");
        }
        sb.setCharAt(sb.length() - 1, ']');
        return sb.toString();
    }
}
