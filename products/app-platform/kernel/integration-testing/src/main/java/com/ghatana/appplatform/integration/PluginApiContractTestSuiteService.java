package com.ghatana.appplatform.integration;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Plugin SDK contract test suite (K-12).
 *              Validates: plugins must implement required lifecycle interfaces per tier;
 *              tier violation denied certification; T1 plugin satisfies required interfaces;
 *              SDK verification tool checks all interfaces; certification gate is enforced;
 *              clear error messages on violations.
 * @doc.layer   Integration Testing (T-01)
 * @doc.pattern Port-Adapter; JDBC; Promise.ofBlocking; contract-testing
 *
 * STORY-T01-014: Implement plugin API contract tests
 */
public class PluginApiContractTestSuiteService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface PluginSdkVerificationPort {
        /**
         * Verify a plugin binary satisfies the SDK contract for its tier.
         * Returns VerificationResult with pass/fail and violation messages.
         */
        VerificationResult verifySdkContract(String pluginId, String tier, String pluginBinaryRef) throws Exception;
        /** Return true if the SDK tool detected missing required interfaces. */
        boolean hasMissingInterfaces(String pluginId) throws Exception;
        /** Return true if plugin attempts to use interfaces beyond its tier permission. */
        boolean hasTierViolation(String pluginId) throws Exception;
        /** Get error message for SDK violation. */
        String getViolationMessage(String pluginId) throws Exception;
    }

    public interface PluginCertificationPort {
        boolean isCertified(String pluginId, String tier) throws Exception;
        boolean tryCertify(String pluginId, String tier) throws Exception;
    }

    public interface AuditPort {
        void audit(String event, String detail) throws Exception;
    }

    public record VerificationResult(boolean passed, List<String> violations) {}

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final PluginSdkVerificationPort sdkVerification;
    private final PluginCertificationPort certificationPort;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter suitesPassed;
    private final Counter suitesFailed;

    public PluginApiContractTestSuiteService(
        javax.sql.DataSource ds,
        PluginSdkVerificationPort sdkVerification,
        PluginCertificationPort certificationPort,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds                 = ds;
        this.sdkVerification    = sdkVerification;
        this.certificationPort  = certificationPort;
        this.audit              = audit;
        this.executor           = executor;
        this.suitesPassed       = Counter.builder("integration.plugin_contract.suites_passed").register(registry);
        this.suitesFailed       = Counter.builder("integration.plugin_contract.suites_failed").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public Promise<SuiteResult> runAll() {
        return Promise.ofBlocking(executor, () -> {
            List<ScenarioResult> results = new ArrayList<>();
            results.add(runScenario("missing_lifecycle_interface", this::missingLifecycleInterface));
            results.add(runScenario("tier_violation",              this::tierViolation));
            results.add(runScenario("valid_t1_satisfy",            this::validT1Satisfy));
            results.add(runScenario("sdk_verification_tool",       this::sdkVerificationTool));
            results.add(runScenario("all_interfaces_checked",      this::allInterfacesChecked));
            results.add(runScenario("certification_gate",          this::certificationGate));
            results.add(runScenario("error_messages_clear",        this::errorMessagesClear));

            long passed = results.stream().filter(r -> r.passed).count();
            long failed = results.size() - passed;
            if (failed == 0) suitesPassed.increment(); else suitesFailed.increment();
            audit.audit("PLUGIN_CONTRACT_SUITE", "passed=" + passed + " failed=" + failed);
            return new SuiteResult("PluginApiContract", results, passed, failed);
        });
    }

    // ── Scenarios ─────────────────────────────────────────────────────────────

    /** Plugin missing required PluginLifecycle interface → certification denied. */
    private void missingLifecycleInterface(String runId) throws Exception {
        VerificationResult result = sdkVerification.verifySdkContract(
            "T1-MISSING-LIFECYCLE", "T1", "plugin://T1-MISSING-LIFECYCLE.jar");
        assertStep(runId, "missing_lifecycle_denied", "plugin missing lifecycle interface fails verification", "false",
            !result.passed(), result.passed() ? "passed (wrong!)" : "denied");
        boolean hasMissing = sdkVerification.hasMissingInterfaces("T1-MISSING-LIFECYCLE");
        assertStep(runId, "missing_interface_detected", "SDK tool detects missing interface", "true", hasMissing, hasMissing);
    }

    /** T1 plugin attempting to call T3-only API (e.g., submitChildOrder) → certification denied. */
    private void tierViolation(String runId) throws Exception {
        VerificationResult result = sdkVerification.verifySdkContract(
            "T1-EXCEEDS-TIER", "T1", "plugin://T1-EXCEEDS-TIER.jar");
        // T1-EXCEEDS-TIER plugin references T3-only submitChildOrder
        assertStep(runId, "tier_violation_denied", "T1 plugin using T3 API fails certification", "false",
            !result.passed(), result.passed() ? "passed (wrong!)" : "denied");
        boolean hasTierViolation = sdkVerification.hasTierViolation("T1-EXCEEDS-TIER");
        assertStep(runId, "tier_violation_detected", "SDK tool detects tier violation", "true",
            hasTierViolation, hasTierViolation);
    }

    /** Valid T1 plugin implementing all required interfaces → certified. */
    private void validT1Satisfy(String runId) throws Exception {
        VerificationResult result = sdkVerification.verifySdkContract(
            "T1-VALID-001", "T1", "plugin://T1-VALID-001.jar");
        assertStep(runId, "valid_t1_verified", "valid T1 plugin passes SDK verification", "true",
            result.passed(), result.passed() ? "passed" : result.violations().toString());
        boolean certified = certificationPort.tryCertify("T1-VALID-001", "T1");
        assertStep(runId, "valid_t1_certified", "valid T1 plugin obtains certification", "true", certified, certified);
    }

    /** SDK verification tool is invoked and returns structured result. */
    private void sdkVerificationTool(String runId) throws Exception {
        VerificationResult result = sdkVerification.verifySdkContract(
            "T2-VALID-001", "T2", "plugin://T2-VALID-001.jar");
        assertStep(runId, "sdk_tool_runs", "SDK verification tool returns result", "not null",
            result != null, result == null ? "null" : "result received");
        // Violations list should be empty for a valid plugin
        assertStep(runId, "sdk_no_violations", "no violations for valid T2 plugin", "0",
            result != null && result.violations().isEmpty(),
            result == null ? "null" : result.violations().size() + " violations");
    }

    /** SDK tool checks ALL required interfaces (not just presence, but signature). */
    private void allInterfacesChecked(String runId) throws Exception {
        // T3-WRONG-SIGNATURE implements interface but with wrong method signature
        VerificationResult result = sdkVerification.verifySdkContract(
            "T3-WRONG-SIG", "T3", "plugin://T3-WRONG-SIG.jar");
        assertStep(runId, "wrong_sig_detected", "wrong method signature detected by SDK tool", "false",
            !result.passed(), result.passed() ? "passed (wrong!)" : "denied");
    }

    /** Certification is denied for uncertified plugins. */
    private void certificationGate(String runId) throws Exception {
        // T1-INVALID should never be certified
        boolean certified = certificationPort.isCertified("T1-INVALID-001", "T1");
        assertStep(runId, "uncertified_not_active", "uncertified plugin is not certified", "false", !certified, certified);
    }

    /** Error messages for violations are specific and actionable. */
    private void errorMessagesClear(String runId) throws Exception {
        sdkVerification.verifySdkContract("T1-MISSING-LIFECYCLE", "T1", "plugin://T1-MISSING-LIFECYCLE.jar");
        String message = sdkVerification.getViolationMessage("T1-MISSING-LIFECYCLE");
        boolean hasDetail = message != null && !message.isBlank() && message.length() > 10;
        assertStep(runId, "error_message_clear", "violation message is specific and non-empty", "non-empty",
            hasDetail, message == null ? "null" : message.substring(0, Math.min(50, message.length())));
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
                 "INSERT INTO e2e_test_runs (suite_name,scenario) VALUES ('PluginApiContract',?) RETURNING run_id"
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
