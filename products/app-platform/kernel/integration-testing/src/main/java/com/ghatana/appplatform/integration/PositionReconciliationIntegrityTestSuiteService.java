package com.ghatana.appplatform.integration;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Position reconciliation integrity test suite.
 *              Verifies that client positions in the OMS match custodian positions.
 *              Scenarios: dividends, splits, currency revaluation, failed trades, short positions.
 *              Break detection: injects an artificial break, verifies detection.
 *              Tolerance testing: small tolerance allowed, beyond tolerance → break alert.
 * @doc.layer   Integration Testing (T-01)
 * @doc.pattern Port-Adapter; JDBC; Promise.ofBlocking; scenario-execution; assertion
 *
 * STORY-T01-006: Position reconciliation integrity test suite
 */
public class PositionReconciliationIntegrityTestSuiteService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface OmsPositionPort {
        double getOmsPosition(String clientId, String symbol) throws Exception;
    }

    public interface CustodianPositionPort {
        double getCustodianPosition(String clientId, String symbol) throws Exception;
        /** Inject a custodian position override (test-only hook). */
        void injectPositionOverride(String clientId, String symbol, double qty) throws Exception;
        void clearPositionOverride(String clientId, String symbol) throws Exception;
    }

    public interface ReconciliationEnginePort {
        /** Trigger reconciliation run. Returns reconciliationId. */
        String runReconciliation(String clientId, String symbol) throws Exception;
        String getStatus(String reconId) throws Exception; // MATCHED | BREAK | TOLERANCE_BREAK
        double getBreakAmount(String reconId) throws Exception;
        boolean hasBreakAlert(String clientId, String symbol) throws Exception;
    }

    public interface CorporateActionPort {
        void applyDividend(String clientId, String symbol, double cashDividendPerShare) throws Exception;
        void applySplit(String clientId, String symbol, int splitRatio) throws Exception;
    }

    public interface CurrencyRevaluationPort {
        void revalue(String clientId, String symbol, double newRate) throws Exception;
        double getRevaluedPosition(String clientId, String symbol) throws Exception;
    }

    public interface AuditPort {
        void audit(String event, String detail) throws Exception;
    }

    // ── Tolerance constant ────────────────────────────────────────────────────

    private static final double TOLERANCE = 0.01; // 1% tolerance allowed

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final OmsPositionPort omsPosition;
    private final CustodianPositionPort custodianPosition;
    private final ReconciliationEnginePort reconciliation;
    private final CorporateActionPort corporateActions;
    private final CurrencyRevaluationPort revaluation;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter suitesPassed;
    private final Counter suitesFailed;
    private final Counter breaksDetected;

    public PositionReconciliationIntegrityTestSuiteService(
        javax.sql.DataSource ds,
        OmsPositionPort omsPosition,
        CustodianPositionPort custodianPosition,
        ReconciliationEnginePort reconciliation,
        CorporateActionPort corporateActions,
        CurrencyRevaluationPort revaluation,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds               = ds;
        this.omsPosition      = omsPosition;
        this.custodianPosition = custodianPosition;
        this.reconciliation   = reconciliation;
        this.corporateActions = corporateActions;
        this.revaluation      = revaluation;
        this.audit            = audit;
        this.executor         = executor;
        this.suitesPassed     = Counter.builder("integration.recon.suites_passed").register(registry);
        this.suitesFailed     = Counter.builder("integration.recon.suites_failed").register(registry);
        this.breaksDetected   = Counter.builder("integration.recon.breaks_detected").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public Promise<SuiteResult> runAll() {
        return Promise.ofBlocking(executor, () -> {
            List<ScenarioResult> results = new ArrayList<>();
            results.add(runScenario("dividend_position_check",   this::dividendPositionCheck));
            results.add(runScenario("split_position_check",      this::splitPositionCheck));
            results.add(runScenario("artificial_break_detect",   this::artificialBreakDetect));
            results.add(runScenario("tolerance_boundary",        this::toleranceBoundary));
            results.add(runScenario("currency_revaluation",      this::currencyRevaluationCheck));
            results.add(runScenario("short_position",            this::shortPositionCheck));
            results.add(runScenario("failed_trade_exclusion",    this::failedTradeExclusion));
            results.add(runScenario("break_alert",               this::breakAlertFired));

            long passed = results.stream().filter(r -> r.passed).count();
            long failed = results.size() - passed;
            if (failed == 0) suitesPassed.increment(); else suitesFailed.increment();
            audit.audit("RECON_INTEGRITY_SUITE", "passed=" + passed + " failed=" + failed);
            return new SuiteResult("PositionReconciliation", results, passed, failed);
        });
    }

    // ── Scenarios ─────────────────────────────────────────────────────────────

    private void dividendPositionCheck(String runId) throws Exception {
        String clientId = "RECON-CLIENT-001"; String symbol = "NABIL";
        corporateActions.applyDividend(clientId, symbol, 15.0); // NPR 15/share
        Thread.sleep(200);
        String reconId = reconciliation.runReconciliation(clientId, symbol);
        String status = reconciliation.getStatus(reconId);
        assertStep(runId, "dividend_recon_status", "position matched after dividend", "MATCHED",
            "MATCHED".equals(status), status);
    }

    private void splitPositionCheck(String runId) throws Exception {
        String clientId = "RECON-CLIENT-002"; String symbol = "NTC";
        double beforeSplit = omsPosition.getOmsPosition(clientId, symbol);
        corporateActions.applySplit(clientId, symbol, 2); // 2-for-1 split
        Thread.sleep(200);
        double afterSplit = omsPosition.getOmsPosition(clientId, symbol);
        assertStep(runId, "oms_split_adjusted", "OMS position doubled after 2:1 split", "~" + (beforeSplit * 2),
            Math.abs(afterSplit - beforeSplit * 2) < TOLERANCE, String.valueOf(afterSplit));
        String reconId = reconciliation.runReconciliation(clientId, symbol);
        assertStep(runId, "split_recon", "reconciliation matched after split", "MATCHED",
            "MATCHED".equals(reconciliation.getStatus(reconId)), reconciliation.getStatus(reconId));
    }

    private void artificialBreakDetect(String runId) throws Exception {
        String clientId = "RECON-CLIENT-003"; String symbol = "NLIC";
        double oms = omsPosition.getOmsPosition(clientId, symbol);
        // Inject break: custodian shows significantly different position
        custodianPosition.injectPositionOverride(clientId, symbol, oms + 500);
        try {
            String reconId = reconciliation.runReconciliation(clientId, symbol);
            String status = reconciliation.getStatus(reconId);
            boolean isBreak = "BREAK".equals(status) || "TOLERANCE_BREAK".equals(status);
            assertStep(runId, "break_detected", "break detected after artificial injection", "BREAK or TOLERANCE_BREAK",
                isBreak, status);
            double breakAmt = reconciliation.getBreakAmount(reconId);
            assertStep(runId, "break_amount", "break amount ≈ 500", "≈500",
                Math.abs(breakAmt - 500) < 1.0, String.valueOf(breakAmt));
            breaksDetected.increment();
        } finally {
            custodianPosition.clearPositionOverride(clientId, symbol);
        }
    }

    private void toleranceBoundary(String runId) throws Exception {
        String clientId = "RECON-CLIENT-004"; String symbol = "NIMB";
        double oms = omsPosition.getOmsPosition(clientId, symbol);

        // Within tolerance (0.5%) → MATCHED
        custodianPosition.injectPositionOverride(clientId, symbol, oms * 1.005);
        try {
            String reconId = reconciliation.runReconciliation(clientId, symbol);
            assertStep(runId, "within_tolerance", "small diff within tolerance → MATCHED", "MATCHED",
                "MATCHED".equals(reconciliation.getStatus(reconId)), reconciliation.getStatus(reconId));
        } finally { custodianPosition.clearPositionOverride(clientId, symbol); }

        // Beyond tolerance (2%) → TOLERANCE_BREAK
        custodianPosition.injectPositionOverride(clientId, symbol, oms * 1.02);
        try {
            String reconId2 = reconciliation.runReconciliation(clientId, symbol);
            boolean isBreak = !("MATCHED".equals(reconciliation.getStatus(reconId2)));
            assertStep(runId, "beyond_tolerance", "2% diff exceeds tolerance → break", "BREAK",
                isBreak, reconciliation.getStatus(reconId2));
        } finally { custodianPosition.clearPositionOverride(clientId, symbol); }
    }

    private void currencyRevaluationCheck(String runId) throws Exception {
        String clientId = "RECON-CLIENT-005"; String symbol = "GOVT-BOND-USD";
        revaluation.revalue(clientId, symbol, 133.5); // new NPR/USD rate
        Thread.sleep(200);
        double revalued = revaluation.getRevaluedPosition(clientId, symbol);
        assertStep(runId, "position_revalued", "revalued position > 0", "> 0",
            revalued > 0, String.valueOf(revalued));
        String reconId = reconciliation.runReconciliation(clientId, symbol);
        assertStep(runId, "currency_recon", "reconciliation after revaluation matches", "MATCHED",
            "MATCHED".equals(reconciliation.getStatus(reconId)), reconciliation.getStatus(reconId));
    }

    private void shortPositionCheck(String runId) throws Exception {
        String clientId = "RECON-CLIENT-006"; String symbol = "NABIL";
        // Short position is represented as negative; custodian must agree
        double oms = omsPosition.getOmsPosition(clientId, symbol);
        assertStep(runId, "short_position_negative", "OMS short position is negative", "< 0",
            oms < 0, String.valueOf(oms));
        String reconId = reconciliation.runReconciliation(clientId, symbol);
        assertStep(runId, "short_recon", "short position reconciled", "MATCHED",
            "MATCHED".equals(reconciliation.getStatus(reconId)), reconciliation.getStatus(reconId));
    }

    private void failedTradeExclusion(String runId) throws Exception {
        // RECON-CLIENT-007 has a failed trade that should not appear in reconciled position
        String clientId = "RECON-CLIENT-007"; String symbol = "NTC";
        String reconId = reconciliation.runReconciliation(clientId, symbol);
        assertStep(runId, "failed_trade_excluded", "failed trade excluded from recon", "MATCHED",
            "MATCHED".equals(reconciliation.getStatus(reconId)), reconciliation.getStatus(reconId));
    }

    private void breakAlertFired(String runId) throws Exception {
        String clientId = "RECON-CLIENT-008"; String symbol = "NIMB";
        double oms = omsPosition.getOmsPosition(clientId, symbol);
        custodianPosition.injectPositionOverride(clientId, symbol, oms * 1.05); // 5% break → alert
        try {
            reconciliation.runReconciliation(clientId, symbol);
            Thread.sleep(200); // allow alert propagation
            boolean alert = reconciliation.hasBreakAlert(clientId, symbol);
            assertStep(runId, "break_alert", "break alert fired for 5% deviation", "true", alert, true);
        } finally { custodianPosition.clearPositionOverride(clientId, symbol); }
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
                 "INSERT INTO e2e_test_runs (suite_name,scenario) VALUES ('PositionReconciliation',?) RETURNING run_id"
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
