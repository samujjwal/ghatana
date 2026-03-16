package com.ghatana.appplatform.integration;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Consumer-driven service contract test suite.
 *              Validates PACT-style contracts for OMS→EMS, EMS→Settlement, Settlement→Ledger.
 *              Breaking contracts block deployment. Version matrix: old consumer works with
 *              new provider within 1 major version.
 * @doc.layer   Integration Testing (T-01)
 * @doc.pattern Port-Adapter; JDBC; Promise.ofBlocking; contract-testing
 *
 * STORY-T01-012: Implement service contract tests
 */
public class ServiceContractTestSuiteService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface ContractVerificationPort {
        /**
         * Verify that the given provider satisfies the consumer's contract.
         * Returns ContractResult with pass/fail and any violations.
         */
        ContractResult verifyContract(String consumer, String provider, String contractVersion) throws Exception;
        /** Check if a breaking contract change would block deployment. */
        boolean isBreakingChangeBlocked(String consumer, String provider, String breakingChangePayload) throws Exception;
        /** Verify old consumer (vOld) can work with new provider (vNew) – backward compat. */
        boolean verifyBackwardCompatibility(String consumer, String consumerVersion, String provider, String providerVersion) throws Exception;
    }

    public record ContractResult(boolean passed, List<String> violations, String consumer, String provider) {}

    public interface AuditPort {
        void audit(String event, String detail) throws Exception;
    }

    // ── Contract definitions (consumer→provider pairs) ────────────────────────

    private static final String[][] CONTRACTS = {
        {"oms",           "ems",        "1.0"},
        {"ems",           "settlement", "1.0"},
        {"settlement",    "ledger",     "1.0"},
        {"compliance",    "oms",        "1.0"},
        {"recon",         "ledger",     "1.0"},
    };

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final ContractVerificationPort contractPort;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter suitesPassed;
    private final Counter suitesFailed;
    private final Counter contractViolations;

    public ServiceContractTestSuiteService(
        javax.sql.DataSource ds,
        ContractVerificationPort contractPort,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds                = ds;
        this.contractPort      = contractPort;
        this.audit             = audit;
        this.executor          = executor;
        this.suitesPassed      = Counter.builder("integration.contract.suites_passed").register(registry);
        this.suitesFailed      = Counter.builder("integration.contract.suites_failed").register(registry);
        this.contractViolations = Counter.builder("integration.contract.violations").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public Promise<SuiteResult> runAll() {
        return Promise.ofBlocking(executor, () -> {
            List<ScenarioResult> results = new ArrayList<>();
            results.add(runScenario("pact_oms_ems",           this::pactOmsEms));
            results.add(runScenario("pact_ems_settlement",    this::pactEmsSettlement));
            results.add(runScenario("pact_settlement_ledger", this::pactSettlementLedger));
            results.add(runScenario("breaking_contract_block",this::breakingContractBlock));
            results.add(runScenario("version_matrix",         this::versionMatrix));
            results.add(runScenario("consumer_update_required",this::consumerUpdateRequired));
            results.add(runScenario("ci_gate",                this::ciGate));

            long passed = results.stream().filter(r -> r.passed).count();
            long failed = results.size() - passed;
            if (failed == 0) suitesPassed.increment(); else suitesFailed.increment();
            audit.audit("SERVICE_CONTRACT_SUITE", "passed=" + passed + " failed=" + failed);
            return new SuiteResult("ServiceContract", results, passed, failed);
        });
    }

    // ── Scenarios ─────────────────────────────────────────────────────────────

    private void pactOmsEms(String runId) throws Exception {
        ContractResult result = contractPort.verifyContract("oms", "ems", "1.0");
        if (!result.violations().isEmpty()) contractViolations.increment(result.violations().size());
        assertStep(runId, "pact_oms_ems", "OMS→EMS contract satisfied", "passed",
            result.passed(), result.passed() ? "PASS" : result.violations().toString());
    }

    private void pactEmsSettlement(String runId) throws Exception {
        ContractResult result = contractPort.verifyContract("ems", "settlement", "1.0");
        if (!result.violations().isEmpty()) contractViolations.increment(result.violations().size());
        assertStep(runId, "pact_ems_settlement", "EMS→Settlement contract satisfied", "passed",
            result.passed(), result.passed() ? "PASS" : result.violations().toString());
    }

    private void pactSettlementLedger(String runId) throws Exception {
        ContractResult result = contractPort.verifyContract("settlement", "ledger", "1.0");
        if (!result.violations().isEmpty()) contractViolations.increment(result.violations().size());
        assertStep(runId, "pact_settlement_ledger", "Settlement→Ledger contract satisfied", "passed",
            result.passed(), result.passed() ? "PASS" : result.violations().toString());
    }

    /** Breaking change in a provider contract should be blocked from deployment. */
    private void breakingContractBlock(String runId) throws Exception {
        // Simulate a breaking field removal from EMS response schema
        String breakingPayload = "{\"removedField\":\"settlementDate\",\"version\":\"2.0\"}";
        boolean blocked = contractPort.isBreakingChangeBlocked("oms", "ems", breakingPayload);
        assertStep(runId, "breaking_change_blocked", "breaking contract change deployment blocked", "true", blocked, blocked);
    }

    /** Old consumer v1.0 must work with new provider v1.1 (backward compat within 1 major). */
    private void versionMatrix(String runId) throws Exception {
        boolean compat = contractPort.verifyBackwardCompatibility("oms", "1.0", "ems", "1.1");
        assertStep(runId, "v1_consumer_v1_1_provider", "OMS v1.0 consumer works with EMS v1.1 provider", "true", compat, compat);
        boolean breaksMajor = contractPort.verifyBackwardCompatibility("oms", "1.0", "ems", "2.0");
        // v1 consumer vs v2 provider SHOULD be incompatible (expected false)
        assertStep(runId, "v1_consumer_v2_provider_incompatible", "OMS v1.0 incompatible with EMS v2.0", "false",
            !breaksMajor, breaksMajor);
    }

    /** When provider changes breaking field, consumer must be updated (consumer_update_required). */
    private void consumerUpdateRequired(String runId) throws Exception {
        // If EMS v2.0 removes a field, OMS v1.0 contract should fail
        ContractResult result = contractPort.verifyContract("oms", "ems", "2.0");
        boolean requiresUpdate = !result.passed();
        assertStep(runId, "consumer_update_required", "old consumer contract fails against new major provider", "true",
            requiresUpdate, requiresUpdate ? "update required" : "unexpectedly passed");
    }

    /** All registered contracts pass — CI gate simulation. */
    private void ciGate(String runId) throws Exception {
        int violations = 0;
        for (String[] contract : CONTRACTS) {
            ContractResult result = contractPort.verifyContract(contract[0], contract[1], contract[2]);
            if (!result.passed()) { violations++; contractViolations.increment(); }
        }
        assertStep(runId, "ci_gate", "all registered contracts pass CI gate", "0 violations",
            violations == 0, violations + " violations across " + CONTRACTS.length + " contracts");
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
                 "INSERT INTO e2e_test_runs (suite_name,scenario) VALUES ('ServiceContract',?) RETURNING run_id"
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
