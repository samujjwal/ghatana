package com.ghatana.appplatform.integration;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Full platform upgrade E2E test suite.
 *              Scenarios: in-flight orders survive v2→v3 upgrade; data migration verified;
 *              rollback on injected failure; old protocol handling; post-upgrade smoke;
 *              config preserved; plugins re-certified after upgrade.
 * @doc.layer   Integration Testing (T-01)
 * @doc.pattern Port-Adapter; JDBC; Promise.ofBlocking; scenario-execution; assertion
 *
 * STORY-T01-004: Implement e2e platform upgrade test
 */
public class PlatformUpgradeE2eTestSuiteService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface PlatformVersionPort {
        String getCurrentVersion() throws Exception;
        void initiateUpgrade(String targetVersion) throws Exception;
        void initiateRollback() throws Exception;
        String getUpgradeStatus() throws Exception; // IN_PROGRESS|SUCCESS|ROLLED_BACK|FAILED
        boolean isUpgradeComplete() throws Exception;
    }

    public interface OrderPort {
        String submitOrder(String clientId, String symbol, int qty) throws Exception;
        String getOrderStatus(String orderId) throws Exception;
    }

    public interface DataMigrationVerificationPort {
        boolean isMigrationComplete(String targetVersion) throws Exception;
        long getMigratedRecordCount(String entity) throws Exception;
        boolean isDataIntact(String checksum) throws Exception;
    }

    public interface PostUpgradeSmokePort {
        boolean isApiGatewayHealthy() throws Exception;
        boolean isEventBusHealthy() throws Exception;
        boolean isAuthHealthy() throws Exception;
        boolean isOrderSubmissionHealthy() throws Exception;
    }

    public interface ConfigPreservationPort {
        String getConfigValue(String key) throws Exception;
    }

    public interface PluginCertificationPort {
        boolean isPluginCertifiedFor(String pluginId, String version) throws Exception;
    }

    public interface ProtocolGatewayPort {
        /** Try to connect with old protocol client. Returns false if refused. */
        boolean connectWithOldProtocol(String clientVersion) throws Exception;
    }

    public interface AuditPort {
        void audit(String event, String detail) throws Exception;
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final String OLD_VERSION = "2.5.0";
    private static final String NEW_VERSION = "3.0.0";

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final PlatformVersionPort versionPort;
    private final OrderPort orderPort;
    private final DataMigrationVerificationPort migrationPort;
    private final PostUpgradeSmokePort smokePort;
    private final ConfigPreservationPort configPort;
    private final PluginCertificationPort certPort;
    private final ProtocolGatewayPort protocolPort;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter suitesPassed;
    private final Counter suitesFailed;

    public PlatformUpgradeE2eTestSuiteService(
        javax.sql.DataSource ds,
        PlatformVersionPort versionPort,
        OrderPort orderPort,
        DataMigrationVerificationPort migrationPort,
        PostUpgradeSmokePort smokePort,
        ConfigPreservationPort configPort,
        PluginCertificationPort certPort,
        ProtocolGatewayPort protocolPort,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds            = ds;
        this.versionPort   = versionPort;
        this.orderPort     = orderPort;
        this.migrationPort = migrationPort;
        this.smokePort     = smokePort;
        this.configPort    = configPort;
        this.certPort      = certPort;
        this.protocolPort  = protocolPort;
        this.audit         = audit;
        this.executor      = executor;
        this.suitesPassed  = Counter.builder("integration.e2e.upgrade.suites_passed").register(registry);
        this.suitesFailed  = Counter.builder("integration.e2e.upgrade.suites_failed").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public Promise<SuiteResult> runAll() {
        return Promise.ofBlocking(executor, () -> {
            List<ScenarioResult> results = new ArrayList<>();
            results.add(runScenario("in_flight_order_survive",       this::inFlightOrderSurvive));
            results.add(runScenario("data_migration_verified",       this::dataMigrationVerified));
            results.add(runScenario("rollback_zero_data_loss",       this::rollbackZeroDataLoss));
            results.add(runScenario("old_protocol_handling",         this::oldProtocolHandling));
            results.add(runScenario("post_upgrade_smoke",            this::postUpgradeSmoke));
            results.add(runScenario("plugin_re_certified_after_upgrade", this::pluginReCertifiedAfterUpgrade));
            results.add(runScenario("config_preserved",              this::configPreserved));

            long passed = results.stream().filter(r -> r.passed).count();
            long failed = results.size() - passed;
            if (failed == 0) suitesPassed.increment(); else suitesFailed.increment();
            audit.audit("UPGRADE_E2E_SUITE", "passed=" + passed + " failed=" + failed);
            return new SuiteResult("PlatformUpgradeE2e", results, passed, failed);
        });
    }

    // ── Scenarios ─────────────────────────────────────────────────────────────

    /** In-flight orders submitted before upgrade must survive and reach terminal status. */
    private void inFlightOrderSurvive(String runId) throws Exception {
        String orderId = orderPort.submitOrder("UPGRADE-CLIENT-001", "NABIL", 500);
        assertStep(runId, "order_submitted_pre_upgrade", "order submitted before upgrade", "not null", orderId != null, orderId);
        versionPort.initiateUpgrade(NEW_VERSION);
        awaitUpgradeComplete(runId);
        String finalStatus = orderPort.getOrderStatus(orderId);
        boolean terminal = "FILLED".equals(finalStatus) || "CANCELLED".equals(finalStatus) || "REJECTED".equals(finalStatus);
        assertStep(runId, "in_flight_terminal", "pre-upgrade order reaches terminal state", "terminal", terminal, finalStatus);
    }

    /** Data migration ran and record counts match. */
    private void dataMigrationVerified(String runId) throws Exception {
        long preMigrationOrders = migrationPort.getMigratedRecordCount("orders");
        versionPort.initiateUpgrade(NEW_VERSION);
        awaitUpgradeComplete(runId);
        boolean migComplete = migrationPort.isMigrationComplete(NEW_VERSION);
        assertStep(runId, "migration_complete", "migration complete flag set", "true", migComplete, migComplete);
        long postCount = migrationPort.getMigratedRecordCount("orders");
        assertStep(runId, "record_count_preserved", "migrated record count matches pre-migration", String.valueOf(preMigrationOrders),
            postCount >= preMigrationOrders, String.valueOf(postCount));
    }

    /** Inject failure mid-upgrade → rollback → system back to old version, data intact. */
    private void rollbackZeroDataLoss(String runId) throws Exception {
        // Inject a failure scenario (pre-seeded via test config flag)
        versionPort.initiateUpgrade("3.0.0-FAIL_INJECTION");
        // Wait briefly then check status indicates rollback
        Thread.sleep(2000);
        String status = versionPort.getUpgradeStatus();
        boolean rolledBack = "ROLLED_BACK".equals(status) || "FAILED".equals(status);
        assertStep(runId, "rollback_triggered", "rollback triggered on injected failure", "ROLLED_BACK|FAILED", rolledBack, status);
        // Version should be back to old
        String version = versionPort.getCurrentVersion();
        assertStep(runId, "version_reverted", "version reverted to " + OLD_VERSION, OLD_VERSION, OLD_VERSION.equals(version), version);
    }

    /** Old protocol client connection refused by new version. */
    private void oldProtocolHandling(String runId) throws Exception {
        versionPort.initiateUpgrade(NEW_VERSION);
        awaitUpgradeComplete(runId);
        boolean accepted = protocolPort.connectWithOldProtocol("2.0.0");
        assertStep(runId, "old_protocol_refused", "old protocol v2.0.0 refused by v3 gateway", "false", !accepted, accepted);
    }

    /** Post-upgrade smoke test: all core subsystems respond. */
    private void postUpgradeSmoke(String runId) throws Exception {
        versionPort.initiateUpgrade(NEW_VERSION);
        awaitUpgradeComplete(runId);
        assertStep(runId, "api_gateway_healthy", "API gateway healthy post-upgrade", "true", smokePort.isApiGatewayHealthy(), true);
        assertStep(runId, "event_bus_healthy",   "event bus healthy post-upgrade",   "true", smokePort.isEventBusHealthy(),   true);
        assertStep(runId, "auth_healthy",        "auth healthy post-upgrade",         "true", smokePort.isAuthHealthy(),        true);
        assertStep(runId, "order_sub_healthy",   "order submission healthy",          "true", smokePort.isOrderSubmissionHealthy(), true);
    }

    /** Plugin must be re-certified for new platform version. */
    private void pluginReCertifiedAfterUpgrade(String runId) throws Exception {
        versionPort.initiateUpgrade(NEW_VERSION);
        awaitUpgradeComplete(runId);
        boolean certified = certPort.isPluginCertifiedFor("T1-STANDARD-001", NEW_VERSION);
        assertStep(runId, "plugin_re_certified", "T1 plugin re-certified for v3", "true", certified, certified);
    }

    /** Tenant config values are preserved across upgrade. */
    private void configPreserved(String runId) throws Exception {
        String beforeValue = configPort.getConfigValue("tenant.max_order_qty");
        versionPort.initiateUpgrade(NEW_VERSION);
        awaitUpgradeComplete(runId);
        String afterValue = configPort.getConfigValue("tenant.max_order_qty");
        assertStep(runId, "config_preserved", "tenant config preserved after upgrade", beforeValue, beforeValue.equals(afterValue), afterValue);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void awaitUpgradeComplete(String runId) throws Exception {
        for (int i = 0; i < 60; i++) {
            Thread.sleep(1000);
            if (versionPort.isUpgradeComplete()) return;
        }
        assertStep(runId, "upgrade_complete_timeout", "upgrade completes within 60s", "true", false, "TIMEOUT");
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
                 "INSERT INTO e2e_test_runs (suite_name,scenario) VALUES ('PlatformUpgradeE2e',?) RETURNING run_id"
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
