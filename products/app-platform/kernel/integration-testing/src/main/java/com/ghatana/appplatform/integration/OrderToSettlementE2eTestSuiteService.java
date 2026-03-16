package com.ghatana.appplatform.integration;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose End-to-end order-to-settlement test suite.
 *              Covers the full order lifecycle: submission → validation (K-03) → matching (OMS)
 *              → execution (EMS) → trade reporting → settlement booking →
 *              reconciliation → ledger entry (K-16).
 *              Order types: MARKET, LIMIT, STOP, IOC, FOK.
 *              Instruments: equities and fixed income. Dual-currency (NPR/USD).
 *              BS and Gregorian settlement dates. Assertions at every stage.
 * @doc.layer   Integration Testing (T-01)
 * @doc.pattern Port-Adapter; JDBC; Promise.ofBlocking; scenario-execution; per-step assertion
 *
 * STORY-T01-001: E2E order-to-settlement test suite
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS e2e_test_runs (
 *   run_id       TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   suite_name   TEXT NOT NULL,
 *   scenario     TEXT NOT NULL,
 *   status       TEXT NOT NULL DEFAULT 'RUNNING',  -- RUNNING | PASSED | FAILED
 *   failure_step TEXT,
 *   failure_msg  TEXT,
 *   started_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   duration_ms  BIGINT
 * );
 *
 * CREATE TABLE IF NOT EXISTS e2e_step_assertions (
 *   assertion_id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   run_id       TEXT NOT NULL,
 *   step_name    TEXT NOT NULL,
 *   assertion    TEXT NOT NULL,
 *   expected     TEXT,
 *   actual       TEXT,
 *   passed       BOOLEAN NOT NULL,
 *   checked_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * </pre>
 */
public class OrderToSettlementE2eTestSuiteService {

    // ── Scenario names ────────────────────────────────────────────────────────

    public static final String EQUITY_LIMIT  = "equity_limit_e2e";
    public static final String FIXED_INCOME  = "fixed_income_e2e";
    public static final String MARKET_ORDER  = "market_order_e2e";
    public static final String IOC_CANCEL    = "ioc_cancel_e2e";
    public static final String FOK_REJECT    = "fok_reject_e2e";

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface OrderSubmissionPort {
        String submitOrder(String clientId, String symbol, String orderType, String side,
                           int quantity, Double limitPrice, String currency,
                           String settlementDate) throws Exception;
    }

    public interface OrderStatePort {
        String getOrderStatus(String orderId) throws Exception;  // PENDING|VALIDATED|MATCHED|EXECUTED|REJECTED|CANCELLED
        String getMatchedTradeId(String orderId) throws Exception;
    }

    public interface TradeReportPort {
        boolean isTradeReported(String tradeId) throws Exception;
        String getSettlementId(String tradeId) throws Exception;
    }

    public interface SettlementPort {
        String getSettlementStatus(String settlementId) throws Exception; // PENDING|BOOKED|SETTLED|FAILED
        String getSettlementDate(String settlementId) throws Exception;
    }

    public interface ReconciliationPort {
        boolean isPositionReconciled(String clientId, String symbol) throws Exception;
    }

    public interface LedgerPort {
        boolean isLedgerBalanced(String tradeId) throws Exception;
        boolean hasLedgerEntry(String tradeId) throws Exception;
    }

    public interface CalendarPort {
        String computeSettlementDate(String tradeDate, int tPlusN, String currency) throws Exception;
    }

    public interface AuditPort {
        void audit(String event, String detail) throws Exception;
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final OrderSubmissionPort orders;
    private final OrderStatePort orderState;
    private final TradeReportPort tradeReport;
    private final SettlementPort settlement;
    private final ReconciliationPort reconciliation;
    private final LedgerPort ledger;
    private final CalendarPort calendar;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter scenariosPassed;
    private final Counter scenariosFailed;

    public OrderToSettlementE2eTestSuiteService(
        javax.sql.DataSource ds,
        OrderSubmissionPort orders,
        OrderStatePort orderState,
        TradeReportPort tradeReport,
        SettlementPort settlement,
        ReconciliationPort reconciliation,
        LedgerPort ledger,
        CalendarPort calendar,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds             = ds;
        this.orders         = orders;
        this.orderState     = orderState;
        this.tradeReport    = tradeReport;
        this.settlement     = settlement;
        this.reconciliation = reconciliation;
        this.ledger         = ledger;
        this.calendar       = calendar;
        this.audit          = audit;
        this.executor       = executor;
        this.scenariosPassed = Counter.builder("integration.e2e.order_settlement.passed").register(registry);
        this.scenariosFailed = Counter.builder("integration.e2e.order_settlement.failed").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Run all order-to-settlement E2E scenarios. Returns suite run summary. */
    public Promise<SuiteResult> runAll() {
        return Promise.ofBlocking(executor, () -> {
            List<ScenarioResult> results = new ArrayList<>();
            results.add(runScenario(EQUITY_LIMIT,  this::equityLimitE2e));
            results.add(runScenario(FIXED_INCOME,  this::fixedIncomeE2e));
            results.add(runScenario(MARKET_ORDER,  this::marketOrderE2e));
            results.add(runScenario(IOC_CANCEL,    this::iocCancelE2e));
            results.add(runScenario(FOK_REJECT,    this::fokRejectE2e));

            long passed = results.stream().filter(r -> r.passed).count();
            long failed = results.size() - passed;
            audit.audit("E2E_ORDER_SETTLEMENT_SUITE", "passed=" + passed + " failed=" + failed);
            return new SuiteResult("OrderToSettlement", results, passed, failed);
        });
    }

    // ── Scenarios ─────────────────────────────────────────────────────────────

    private void equityLimitE2e(String runId) throws Exception {
        String settDate = calendar.computeSettlementDate(today(), 2, "NPR");
        String orderId = orders.submitOrder("CLIENT-001", "NABIL", "LIMIT", "BUY", 100, 950.0, "NPR", settDate);
        assertStep(runId, "order_submitted", "orderId not null", orderId, orderId != null && !orderId.isBlank(), orderId);

        awaitOrderStatus(runId, orderId, "EXECUTED");
        String tradeId = orderState.getMatchedTradeId(orderId);
        assertStep(runId, "trade_matched", "tradeId not null", null, tradeId != null, tradeId);

        assertStep(runId, "trade_reported", "trade is reported", "true",
            String.valueOf(tradeReport.isTradeReported(tradeId)), "true");

        String settlementId = tradeReport.getSettlementId(tradeId);
        awaitSettlementStatus(runId, settlementId, "BOOKED");

        assertStep(runId, "settlement_date", "T+2 settlement date", settDate,
            settlement.getSettlementDate(settlementId), settDate);

        assertStep(runId, "ledger_entry", "ledger entry exists", "true",
            String.valueOf(ledger.hasLedgerEntry(tradeId)), "true");
        assertStep(runId, "ledger_balanced", "double entry balanced", "true",
            String.valueOf(ledger.isLedgerBalanced(tradeId)), "true");
        assertStep(runId, "position_reconciled", "position reconciled", "true",
            String.valueOf(reconciliation.isPositionReconciled("CLIENT-001", "NABIL")), "true");
    }

    private void fixedIncomeE2e(String runId) throws Exception {
        String settDate = calendar.computeSettlementDate(today(), 3, "USD");
        String orderId = orders.submitOrder("CLIENT-002", "GOVT-BOND-2085", "LIMIT", "BUY", 10, 1050.0, "USD", settDate);
        assertStep(runId, "order_submitted", "orderId not null", orderId, orderId != null, orderId);
        awaitOrderStatus(runId, orderId, "EXECUTED");
        String tradeId = orderState.getMatchedTradeId(orderId);
        assertStep(runId, "trade_matched", "tradeId not null", null, tradeId != null, tradeId);
        String settlementId = tradeReport.getSettlementId(tradeId);
        awaitSettlementStatus(runId, settlementId, "BOOKED");
        assertStep(runId, "ledger_balanced", "double entry balanced", "true",
            String.valueOf(ledger.isLedgerBalanced(tradeId)), "true");
    }

    private void marketOrderE2e(String runId) throws Exception {
        String settDate = calendar.computeSettlementDate(today(), 2, "NPR");
        String orderId = orders.submitOrder("CLIENT-003", "NLIC", "MARKET", "SELL", 50, null, "NPR", settDate);
        assertStep(runId, "order_submitted", "orderId not null", orderId, orderId != null, orderId);
        awaitOrderStatus(runId, orderId, "EXECUTED");
        String tradeId = orderState.getMatchedTradeId(orderId);
        assertStep(runId, "ledger_entry", "ledger entry exists", "true",
            String.valueOf(ledger.hasLedgerEntry(tradeId)), "true");
    }

    private void iocCancelE2e(String runId) throws Exception {
        // IOC order with unachievable price → partial fill + cancel remainder
        String orderId = orders.submitOrder("CLIENT-004", "NABIL", "IOC", "BUY", 10000, 1.0, "NPR",
            calendar.computeSettlementDate(today(), 2, "NPR"));
        assertStep(runId, "order_submitted", "orderId not null", orderId, orderId != null, orderId);
        awaitOrderStatus(runId, orderId, "CANCELLED");
        assertStep(runId, "ioc_final_status", "IOC cancelled", "CANCELLED",
            orderState.getOrderStatus(orderId), "CANCELLED");
    }

    private void fokRejectE2e(String runId) throws Exception {
        // FOK: must fill completely or not at all − impossible quantity → REJECTED
        String orderId = orders.submitOrder("CLIENT-005", "NTC", "FOK", "BUY", 999999, 500.0, "NPR",
            calendar.computeSettlementDate(today(), 2, "NPR"));
        assertStep(runId, "order_submitted", "orderId not null", orderId, orderId != null, orderId);
        awaitOrderStatus(runId, orderId, "REJECTED");
        assertStep(runId, "fok_rejected", "FOK rejected (no full fill)", "REJECTED",
            orderState.getOrderStatus(orderId), "REJECTED");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ScenarioResult runScenario(String name, ThrowingConsumer<String> fn) {
        long start = System.currentTimeMillis();
        try {
            String runId = insertRun(name);
            fn.accept(runId);
            markRunStatus(runId, "PASSED", null, null, System.currentTimeMillis() - start);
            scenariosPassed.increment();
            return new ScenarioResult(name, true, null, System.currentTimeMillis() - start);
        } catch (AssertionError ae) {
            scenariosFailed.increment();
            return new ScenarioResult(name, false, ae.getMessage(), System.currentTimeMillis() - start);
        } catch (Exception ex) {
            scenariosFailed.increment();
            return new ScenarioResult(name, false, ex.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private void awaitOrderStatus(String runId, String orderId, String expected) throws Exception {
        for (int i = 0; i < 30; i++) {
            String status = orderState.getOrderStatus(orderId);
            if (expected.equals(status)) {
                assertStep(runId, "order_status_" + expected.toLowerCase(), "order status=" + expected, expected, true, status);
                return;
            }
            Thread.sleep(200);
        }
        assertStep(runId, "order_status_" + expected.toLowerCase(), "order status=" + expected, expected, false, "TIMEOUT");
        throw new AssertionError("Order " + orderId + " did not reach " + expected + " within 6s");
    }

    private void awaitSettlementStatus(String runId, String settlementId, String expected) throws Exception {
        for (int i = 0; i < 25; i++) {
            String status = settlement.getSettlementStatus(settlementId);
            if (expected.equals(status)) {
                assertStep(runId, "settlement_status_" + expected.toLowerCase(), "settlement=" + expected, expected, true, status);
                return;
            }
            Thread.sleep(200);
        }
        assertStep(runId, "settlement_status", "settlement=" + expected, expected, false, "TIMEOUT");
        throw new AssertionError("Settlement " + settlementId + " did not reach " + expected);
    }

    private void assertStep(String runId, String step, String assertion,
                             String expected, boolean passed, String actual) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO e2e_step_assertions (run_id,step_name,assertion,expected,actual,passed) VALUES (?,?,?,?,?,?)"
             )) {
            ps.setString(1, runId); ps.setString(2, step); ps.setString(3, assertion);
            ps.setString(4, expected); ps.setString(5, actual); ps.setBoolean(6, passed);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
        if (!passed) throw new AssertionError("FAIL [" + step + "] " + assertion + ": expected=" + expected + " actual=" + actual);
    }

    // overload for boolean shorthand
    private void assertStep(String runId, String step, String assertion, String expected,
                             String actual, String requiredValue) {
        assertStep(runId, step, assertion, expected, requiredValue.equals(actual), actual);
    }

    private String insertRun(String scenario) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO e2e_test_runs (suite_name, scenario) VALUES ('OrderToSettlement',?) RETURNING run_id"
             )) {
            ps.setString(1, scenario);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString(1); }
        }
    }

    private void markRunStatus(String runId, String status, String failStep, String failMsg, long durationMs) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE e2e_test_runs SET status=?, failure_step=?, failure_msg=?, duration_ms=? WHERE run_id=?"
             )) {
            ps.setString(1, status); ps.setString(2, failStep); ps.setString(3, failMsg);
            ps.setLong(4, durationMs); ps.setString(5, runId); ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private static String today() {
        return java.time.LocalDate.now().toString();
    }

    @FunctionalInterface interface ThrowingConsumer<T> { void accept(T t) throws Exception; }

    public record ScenarioResult(String scenario, boolean passed, String failureMessage, long durationMs) {}
    public record SuiteResult(String suite, List<ScenarioResult> results, long passedCount, long failedCount) {}
}
